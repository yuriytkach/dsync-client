package com.yet.dsync.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.yet.dsync.dao.MetadataDao;
import com.yet.dsync.dto.LocalFolderData;
import com.yet.dsync.exception.DSyncClientException;

public class UploadService
        extends AbstractChangeProcessingService<LocalFolderData> {

    private static final Logger LOG = LogManager.getLogger(UploadService.class);

    private final MetadataDao metadaDao;
    private final LocalFolderService localFolderService;
    private final DropboxService dropboxService;

    public UploadService(MetadataDao metadataDao,
            LocalFolderService localFolderService,
            DropboxService dropboxService) {
        super("upload");
        this.metadaDao = metadataDao;
        this.localFolderService = localFolderService;
        this.dropboxService = dropboxService;
    }

    @Override
    protected void processChange(LocalFolderData changeData) {
        uploadData(changeData);
    }

    private void uploadData(LocalFolderData changeData) {
        String dropboxPath = localFolderService.extractDropboxPath(changeData.getPath().toFile());
        
        if (! changeData.fileExists() ) {
            deleteData(dropboxPath);
            
        } else if (changeData.isDirectory()) {
            createDirectory(dropboxPath);
            
        } else {
            uploadFile(dropboxPath, changeData);
        }
    }

    private void uploadFile(String dropboxPath, LocalFolderData changeData) {
        File file = changeData.getPath().toFile();
        try (InputStream is = new FileInputStream(file)) {
            
            dropboxService.uploadFile(dropboxPath, is, changeData.getSize());
            
        } catch (IOException e) {
            LOG.error("Error when reading file for upload", e);
            throw new DSyncClientException(e);
        }
    }

    private void createDirectory(String dropboxPath) {
        dropboxService.createFolder(dropboxPath);
    }

    private void deleteData(String dropboxPath) {
        dropboxService.deleteFile(dropboxPath);
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

}
