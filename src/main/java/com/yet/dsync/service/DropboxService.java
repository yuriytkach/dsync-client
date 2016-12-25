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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.ListFolderGetLatestCursorResult;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.files.UploadSessionAppendV2Uploader;
import com.dropbox.core.v2.files.UploadSessionCursor;
import com.dropbox.core.v2.files.UploadSessionFinishUploader;
import com.dropbox.core.v2.files.UploadSessionStartResult;
import com.dropbox.core.v2.files.UploadSessionStartUploader;
import com.dropbox.core.v2.files.UploadUploader;
import com.dropbox.core.v2.users.FullAccount;
import com.dropbox.core.v2.users.SpaceUsage;
import com.yet.dsync.dao.ConfigDao;
import com.yet.dsync.dto.DropboxFileData;
import com.yet.dsync.dto.UserData;
import com.yet.dsync.exception.DSyncClientException;
import com.yet.dsync.util.Config;
import com.yet.dsync.util.DropboxUtil;

public class DropboxService {
    
    private static final Logger LOG = LogManager.getLogger(DropboxService.class);
    
    /* According to API, can't upload chunks/files more than 150MB */
    private static final long MAX_FILE_UPLOAD_CHUNK = 150 * 1024 * 1024;

    private DbxClientV2 client;
    private DbxRequestConfig config;
    private ConfigDao configDao;

    private String appKeyFromProvider = "YOUR_APP_KEY";

    private String appSecretFromProvider = "YOUR_APP_SECRET";

    public DropboxService(ConfigDao configDao) {
        this.configDao = configDao;
    }

    public void createConfig() {
        Builder configBuilder = DbxRequestConfig.newBuilder("dsyncclient");
        config = configBuilder.withAutoRetryEnabled(3).withUserLocaleFromPreferences().build();
    }

    public void authenticate() {
        loadAppKeyProvider();
        final String APP_KEY = loadAppKey();
        final String APP_SECRET = loadAppSecret();
        
        DbxAppInfo appInfo = new DbxAppInfo(APP_KEY, APP_SECRET);

        Request request = DbxWebAuth.newRequestBuilder().withNoRedirect().build();
        DbxWebAuth webAuth = new DbxWebAuth(config, appInfo);
        String authWebUrl = webAuth.authorize(request);

        System.out.println("---------------------------");
        System.out.println("1. Go to: " + authWebUrl);
        System.out.println("2. Click \"Allow\" (you might have to log in first)");
        System.out.println("3. Copy the authorization code.");
        System.out.print("4. Input code and hit Enter: ");
        try {
            String code = new BufferedReader(new InputStreamReader(System.in)).readLine().trim();
            
            DbxAuthFinish finish = webAuth.finishFromCode(code);
            configDao.write(Config.ACCESS_TOKEN, finish.getAccessToken());
            
        } catch (IOException e) {
            LOG.error("IO error", e);
            System.exit(-1);
        } catch (DbxException e) {
            LOG.error("Failed to authorize", e);
            System.exit(-2);
        }
    }

    private void loadAppKeyProvider() {
        try {
            Class<?> clazz = Class.forName("com.yet.dsync.DSyncClientKeyProvider");
            LOG.debug("DSyncClientKeyProvider class was found");
            
            Object obj = clazz.newInstance();
            
            Method getKeyMethod = clazz.getDeclaredMethod("getKey");
            Method getSecretMethod = clazz.getDeclaredMethod("getSecret");
            
            this.appKeyFromProvider = (String)getKeyMethod.invoke(obj);
            this.appSecretFromProvider = (String)getSecretMethod.invoke(obj);
            
            LOG.debug("Got key and secret from key provider");
        } catch (Exception e) {
            LOG.debug("DSyncClientKeyProvider class not found");
        }
    }

    private String loadAppSecret() {
        final String APP_SECRET = System.getProperty("APP_SECRET", appSecretFromProvider);
        return APP_SECRET;
    }

    private String loadAppKey() {
        final String APP_KEY = System.getProperty("APP_KEY", appKeyFromProvider);
        return APP_KEY;
    }

    public void createClient() {
        String accessToken = configDao.read(Config.ACCESS_TOKEN);
        client = new DbxClientV2(config, accessToken);
    }

    public String retrieveLatestCursor() {
        try {
            ListFolderGetLatestCursorResult result = client.files().listFolderGetLatestCursorBuilder("")
                    .withRecursive(Boolean.TRUE).start();
            return result.getCursor();
        } catch (DbxException e) {
            throw new DSyncClientException(e);
        }
    }

    public UserData retrieveUserData() {
        try {
            FullAccount account = client.users().getCurrentAccount();
            String username = account.getName().getDisplayName();

            SpaceUsage space = client.users().getSpaceUsage();
            long usedBytes = space.getUsed();
            long availBytes = space.getAllocation().getIndividualValue().getAllocated();

            return new UserData(username, usedBytes, availBytes);
        } catch (DbxException e) {
            throw new DSyncClientException(e);
        }
    }

    public Runnable createPollingThread(DropboxChange changeListener) {
        return new DropboxPolling(client, configDao, changeListener);
    }
    
    public Runnable createInitialSyncThread(DropboxChange changeListener) {
        return ()-> {
            try {
                String cursor = configDao.read(Config.CURSOR);
                ListFolderResult listFolderResult = null;
                
                if (cursor.isEmpty()) {
                    listFolderResult = client.files().listFolderBuilder("")
                        .withRecursive(Boolean.TRUE).start();
                    
                    Set<DropboxFileData> fileDataSet = listFolderResult.getEntries().stream()
                            .map(DropboxUtil::convertMetadata)
                            .collect(Collectors.toSet());
                        
                    changeListener.processChange(fileDataSet);
                
                    cursor = listFolderResult.getCursor();
                    configDao.write(Config.CURSOR, cursor);
                }
                
                while (listFolderResult == null || listFolderResult.getHasMore()) {
                    listFolderResult = client.files().listFolderContinue(cursor);
                    
                    Set<DropboxFileData> fileDataSet = listFolderResult.getEntries().stream()
                            .map(DropboxUtil::convertMetadata)
                            .collect(Collectors.toSet());
                        
                    changeListener.processChange(fileDataSet);
                    
                    cursor = listFolderResult.getCursor();
                    configDao.write(Config.CURSOR, cursor);
                }
                
            } catch (Exception e) {
                LOG.error("Failed in initial sync", e);
            }
        };
    }
    
    public void downloadFile(String path, OutputStream outputStream) {
        try {
            DbxDownloader<FileMetadata> downloader = client.files().download(path);
            
            downloader.download(outputStream);
        } catch (Exception ex) {
            LOG.error("Failed to download from Dropbox: " + path, ex);
            throw new DSyncClientException(ex);
        }
    }

    public void deleteFile(String dropboxPath) {
        try {
            client.files().delete(dropboxPath);
        } catch (DbxException e) {
            LOG.error("Failed to delete from Dropbox: " + dropboxPath, e);
            throw new DSyncClientException(e);
        }
    }
    
    public DropboxFileData createFolder(String dropboxPath) {
        try {
            Metadata metadata = client.files().createFolder(dropboxPath);
            return DropboxUtil.convertMetadata(metadata);
        } catch (DbxException e) {
            LOG.error("Failed to create folder in Dropbox: " + dropboxPath, e);
            throw new DSyncClientException(e);
        }
    }

    public DropboxFileData uploadFile(String dropboxPath, InputStream inputStream, long size) {
        try {
            
            final Metadata metadata;
            
            int chunks = (int)(size / MAX_FILE_UPLOAD_CHUNK);
            if (chunks*MAX_FILE_UPLOAD_CHUNK < size) {
                chunks += 1;
            }
            
            if (chunks == 1) {
                LOG.debug("File size is smaller than MAX. Uploading in single call ({})", () -> dropboxPath);
                UploadUploader uploader = client.files().upload(dropboxPath);
                metadata = uploader.uploadAndFinish(inputStream);
                
            } else {
                LOG.debug("Chunk upload (1 of {}) for {}", chunks, dropboxPath);
                UploadSessionStartUploader startUploader = client.files().uploadSessionStart();
                UploadSessionStartResult startResult = startUploader.uploadAndFinish(inputStream, MAX_FILE_UPLOAD_CHUNK);
                int chunksUploaded = 1;
            
                String sessionId = startResult.getSessionId();
            
                while ( chunksUploaded < (chunks-1) ) {
                    LOG.debug("Chunk upload ({} of {}) for {}", chunksUploaded+1, chunks, dropboxPath);
                    UploadSessionCursor cursor = new UploadSessionCursor(sessionId, chunksUploaded*MAX_FILE_UPLOAD_CHUNK);
                    UploadSessionAppendV2Uploader appendUploader = client.files().uploadSessionAppendV2(cursor);
                    appendUploader.uploadAndFinish(inputStream, MAX_FILE_UPLOAD_CHUNK);
                    chunksUploaded++;
                }
                
                LOG.debug("Chunk upload ({} of {}) for {}", chunksUploaded+1, chunks, dropboxPath);
                UploadSessionCursor cursor = new UploadSessionCursor(sessionId, chunksUploaded*MAX_FILE_UPLOAD_CHUNK);
                CommitInfo commitInfo = new CommitInfo(dropboxPath);
                UploadSessionFinishUploader finishUploader = client.files().uploadSessionFinish(cursor, commitInfo);
                metadata = finishUploader.uploadAndFinish(inputStream);
                
                LOG.debug("Upload completed for {}", ()-> dropboxPath);
            }
            
            return DropboxUtil.convertMetadata(metadata);
            
        } catch (Exception ex) {
            LOG.error("Failed to upload file to Dropbox: " + dropboxPath, ex);
            throw new DSyncClientException(ex);
        }
    }

}
