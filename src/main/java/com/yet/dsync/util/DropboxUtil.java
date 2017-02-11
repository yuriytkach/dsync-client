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

import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.FolderMetadata;
import com.dropbox.core.v2.files.Metadata;
import com.yet.dsync.dto.DropboxChangeType;
import com.yet.dsync.dto.DropboxFileData;
import com.yet.dsync.dto.DropboxFileData.Builder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

public class DropboxUtil {

    public static DropboxFileData convertMetadata(final Metadata metadata) {
        final DropboxFileData.Builder builder = new Builder();
        builder
            .changeType(DropboxChangeType.fromMetadata(metadata))
            .pathDisplay(metadata.getPathDisplay())
            .pathLower(metadata.getPathLower());

        if (metadata instanceof FolderMetadata) {
            final FolderMetadata folderMetadata = (FolderMetadata) metadata;
            builder.id(folderMetadata.getId());

        } else if (metadata instanceof FileMetadata) {
            final FileMetadata fileMetadata = (FileMetadata) metadata;
            final ZoneId zoneId = ZoneOffset.UTC;
            final Instant clientModifiedInstant = fileMetadata.getClientModified().toInstant();
            final Instant serverModifiedInstant = fileMetadata.getServerModified().toInstant();
            builder
                .id(fileMetadata.getId())
                .rev(fileMetadata.getRev())
                .size(fileMetadata.getSize())
                .clientModified(LocalDateTime.ofInstant(clientModifiedInstant, zoneId))
                .serverModified(LocalDateTime.ofInstant(serverModifiedInstant, zoneId));
        }

        return builder.build();
    }

}
