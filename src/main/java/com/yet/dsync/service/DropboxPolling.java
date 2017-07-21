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

import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.ListFolderLongpollResult;
import com.dropbox.core.v2.files.ListFolderResult;
import com.yet.dsync.dao.ConfigDao;
import com.yet.dsync.dto.DropboxFileData;
import com.yet.dsync.util.Config;
import com.yet.dsync.util.DropboxUtil;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class DropboxPolling implements Runnable {

    private static final Logger LOG = LogManager.getLogger(DropboxPolling.class);
    private static final int MILLI_SEC = 1000;

    private final DbxClientV2 client;
    private final ConfigDao configDao;
    private final DropboxChange changeListener;

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

                final Set<DropboxFileData> fileDataSet = listFolderResult.getEntries().stream()
                    .map(DropboxUtil::convertMetadata)
                    .collect(Collectors.toSet());

                changeListener.processChange(fileDataSet);

                if (listFolderResult.getHasMore()) {
                    listFolderResult = client.files().listFolderContinue(cursor);
                } else {
                    boolean changes = false;

                    while (!changes) {
                        try {
                            final ListFolderLongpollResult listFolderLongpollResult = client
                                    .files().listFolderLongpoll(cursor);

                            changes = listFolderLongpollResult.getChanges();

                            if (!changes) {

                                final Long backoff = listFolderLongpollResult.getBackoff();
                                if (backoff != null) {
                                    try {
                                        Thread.sleep(backoff * MILLI_SEC);
                                    } catch (final InterruptedException ex) {
                                        LOG.error("Interrupted", ex);
                                    }
                                }
                            }
                        } catch (final DbxException ex) {
                            LOG.warn("Failed during long poll: {}", ex.getMessage());
                            LOG.warn("Retrying long poll");
                        }
                    }
                    listFolderResult = client.files().listFolderContinue(cursor);
                }
            }
        } catch (final DbxException ex) {
            LOG.error("Dropbox polling error", ex);
        }
    }

    private String readCursor() {
        return configDao.read(Config.CURSOR);
    }

    private void saveCursor(final String cursor) {
        configDao.write(Config.CURSOR, cursor);
    }

}
