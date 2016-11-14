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

import com.yet.dsync.dao.ConfigDao;
import com.yet.dsync.dao.DatabaseInit;
import com.yet.dsync.dto.UserData;
import com.yet.dsync.exception.DSyncClientException;
import com.yet.dsync.service.DropboxService;
import com.yet.dsync.service.LocalFolderService;
import com.yet.dsync.util.Config;

public class DSyncClient {

    public static void main(String[] args) {
        new DSyncClient().start();
    }

    private DropboxService dropboxService;
    private LocalFolderService localFolderService;
    
    private ConfigDao configDao;

    private void start() {
        boolean firstRun = isFirstRun();
        
        initDao(firstRun);
        initServices();
        
        if (firstRun) {
            initialStart();
        } else {
            normalStart();
        }

        greeting();
        run();
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
    }
    
    private void initServices() {
        localFolderService = new LocalFolderService(configDao);
        dropboxService = new DropboxService(configDao);
    }

    private void initialStart() {
        System.out.println("First run of the client.");
        System.out.println();

        dropboxService.createConfig();
        dropboxService.authenticate();
        dropboxService.createClient();
        String cursor = dropboxService.retrieveLatestCursor();
        configDao.write(Config.CURSOR, cursor);

        System.out.println("Got latest cursor");
        
        localFolderService.setupLocalFolder();
    }

    private void normalStart() {
        dropboxService.createConfig();
        dropboxService.createClient();
    }

    private void greeting() {
        UserData userData = dropboxService.retrieveUserData();

        System.out.println("Hello, " + userData.getUserName());
        System.out.println("Used storage " + userData.getUsedBytesDisplay() + " of " + userData.getAvailBytesDisplay());
    }

    private void run() {
        Thread polling = dropboxService.createPollingThread();

        polling.start();

        System.out.println();
        System.out.println("Client is running. Use Ctrl+c to kill it.");

        printWarning();

        try {
            polling.join();
        } catch (InterruptedException e) {
            throw new DSyncClientException(e);
        }
    }

    private void printWarning() {
        System.out.println();
        System.out.println(
                "(Note. The client does not do much. For now it only logs events that happen in Dropbox folder on server)");
        System.out.println();
    }

}
