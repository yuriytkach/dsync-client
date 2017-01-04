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

import java.io.File;
import java.nio.file.Path;

import org.apache.commons.io.FilenameUtils;

public class PathUtil {

    public static String extractDropboxPath(String localDir, String fullPath) {
        localDir = FilenameUtils.normalize(localDir, true);
        fullPath = FilenameUtils.normalize(fullPath, true);
        return fullPath.substring(localDir.length()).trim();
    }

    public static String extractDropboxPath(File localDir, Path fullPath) {
        String localDirAbsolutePath = localDir.getAbsolutePath();
        String fullPathAbsolutePath = fullPath.toAbsolutePath().toString();
        return extractDropboxPath(localDirAbsolutePath, fullPathAbsolutePath);
    }

    public static String extractDropboxPath(String localDirAbsolutePath, Path fullPath) {
        String fullPathAbsolutePath = fullPath.toAbsolutePath().toString();
        return extractDropboxPath(localDirAbsolutePath, fullPathAbsolutePath);
    }

}
