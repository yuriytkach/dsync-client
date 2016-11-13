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

package com.yet.dsync.dto;

public class UserData {

    public static String humanReadableByteCount(long bytes) {
        int unit = 1024;
        if (bytes < unit) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = ("KMGTPE").charAt(exp - 1) + "i";
        return String.format("%.2f %sB", bytes / Math.pow(unit, exp), pre);
    }

    private String userName;

    private long usedBytes;

    private long availBytes;

    public UserData(String userName, long usedBytes, long availBytes) {
        super();
        this.userName = userName;
        this.usedBytes = usedBytes;
        this.availBytes = availBytes;
    }

    public String getUserName() {
        return userName;
    }

    public long getUsedBytes() {
        return usedBytes;
    }

    public long getAvailBytes() {
        return availBytes;
    }

    public String getUsedBytesDisplay() {
        return humanReadableByteCount(usedBytes);
    }

    public String getAvailBytesDisplay() {
        return humanReadableByteCount(availBytes);
    }

}
