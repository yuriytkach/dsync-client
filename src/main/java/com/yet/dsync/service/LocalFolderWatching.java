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

import com.yet.dsync.dao.ConfigDao;
import com.yet.dsync.exception.DSyncClientException;
import com.yet.dsync.util.Config;
import com.yet.dsync.util.WatcherRegisterConsumer;

public class LocalFolderWatching implements Runnable {

    private final ConfigDao configDao;
    private final WatchService watchService;

    public LocalFolderWatching(ConfigDao configDao) {
        this.configDao = configDao;
        try {
            this.watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            throw new DSyncClientException(e);
        }
    }

    @Override
    public void run() {
        System.out.println("Started local folder watching...");
        
        String localDirPath = configDao.read(Config.LOCAL_DIR);

        Path localDir = Paths.get(localDirPath);
        
        final Map<WatchKey, Path> keys = new HashMap<>();
        
        final WatcherRegisterConsumer watcherConsumer = new WatcherRegisterConsumer(watchService, key -> {
            Path path = (Path) key.watchable();
            keys.put(key, path);
        });
        
        try {
            
            watcherConsumer.accept(localDir);

            while (!Thread.interrupted()) {
                final WatchKey key;
                try {
                    key = watchService.take();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    continue;
                }
                
                final Path dir = keys.get(key);
                if (dir == null) {
                    System.err.println("WatchKey " + key + " not recognized!");
                    continue;
                }
                
                key.pollEvents().stream()
                    .filter(e -> (e.kind() != StandardWatchEventKinds.OVERFLOW))
                    .forEach(e -> {
                        WatchEvent<Path> event = (WatchEvent<Path>)e;
                        Kind<Path> watchEventKind = event.kind();
                        
                        if (watchEventKind == StandardWatchEventKinds.ENTRY_CREATE) {
                            final Path createdPath = dir.resolve(event.context());
                            if (createdPath.toFile().isDirectory()) {
                                System.out.println("Dir Created:" + event.context());
                                watcherConsumer.accept(createdPath);
                            } else {
                                System.out.println("File Created:" + event.context());
                            }
                            
                        } else if (watchEventKind == StandardWatchEventKinds.ENTRY_MODIFY) {
                            System.out.println("File Modified:" + event.context());
                            
                        } else if (watchEventKind == StandardWatchEventKinds.ENTRY_DELETE) {
                            System.out.println("File deleted:" + event.context());
                        }
                    });
                
                boolean valid = key.reset(); // IMPORTANT: The key must be reset after processed
                if (!valid) {
                    break;
                }
            }

            watchService.close();

        } catch (IOException e) {
            throw new DSyncClientException(e);
        }
    }

}
