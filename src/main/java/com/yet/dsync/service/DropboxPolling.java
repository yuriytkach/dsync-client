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

import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.DeletedMetadata;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.FolderMetadata;
import com.dropbox.core.v2.files.ListFolderLongpollResult;
import com.dropbox.core.v2.files.ListFolderResult;
import com.yet.dsync.dto.UserData;
import com.yet.dsync.util.Config;

public class DropboxPolling implements Runnable {

    private DbxClientV2 client;

    public DropboxPolling(DbxClientV2 client) {
        this.client = client;
    }

    @Override
    public void run() {
        String cursor = Config.getInstance().getCursor();

        try {

            ListFolderResult listFolderResult = client.files()
                    .listFolderContinue(cursor);

            while (!Thread.interrupted()) {
                cursor = listFolderResult.getCursor();
                saveCursor(cursor);

                listFolderResult.getEntries().forEach(e -> {
                    if (e instanceof DeletedMetadata) {
                        System.out.println("DELETE " + e.getPathLower());
                    } else {
                        if (e instanceof FolderMetadata) {
                            System.out.println("FOLDER " + e.getPathLower());
                        } else {
                            FileMetadata fileMetadata = (FileMetadata) e;
                            System.out
                                    .println("FILE   " + e.getPathLower() + " ("
                                            + UserData.humanReadableByteCount(
                                                    fileMetadata.getSize())
                                            + ")");
                        }
                    }
                });

                if (listFolderResult.getHasMore()) {
                    listFolderResult = client.files()
                            .listFolderContinue(cursor);
                } else {
                    boolean changes = false;

                    while (!changes) {
                        ListFolderLongpollResult listFolderLongpollResult = client
                                .files().listFolderLongpoll(cursor);
                        changes = listFolderLongpollResult.getChanges();

                        if (!changes) {

                            Long backoff = listFolderLongpollResult
                                    .getBackoff();
                            if (backoff != null) {
                                try {
                                    Thread.sleep(backoff * 1000);
                                } catch (InterruptedException e1) {
                                    e1.printStackTrace();
                                }
                            }
                        }
                    }
                    listFolderResult = client.files()
                            .listFolderContinue(cursor);
                }
            }
        } catch (DbxException e1) {
            e1.printStackTrace();
        }
    }

    private void saveCursor(String cursor) {
        Config.getInstance().setCursor(cursor);
    }

}
