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
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadFactory;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.dropbox.core.v2.files.DownloadErrorException;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.yet.dsync.dao.MetadataDao;
import com.yet.dsync.dto.FileData;
import com.yet.dsync.exception.DSyncClientException;

public class DownloadService {
    
    private static final Logger LOG = LogManager.getLogger(DownloadService.class);
    
    private static final int QUICK_THREAD_NUMBER = 5;
    private static final int SLOW_THREAD_NUMBER = 2;
    
    private static final long SLOW_THRESHOLD = 256*1024; //256KB
    
    private final MetadataDao metadaDao;
    private final LocalFolderService localFolderService;
    private final DropboxService dropboxService;
    
    private final BlockingQueue<FileData> quickDownloadQueue;
    private final BlockingQueue<FileData> slowDownloadQueue;
    
    private final ExecutorService executorService;
    
    public DownloadService(MetadataDao metadaDao, LocalFolderService localFolderService, DropboxService dropboxService) {
        this.metadaDao = metadaDao;
        this.localFolderService = localFolderService;
        this.dropboxService = dropboxService;
        
        this.slowDownloadQueue = createDownloadQueue();
        this.quickDownloadQueue = createDownloadQueue();
        
        ThreadFactory namedThreadFactory = new ThreadFactoryBuilder()
                .setNameFormat("download-thread-%d").build();
        
        this.executorService = Executors.newFixedThreadPool(SLOW_THREAD_NUMBER + QUICK_THREAD_NUMBER,
                namedThreadFactory);
        
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
        for (int i=0; i < QUICK_THREAD_NUMBER; i++) {
            executorService.submit(new DownloadThread(quickDownloadQueue));
        }
        
        for (int i=0; i < SLOW_THREAD_NUMBER; i++) {
            executorService.submit(new DownloadThread(slowDownloadQueue));
        }
    }
    
    private void downloadData(FileData fileData) {
        if (fileData.isDirectory()) {
            createDirectory(fileData);
        } else {
            File file = resolveFile(fileData);
            
            if ( file.getParentFile().exists() ) {
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    dropboxService.downloadFile(fileData.getPathDisplay(), fos);
                    metadaDao.writeLoadedFlag(fileData.getId(), true);
                } catch (IOException e) {
                    throw new DSyncClientException(e);
                }
                LOG.info("Downloaded {}", ()-> fileData.getPathDisplay());
            } else {
                LOG.warn("Skipped {}", ()-> fileData.getPathDisplay());
            }
        }
    }

    private File resolveFile(FileData fileData) {
        final String fileDir = FilenameUtils.getFullPathNoEndSeparator(fileData.getPathDisplay());
        final String fileName = FilenameUtils.getName(fileData.getPathDisplay());
        
        File dir = localFolderService.buildFileObject(fileDir);
        
        final String fullFilePath;
        if (dir.exists()) {
            fullFilePath = dir.getAbsolutePath() + File.separator + fileName;
        } else {
            FileData dirData = metadaDao.readByLowerPath(fileDir.toLowerCase());
            if (dirData != null) {
                String fileDisplayPath = dirData.getPathDisplay() + File.separator + fileName;
                
                FileData.Builder newFileDataBuilder = new FileData.Builder();
                newFileDataBuilder.init(fileData).pathDisplay(fileDisplayPath);
                FileData newFileData = newFileDataBuilder.build();
                metadaDao.write(newFileData);
                
                fullFilePath = localFolderService.buildFileObject(fileDisplayPath).getAbsolutePath();
            } else {
                fullFilePath = dir.getAbsolutePath() + File.separator + fileName;
            }
        }
        
        return new File(fullFilePath);
    }

    private void createDirectory(FileData fileData) {
        localFolderService.createFolder(fileData.getPathDisplay());
        metadaDao.writeLoadedFlag(fileData.getId(), true);
        
        LOG.info("Created directory {}", ()-> fileData.getPathDisplay());
    }
    
    public void downloadAllNotLoaded() {
        Collection<FileData> allNotLoaded = metadaDao.readAllNotLoaded();
        LOG.debug("Downloading {} objects that are not loaded..", ()-> allNotLoaded.size());
        allNotLoaded.forEach(this::scheduleDownload);
    }
    
    public void scheduleDownload(FileData fileData) {
        try {
            if (fileData.isFile()) {
                long size = fileData.getSize();
                if (size > SLOW_THRESHOLD) {
                    slowDownloadQueue.put(fileData);
                } else {
                    quickDownloadQueue.put(fileData);
                }
            } else {
                quickDownloadQueue.put(fileData);
            }
        } catch (Exception e) {
            throw new DSyncClientException(e);
        }
    }
    
    private class DownloadThread implements Runnable {

        private BlockingQueue<FileData> queue;
        
        public DownloadThread(BlockingQueue<FileData> queue) {
            this.queue = queue;
        }

        @Override
        public void run() {
            while (!Thread.interrupted()) {
                try {
                    FileData fileData = queue.take();
                    try {
                        downloadData(fileData);
                    } catch (DSyncClientException dee) {
                        if (dee.getCause() instanceof DownloadErrorException) {
                            // TODO That is not correct. And we need to check, maybe
                            // other thread already deleted that file and its metadata
                            // while we were waiting for this one to download
                            metadaDao.deleteByLowerPath(fileData);
                        } else {
                            throw dee;
                        }
                    }
                    
                } catch (Exception e) {
                    LOG.error("Failed to download file", e);
                }
            }
        }
        
    }

}
