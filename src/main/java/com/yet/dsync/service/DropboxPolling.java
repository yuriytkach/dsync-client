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

import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.ListFolderLongpollResult;
import com.dropbox.core.v2.files.ListFolderResult;
import com.yet.dsync.dao.ConfigDao;
import com.yet.dsync.dto.DropboxFileData;
import com.yet.dsync.util.Config;
import com.yet.dsync.util.DropboxUtil;

public class DropboxPolling implements Runnable {
    
    private static final Logger LOG = LogManager.getLogger(DropboxPolling.class);

    private DbxClientV2 client;
    private ConfigDao configDao;
    private DropboxChange changeListener;

    public DropboxPolling(DbxClientV2 client, ConfigDao configDao, DropboxChange changeListener) {
        this.client = client;
        this.configDao = configDao;
        this.changeListener = changeListener;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("drpbx-poll");
        LOG.info("Started Dropbox polling");
        
        String cursor = readCursor();

        try {

            ListFolderResult listFolderResult = client.files().listFolderContinue(cursor);

            while (!Thread.interrupted()) {
                cursor = listFolderResult.getCursor();
                saveCursor(cursor);
                
                Set<DropboxFileData> fileDataSet = listFolderResult.getEntries().stream()
                    .map(DropboxUtil::convertMetadata)
                    .collect(Collectors.toSet());
                
                changeListener.processChange(fileDataSet);

                if (listFolderResult.getHasMore()) {
                    listFolderResult = client.files().listFolderContinue(cursor);
                } else {
                    boolean changes = false;

                    while (!changes) {
                        ListFolderLongpollResult listFolderLongpollResult = client.files().listFolderLongpoll(cursor);
                        changes = listFolderLongpollResult.getChanges();

                        if (!changes) {

                            Long backoff = listFolderLongpollResult.getBackoff();
                            if (backoff != null) {
                                try {
                                    Thread.sleep(backoff * 1000);
                                } catch (InterruptedException e1) {
                                    LOG.error("Interrupted", e1);
                                }
                            }
                        }
                    }
                    listFolderResult = client.files().listFolderContinue(cursor);
                }
            }
        } catch (DbxException e1) {
            LOG.error("Dropbox polling error", e1);
        }
    }

    private String readCursor() {
        return configDao.read(Config.CURSOR);
    }

    private void saveCursor(String cursor) {
        configDao.write(Config.CURSOR, cursor);
    }

}
