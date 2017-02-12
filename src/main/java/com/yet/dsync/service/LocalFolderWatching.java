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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.yet.dsync.dto.LocalFolderChangeType;
import com.yet.dsync.dto.LocalFolderData;
import com.yet.dsync.exception.DSyncClientException;
import com.yet.dsync.util.PathUtil;
import com.yet.dsync.util.WatcherRegisterConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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

public class LocalFolderWatching implements Runnable {

    private static final int WAIT_THREAD_COUNT = 10;

    private static final int LOCAL_CHANGE_WAIT_TIME = 1000;

    private static final int FILE_WAIT_TIME_SEC = 3;

    private static final Logger LOG = LogManager
            .getLogger(LocalFolderWatching.class);

    private final String localDir;
    private final LocalFolderChange changeListener;

    private final WatchService watchService;

    private final BlockingQueue<LocalFolderData> localPathChanges = new LinkedBlockingDeque<>(
            10);
    private final Map<WatchKey, Path> keys = new HashMap<>();
    private final WatcherRegisterConsumer watcherConsumer;

    private final ConcurrentMap<Path, FileChangeData> filesModifiedMap = new ConcurrentHashMap<>();

    private final GlobalOperationsTracker globalOperationsTracker;

    public LocalFolderWatching(final String localDir,
            final LocalFolderChange changeListener,
            final GlobalOperationsTracker globalOperationsTracker) {
        this.localDir = localDir;
        this.changeListener = changeListener;
        this.globalOperationsTracker = globalOperationsTracker;

        try {
            this.watchService = FileSystems.getDefault().newWatchService();
        } catch (final IOException ex) {
            throw new DSyncClientException(ex);
        }

        watcherConsumer = new WatcherRegisterConsumer(watchService, key -> {
            final Path path = (Path) key.watchable();
            keys.put(key, path);
        });

        final ThreadFactory namedThreadFactory = new ThreadFactoryBuilder()
                .setNameFormat("local-change-wait-%d").build();

        final ExecutorService executorService = Executors
                .newFixedThreadPool(WAIT_THREAD_COUNT, namedThreadFactory);
        for (int i = 0; i < WAIT_THREAD_COUNT; i++) {
            executorService.execute(new ChangeWaitThread());
        }

        final ThreadFactory namedThreadFactoryLocalWait = new ThreadFactoryBuilder()
                .setNameFormat("local-file-wait-%d").build();

        final ScheduledExecutorService executorServiceForFiles = Executors
                .newSingleThreadScheduledExecutor(namedThreadFactoryLocalWait);
        executorServiceForFiles.scheduleAtFixedRate(new FileWaitThread(),
                FILE_WAIT_TIME_SEC, FILE_WAIT_TIME_SEC, TimeUnit.SECONDS);
    }

    @Override
    public void run() {
        Thread.currentThread().setName("local-poll");
        LOG.info("Started local folder watching");

        final Path localDirPath = Paths.get(localDir);

        try {

            watcherConsumer.accept(localDirPath);

            while (!Thread.interrupted()) {
                final WatchKey key;
                try {
                    key = watchService.take();
                } catch (final InterruptedException ex) {
                    LOG.error("Interrupted", ex);
                    continue;
                }

                final Path dir = keys.get(key);
                if (dir == null) {
                    LOG.error("WatchKey {} not recognized!", () -> key);
                    continue;
                }

                key.pollEvents()
                        .stream()
                        .filter(e -> e.kind() != StandardWatchEventKinds.OVERFLOW)
                        .forEach(e -> {
                            @SuppressWarnings("unchecked")
                            final WatchEvent<Path> event = (WatchEvent<Path>) e;

                            final Kind<Path> watchEventKind = event.kind();

                            final Path path = dir.resolve(event.context());

                            processWatchEvent(watchEventKind, path);
                        });

                final boolean valid = key.reset(); // IMPORTANT: The key must be reset
                                             // after processed
                if (!valid) {
                    LOG.warn(
                            "Key reset was not valid. Discard key and continue");
                    continue;
                }
            }

            LOG.debug("Closing watchService");
            watchService.close();

        } catch (final IOException ex) {
            LOG.debug("Error in local watcher", ex);
        }
    }

    private void processWatchEvent(final Kind<Path> watchEventKind, final Path path) {
        final String dropboxPathLower = PathUtil.extractDropboxPath(localDir, path)
                .toLowerCase(Locale.getDefault());
        if (globalOperationsTracker.isTracked(dropboxPathLower)) {
            LOG.trace("Path already tracked. Skipping: {}", () -> path);
        } else {
            final LocalFolderChangeType changeType = LocalFolderChangeType
                    .fromWatchEventKind(watchEventKind);

            final LocalFolderData localPathChange = new LocalFolderData(path,
                    changeType);

            LOG.trace("Local event {} on path {}", changeType, path);

            try {
                localPathChanges.put(localPathChange);
            } catch (final Exception ex) {
                LOG.error("Interrupted", ex);
            }
        }
    }

    /**
     * The thread will take localFolderData object from the queue and sleep for
     * predefined time. After that the change will be processed based on change
     * type.
     */
    private class ChangeWaitThread implements Runnable {

        @Override
        public void run() {
            while (!Thread.interrupted()) {
                try {
                    final LocalFolderData folderData = localPathChanges.take();

                    Thread.sleep(LOCAL_CHANGE_WAIT_TIME);

                    final LocalFolderChangeType changeType = folderData
                            .getChangeType();

                    switch (changeType) {
                        case DELETE:
                            processDeleteChange(folderData);
                            break;
                        case CREATE:
                            processCreateChange(folderData, changeType);
                            break;
                        case MODIFY:
                            processModifyChange(folderData, changeType);
                            break;
                        default:
                            LOG.debug("Strange change type {}", changeType);
                            break;
                    }

                } catch (final InterruptedException | IOException ex) {
                    LOG.error("Failed in change wait", ex);
                }
            }
        }

        private void processModifyChange(final LocalFolderData folderData,
                                         final LocalFolderChangeType changeType) {
            filesModifiedMap.putIfAbsent(folderData.getPath(),
                    new FileChangeData(changeType, folderData.getSize()));
        }

        private void processCreateChange(final LocalFolderData folderData,
                                         final LocalFolderChangeType changeType) throws IOException {
            // If that's folder, then registering it for watching
            if (folderData.fileExists() && folderData.isDirectory()) {
                processFolderCreateChange(folderData);
            } else {
                processFileCreateChange(folderData, changeType);
            }
        }

        private void processFolderCreateChange(final LocalFolderData folderData)
                throws IOException {
            watcherConsumer.accept(folderData.getPath());
            changeListener.processChange(folderData);

            Files.walkFileTree(folderData.getPath(),
                new SimpleFileVisitor<Path>() {

                    @Override
                    public FileVisitResult visitFile(final Path file,
                                                     final BasicFileAttributes attrs) throws IOException {
                        processWatchEvent(StandardWatchEventKinds.ENTRY_CREATE, file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult preVisitDirectory(final Path dir,
                                                             final BasicFileAttributes attrs) throws IOException {
                        if (dir.equals(folderData.getPath())) {
                            return FileVisitResult.CONTINUE;
                        } else {
                            processWatchEvent(StandardWatchEventKinds.ENTRY_CREATE, dir);
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                    }
                });
        }

        private void processFileCreateChange(final LocalFolderData folderData,
                                             final LocalFolderChangeType changeType) {
            filesModifiedMap.put(folderData.getPath(),
                    new FileChangeData(changeType, folderData.getSize()));
            LOG.trace("File created. Waiting for completion ({})",
                () -> folderData.getPath().toAbsolutePath());
        }

        private void processDeleteChange(final LocalFolderData folderData) {
            filesModifiedMap.remove(folderData.getPath());
            // Forwarding delete, as nothing to be done here
            changeListener.processChange(folderData);
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
                final List<LocalFolderData> filesToProcess = filesModifiedMap
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
        private boolean fileIsReady(final Entry<Path, FileChangeData> entry) {
            final File file = entry.getKey().toFile();

            final long currentSize = file.length();
            final long prevSize = entry.getValue().getSize();

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
    private static class FileChangeData {
        private final LocalFolderChangeType changeType;
        private Long size;

        private boolean firstEqualCheckDone;

        FileChangeData(final LocalFolderChangeType changeType, final Long size) {
            this.changeType = changeType;
            this.size = size;
        }

        public Long getSize() {
            return size;
        }

        public void setSize(final Long size) {
            this.size = size;
        }

        public LocalFolderChangeType getChangeType() {
            return changeType;
        }

        public boolean isFirstEqualCheckDone() {
            return firstEqualCheckDone;
        }

        public void setFirstEqualCheckDone(final boolean firstEqualCheckDone) {
            this.firstEqualCheckDone = firstEqualCheckDone;
        }
    }

}
