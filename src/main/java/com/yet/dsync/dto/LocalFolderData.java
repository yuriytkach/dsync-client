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

import lombok.Data;

import java.nio.file.Path;

@Data
public class LocalFolderData  {

    private final Path path;
    private final LocalFolderChangeType changeType;

    public boolean isFile() {
        return path.toFile().isFile();
    }

    public boolean isDirectory() {
        return path.toFile().isDirectory();
    }

    public boolean fileExists() {
        return path.toFile().exists();
    }

    public long getSize() {
        return path.toFile().length();
    }

}