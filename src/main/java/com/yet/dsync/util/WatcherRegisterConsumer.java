package com.yet.dsync.util;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.Consumer;

//import com.sun.nio.file.SensitivityWatchEventModifier;

import com.yet.dsync.exception.DSyncClientException;

public class WatcherRegisterConsumer implements Consumer<Path> {

    private WatchService watchService;
    private Consumer<WatchKey> watchKeyConsumer;

    public WatcherRegisterConsumer(WatchService watchService, Consumer<WatchKey> watchKeyConsumer) {
        this.watchService = watchService;
        this.watchKeyConsumer = watchKeyConsumer;
    }

    @Override
    public void accept(Path p) {
        if (!p.toFile().exists() || !p.toFile().isDirectory()) {
            throw new DSyncClientException("folder " + p + " does not exist or is not a directory");
        }
        try {
            Files.walkFileTree(p, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    System.out.println("registering " + dir + " in watcher service");
                    WatchKey watchKey = dir
                            .register(watchService,
                                    new WatchEvent.Kind[] { StandardWatchEventKinds.ENTRY_CREATE,
                                            StandardWatchEventKinds.ENTRY_DELETE,
                                            StandardWatchEventKinds.ENTRY_MODIFY });
                    // , SensitivityWatchEventModifier.HIGH);
                    if (watchKeyConsumer != null) {
                        watchKeyConsumer.accept(watchKey);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new DSyncClientException("Error registering path " + p);
        }

    }

}
