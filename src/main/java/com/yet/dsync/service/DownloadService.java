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
import com.yet.dsync.dto.DropboxChangeType;
import com.yet.dsync.dto.DropboxFileData;
import com.yet.dsync.exception.DSyncClientException;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Locale;

public class DownloadService
        extends AbstractChangeProcessingService<DropboxFileData> {

    private static final Logger LOG = LogManager
            .getLogger(DownloadService.class);

    private final MetadataDao metadataDao;
    private final LocalFolderService localFolderService;
    private final DropboxService dropboxService;

    public DownloadService(final GlobalOperationsTracker globalOperationsTracker,
                           final MetadataDao metadaDao,
                           final LocalFolderService localFolderService,
                           final DropboxService dropboxService) {
        super("download", globalOperationsTracker);

        this.metadataDao = metadaDao;
        this.localFolderService = localFolderService;
        this.dropboxService = dropboxService;
    }

    private void downloadData(final DropboxFileData fileData) {
        getGlobalOperationsTracker().start(fileData.getPathLower());
        try {
            if (DropboxChangeType.DELETE == fileData.getChangeType()) {
                deleteFileOrDirectory(fileData);
            } else if (fileData.isDirectory()) {
                createDirectory(fileData);
            } else {
                final File file = resolveFile(fileData);

                if (file.getParentFile().exists()) {
                    try (OutputStream fos = new BufferedOutputStream(new FileOutputStream(file))) {
                        dropboxService.downloadFile(fileData.getPathDisplay(), fos);
                        metadataDao.writeLoadedFlag(fileData.getId(), true);
                    } catch (final IOException ex) {
                        throw new DSyncClientException(ex);
                    }
                    LOG.info("Downloaded {}", () -> fileData.getPathDisplay());
                } else {
                    LOG.warn("Skipped {}", () -> fileData.getPathDisplay());
                }
            }
        } finally {
            getGlobalOperationsTracker().stop(fileData.getPathLower());
        }
    }

    private void deleteFileOrDirectory(final DropboxFileData fd) {
        localFolderService.deleteFileOrFolder(fd.getPathDisplay());
        metadataDao.deleteByLowerPath(fd.getPathLower());
        LOG.info("Removed {}", () -> fd.getPathDisplay());
    }

    private void createDirectory(final DropboxFileData fileData) {
        localFolderService.createFolder(fileData.getPathDisplay());
        metadataDao.writeLoadedFlag(fileData.getId(), true);

        LOG.info("Created directory {}", () -> fileData.getPathDisplay());
    }

    private File resolveFile(final DropboxFileData fileData) {
        final String fileDir = FilenameUtils
                .getFullPathNoEndSeparator(fileData.getPathDisplay());
        final String fileName = FilenameUtils
                .getName(fileData.getPathDisplay());

        final File dir = localFolderService.buildFileObject(fileDir);

        final String fullFilePath;
        if (dir.exists()) {
            fullFilePath = dir.getAbsolutePath() + File.separator + fileName;
        } else {
            final DropboxFileData dirData = metadataDao
                    .readByLowerPath(fileDir.toLowerCase(Locale.getDefault()));
            if (dirData == null) {
                fullFilePath = dir.getAbsolutePath() + File.separator
                        + fileName;
            } else {
                final String fileDisplayPath = dirData.getPathDisplay()
                        + File.separator + fileName;

                final DropboxFileData newFileData = fileData.toBuilder()
                  .pathDisplay(fileDisplayPath)
                  .build();
                metadataDao.write(newFileData);

                fullFilePath = localFolderService
                        .buildFileObject(fileDisplayPath).getAbsolutePath();
            }
        }

        return new File(fullFilePath);
    }

    public void downloadAllNotLoaded() {
        final Collection<DropboxFileData> allNotLoaded = metadataDao.readAllNotLoaded();
        LOG.debug("Downloading {} objects that are not loaded..",
            () -> allNotLoaded.size());
        allNotLoaded.forEach(this::scheduleProcessing);
    }

    @Override
    protected void processChange(final DropboxFileData changeData) {
        downloadData(changeData);
    }

    @Override
    protected boolean isFile(final DropboxFileData changeData) {
        return changeData.isFile();
    }

    @Override
    protected long getFileSize(final DropboxFileData changeData) {
        return changeData.getSize();
    }

    @Override
    protected boolean isDeleteData(final DropboxFileData changeData) {
        return !changeData.isFile() && !changeData.isDirectory();
    }

    @Override
    protected String extractPathLower(final DropboxFileData changeData) {
        return changeData.getPathLower();
    }

}
