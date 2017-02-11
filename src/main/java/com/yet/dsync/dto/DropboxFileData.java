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

import java.time.LocalDateTime;

public class DropboxFileData {

    public static class Builder {
        private DropboxChangeType changeType;

        private String pathDisplay;

        private String pathLower;

        private String id;

        private String rev;

        private Long size;

        private LocalDateTime serverModified;

        private LocalDateTime clientModified;

        public Builder changeType(final DropboxChangeType changeType) {
            this.changeType = changeType;
            return this;
        }

        public Builder pathDisplay(final String pathDisplay) {
            this.pathDisplay = pathDisplay;
            return this;
        }

        public Builder pathLower(final String pathLower) {
            this.pathLower = pathLower;
            return this;
        }

        public Builder id(final String id) {
            this.id = id;
            return this;
        }

        public Builder rev(final String rev) {
            this.rev = rev;
            return this;
        }

        public Builder size(final Long size) {
            this.size = size;
            return this;
        }

        public Builder serverModified(final LocalDateTime serverModified) {
            this.serverModified = serverModified;
            return this;
        }

        public Builder clientModified(final LocalDateTime clientModified) {
            this.clientModified = clientModified;
            return this;
        }

        public Builder init(final DropboxFileData fileData) {
            this.changeType = fileData.changeType;
            this.clientModified = fileData.clientModified;
            this.id = fileData.id;
            this.pathDisplay = fileData.pathDisplay;
            this.pathLower = fileData.pathLower;
            this.rev = fileData.rev;
            this.serverModified = fileData.serverModified;
            this.size = fileData.size;
            return this;
        }

        public DropboxFileData build() {
            return new DropboxFileData(this);
        }
    }

    private final DropboxChangeType changeType;

    private final String pathDisplay;

    private final String pathLower;

    private final String id;

    private final String rev;

    private final Long size;

    private final LocalDateTime serverModified;

    private final LocalDateTime clientModified;

    private DropboxFileData(final Builder builder) {
        this.changeType = builder.changeType;
        this.pathDisplay = builder.pathDisplay;
        this.pathLower = builder.pathLower;
        this.id = builder.id;
        this.rev = builder.rev;
        this.size = builder.size;
        this.serverModified = builder.serverModified;
        this.clientModified = builder.clientModified;
    }

    public boolean isFile() {
        return rev != null;
    }

    public boolean isDirectory() {
        return id != null && rev == null;
    }

    public DropboxChangeType getChangeType() {
        return changeType;
    }

    public String getPathDisplay() {
        return pathDisplay;
    }

    public String getPathLower() {
        return pathLower;
    }

    public String getId() {
        return id;
    }

    public String getRev() {
        return rev;
    }

    public Long getSize() {
        return size;
    }

    public LocalDateTime getServerModified() {
        return serverModified;
    }

    public LocalDateTime getClientModified() {
        return clientModified;
    }

    @Override
    public String toString() {
        String str = this.getClass().getSimpleName() + " [" + changeType + " " + pathDisplay;

        if (DropboxChangeType.FOLDER == changeType) {
            str += ", id: " + id;
        } else if (DropboxChangeType.FILE == changeType) {
            str += ", id: " + id + ", rev: " + rev + ", size: " + size + ", client: " + clientModified + ", server: " + serverModified;
        }

        str += "]";
        return str;
    }



}
