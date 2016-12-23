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

package com.yet.dsync.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.yet.dsync.dao.ConfigDao;
import com.yet.dsync.exception.DSyncClientException;
import com.yet.dsync.util.Config;

public class LocalFolderService {
    
    private static final Logger LOG = LogManager.getLogger(LocalFolderService.class);

    private final ConfigDao configDao;
    
    private File localDir;
    
    public LocalFolderService(ConfigDao configDao) {
        this.configDao = configDao;
    }

    public void setupLocalFolder() {
        System.out.print("Input local folder to use for Dropbox: ");

        try {
            String folder = new BufferedReader(new InputStreamReader(System.in)).readLine().trim();

            File dir = new File(folder);
            if (dir.exists()) {
                System.out.print("WARNING! The folder exists. All its contents will be deleted. Continue? [y/n] ");
                char answer = (char) new BufferedReader(new InputStreamReader(System.in)).read();
                System.out.println();
                if (answer == 'y') {
                    FileUtils.deleteDirectory(dir);
                } else {
                    throw new DSyncClientException("Cancelled");
                }
            }
            if (dir.mkdirs()) {
                System.out.println("Created directory " + dir.getAbsolutePath());
                
                configDao.write(Config.LOCAL_DIR, dir.getAbsolutePath());
            } else {
                System.out.println("FAILED to create directory");
            }
        } catch (IOException e) {
            throw new DSyncClientException(e);
        }
    }
    
    public void checkOrSetupLocalDir() {
        String localDirPath = configDao.read(Config.LOCAL_DIR);
        if (StringUtils.isBlank(localDirPath)) {
            setupLocalFolder();
            localDirPath = configDao.read(Config.LOCAL_DIR);
        }
        
        localDir = new File(localDirPath);
        if ( ! localDir.exists() ) {
            LOG.info("Local folder does not exist: {}", ()->localDir.getAbsolutePath());
            setupLocalFolder();
            localDirPath = configDao.read(Config.LOCAL_DIR);
            localDir = new File(localDirPath);
        }
        
        LOG.debug("Local folder: {}", ()->localDir.getAbsolutePath());
    }
    
    public void createFolder(String path) {
        File folder = new File(localDir.getAbsolutePath() + path);
        
        if ( ! folder.exists() ) {
            if ( ! folder.mkdirs() ) {
                throw new DSyncClientException("Failed in creating directories at " + folder.getAbsolutePath());
            }
        }
    }
    
    public void deleteFileOrFolder(String path) {
        File file = buildFileObject(path);
        if (file.exists()) {
            if (file.isDirectory()) {
                try {
                    FileUtils.deleteDirectory(file);
                } catch (Exception e) {
                    throw new DSyncClientException("Failed to delete directory: " + file.getAbsolutePath(), e);
                }
            } else {
                if ( !file.delete() ) {
                    throw new DSyncClientException("Failed to delete file: " + file.getAbsolutePath());
                }
            }
        }
    }
    
    public File buildFileObject(String path) {
        return new File(localDir.getAbsolutePath() + path);
    }
    
    public String extractDropboxPath(Path path) {
        String fullFilePath = path.toAbsolutePath().toString();
        String fullLocalDirPath = localDir.getAbsolutePath();
        return fullFilePath.substring(fullLocalDirPath.length()).trim();
    }
    
    public Runnable createFolderWatchingThread(LocalFolderChange changeListener) {
        return new LocalFolderWatching(localDir.getAbsolutePath(), changeListener);
    }

}
