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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.stream.Collectors;

import com.dropbox.core.DbxAppInfo;
import com.dropbox.core.DbxAuthFinish;
import com.dropbox.core.DbxDownloader;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.DbxRequestConfig.Builder;
import com.dropbox.core.DbxWebAuth;
import com.dropbox.core.DbxWebAuth.Request;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.ListFolderGetLatestCursorResult;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.users.FullAccount;
import com.dropbox.core.v2.users.SpaceUsage;
import com.yet.dsync.dao.ConfigDao;
import com.yet.dsync.dto.FileData;
import com.yet.dsync.dto.UserData;
import com.yet.dsync.exception.DSyncClientException;
import com.yet.dsync.util.Config;
import com.yet.dsync.util.DropboxUtil;

public class DropboxService {

    private DbxClientV2 client;
    private DbxRequestConfig config;
    private ConfigDao configDao;

    public DropboxService(ConfigDao configDao) {
        this.configDao = configDao;
    }

    public void createConfig() {
        Builder configBuilder = DbxRequestConfig.newBuilder("dsyncclient");
        config = configBuilder.withAutoRetryEnabled(3).withUserLocaleFromPreferences().build();
    }

    public void authenticate() {
        final String APP_KEY = System.getProperty("APP_KEY", "YOUR_APP_KEY");
        final String APP_SECRET = System.getProperty("APP_SECRET", "YOUR_APP_SECRET");
        
        DbxAppInfo appInfo = new DbxAppInfo(APP_KEY, APP_SECRET);

        Request request = DbxWebAuth.newRequestBuilder().withNoRedirect().build();
        DbxWebAuth webAuth = new DbxWebAuth(config, appInfo);
        String authWebUrl = webAuth.authorize(request);

        System.out.println("1. Go to: " + authWebUrl);
        System.out.println("2. Click \"Allow\" (you might have to log in first)");
        System.out.println("3. Copy the authorization code.");
        System.out.print("4. Input code and hit Enter: ");
        try {
            String code = new BufferedReader(new InputStreamReader(System.in)).readLine().trim();
            
            DbxAuthFinish finish = webAuth.finishFromCode(code);
            configDao.write(Config.ACCES_TOKEN, finish.getAccessToken());
            
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        } catch (DbxException e) {
            System.err.println("Failed to authorize: " + e.getLocalizedMessage());
            System.exit(-2);
        }
    }

    public void createClient() {
        String accessToken = configDao.read(Config.ACCES_TOKEN);
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
                    
                    Set<FileData> fileDataSet = listFolderResult.getEntries().stream()
                            .map(DropboxUtil::convertMetadata)
                            .collect(Collectors.toSet());
                        
                    changeListener.processChange(fileDataSet);
                
                    cursor = listFolderResult.getCursor();
                    configDao.write(Config.CURSOR, cursor);
                }
                
                while (listFolderResult == null || listFolderResult.getHasMore()) {
                    listFolderResult = client.files().listFolderContinue(cursor);
                    
                    Set<FileData> fileDataSet = listFolderResult.getEntries().stream()
                            .map(DropboxUtil::convertMetadata)
                            .collect(Collectors.toSet());
                        
                    changeListener.processChange(fileDataSet);
                    
                    cursor = listFolderResult.getCursor();
                    configDao.write(Config.CURSOR, cursor);
                }
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        };
    }
    
    public void downloadFile(String path, FileOutputStream fileOutputStream) {
        try {
            
            DbxDownloader<FileMetadata> downloader = client.files().download(path);
            
            downloader.download(fileOutputStream);
            
        } catch (Exception ex) {
            throw new DSyncClientException(ex);
        }
    }

}
