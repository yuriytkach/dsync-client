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

package com.yet.dsync.util;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

//import com.sun.nio.file.SensitivityWatchEventModifier;

import com.sun.nio.file.ExtendedWatchEventModifier;
import com.yet.dsync.exception.DSyncClientException;

public class WatcherRegisterConsumer implements Consumer<Path> {
    
    private static final Logger LOG = LogManager.getLogger(WatcherRegisterConsumer.class);

    private WatchService watchService;
    private Consumer<WatchKey> watchKeyConsumer;

    public WatcherRegisterConsumer(WatchService watchService, Consumer<WatchKey> watchKeyConsumer) {
        this.watchService = watchService;
        this.watchKeyConsumer = watchKeyConsumer;
    }

    @Override
    public void accept(Path path) {
        if (!path.toFile().exists() || !path.toFile().isDirectory()) {
            throw new DSyncClientException("folder " + path + " does not exist or is not a directory");
        }
        try {
            //FIXME: fix recursive subscription in case of windows os
            registerWatchersRecursively(path);
//            if (SystemUtils.IS_OS_UNIX) {
//                registerWatchersRecursively(path);
//            } else if (SystemUtils.IS_OS_WINDOWS) {
//                registerWatcherForFileTree(path);
//            }
        } catch (IOException e) {
            throw new DSyncClientException("Error registering path " + path);
        }

    }

    /**
     * Register watchers recursively. Applicable for unix type operation systems.
     *
     * @param path to directory
     * @throws IOException during watchers registration
     */
    private void registerWatchersRecursively(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                LOG.trace("Registering in watcher server: {}", () -> dir);
                WatchKey watchKey = dir
                        .register(watchService,
                                StandardWatchEventKinds.ENTRY_CREATE,
                                StandardWatchEventKinds.ENTRY_DELETE,
                                StandardWatchEventKinds.ENTRY_MODIFY);
//                 , SensitivityWatchEventModifier.HIGH);
                if (watchKeyConsumer != null) {
                    watchKeyConsumer.accept(watchKey);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Register watcher for a whole file tree using undocumented ExtendedWatchEventModifier.FILE_TREE.
     * Applicable ONLY for Windows type operation systems.
     *
     * @param path to directory
     * @throws IOException during watchers registration
     */
    private void registerWatcherForFileTree(Path path) throws IOException {
        WatchKey watchKey = path.register(watchService,
                new WatchEvent.Kind[] {
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_DELETE,
                        StandardWatchEventKinds.ENTRY_MODIFY},
                ExtendedWatchEventModifier.FILE_TREE);
        if (watchKeyConsumer != null) {
            watchKeyConsumer.accept(watchKey);
        }
    }

}
