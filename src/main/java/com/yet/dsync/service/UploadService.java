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

import com.yet.dsync.dao.MetadataDao;
import com.yet.dsync.dto.DropboxFileData;
import com.yet.dsync.dto.LocalFolderChangeType;
import com.yet.dsync.dto.LocalFolderData;
import com.yet.dsync.exception.DSyncClientException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Locale;

public class UploadService
        extends AbstractChangeProcessingService<LocalFolderData> {

    private static final Logger LOG = LogManager.getLogger(UploadService.class);

    private final MetadataDao metadataDao;
    private final LocalFolderService localFolderService;
    private final DropboxService dropboxService;

    public UploadService(final GlobalOperationsTracker globalOperationsTracker,
                         final MetadataDao metadataDao,
                         final LocalFolderService localFolderService,
                         final DropboxService dropboxService) {
        super("upload", globalOperationsTracker);
        this.metadataDao = metadataDao;
        this.localFolderService = localFolderService;
        this.dropboxService = dropboxService;
    }

    @Override
    protected void processChange(final LocalFolderData changeData) {
        uploadData(changeData);
    }

    @SuppressWarnings("PMD.ConfusingTernary")
    private void uploadData(final LocalFolderData changeData) {
        final String dropboxPath = extractPath(changeData);

        getGlobalOperationsTracker().start(dropboxPath.toLowerCase(Locale.getDefault()));
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
            getGlobalOperationsTracker().stop(dropboxPath.toLowerCase(Locale.getDefault()));
        }
    }

    private void uploadFile(final String dropboxPath, final LocalFolderData changeData) {
        final File file = changeData.getPath().toFile();

        final long lastModified = file.lastModified();
        final Date lastModifiedDate = (lastModified == 0L) ? new Date() : new Date(lastModified);

        final LocalDateTime lastModifiedDateTime = LocalDateTime.ofInstant(lastModifiedDate.toInstant(),
                ZoneOffset.UTC);

        LOG.debug("File modified dateTime is {} for {}", lastModifiedDateTime.toString(), dropboxPath);

        final DropboxFileData existingFileData = metadataDao.
                readByLowerPath(dropboxPath.toLowerCase(Locale.getDefault()));
        final boolean override;
        if (existingFileData == null) {
            override = false;
            LOG.debug("Existing file info is not found for {}", () -> dropboxPath);
        } else if (existingFileData.getServerModified().isBefore(lastModifiedDateTime)) {
            override = true;
            LOG.debug("Existing file info is found and serverModified is earlier for {}", () -> dropboxPath);
        } else {
            override = false;
            LOG.debug("Existing file info is found and serverModified is later for {}", () -> dropboxPath);
        }

        try (InputStream is = new BufferedInputStream(
                new FileInputStream(file))) {

            final DropboxFileData fileData = dropboxService.uploadFile(dropboxPath,
                    is, changeData.getSize(), lastModifiedDate, override);

            metadataDao.write(fileData);
            metadataDao.writeLoadedFlag(fileData.getId(), true);

        } catch (final IOException ex) {
            LOG.error("Error when reading file for upload", ex);
            throw new DSyncClientException(ex);
        }
    }

    private void createDirectory(final String dropboxPath) {
        final DropboxFileData fileData = dropboxService.createFolder(dropboxPath);

        metadataDao.write(fileData);
        metadataDao.writeLoadedFlag(fileData.getId(), true);
    }

    private void deleteData(final String dropboxPath) {
        dropboxService.deleteFile(dropboxPath);

        metadataDao.deleteByLowerPath(dropboxPath.toLowerCase(Locale.getDefault()));
    }

    private String extractPath(final LocalFolderData changeData) {
        final String dropboxPath = localFolderService
                .extractDropboxPath(changeData.getPath());
        return dropboxPath;
    }

    @Override
    protected boolean isFile(final LocalFolderData changeData) {
        return changeData.isFile();
    }

    @Override
    protected long getFileSize(final LocalFolderData changeData) {
        return changeData.getSize();
    }

    @Override
    protected boolean isDeleteData(final LocalFolderData changeData) {
        return !changeData.fileExists();
    }

    @Override
    protected String extractPathLower(final LocalFolderData changeData) {
        final String dropboxPath = extractPath(changeData);
        return dropboxPath.toLowerCase(Locale.getDefault());
    }

}
