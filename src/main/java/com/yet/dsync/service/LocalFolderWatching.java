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
import java.util.List;

import com.yet.dsync.dao.ConfigDao;
import com.yet.dsync.exception.DSyncClientException;
import com.yet.dsync.util.Config;

public class LocalFolderWatching implements Runnable {

    private final ConfigDao configDao;
    private WatchService watchService;

    public LocalFolderWatching(ConfigDao configDao) {
        this.configDao = configDao;
        try {
            watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            throw new DSyncClientException(e);
        }
    }

    @Override
    public void run() {
        System.out.println("Started local folder watching...");
        
        String localDirPath = configDao.read(Config.LOCAL_DIR);

        Path localDir = Paths.get(localDirPath);

        try {
            WatchKey key = localDir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);

            while (!Thread.interrupted()) {
                try {
                    key = watchService.take();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                List<WatchEvent<?>> keys = key.pollEvents();
                for (WatchEvent<?> watchEvent : keys) {
                    // get the kind of event
                    Kind<?> watchEventKind = watchEvent.kind();
                    // sometimes events are created faster than they are
                    // registered
                    // or the implementation
                    // may specify a maximum number of events and further events
                    // are
                    // discarded. In these cases
                    // an event of kind overflow is returned. We ignore this
                    // case
                    // for nowl
                    if (watchEventKind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    if (watchEventKind == StandardWatchEventKinds.ENTRY_CREATE) {
                        // a new file has been created
                        // print the name of the file. To test this, go to the
                        // temp
                        // directory
                        // and create a plain text file. name the file a.txt. If
                        // you
                        // are on windows, watch what happens!
                        System.out.println("File Created:" + watchEvent.context());
                    } else if (watchEventKind == StandardWatchEventKinds.ENTRY_MODIFY) {
                        // The file has been modified. Go to the file created
                        // above
                        // and modify it
                        System.out.println("File Modified:" + watchEvent.context());
                    } else if (watchEventKind == StandardWatchEventKinds.ENTRY_DELETE) {
                        // the file has been deleted. delete the file. and exit
                        // the
                        // loop.
                        System.out.println("File deleted:" + watchEvent.context());
                    }
                    // we need to reset the key so the further key events may be
                    // polled
                    key.reset();
                }
            }

            watchService.close();

        } catch (IOException e) {
            throw new DSyncClientException(e);
        }
    }

}
