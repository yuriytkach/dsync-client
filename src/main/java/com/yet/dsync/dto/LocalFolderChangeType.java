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
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent.Kind;

import com.yet.dsync.exception.DSyncClientException;

public enum LocalFolderChangeType {
    
    CREATE,
    
    MODIFY,
    
    DELETE;
    
    public static LocalFolderChangeType fromWatchEventKind(Kind<Path> watchEventKind) {
        if (watchEventKind == StandardWatchEventKinds.ENTRY_CREATE) {
            return CREATE;
            
        } else if (watchEventKind == StandardWatchEventKinds.ENTRY_MODIFY) {
            return MODIFY;
            
        } else if (watchEventKind == StandardWatchEventKinds.ENTRY_DELETE) {
            return DELETE;
            
        } else {
            throw new DSyncClientException("Unsupported watchEventKind " + watchEventKind);
        }
    }

}
