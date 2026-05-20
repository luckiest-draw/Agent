package com.agent.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class CleanupTask {

    private static final Logger log = LoggerFactory.getLogger(CleanupTask.class);
    private static final long MAX_AGE_HOURS = 24;

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupOldChatImages() {
        Path dir = Paths.get("uploads/chat-images");
        if (!Files.exists(dir)) return;

        Instant cutoff = Instant.now().minus(MAX_AGE_HOURS, ChronoUnit.HOURS);
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.lastModifiedTime().toInstant().isBefore(cutoff)) {
                        try {
                            Files.delete(file);
                        } catch (IOException e) {
                            log.warn("Failed to delete old image: {}", file);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    try {
                        if (Files.list(dir).findAny().isEmpty()) {
                            Files.delete(dir);
                        }
                    } catch (IOException ignored) {}
                    return FileVisitResult.CONTINUE;
                }
            });
            log.info("Chat image cleanup completed");
        } catch (IOException e) {
            log.error("Chat image cleanup error: {}", e.getMessage());
        }
    }
}
