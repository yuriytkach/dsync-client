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

package com.yet.dsync.util;

import java.io.File;

public enum Config {

    ACCESS_TOKEN,

    CURSOR,

    LOCAL_DIR,

    INITIAL_SYNC;

    public static final String DB_NAME  = "dsync.db";

    public static String getProgramConfigurationDirectory() {
        return System.getProperty("user.home") + File.separator + ".dsyncclient";
    }

}
