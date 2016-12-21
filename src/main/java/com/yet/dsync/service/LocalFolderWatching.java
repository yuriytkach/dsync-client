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
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.yet.dsync.dao.ConfigDao;
import com.yet.dsync.dto.LocalFolderChangeType;
import com.yet.dsync.dto.LocalFolderData;
import com.yet.dsync.exception.DSyncClientException;
import com.yet.dsync.util.Config;
import com.yet.dsync.util.WatcherRegisterConsumer;

public class LocalFolderWatching implements Runnable {
    
    private static final int WAIT_THREAD_COUNT = 10;

    private static final int LOCAL_CHANGE_WAIT_TIME = 1000;

    private static final Logger LOG = LogManager.getLogger(LocalFolderWatching.class);

    private final ConfigDao configDao;
    private final LocalFolderChange changeListener;

    private final WatchService watchService;

    private final BlockingQueue<LocalFolderData> localPatheChanges = new LinkedBlockingDeque<>(10);
    private Map<WatchKey, Path> keys;
    private WatcherRegisterConsumer watcherConsumer;

    public LocalFolderWatching(ConfigDao configDao, LocalFolderChange changeListener) {
        this.configDao = configDao;
        this.changeListener = changeListener;
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
                .setNameFormat("local-wait-%d").build();
        
        final ExecutorService executorService = Executors.newFixedThreadPool(WAIT_THREAD_COUNT, namedThreadFactory);
        for (int i = 0; i < WAIT_THREAD_COUNT; i++) {
            executorService.execute(new ChangeWaitThread());
        }
    }

    @Override
    public void run() {
        Thread.currentThread().setName("local-poll");
        LOG.info("Started local folder watching");

        String localDirPath = configDao.read(Config.LOCAL_DIR);

        Path localDir = Paths.get(localDirPath);

        try {

            watcherConsumer.accept(localDir);

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

                key.pollEvents().stream().filter(e -> (e.kind() != StandardWatchEventKinds.OVERFLOW)).forEach(e -> {
                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> event = (WatchEvent<Path>) e;

                    Kind<Path> watchEventKind = event.kind();

                    Path path = dir.resolve(event.context());

                    LocalFolderChangeType changeType = LocalFolderChangeType.fromWatchEventKind(watchEventKind);
                    
                    LocalFolderData localPathChange = new LocalFolderData(path, changeType);
                    
                    LOG.trace("Local event {} on path {}", changeType, localPathChange);
                    
                    try {
                        localPatheChanges.put(localPathChange);
                    } catch (Exception e1) {
                        LOG.error("Interrupted", e1);
                    }
                });

                boolean valid = key.reset(); // IMPORTANT: The key must be reset
                                             // after processed
                if (!valid) {
                    break;
                }
            }

            watchService.close();

        } catch (IOException e) {
            throw new DSyncClientException(e);
        }
    }
    
    private class ChangeWaitThread implements Runnable {

        @Override
        public void run() {
            try {
                LocalFolderData folderData = localPatheChanges.take();

                Thread.sleep(LOCAL_CHANGE_WAIT_TIME);

                if ( folderData.exists() && folderData.isDirectory() ) {
                    watcherConsumer.accept(folderData.getPath());
                }

                changeListener.processChange(folderData);
            } catch (InterruptedException e) {
                LOG.error("Interrupted watcher", e);
            }
        }
        
    }

}
