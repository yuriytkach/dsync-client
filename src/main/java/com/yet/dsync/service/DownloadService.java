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
import java.io.FileOutputStream;

import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;

import com.yet.dsync.dto.FileData;
import com.yet.dsync.dao.MetadataDao;
import com.yet.dsync.service.LocalFolderService;
import com.yet.dsync.exception.DSyncClientException;

public class DownloadService {
    
    private static final int THREAD_NUMBER = 5;
    
    private final MetadataDao metadaDao;
    private final LocalFolderService localFolderService;
    private final DropboxService dropboxService;
    
    private final BlockingQueue<FileData> downloadQueue;
    
    private final ExecutorService executorService;
    
    public DownloadService(MetadataDao metadaDao, LocalFolderService localFolderService, DropboxService dropboxService) {
        this.metadaDao = metadaDao;
        this.localFolderService = localFolderService;
        this.dropboxService = dropboxService;
        
        this.downloadQueue = createDownloadQueue();
        this.executorService = Executors.newFixedThreadPool(THREAD_NUMBER);
        
        initDownloadThreads();
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
    
    private void initDownloadThreads() {
        for (int i=0; i < THREAD_NUMBER; i++) {
            executorService.submit(() -> {
                while (!Thread.interrupted()) {
                    try {
                        FileData fileData = downloadQueue.take();
                        downloadData(fileData);
                    } catch (Exception e) {
                        System.err.println("Failed to download file: " + e.getMessage());
                    }
                }
            });
        }
    }
    
    private void downloadData(FileData fileData) {
        if (fileData.getRev() == null) {
            localFolderService.createFolder(fileData.getPathDisplay());
            System.out.println("LOCAL_DIR " + fileData.getPathDisplay());
        } else {
            File file = localFolderService.buildFileObject(fileData.getPathDisplay());
            try (FileOutputStream fos = new FileOutputStream(file)) {
                dropboxService.downloadFile(fileData.getPathDisplay(), fos);
            } catch (Exception e) {
                throw new DSyncClientException(e);
            }
            System.out.println("DOWNLOADED " + fileData.getPathDisplay());
        }
        metadaDao.writeLoadedFlag(fileData.getId(), true);
    }
    
    public void downloadAllNotLoaded() {
        Collection<FileData> allNotLoaded = metadaDao.readAllNotLoaded();
        allNotLoaded.forEach(f -> {
            try {
                downloadQueue.put(f);
            } catch (Exception e) {
                throw new DSyncClientException(e);
            }
        });
    }
    
    public void scheduleDownload(FileData fileData) {
        try {
            downloadQueue.put(fileData);
        } catch (Exception e) {
            throw new DSyncClientException(e);
        }
    }

}
