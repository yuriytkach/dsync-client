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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.yet.dsync.dao.MetadataDao;
import com.yet.dsync.dto.DropboxFileData;
import com.yet.dsync.dto.LocalFolderChangeType;
import com.yet.dsync.dto.LocalFolderData;
import com.yet.dsync.exception.DSyncClientException;

public class UploadService
        extends AbstractChangeProcessingService<LocalFolderData> {

    private static final Logger LOG = LogManager.getLogger(UploadService.class);

    private final MetadataDao metadataDao;
    private final LocalFolderService localFolderService;
    private final DropboxService dropboxService;

    public UploadService(GlobalOperationsTracker globalOperationsTracker,
            MetadataDao metadataDao, LocalFolderService localFolderService,
            DropboxService dropboxService) {
        super("upload", globalOperationsTracker);
        this.metadataDao = metadataDao;
        this.localFolderService = localFolderService;
        this.dropboxService = dropboxService;
    }

    @Override
    protected void processChange(LocalFolderData changeData) {
        uploadData(changeData);
    }

    private void uploadData(LocalFolderData changeData) {
        String dropboxPath = extractPath(changeData);

        globalOperationsTracker.start(dropboxPath.toLowerCase());
        try {
            if (!changeData.fileExists()) {
                deleteData(dropboxPath);
                LOG.info("Deleted from Dropbox {}", () -> dropboxPath);

            } else if (changeData.isDirectory()) {
                if (LocalFolderChangeType.CREATE == changeData.getChangeType()) {
                    createDirectory(dropboxPath);
                    LOG.info("Created in Dropbox {}", () -> dropboxPath);
                } else {
                    LOG.info("Modify on local folder. Doing nothing for {}", () -> dropboxPath);
                }

            } else {
                uploadFile(dropboxPath, changeData);
                LOG.info("Uploaded to Dropbox {}", () -> dropboxPath);
            }
        } finally {
            globalOperationsTracker.stop(dropboxPath.toLowerCase());
        }
    }

    private void uploadFile(String dropboxPath, LocalFolderData changeData) {
        File file = changeData.getPath().toFile();
        try (InputStream is = new BufferedInputStream(
                new FileInputStream(file))) {

            DropboxFileData fileData = dropboxService.uploadFile(dropboxPath,
                    is, changeData.getSize());

            metadataDao.write(fileData);
            metadataDao.writeLoadedFlag(fileData.getId(), true);

        } catch (IOException e) {
            LOG.error("Error when reading file for upload", e);
            throw new DSyncClientException(e);
        }
    }

    private void createDirectory(String dropboxPath) {
        DropboxFileData fileData = dropboxService.createFolder(dropboxPath);

        metadataDao.write(fileData);
        metadataDao.writeLoadedFlag(fileData.getId(), true);
    }

    private void deleteData(String dropboxPath) {
        dropboxService.deleteFile(dropboxPath);
        
        metadataDao.deleteByLowerPath(dropboxPath.toLowerCase());
    }
    
    private String extractPath(LocalFolderData changeData) {
        String dropboxPath = localFolderService
                .extractDropboxPath(changeData.getPath());
        return dropboxPath;
    }

    @Override
    protected boolean isFile(LocalFolderData changeData) {
        return changeData.isFile();
    }

    @Override
    protected long getFileSize(LocalFolderData changeData) {
        return changeData.getSize();
    }

    @Override
    protected boolean isDeleteData(LocalFolderData changeData) {
        return !changeData.fileExists();
    }

    @Override
    protected String extractPathLower(LocalFolderData changeData) {
        final String dropboxPath = extractPath(changeData);
        return dropboxPath.toLowerCase();
    }
    
}
