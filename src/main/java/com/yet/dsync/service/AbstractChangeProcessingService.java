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

import java.util.Comparator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.yet.dsync.exception.DSyncClientException;

public abstract class AbstractChangeProcessingService<T> {

    private static final Logger LOG = LogManager
            .getLogger(AbstractChangeProcessingService.class);

    private static final int QUICK_THREAD_NUMBER = 5;
    private static final int SLOW_THREAD_NUMBER = 2;

    private static final long SLOW_THRESHOLD = 256 * 1024; // 256KB

    private final Comparator<? super T> changeComparator = (a, b) -> {
        if (!isFile(a) && isFile(b)) {
            return -1;
        } else if (isFile(a) && !isFile(b)) {
            return 1;
        } else if (isFile(a) && isFile(b)) {
            return Long.compare(getFileSize(a), getFileSize(b));
        } else if (isDeleteData(a) && !isDeleteData(b)) {
            return -1;
        } else if (!isDeleteData(a) && isDeleteData(b)) {
            return 1;
        } else {
            return 0;
        }
    };

    private final BlockingQueue<T> quickProcessingQueue;
    private final BlockingQueue<T> slowProcessingQueue;

    private final ExecutorService executorService;

    public AbstractChangeProcessingService(String processingThreadName) {
        this.slowProcessingQueue = createDownloadQueue(changeComparator);
        this.quickProcessingQueue = createDownloadQueue(changeComparator);

        ThreadFactory namedThreadFactory = new ThreadFactoryBuilder()
                .setNameFormat(processingThreadName + "-%d").build();

        this.executorService = Executors.newFixedThreadPool(
                SLOW_THREAD_NUMBER + QUICK_THREAD_NUMBER, namedThreadFactory);

        initDownloadThreads();
    }

    /**
     * Creating PriorityBlockingQueue. Note! It is unbounded, so in the future
     * we might need to enhance it to make it bounded, so the caller will block
     * before putting next elem them. Also, we can think of different download
     * threads, some of which will handle only directories and small files,
     * another will handle only big files. For now, we make priority in the
     * following way: folders, small files, big files;
     */
    private BlockingQueue<T> createDownloadQueue(
            Comparator<? super T> changeComparator) {
        return new PriorityBlockingQueue<T>(100, changeComparator);
    }

    private void initDownloadThreads() {
        for (int i = 0; i < QUICK_THREAD_NUMBER; i++) {
            executorService.submit(new ProcessingThread(quickProcessingQueue));
        }

        for (int i = 0; i < SLOW_THREAD_NUMBER; i++) {
            executorService.submit(new ProcessingThread(slowProcessingQueue));
        }
    }

    protected abstract void processChange(T changeData);

    protected abstract boolean isFile(T changeData);

    protected abstract boolean isDeleteData(T changeData);

    protected abstract long getFileSize(T changeData);

    /**
     * If the changeData is file, then scheduling it either in quick or slow
     * processing queue based on size.
     * 
     * Otherwise, scheduling it to quick processing queue.
     * 
     * @param changeData
     *            Change data object that needs to be scheduled for processing
     */
    public void scheduleProcessing(T changeData) {
        try {
            if (isFile(changeData)) {
                long size = getFileSize(changeData);
                if (size > SLOW_THRESHOLD) {
                    slowProcessingQueue.put(changeData);
                } else {
                    quickProcessingQueue.put(changeData);
                }
            } else {
                quickProcessingQueue.put(changeData);
            }
        } catch (Exception e) {
            throw new DSyncClientException(e);
        }
    }

    /**
     * Processing thread that will take change data from the queue and call the
     * {@link #processChange(Object)} method
     */
    private class ProcessingThread implements Runnable {

        private BlockingQueue<T> queue;

        public ProcessingThread(BlockingQueue<T> queue) {
            this.queue = queue;
        }

        @Override
        public void run() {
            while (!Thread.interrupted()) {
                T changeData;
                try {
                    changeData = queue.take();
                    processChange(changeData);
                } catch (InterruptedException e) {
                    LOG.error("Interrupted", e);
                }
            }
        }
    }

}
