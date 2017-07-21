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

package com.yet.dsync.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder(toBuilder = true)
public final class DropboxFileData {

    private final DropboxChangeType changeType;

    private final String pathDisplay;

    private final String pathLower;

    private final String id;

    private final String rev;

    private final Long size;

    private final LocalDateTime serverModified;

    private final LocalDateTime clientModified;

    public boolean isFile() {
        return rev != null;
    }

    public boolean isDirectory() {
        return id != null && rev == null;
    }

    @Override
    public String toString() {
        String str = this.getClass().getSimpleName() + " [" + changeType + " " + pathDisplay;

        if (DropboxChangeType.FOLDER == changeType) {
            str += ", id: " + id;
        } else if (DropboxChangeType.FILE == changeType) {
            str += ", id: " + id + ", rev: " + rev + ", size: " + size
                    + ", client: " + clientModified + ", server: " + serverModified;
        }

        str += "]";
        return str;
    }
}
