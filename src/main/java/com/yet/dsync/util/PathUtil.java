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

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.nio.file.Path;

public class PathUtil {

    public static String extractDropboxPath(final String localDir, final String fullPath) {
        final String localDirNorm = FilenameUtils.normalize(localDir, true);
        final String fullPathNorm = FilenameUtils.normalize(fullPath, true);
        return fullPathNorm.substring(localDirNorm.length()).trim();
    }

    public static String extractDropboxPath(final File localDir, final Path fullPath) {
        final String localDirAbsolutePath = localDir.getAbsolutePath();
        final String fullPathAbsolutePath = fullPath.toAbsolutePath().toString();
        return extractDropboxPath(localDirAbsolutePath, fullPathAbsolutePath);
    }

    public static String extractDropboxPath(final String localDirAbsolutePath, final Path fullPath) {
        final String fullPathAbsolutePath = fullPath.toAbsolutePath().toString();
        return extractDropboxPath(localDirAbsolutePath, fullPathAbsolutePath);
    }

}
