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

package com.yet.dsync;

import java.io.File;
import java.sql.Connection;
import java.util.concurrent.CompletableFuture;

import com.yet.dsync.dao.ConfigDao;
import com.yet.dsync.dao.DatabaseInit;
import com.yet.dsync.dao.MetadataDao;
import com.yet.dsync.dto.UserData;
import com.yet.dsync.service.DropboxService;
import com.yet.dsync.service.DownloadService;
import com.yet.dsync.service.LocalFolderService;
import com.yet.dsync.util.Config;

public class DSyncClient {

    public static void main(String[] args) {
        new DSyncClient().start();
    }

    private DropboxService dropboxService;
    private LocalFolderService localFolderService;
    
    private DownloadService downloadService;
    
    private ConfigDao configDao;
    private MetadataDao metadataDao;

    private void start() {
        boolean firstRun = isFirstRun();
        
        initDao(firstRun);
        initServices();
        
        if (firstRun) {
            initialStart();            
        } else {
            normalStart();
        }

        CompletableFuture<Void> greetingFuture = CompletableFuture.runAsync(()->greeting());
        
        if (firstRun) {
            greetingFuture = initialSync(greetingFuture);
        }
        
        CompletableFuture<Void> downloadAllNotLoadedFuture = greetingFuture.thenRunAsync(() -> downloadService.downloadAllNotLoaded());
        
        CompletableFuture<Void> pollFuture = runPolling(downloadAllNotLoadedFuture);
        
        
        pollFuture.join();
    }

    private boolean isFirstRun() {
        String configDir = Config.getProgramConfigurationDirectory();
        File db = new File(configDir + File.separator + Config.DB_NAME);
        return ! db.exists();
    }
    
    private void initDao(boolean firstRun) {
        DatabaseInit dbInit = new DatabaseInit();
        
        String configDir = Config.getProgramConfigurationDirectory();
        
        File configDirFile = new File(configDir);
        if (!configDirFile.exists() && firstRun) {
            configDirFile.mkdirs();
        }
        
        Connection connection = dbInit.createConnection(configDirFile.getAbsolutePath(), Config.DB_NAME);
        if (firstRun) {
            dbInit.createTables(connection);
        }
        
        configDao = new ConfigDao(connection);
        metadataDao = new MetadataDao(connection);
    }
    
    private void initServices() {
        localFolderService = new LocalFolderService(configDao);
        dropboxService = new DropboxService(configDao);
        
        downloadService = new DownloadService(metadataDao, localFolderService);
    }

    private void initialStart() {
        System.out.println("First run of the client.");
        System.out.println();

        dropboxService.createConfig();
        dropboxService.authenticate();
        dropboxService.createClient();

        localFolderService.setupLocalFolder();
    }

    private void normalStart() {
        dropboxService.createConfig();
        dropboxService.createClient();
        
        localFolderService.validateLocalDir();
    }

    private void greeting() {
        UserData userData = dropboxService.retrieveUserData();

        System.out.println("Hello, " + userData.getUserName());
        System.out.println("Used storage " + userData.getUsedBytesDisplay() + " of " + userData.getAvailBytesDisplay());
        
        System.out.println();
        System.out.println("Client is running. Use Ctrl+c to kill it.");

        printWarning();
    }
    
    private void printWarning() {
        System.out.println();
        System.out.println(
                "(Note. The client does not do much. For now it only logs events that happen in Dropbox folder on server)");
        System.out.println();
    }
    
    private CompletableFuture<Void> initialSync(CompletableFuture<Void> prevFuture) {
        Runnable syncThread = dropboxService.createInitialSyncThread(fileData -> {
            // TODO: Needs optimization for batch processing
            System.out.println(fileData);
            metadataDao.write(fileData);
        });
        return prevFuture.thenRunAsync(syncThread);
    }

    private CompletableFuture<Void> runPolling(CompletableFuture<Void> prevFuture) {
        Runnable pollThread = dropboxService.createPollingThread(System.out::println);
        return prevFuture.thenRunAsync(pollThread);
    }

}
