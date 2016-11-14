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
import java.io.InputStreamReader;

import com.dropbox.core.DbxAppInfo;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.DbxRequestConfig.Builder;
import com.dropbox.core.DbxWebAuth;
import com.dropbox.core.DbxWebAuth.Request;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.ListFolderGetLatestCursorResult;
import com.dropbox.core.v2.users.FullAccount;
import com.dropbox.core.v2.users.SpaceUsage;
import com.yet.dsync.dto.UserData;
import com.yet.dsync.exception.DSyncClientException;
import com.yet.dsync.util.Config;

public class DropboxService {

    private DbxClientV2 client;
    private DbxRequestConfig config;

    public void createConfig() {
        Builder configBuilder = DbxRequestConfig.newBuilder("dsyncclient");
        config = configBuilder.withAutoRetryEnabled(3).withUserLocaleFromPreferences().build();
    }

    public void authenticate() {
        final String APP_KEY = "nysa3bywhz237k3";

        DbxAppInfo appInfo = new DbxAppInfo(APP_KEY, "none");

        Request request = DbxWebAuth.newRequestBuilder().withNoRedirect().build();
        DbxWebAuth webAuth = new DbxWebAuth(config, appInfo);
        String authWebUrl = webAuth.authorize(request);

        System.out.println("1. Go to: " + authWebUrl);
        System.out.println("2. Click \"Allow\" (you might have to log in first)");
        System.out.println("3. Copy the authorization code.");
        System.out.print("4. Input code and hit Enter: ");
        try {
            String code = new BufferedReader(new InputStreamReader(System.in)).readLine().trim();
            Config.getInstance().setAccessToken(code);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    public void createClient() {
        String accessToken = Config.getInstance().getAccessToken();
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

    public Thread createPollingThread() {
        return new Thread(new DropboxPolling(client));
    }

}
