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

import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;

import com.yet.dsync.dto.FileData;
import com.yet.dsync.dao.MetadataDao;
import com.yet.dsync.service.LocalFolderService;

public class DownloadService {
    
    private final MetadataDao metadaDao;
    private final LocalFolderService localFolderService;
    
    private final BlockingQueue<FileData> downloadQueue; 
    
    public DownloadService(MetadataDao metadaDao, LocalFolderService localFolderService) {
        this.metadaDao = metadaDao;
        this.localFolderService = localFolderService;
        
        this.downloadQueue = createDownloadQueue();
    }
    
    /**
     * Creating PriorityBlockingQueue.
     * Note! It is unbounded, so in the future we might need to enhance it to make it bounded,
     * so the caller will block before putting next elem them. Also, we can think of different
     * download threads, some of which will handle only directories and small files, another
     * will handle only big files.
     * For now, we make priority in the following way: folders, small files, big files;
     */
    private BlockingQueue<FileData> createDownloadQueue() {
        return new PriorityBlockingQueue<FileData>(100, (a, b) -> {
            if (a.getRev() == null && b.getRev() != null) {
                return -1;
            } else if (a.getRev() != null && b.getRev() == null) {
                return 1;
            } else if (a.getRev() != null && b.getRev() != null) {
                return a.getSize().intValue() - b.getSize().intValue();
            } else {
                return a.getPathDisplay().compareTo(b.getPathDisplay());
            }
        });
    }
    
    public void downloadAllNotLoaded() {
        Collection<FileData> allNotLoaded = metadaDao.readAllNotLoaded();
        
        allNotLoaded.stream()
            .filter(fd -> fd.getRev() == null)
            .map(FileData::getPathDisplay)
            .forEach(localFolderService::createFolder);
    }

}
