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

import java.nio.file.Path;

public class LocalFolderData  {
    
    private final Path path;
    private final LocalFolderChangeType changeType;
    
    public LocalFolderData(Path path, LocalFolderChangeType changeType) {
        this.path = path;
        this.changeType = changeType;
    }

    public Path getPath() {
        return path;
    }

    public LocalFolderChangeType getChangeType() {
        return changeType;
    }
    
    public boolean isFile() {
        return path.toFile().isFile();
    }
    
    public long getSize() {
        return path.toFile().length();
    }

    @Override
    public String toString() {
        return LocalFolderData.class.getSimpleName() 
                + " [changeType=" + changeType
                + ", path=" + path 
                + "]";
    }
    
    
}