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

import com.yet.dsync.dto.UserData;
import com.yet.dsync.exception.DSyncClientException;
import com.yet.dsync.service.DropboxService;
import com.yet.dsync.util.Config;

public class DSyncClient {

    public static void main(String[] args) {
        new DSyncClient().start();
    }

    private DropboxService dropboxService = new DropboxService();

    private void start() {
        if (Config.getInstance().isFirstRun()) {
            initialStart();
        } else {
            normalStart();
        }

        greeting();
        run();
    }

    private void initialStart() {
        System.out.println("First run of the client.");
        System.out.println();

        dropboxService.createConfig();
        dropboxService.authenticate();
        dropboxService.createClient();
        String cursor = dropboxService.retrieveLatestCursor();
        Config.getInstance().setCursor(cursor);
        Config.getInstance().setFirstRun(false);

        System.out.println("Got latest cursor");
    }

    private void normalStart() {
        dropboxService.createConfig();
        dropboxService.createClient();
    }

    private void greeting() {
        UserData userData = dropboxService.retrieveUserData();

        System.out.println("Hello, " + userData.getUserName());
        System.out.println("Used storage " + userData.getUsedBytesDisplay()
                + " of " + userData.getAvailBytesDisplay());
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
