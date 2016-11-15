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

import com.dropbox.core.v2.files.DeletedMetadata;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.FolderMetadata;
import com.dropbox.core.v2.files.Metadata;
import com.yet.dsync.exception.DSyncClientException;

public enum ChangeType {
    DELETE, 
    
    FOLDER, 
    
    FILE;

   
    public static ChangeType fromMetadata(Metadata metadata) {
        if (metadata instanceof DeletedMetadata) {
            return ChangeType.DELETE;
        
        } else if (metadata instanceof FolderMetadata) {
            return ChangeType.FOLDER;
            
        } else if (metadata instanceof FileMetadata) {
            return ChangeType.FILE;
            
        } else {
            throw new DSyncClientException("Unknow Metadata class " + metadata.getClass());
        }
    }

}