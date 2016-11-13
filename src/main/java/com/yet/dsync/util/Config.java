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

package com.yet.dsync.util;

import java.util.prefs.Preferences;

import com.yet.dsync.DSyncClient;

public class Config {

    private static final String KEY_FIRST_RUN = "first.run";

    private static final String ACCES_TOKEN = "access.token";

    private static final String CURSOR = "cursor";

    private static Config _instance = new Config();

    public static Config getInstance() {
        return _instance;
    }

    private Preferences prefs = Preferences
            .userNodeForPackage(DSyncClient.class);

    public boolean isFirstRun() {
        return prefs.getBoolean(KEY_FIRST_RUN, true);
    }

    public void setFirstRun(boolean firstRun) {
        prefs.putBoolean(KEY_FIRST_RUN, firstRun);
    }

    public String getAccessToken() {
        return prefs.get(ACCES_TOKEN, "");
    }

    public void setAccessToken(String token) {
        prefs.put(ACCES_TOKEN, token);
    }

    public String getCursor() {
        return prefs.get(CURSOR, "");
    }

    public void setCursor(String cursor) {
        prefs.put(CURSOR, cursor);
    }

}
