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
import java.util.Comparator;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.yet.dsync.dao.MetadataDao;
import com.yet.dsync.dto.DropboxFileData;
import com.yet.dsync.exception.DSyncClientException;

public class DownloadService extends AbstractChangeProcessingService<DropboxFileData>{
    
    private static final Logger LOG = LogManager.getLogger(DownloadService.class);
    
    private final MetadataDao metadaDao;
    private final LocalFolderService localFolderService;
    private final DropboxService dropboxService;
    
    public DownloadService(MetadataDao metadaDao, LocalFolderService localFolderService, DropboxService dropboxService) {
        super("download", new FileDataSizeComparator());
        
        this.metadaDao = metadaDao;
        this.localFolderService = localFolderService;
        this.dropboxService = dropboxService;
    }
    
    
    private void downloadData(DropboxFileData fileData) {
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

    private File resolveFile(DropboxFileData fileData) {
        final String fileDir = FilenameUtils.getFullPathNoEndSeparator(fileData.getPathDisplay());
        final String fileName = FilenameUtils.getName(fileData.getPathDisplay());
        
        File dir = localFolderService.buildFileObject(fileDir);
        
        final String fullFilePath;
        if (dir.exists()) {
            fullFilePath = dir.getAbsolutePath() + File.separator + fileName;
        } else {
            DropboxFileData dirData = metadaDao.readByLowerPath(fileDir.toLowerCase());
            if (dirData != null) {
                String fileDisplayPath = dirData.getPathDisplay() + File.separator + fileName;
                
                DropboxFileData.Builder newFileDataBuilder = new DropboxFileData.Builder();
                newFileDataBuilder.init(fileData).pathDisplay(fileDisplayPath);
                DropboxFileData newFileData = newFileDataBuilder.build();
                metadaDao.write(newFileData);
                
                fullFilePath = localFolderService.buildFileObject(fileDisplayPath).getAbsolutePath();
            } else {
                fullFilePath = dir.getAbsolutePath() + File.separator + fileName;
            }
        }
        
        return new File(fullFilePath);
    }

    private void createDirectory(DropboxFileData fileData) {
        localFolderService.createFolder(fileData.getPathDisplay());
        metadaDao.writeLoadedFlag(fileData.getId(), true);
        
        LOG.info("Created directory {}", ()-> fileData.getPathDisplay());
    }
    
    public void downloadAllNotLoaded() {
        Collection<DropboxFileData> allNotLoaded = metadaDao.readAllNotLoaded();
        LOG.debug("Downloading {} objects that are not loaded..", ()-> allNotLoaded.size());
        allNotLoaded.forEach(this::scheduleProcessing);
    }
    
    private static class FileDataSizeComparator implements Comparator<DropboxFileData> {

        @Override
        public int compare(DropboxFileData a, DropboxFileData b) {
            if (a.getRev() == null && b.getRev() != null) {
                return -1;
            } else if (a.getRev() != null && b.getRev() == null) {
                return 1;
            } else if (a.getRev() != null && b.getRev() != null) {
                return a.getSize().intValue() - b.getSize().intValue();
            } else {
                return a.getPathDisplay().compareTo(b.getPathDisplay());
            }
        }
        
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

}
