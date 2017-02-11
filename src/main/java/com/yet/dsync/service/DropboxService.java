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

import com.dropbox.core.DbxAppInfo;
import com.dropbox.core.DbxAuthFinish;
import com.dropbox.core.DbxDownloader;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.DbxRequestConfig.Builder;
import com.dropbox.core.DbxWebAuth;
import com.dropbox.core.DbxWebAuth.Request;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.CommitInfo;
import com.dropbox.core.v2.files.DeleteErrorException;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.ListFolderGetLatestCursorResult;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.files.UploadBuilder;
import com.dropbox.core.v2.files.UploadSessionAppendV2Uploader;
import com.dropbox.core.v2.files.UploadSessionCursor;
import com.dropbox.core.v2.files.UploadSessionFinishUploader;
import com.dropbox.core.v2.files.UploadSessionStartResult;
import com.dropbox.core.v2.files.UploadSessionStartUploader;
import com.dropbox.core.v2.files.WriteMode;
import com.dropbox.core.v2.users.FullAccount;
import com.dropbox.core.v2.users.SpaceUsage;
import com.yet.dsync.dao.ConfigDao;
import com.yet.dsync.dto.DropboxFileData;
import com.yet.dsync.dto.UserData;
import com.yet.dsync.exception.DSyncClientException;
import com.yet.dsync.util.Config;
import com.yet.dsync.util.DropboxUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

public class DropboxService {

    private static final Logger LOG = LogManager.getLogger(DropboxService.class);

    /* According to API, can't upload chunks/files more than 150MB */
    private static final long MAX_FILE_UPLOAD_CHUNK = 150 * 1024 * 1024;

    private static final int MAX_RETRIES = 3;

    private static final int STATUS_IO_ERROR = -1;
    private static final int STATUS_DBX_ERROR = -2;

    private DbxClientV2 client;
    private DbxRequestConfig config;
    private final ConfigDao configDao;

    private String appKeyFromProvider = "YOUR_APP_KEY";

    private String appSecretFromProvider = "YOUR_APP_SECRET";

    public DropboxService(final ConfigDao configDao) {
        this.configDao = configDao;
    }

    public void createConfig() {
        final Builder configBuilder = DbxRequestConfig.newBuilder("dsyncclient");
        config = configBuilder.withAutoRetryEnabled(MAX_RETRIES).withUserLocaleFromPreferences().build();
    }

    @SuppressWarnings({"PMD.SystemPrintln", "PMD.DoNotCallSystemExit"})
    public void authenticate() {
        loadAppKeyProvider();
        final String appKey = loadAppKey();
        final String appSecret = loadAppSecret();

        final DbxAppInfo appInfo = new DbxAppInfo(appKey, appSecret);

        final Request request = DbxWebAuth.newRequestBuilder().withNoRedirect().build();
        final DbxWebAuth webAuth = new DbxWebAuth(config, appInfo);
        final String authWebUrl = webAuth.authorize(request);

        System.out.println("---------------------------");
        System.out.println("1. Go to: " + authWebUrl);
        System.out.println("2. Click \"Allow\" (you might have to log in first)");
        System.out.println("3. Copy the authorization code.");
        System.out.print("4. Input code and hit Enter: ");
        try {
            final String code = new BufferedReader(new InputStreamReader(System.in)).readLine().trim();

            final DbxAuthFinish finish = webAuth.finishFromCode(code);
            configDao.write(Config.ACCESS_TOKEN, finish.getAccessToken());

        } catch (final IOException ex) {
            LOG.error("IO error", ex);
            System.exit(STATUS_IO_ERROR);
        } catch (final DbxException ex) {
            LOG.error("Failed to authorize", ex);
            System.exit(STATUS_DBX_ERROR);
        }
    }

    private void loadAppKeyProvider() {
        try {
            final Class<?> clazz = Class.forName("com.yet.dsync.DSyncClientKeyProvider");
            LOG.debug("DSyncClientKeyProvider class was found");

            final Object obj = clazz.newInstance();

            final Method getKeyMethod = clazz.getDeclaredMethod("getKey");
            final Method getSecretMethod = clazz.getDeclaredMethod("getSecret");

            this.appKeyFromProvider = (String) getKeyMethod.invoke(obj);
            this.appSecretFromProvider = (String) getSecretMethod.invoke(obj);

            LOG.debug("Got key and secret from key provider");
        } catch (final Exception ex) {
            LOG.debug("DSyncClientKeyProvider class not found");
        }
    }

    private String loadAppSecret() {
        return System.getProperty("APP_SECRET", appSecretFromProvider);
    }

    private String loadAppKey() {
        return System.getProperty("APP_KEY", appKeyFromProvider);
    }

    public void createClient() {
        final String accessToken = configDao.read(Config.ACCESS_TOKEN);
        client = new DbxClientV2(config, accessToken);
    }

    public String retrieveLatestCursor() {
        try {
            final ListFolderGetLatestCursorResult result = client.files()
                    .listFolderGetLatestCursorBuilder(StringUtils.EMPTY)
                    .withRecursive(Boolean.TRUE).start();
            return result.getCursor();
        } catch (final DbxException ex) {
            throw new DSyncClientException(ex);
        }
    }

    public UserData retrieveUserData() {
        try {
            final FullAccount account = client.users().getCurrentAccount();
            final String username = account.getName().getDisplayName();

            final SpaceUsage space = client.users().getSpaceUsage();
            final long usedBytes = space.getUsed();
            final long availBytes = space.getAllocation().getIndividualValue().getAllocated();

            return new UserData(username, usedBytes, availBytes);
        } catch (final DbxException ex) {
            throw new DSyncClientException(ex);
        }
    }

    public Runnable createPollingThread(final DropboxChange changeListener) {
        return new DropboxPolling(client, configDao, changeListener);
    }

    public Runnable createInitialSyncThread(final DropboxChange changeListener) {
        return () -> {
            try {
                String cursor = configDao.read(Config.CURSOR);
                ListFolderResult listFolderResult = null;

                if (cursor.isEmpty()) {
                    listFolderResult = client.files()
                            .listFolderBuilder(StringUtils.EMPTY)
                            .withRecursive(Boolean.TRUE).start();

                    final Set<DropboxFileData> fileDataSet = listFolderResult.getEntries().stream()
                            .map(DropboxUtil::convertMetadata)
                            .collect(Collectors.toSet());

                    changeListener.processChange(fileDataSet);

                    cursor = listFolderResult.getCursor();
                    configDao.write(Config.CURSOR, cursor);
                }

                while (listFolderResult == null || listFolderResult.getHasMore()) {
                    listFolderResult = client.files().listFolderContinue(cursor);

                    final Set<DropboxFileData> fileDataSet = listFolderResult.getEntries().stream()
                            .map(DropboxUtil::convertMetadata)
                            .collect(Collectors.toSet());

                    changeListener.processChange(fileDataSet);

                    cursor = listFolderResult.getCursor();
                    configDao.write(Config.CURSOR, cursor);
                }

            } catch (final Exception ex) {
                LOG.error("Failed in initial sync", ex);
            }
        };
    }

    public void downloadFile(final String path, final OutputStream outputStream) {
        try {
            final DbxDownloader<FileMetadata> downloader = client.files().download(path);

            downloader.download(outputStream);
        } catch (final Exception ex) {
            LOG.error("Failed to download from Dropbox: " + path, ex);
            throw new DSyncClientException(ex);
        }
    }

    public void deleteFile(final String dropboxPath) {
        try {
            client.files().delete(dropboxPath);
        } catch (final DeleteErrorException ex) {
            if (ex.errorValue.getPathLookupValue().isNotFound()) {
                LOG.warn("Didn't delete, because path was not found on server: {}", () -> dropboxPath);
            } else {
                LOG.error("Failed to delete from Dropbox: " + dropboxPath, ex);
                throw new DSyncClientException(ex);
            }
        } catch (final DbxException ex) {
            LOG.error("Failed to delete from Dropbox: " + dropboxPath, ex);
            throw new DSyncClientException(ex);
        }
    }

    public DropboxFileData createFolder(final String dropboxPath) {
        try {
            final Metadata metadata = client.files().createFolder(dropboxPath);
            return DropboxUtil.convertMetadata(metadata);
        } catch (final DbxException ex) {
            LOG.error("Failed to create folder in Dropbox: " + dropboxPath, ex);
            throw new DSyncClientException(ex);
        }
    }

    public DropboxFileData uploadFile(final String dropboxPath,
                                      final InputStream inputStream,
                                      final long size,
                                      final Date lastModified,
                                      final boolean override) {
        try {

            final Metadata metadata;

            int chunks = (int) (size / MAX_FILE_UPLOAD_CHUNK);
            if (chunks * MAX_FILE_UPLOAD_CHUNK <= size) {
                chunks += 1;
            }

            final WriteMode writeMode = override ? WriteMode.OVERWRITE : WriteMode.ADD;
            final Boolean autoRename = override ? Boolean.FALSE : Boolean.TRUE;

            if (chunks == 1) {
                LOG.debug("File size is smaller than MAX. Uploading in single call ({})", () -> dropboxPath);
                final UploadBuilder uploadBuilder = client.files().uploadBuilder(dropboxPath);
                uploadBuilder.withClientModified(lastModified);
                uploadBuilder.withMode(writeMode);
                uploadBuilder.withAutorename(autoRename);
                metadata = uploadBuilder.uploadAndFinish(inputStream);

            } else {
                LOG.debug("Chunk upload (1 of {}) for {}", chunks, dropboxPath);
                final UploadSessionStartUploader startUploader = client.files().uploadSessionStart();
                final UploadSessionStartResult startResult = startUploader
                        .uploadAndFinish(inputStream, MAX_FILE_UPLOAD_CHUNK);
                int chunksUploaded = 1;

                final String sessionId = startResult.getSessionId();

                while (chunksUploaded < (chunks - 1)) {
                    LOG.debug("Chunk upload ({} of {}) for {}", chunksUploaded + 1, chunks, dropboxPath);
                    final UploadSessionCursor cursor = new UploadSessionCursor(
                            sessionId, chunksUploaded * MAX_FILE_UPLOAD_CHUNK);
                    final UploadSessionAppendV2Uploader appendUploader = client.files()
                            .uploadSessionAppendV2(cursor);
                    appendUploader.uploadAndFinish(inputStream, MAX_FILE_UPLOAD_CHUNK);
                    chunksUploaded++;
                }

                LOG.debug("Chunk upload ({} of {}) for {}", chunksUploaded + 1, chunks, dropboxPath);
                final UploadSessionCursor cursor = new UploadSessionCursor(
                        sessionId, chunksUploaded * MAX_FILE_UPLOAD_CHUNK);
                final CommitInfo commitInfo = new CommitInfo(dropboxPath, writeMode,
                        autoRename, lastModified, false);
                final UploadSessionFinishUploader finishUploader = client.files()
                        .uploadSessionFinish(cursor, commitInfo);
                metadata = finishUploader.uploadAndFinish(inputStream);

                LOG.debug("Upload completed for {}", () -> dropboxPath);
            }

            return DropboxUtil.convertMetadata(metadata);

        } catch (final Exception ex) {
            LOG.error("Failed to upload file to Dropbox: " + dropboxPath, ex);
            throw new DSyncClientException(ex);
        }
    }

}
