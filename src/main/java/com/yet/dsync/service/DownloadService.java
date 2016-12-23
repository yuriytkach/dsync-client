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

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.yet.dsync.dao.MetadataDao;
import com.yet.dsync.dto.DropboxChangeType;
import com.yet.dsync.dto.DropboxFileData;
import com.yet.dsync.exception.DSyncClientException;

public class DownloadService
        extends AbstractChangeProcessingService<DropboxFileData> {

    private static final Logger LOG = LogManager
            .getLogger(DownloadService.class);

    private final MetadataDao metadataDao;
    private final LocalFolderService localFolderService;
    private final DropboxService dropboxService;

    public DownloadService(GlobalOperationsTracker globalOperationsTracker,
            MetadataDao metadaDao,
            LocalFolderService localFolderService,
            DropboxService dropboxService) {
        super("download", globalOperationsTracker);

        this.metadataDao = metadaDao;
        this.localFolderService = localFolderService;
        this.dropboxService = dropboxService;
    }

    private void downloadData(final DropboxFileData fileData) {
        globalOperationsTracker.start(fileData.getPathLower());
        try {
            if (DropboxChangeType.DELETE == fileData.getChangeType()) {
                deleteFileOrDirectory(fileData);
            } else if (fileData.isDirectory()) {
                createDirectory(fileData);
            } else {
                File file = resolveFile(fileData);
    
                if (file.getParentFile().exists()) {
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        dropboxService.downloadFile(fileData.getPathDisplay(), fos);
                        metadataDao.writeLoadedFlag(fileData.getId(), true);
                    } catch (IOException e) {
                        throw new DSyncClientException(e);
                    }
                    LOG.info("Downloaded {}", () -> fileData.getPathDisplay());
                } else {
                    LOG.warn("Skipped {}", () -> fileData.getPathDisplay());
                }
            }
        } finally {
            globalOperationsTracker.stop(fileData.getPathLower());
        }
    }

    private void deleteFileOrDirectory(DropboxFileData fd) {
        localFolderService.deleteFileOrFolder(fd.getPathDisplay());
        metadataDao.deleteByLowerPath(fd);
        LOG.info("Removed {}", () -> fd.getPathDisplay());
    }
    
    private void createDirectory(DropboxFileData fileData) {
        localFolderService.createFolder(fileData.getPathDisplay());
        metadataDao.writeLoadedFlag(fileData.getId(), true);

        LOG.info("Created directory {}", () -> fileData.getPathDisplay());
    }

    private File resolveFile(DropboxFileData fileData) {
        final String fileDir = FilenameUtils
                .getFullPathNoEndSeparator(fileData.getPathDisplay());
        final String fileName = FilenameUtils
                .getName(fileData.getPathDisplay());

        File dir = localFolderService.buildFileObject(fileDir);

        final String fullFilePath;
        if (dir.exists()) {
            fullFilePath = dir.getAbsolutePath() + File.separator + fileName;
        } else {
            DropboxFileData dirData = metadataDao
                    .readByLowerPath(fileDir.toLowerCase());
            if (dirData != null) {
                String fileDisplayPath = dirData.getPathDisplay()
                        + File.separator + fileName;

                DropboxFileData.Builder newFileDataBuilder = new DropboxFileData.Builder();
                newFileDataBuilder.init(fileData).pathDisplay(fileDisplayPath);
                DropboxFileData newFileData = newFileDataBuilder.build();
                metadataDao.write(newFileData);

                fullFilePath = localFolderService
                        .buildFileObject(fileDisplayPath).getAbsolutePath();
            } else {
                fullFilePath = dir.getAbsolutePath() + File.separator
                        + fileName;
            }
        }

        return new File(fullFilePath);
    }

    public void downloadAllNotLoaded() {
        Collection<DropboxFileData> allNotLoaded = metadataDao.readAllNotLoaded();
        LOG.debug("Downloading {} objects that are not loaded..",
                () -> allNotLoaded.size());
        allNotLoaded.forEach(this::scheduleProcessing);
    }

    @Override
    protected void processChange(DropboxFileData changeData) {
        downloadData(changeData);
    }

    @Override
    protected boolean isFile(DropboxFileData changeData) {
        return changeData.isFile();
    }

    @Override
    protected long getFileSize(DropboxFileData changeData) {
        return changeData.getSize();
    }

    @Override
    protected boolean isDeleteData(DropboxFileData changeData) {
        return !changeData.isFile() && !changeData.isDirectory();
    }

    @Override
    protected String extractPathLower(DropboxFileData changeData) {
        return changeData.getPathLower();
    }

}
