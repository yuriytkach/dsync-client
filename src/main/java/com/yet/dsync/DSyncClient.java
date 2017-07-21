/*
 * Copyright (c) 2017 Yuriy Tkach
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package com.yet.dsync;

import com.yet.dsync.dao.ConfigDao;
import com.yet.dsync.dao.DatabaseInit;
import com.yet.dsync.dao.MetadataDao;
import com.yet.dsync.dto.UserData;
import com.yet.dsync.exception.DSyncClientException;
import com.yet.dsync.service.DownloadService;
import com.yet.dsync.service.DropboxService;
import com.yet.dsync.service.GlobalOperationsTracker;
import com.yet.dsync.service.LocalFolderService;
import com.yet.dsync.service.UploadService;
import com.yet.dsync.util.Config;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.sql.Connection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DSyncClient {

    private static final Logger LOG = LogManager.getLogger(DSyncClient.class);

    private DropboxService dropboxService;
    private LocalFolderService localFolderService;
    private DownloadService downloadService;
    private UploadService uploadService;
    private ConfigDao configDao;
    private MetadataDao metadataDao;

    public static void main(final String[] args) throws ParseException {
        final Options options = createCommandLineOptions();
        final CommandLineParser parser = new DefaultParser();
        final CommandLine cmd = parser.parse(options, args);

        if (cmd.hasOption('h')) {
            final HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(
                    "java -cp \"lib/*\" " + DSyncClient.class.getCanonicalName()
                            + " [options]", options);

        } else {
            final boolean reset = cmd.hasOption('r');
            final String dbPath = cmd.getOptionValue("db", getDefaultDbPath());

            new DSyncClient().start(dbPath, reset);
        }
    }

    private static String getDefaultDbPath() {
        final String configDir = Config.getProgramConfigurationDirectory();
        final File db = new File(configDir + File.separator + Config.DB_NAME);
        return db.getAbsolutePath();
    }

    private static Options createCommandLineOptions() {
        final Options options = new Options();
        options.addOption("db", "database", true, "Full path to database");
        options.addOption("r", "reset", false,
                "Remove the database and start configuration procedure");
        options.addOption("h", "help", false, "Display this help");
        return options;
    }

    private void start(final String dbPath, final boolean reset) {
        initDao(dbPath, reset);
        initServices();

        startServices();

        greeting();

        if (!isInitialSyncDone()) {
            initialSync();
        }

        downloadService.downloadAllNotLoaded();

        final ExecutorService pool = Executors.newFixedThreadPool(2);

        final CompletableFuture<Void> pollFuture = runPolling(pool);

        final CompletableFuture<Void> watchFuture = runWatching(pool);

        CompletableFuture.allOf(pollFuture, watchFuture).join();
    }

    private boolean isInitialSyncDone() {
        final String initialSyncDone = configDao.read(Config.INITIAL_SYNC);
        final String cursor = configDao.read(Config.CURSOR);
        return StringUtils.isNotBlank(cursor) && ConfigDao.YES.equals(initialSyncDone);
    }

    private void initDao(final String dbPath, final boolean reset) {
        final File dbPathFile = new File(dbPath).getAbsoluteFile();

        if (reset) {
            LOG.info("Resetting configuration");
            final boolean fileDeleteResult = dbPathFile.delete();
            if (!fileDeleteResult) {
                throw new DSyncClientException("Failed to delete previous configuration");
            }
        }

        final File dbDir = dbPathFile.getParentFile();
        if (!dbDir.exists() && !dbDir.mkdirs()) {
            throw new DSyncClientException("Failed to create directories for new configuration file");
        }

        final boolean firstRun = reset || !dbPathFile.exists();

        LOG.debug("Using database at {}", () -> dbPathFile.getAbsolutePath());

        final String dbName = dbPathFile.getName();

        final DatabaseInit dbInit = new DatabaseInit();

        @SuppressWarnings("PMD.CloseResource")
        final Connection connection = dbInit.createConnection(dbDir.getAbsolutePath(), dbName);
        if (firstRun) {
            LOG.debug("Creating database tables");
            dbInit.createTables(connection);
            LOG.debug("Tables created successfully");
        }

        configDao = new ConfigDao(connection);
        metadataDao = new MetadataDao(connection);
    }

    private void initServices() {
        final GlobalOperationsTracker globalOperationsTracker = new GlobalOperationsTracker();

        localFolderService = new LocalFolderService(configDao, globalOperationsTracker);
        dropboxService = new DropboxService(configDao);

        downloadService = new DownloadService(globalOperationsTracker, metadataDao, localFolderService, dropboxService);
        uploadService = new UploadService(globalOperationsTracker, metadataDao, localFolderService, dropboxService);
    }

    private void startServices() {
        dropboxService.createConfig();

        final String authCode = configDao.read(Config.ACCESS_TOKEN);
        if (StringUtils.isBlank(authCode)) {
            dropboxService.authenticate();
        }
        dropboxService.createClient();

        localFolderService.checkOrSetupLocalDir();
    }

    private void greeting() {
        final UserData userData = dropboxService.retrieveUserData();

        LOG.info("Hello, {}", () -> userData.getUserName());
        LOG.info("Used storage {} of {}", userData.getUsedBytesDisplay(), userData.getAvailBytesDisplay());

        LOG.info("Client is running. Use Ctrl+c to kill it.");
    }

    private void initialSync() {
        final Runnable syncThread = dropboxService.createInitialSyncThread(fileDataSet -> {
            fileDataSet.forEach(fd -> LOG.info("DROPBOX {}", () -> fd.toString()));
            LOG.debug("Writing DB: {} records", () -> fileDataSet.size());
            metadataDao.write(fileDataSet);
            LOG.debug("Writing DB done");
        });
        syncThread.run();

        configDao.write(Config.INITIAL_SYNC, ConfigDao.YES);
    }

    private CompletableFuture<Void> runPolling(final ExecutorService pool) {
        final Runnable pollThread = dropboxService.createPollingThread(fileDataSet -> {
            fileDataSet.forEach(dropboxFileData -> {
                LOG.info("DROPBOX {}", () -> dropboxFileData.toString());
                downloadService.scheduleProcessing(dropboxFileData);
            });
        });
        return CompletableFuture.runAsync(pollThread, pool);
    }

    private CompletableFuture<Void> runWatching(final ExecutorService pool) {
        final Runnable watchThread = localFolderService.createFolderWatchingThread(localFolderData -> {
            LOG.info(localFolderData);
            uploadService.scheduleProcessing(localFolderData);
        });
        return CompletableFuture.runAsync(watchThread, pool);
    }

}
