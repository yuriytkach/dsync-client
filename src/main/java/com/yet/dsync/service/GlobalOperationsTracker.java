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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

/**
 * This class can track operations that are being processed either by Download or Upload services.
 * Its main functions are to track the start of the operation. And then when stop is called, to remove
 * the operation from track after some time. 
 * This class will allow to skip the recursive operation processing, when file that is downloaded from Dropbox
 * being picked up by the local folder watching service, and vice versa. 
 */
public class GlobalOperationsTracker {

    private static final int WAIT_TIME_BEFORE_TRACK_REMOVE_SEC = 3;

    private static final int SCHEDULED_POOL_SIZE = 5;

    private static final Logger LOG = LogManager.getLogger(GlobalOperationsTracker.class);
    
    private final ConcurrentMap<String, Boolean> trackMap = new ConcurrentHashMap<>();
    
    private final ScheduledExecutorService scheduledExecutorService;
    
    public GlobalOperationsTracker() {
        ThreadFactory namedThreadFactory = new ThreadFactoryBuilder()
                .setNameFormat("global-tracker-%d").build();
        
        scheduledExecutorService = Executors.newScheduledThreadPool(SCHEDULED_POOL_SIZE, namedThreadFactory);
    }

    public void startTracking(String pathLower) {
        trackMap.putIfAbsent(pathLower, Boolean.TRUE);
        LOG.trace("Added path to global tracking: {}", () -> pathLower);
    }
    
    public void stopTracking(String pathLower) {
        trackMap.put(pathLower, false);
        LOG.trace("Scheduled tracking stop for path: {}", () -> pathLower);    
        scheduledExecutorService.schedule(new RemoveTrackingThread(pathLower),
                WAIT_TIME_BEFORE_TRACK_REMOVE_SEC, TimeUnit.SECONDS);
    }
    
    public boolean isTracked(String pathLower) {
        return trackMap.getOrDefault(pathLower, Boolean.FALSE);
    }
    
    private class RemoveTrackingThread implements Runnable {
        
        private String pathLower;
        
        public RemoveTrackingThread(String pathLower) {
            this.pathLower = pathLower;
        }

        @Override
        public void run() {
            trackMap.remove(pathLower);
            LOG.trace("Competely removed path from global tracking: {}", ()->pathLower);
        }
        
    }
}
