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

import java.time.LocalDateTime;

public class FileData {
    
    public static class Builder {
        private ChangeType changeType;
        
        private String pathDisplay;
        
        private String id;
        
        private String rev;
        
        private Long size;
        
        private LocalDateTime serverModified;
        
        private LocalDateTime clientModified;
        
        public Builder changeType(ChangeType changeType) {
            this.changeType = changeType;
            return this;
        }
        
        public Builder pathDisplay(String pathDisplay) {
            this.pathDisplay = pathDisplay;
            return this;
        }
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder rev(String rev) {
            this.rev = rev;
            return this;
        }
        
        public Builder size(Long size) {
            this.size = size;
            return this;
        }
        
        public Builder serverModified(LocalDateTime serverModified) {
            this.serverModified = serverModified;
            return this;
        }
        
        public Builder clientModified(LocalDateTime clientModified) {
            this.clientModified = clientModified;
            return this;
        }
        
        public FileData build() {
            return new FileData(this);
        }
    }

    private final ChangeType changeType;
    
    private final String pathDisplay;
    
    private final String id;
    
    private final String rev;
    
    private final Long size;
    
    private final LocalDateTime serverModified;
    
    private final LocalDateTime clientModified;

    private FileData(Builder builder) {
        this.changeType = builder.changeType;
        this.pathDisplay = builder.pathDisplay;
        this.id = builder.id;
        this.rev = builder.rev;
        this.size = builder.size;
        this.serverModified = builder.serverModified;
        this.clientModified = builder.clientModified;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    public String getPathDisplay() {
        return pathDisplay;
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
        
        if (ChangeType.FOLDER == changeType) {
            str += ", id: " + id;
        } else if (ChangeType.FILE == changeType) {
            str += ", id: " + id + ", rev: " + rev + ", size: " + size + ", client: " + clientModified + ", server: " + serverModified;
        }
        
        str += "]";
        return str;
    }
    
    

}
