package com.yet.dsync.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.yet.dsync.dao.MetadataDao;
import com.yet.dsync.dto.LocalFolderData;

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
        LOG.warn("Doing nothing :)");
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
        return !changeData.exists();
    }

}
