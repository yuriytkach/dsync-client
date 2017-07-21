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

package com.yet.dsync.service;

import com.yet.dsync.dao.ConfigDao;
import com.yet.dsync.exception.DSyncClientException;
import com.yet.dsync.util.Config;
import com.yet.dsync.util.PathUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@RequiredArgsConstructor
public class LocalFolderService {

    private static final Logger LOG = LogManager.getLogger(LocalFolderService.class);

    private final ConfigDao configDao;
    private final GlobalOperationsTracker globalOperationsTracker;

    private final Lock syncLock = new ReentrantLock(true);

    private File localDir;

    @SuppressWarnings("PMD.SystemPrintln")
    public void setupLocalFolder() {
        System.out.print("Input local folder to use for Dropbox: ");

        try {
            final String folder = new BufferedReader(new InputStreamReader(System.in, Charset.defaultCharset()))
                    .readLine();

            if (StringUtils.isBlank(folder)) {
                throw new DSyncClientException("No local folder specified");
            }

            final File dir = new File(folder);
            if (dir.exists()) {
                System.out.print("WARNING! The folder exists. All its contents will be deleted. Continue? [y/n] ");
                final char answer = (char) new InputStreamReader(System.in, Charset.defaultCharset()).read();
                System.out.println();
                if (answer == 'y') {
                    FileUtils.deleteDirectory(dir);
                } else {
                    throw new DSyncClientException("Cancelled");
                }
            }
            if (dir.mkdirs()) {
                System.out.println("Created directory " + dir.getAbsolutePath());

                configDao.write(Config.LOCAL_DIR, dir.getAbsolutePath());
            } else {
                System.out.println("FAILED to create directory");
            }
        } catch (final IOException ex) {
            throw new DSyncClientException(ex);
        }
    }

    public void checkOrSetupLocalDir() {
        String localDirPath = configDao.read(Config.LOCAL_DIR);
        if (StringUtils.isBlank(localDirPath)) {
            setupLocalFolder();
            localDirPath = configDao.read(Config.LOCAL_DIR);
        }

        localDir = new File(localDirPath);
        if (!localDir.exists()) {
            LOG.info("Local folder does not exist: {}", () -> localDir.getAbsolutePath());
            setupLocalFolder();
            localDirPath = configDao.read(Config.LOCAL_DIR);
            localDir = new File(localDirPath);
        }

        LOG.debug("Local folder: {}", () -> localDir.getAbsolutePath());
    }

    public void createFolder(final String path) {
        final File folder = new File(localDir.getAbsolutePath() + path);

        if (!folder.exists() && !folder.mkdirs()) {
            throw new DSyncClientException("Failed in creating directories at " + folder.getAbsolutePath());
        }
    }

    public void deleteFileOrFolder(final String path) {
        final File file = buildFileObject(path);
        syncLock.lock();
        try {
            if (file.exists()) {
                if (file.isDirectory()) {
                    try {
                        FileUtils.deleteDirectory(file);
                    } catch (final Exception ex) {
                        throw new DSyncClientException("Failed to delete directory: " + file.getAbsolutePath(), ex);
                    }
                } else {
                    if (!file.delete()) {
                        throw new DSyncClientException("Failed to delete file: " + file.getAbsolutePath());
                    }
                }
            }
        } finally {
            syncLock.unlock();
        }
    }

    public File buildFileObject(final String path) {
        return new File(localDir.getAbsolutePath() + path);
    }

    public String extractDropboxPath(final Path path) {
        return PathUtil.extractDropboxPath(localDir, path);
    }

    public Runnable createFolderWatchingThread(final LocalFolderChange changeListener) {
        return new LocalFolderWatching(localDir.getAbsolutePath(), changeListener, globalOperationsTracker);
    }

}
