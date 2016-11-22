/*
 * Copyright (C) 2016  Yuriy Tkach
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

import java.io.File;
import java.sql.Connection;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang.StringUtils;

import com.yet.dsync.dao.ConfigDao;
import com.yet.dsync.dao.DatabaseInit;
import com.yet.dsync.dao.MetadataDao;
import com.yet.dsync.dto.ChangeType;
import com.yet.dsync.dto.UserData;
import com.yet.dsync.service.DownloadService;
import com.yet.dsync.service.DropboxService;
import com.yet.dsync.service.LocalFolderService;
import com.yet.dsync.util.Config;

public class DSyncClient {

    public static void main(String[] args) throws ParseException {
        Options options = createCommandLineOptions();
        CommandLineParser parser = new BasicParser();
        CommandLine cmd = parser.parse(options, args);
        
        if (cmd.hasOption('h')) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(
                    "java -cp \"lib/*\" " + DSyncClient.class.getCanonicalName()
                            + " [options]", options);
            
        } else {
            boolean reset = cmd.hasOption('r');
            String dbPath = cmd.getOptionValue("db", getDefaultDbPath());
            
            new DSyncClient().start(dbPath, reset);
        }
    }
    
    private static String getDefaultDbPath() {
        String configDir = Config.getProgramConfigurationDirectory();
        File db = new File(configDir + File.separator + Config.DB_NAME);
        return db.getAbsolutePath();
    }
    
    private static Options createCommandLineOptions() {
        Options options = new Options();
        options.addOption("db", "database", true, "Full path to database");
        options.addOption("r", "reset", false,
                "Remove the database and start configuration procedure");
        options.addOption("h", "help", false, "Display this help");
        return options;
    }

    private DropboxService dropboxService;
    private LocalFolderService localFolderService;
    
    private DownloadService downloadService;
    
    private ConfigDao configDao;
    private MetadataDao metadataDao;

    private void start(String dbPath, boolean reset) {
        initDao(dbPath, reset);
        initServices();
        
        startServices();            

        greeting();
        
        String initialSyncDone = configDao.read(Config.INITIAL_SYNC);
        if ( ! ConfigDao.YES.equals(initialSyncDone) ) {
            initialSync();
        }
        
        downloadService.downloadAllNotLoaded();
        
        CompletableFuture<Void> pollFuture = runPolling();
        
        pollFuture.join();
    }
    
    private void initDao(String dbPath, boolean reset) {
        DatabaseInit dbInit = new DatabaseInit();
        
        File dbPathFile = new File(dbPath).getAbsoluteFile();
        
        if (reset) {
            dbPathFile.delete();
        }
        
        File dbDir = dbPathFile.getParentFile();
        if (!dbDir.exists() ) {
            dbDir.mkdirs();
        }
        
        boolean firstRun = reset || ! dbPathFile.exists();
        
        System.out.println("Database at " + dbPathFile.getAbsolutePath());
        
        String dbName = dbPathFile.getName();
        
        Connection connection = dbInit.createConnection(dbDir.getAbsolutePath(), dbName);
        if (firstRun) {
            dbInit.createTables(connection);
        }
        
        configDao = new ConfigDao(connection);
        metadataDao = new MetadataDao(connection);
    }
    
    private void initServices() {
        localFolderService = new LocalFolderService(configDao);
        dropboxService = new DropboxService(configDao);
        
        downloadService = new DownloadService(metadataDao, localFolderService, dropboxService);
    }

    private void startServices() {
        dropboxService.createConfig();
        
        String authCode = configDao.read(Config.ACCES_TOKEN);
        if (StringUtils.isBlank(authCode)) {
            dropboxService.authenticate();
        }
        dropboxService.createClient();

        localFolderService.checkOrSetupLocalDir();
    }

    private void greeting() {
        UserData userData = dropboxService.retrieveUserData();

        System.out.println("Hello, " + userData.getUserName());
        System.out.println("Used storage " + userData.getUsedBytesDisplay() + " of " + userData.getAvailBytesDisplay());
        
        System.out.println();
        System.out.println("Client is running. Use Ctrl+c to kill it.");

        printWarning();
    }
    
    private void printWarning() {
        System.out.println();
        System.out.println(
                "(Note. The client does not do much. For now it only logs events that happen in Dropbox folder on server)");
        System.out.println();
    }
    
    private void initialSync() {
        Runnable syncThread = dropboxService.createInitialSyncThread(fileData -> {
            // TODO: Needs optimization for batch processing
            System.out.println(fileData);
            metadataDao.write(fileData);
        });
        syncThread.run();
        
        configDao.write(Config.INITIAL_SYNC, ConfigDao.YES);
    }

    private CompletableFuture<Void> runPolling() {
        Runnable pollThread = dropboxService.createPollingThread(fd -> {
            System.out.println(fd);
            if (ChangeType.DELETE.equals(fd.getChangeType()) ) {
                localFolderService.deleteFileOrFolder(fd.getPathDisplay());
                metadataDao.deleteByPath(fd.getPathDisplay());
                System.out.println("DELETED " + fd.getPathDisplay());
            } else {
                downloadService.scheduleDownload(fd);
            }
        });
        return CompletableFuture.runAsync(pollThread);
    }

}
