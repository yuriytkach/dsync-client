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
