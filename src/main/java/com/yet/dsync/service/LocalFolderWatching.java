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

package com.yet.dsync.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.yet.dsync.dto.LocalFolderChangeType;
import com.yet.dsync.dto.LocalFolderData;
import com.yet.dsync.exception.DSyncClientException;
import com.yet.dsync.util.PathUtil;
import com.yet.dsync.util.WatcherRegisterConsumer;

public class LocalFolderWatching implements Runnable {

    private static final int WAIT_THREAD_COUNT = 10;

    private static final int LOCAL_CHANGE_WAIT_TIME = 1000;

    private static final int FILE_WAIT_TIME_SEC = 3;

    private static final Logger LOG = LogManager
            .getLogger(LocalFolderWatching.class);

    private final String localDir;
    private final LocalFolderChange changeListener;

    private final WatchService watchService;

    private final BlockingQueue<LocalFolderData> localPatheChanges = new LinkedBlockingDeque<>(
            10);
    private Map<WatchKey, Path> keys;
    private WatcherRegisterConsumer watcherConsumer;

    private ConcurrentMap<Path, FileChangeData> filesModifiedMap = new ConcurrentHashMap<>();

    private GlobalOperationsTracker globalOperationsTracker;

    public LocalFolderWatching(final String localDir,
            final LocalFolderChange changeListener,
            final GlobalOperationsTracker globalOperationsTracker) {
        this.localDir = localDir;
        this.changeListener = changeListener;
        this.globalOperationsTracker = globalOperationsTracker;
        
        try {
            this.watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            throw new DSyncClientException(e);
        }

        keys = new HashMap<>();

        watcherConsumer = new WatcherRegisterConsumer(watchService, key -> {
            Path path = (Path) key.watchable();
            keys.put(key, path);
        });

        ThreadFactory namedThreadFactory = new ThreadFactoryBuilder()
                .setNameFormat("local-change-wait-%d").build();

        final ExecutorService executorService = Executors
                .newFixedThreadPool(WAIT_THREAD_COUNT, namedThreadFactory);
        for (int i = 0; i < WAIT_THREAD_COUNT; i++) {
            executorService.execute(new ChangeWaitThread());
        }

        namedThreadFactory = new ThreadFactoryBuilder()
                .setNameFormat("local-file-wait-%d").build();

        final ScheduledExecutorService executorServiceForFiles = Executors
                .newSingleThreadScheduledExecutor(namedThreadFactory);
        executorServiceForFiles.scheduleAtFixedRate(new FileWaitThread(),
                FILE_WAIT_TIME_SEC, FILE_WAIT_TIME_SEC, TimeUnit.SECONDS);
    }

    @Override
    public void run() {
        Thread.currentThread().setName("local-poll");
        LOG.info("Started local folder watching");

        Path localDirPath = Paths.get(localDir);

        try {

            watcherConsumer.accept(localDirPath);

            while (!Thread.interrupted()) {
                final WatchKey key;
                try {
                    key = watchService.take();
                } catch (InterruptedException e) {
                    LOG.error("Interrupted", e);
                    continue;
                }

                final Path dir = keys.get(key);
                if (dir == null) {
                    LOG.error("WatchKey {} not recognized!", () -> key);
                    continue;
                }

                key.pollEvents()
                        .stream().filter(
                                e -> (e.kind() != StandardWatchEventKinds.OVERFLOW))
                        .forEach(e -> {
                            @SuppressWarnings("unchecked")
                            WatchEvent<Path> event = (WatchEvent<Path>) e;

                            Kind<Path> watchEventKind = event.kind();

                            Path path = dir.resolve(event.context());
                            
                            String dropboxPathLower = PathUtil.extractDropboxPath(localDir, path).toLowerCase();
                            if (globalOperationsTracker.isTracked(dropboxPathLower)) {
                                LOG.trace("Path already tracked. Skipping: {}", () -> path);
                            } else {
                                LocalFolderChangeType changeType = LocalFolderChangeType
                                        .fromWatchEventKind(watchEventKind);
    
                                LocalFolderData localPathChange = new LocalFolderData(
                                        path, changeType);
    
                                LOG.trace("Local event {} on path {}", changeType, path);
    
                                try {
                                    localPatheChanges.put(localPathChange);
                                } catch (Exception e1) {
                                    LOG.error("Interrupted", e1);
                                }
                            }
                        });

                boolean valid = key.reset(); // IMPORTANT: The key must be reset
                                             // after processed
                if (!valid) {
                    LOG.warn(
                            "Key reset was not valid. Discard key and continue");
                    continue;
                }
            }

            LOG.debug("Closing watchService");
            watchService.close();

        } catch (IOException e) {
            throw new DSyncClientException(e);
        }
    }

    private class ChangeWaitThread implements Runnable {

        @Override
        public void run() {
            while (!Thread.interrupted()) {
                try {
                    LocalFolderData folderData = localPatheChanges.take();

                    Thread.sleep(LOCAL_CHANGE_WAIT_TIME);

                    LocalFolderChangeType changeType = folderData
                            .getChangeType();

                    switch (changeType) {
                    case DELETE:
                        filesModifiedMap.remove(folderData.getPath());
                        // Forwarding delete, as nothing to be done here
                        changeListener.processChange(folderData);
                        break;
                    case CREATE:
                        // If that's folder, then registering it for watching
                        if (folderData.fileExists() && folderData.isDirectory()) {
                            watcherConsumer.accept(folderData.getPath());
                            changeListener.processChange(folderData);
                        } else {
                            filesModifiedMap.put(folderData.getPath(),
                                    new FileChangeData(changeType,
                                            folderData.getSize()));
                            LOG.trace(
                                    "File created. Waiting for completion ({})",
                                    () -> folderData.getPath()
                                            .toAbsolutePath());
                        }
                        break;
                    case MODIFY:
                        filesModifiedMap.putIfAbsent(folderData.getPath(),
                                new FileChangeData(changeType,
                                        folderData.getSize()));
                        break;
                    }

                } catch (InterruptedException e) {
                    LOG.error("Interrupted watcher", e);
                }
            }
        }
    }

    /**
     * Checking each file from map and if it is ready, then propagade change
     */
    private class FileWaitThread implements Runnable {

        @Override
        public void run() {
            if (!filesModifiedMap.isEmpty()) {
                LOG.trace("Checking files");
                List<LocalFolderData> filesToProcess = filesModifiedMap
                        .entrySet().stream().filter(this::fileIsReady)
                        .map(entry -> new LocalFolderData(entry.getKey(),
                                entry.getValue().getChangeType()))
                        .collect(Collectors.toList());

                if (!filesToProcess.isEmpty()) {
                    filesToProcess.stream().map(fd -> fd.getPath())
                            .forEach(filesModifiedMap::remove);
                    LOG.trace("Notifying about {} files created/modified",
                            () -> filesToProcess.size());
                    filesToProcess.forEach(changeListener::processChange);
                }
            }
        }

        /**
         * Determining if file creation/modification is completed by comparing
         * current size of file with the recorded one that is stored in
         * FileChangeData.
         * 
         * @param entry
         *            entry with path and file change data
         * @return true if file change can be processed by changeListener
         */
        private boolean fileIsReady(Entry<Path, FileChangeData> entry) {
            File file = entry.getKey().toFile();

            long currentSize = file.length();
            long prevSize = entry.getValue().getSize();

            if (currentSize == prevSize) {
                if (entry.getValue().isFirstEqualCheckDone()) {
                    LOG.trace("File is ready. ({})",
                            () -> file.getAbsolutePath());
                    return true;
                } else {
                    LOG.trace("File is not ready. Size equals ({})",
                            () -> file.getAbsolutePath());
                    entry.getValue().setFirstEqualCheckDone(true);
                    return false;
                }
            } else {
                LOG.trace("File is not ready yet. Size differs ({})",
                        () -> file.getAbsolutePath());
                entry.getValue().setSize(currentSize);
                return false;
            }
        }

    }

    /**
     * Holds internal information about the created/modified file. It is needed
     * to identify when file creation/modification is completed. The file size
     * that was previously recorded is compared to the current one. When size
     * becomes equal the flag is set, so we'll wait some more time to finally
     * trigger the change.
     */
    private class FileChangeData {
        private final LocalFolderChangeType changeType;
        private Long size;

        private boolean firstEqualCheckDone = false;

        public FileChangeData(LocalFolderChangeType changeType, Long size) {
            this.changeType = changeType;
            this.size = size;
        }

        public Long getSize() {
            return size;
        }

        public void setSize(Long size) {
            this.size = size;
        }

        public LocalFolderChangeType getChangeType() {
            return changeType;
        }

        public boolean isFirstEqualCheckDone() {
            return firstEqualCheckDone;
        }

        public void setFirstEqualCheckDone(boolean firstEqualCheckDone) {
            this.firstEqualCheckDone = firstEqualCheckDone;
        }
    }

}
