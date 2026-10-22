package com.yny.utils.telemetry;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Removes old local YNYUtils diagnostic lines without causing JVM memory stalls. */
public final class LocalLogRetention {
    private static final long RETENTION_MS = 30L * 60L * 1000L;
    private static final long MAX_FILE_SIZE = 2L * 1024L * 1024L; // 2 MB cap

    private LocalLogRetention() { }

    public static void prune(File directory) {
        if (directory == null || !directory.isDirectory()) return;
        File[] files = directory.listFiles((dir, name) -> name.matches(".*-\\d{8}\\.log"));
        if (files == null) return;
        Date cutoff = new Date(System.currentTimeMillis() - RETENTION_MS);
        String cutoffDay = new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(cutoff);
        String cutoffTime = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(cutoff);
        for (File file : files) {
            String name = file.getName();
            String day = name.substring(name.length() - 12, name.length() - 4);
            if (day.compareTo(cutoffDay) > 0) continue;
            try {
                // Expired previous days: remove outright
                if (day.compareTo(cutoffDay) < 0) {
                    Files.deleteIfExists(file.toPath());
                    continue;
                }
                // Cap oversized files to prevent memory and I/O lockup
                if (file.length() > MAX_FILE_SIZE) {
                    Files.deleteIfExists(file.toPath());
                    continue;
                }
                // Small files: skip pruning if under 256 KB to save disk operations
                if (file.length() < 256L * 1024L) {
                    continue;
                }
                List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
                List<String> keep = new ArrayList<String>(lines.size());
                for (String line : lines) {
                    if (line.length() >= 14 && line.charAt(0) == '[' &&
                            line.charAt(13) == ']' &&
                            line.substring(1, 13).compareTo(cutoffTime) >= 0) {
                        keep.add(line);
                    }
                }
                if (keep.size() == lines.size()) continue;
                if (keep.isEmpty()) {
                    Files.deleteIfExists(file.toPath());
                    continue;
                }
                Path temp = Files.createTempFile(directory.toPath(), "yny-log-retention-", ".tmp");
                try {
                    Files.write(temp, keep, StandardCharsets.UTF_8);
                    try {
                        Files.move(temp, file.toPath(), StandardCopyOption.ATOMIC_MOVE,
                                StandardCopyOption.REPLACE_EXISTING);
                    } catch (AtomicMoveNotSupportedException e) {
                        Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally {
                    Files.deleteIfExists(temp);
                }
            } catch (IOException ignored) {
                // Preserve the original file and try again during the next cleanup.
            }
        }
    }
}
