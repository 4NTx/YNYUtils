package com.yny.utils.core;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.lang.reflect.Field;
import com.yny.utils.telemetry.RemoteLogUploader;
import com.yny.utils.telemetry.LocalLogRetention;

/** High-performance bounded asynchronous logger. Game callbacks never block on disk I/O. */
public final class DebugLog {
    private static final int QUEUE_CAPACITY = 4096;
    private static final long RETENTION_MS = 30L * 60L * 1000L;
    private static final long MAX_TOTAL_BYTES = 32L * 1024L * 1024L;
    private static final long CLEANUP_INTERVAL_MS = 60L * 1000L;
    private static final ArrayBlockingQueue<Entry> QUEUE = new ArrayBlockingQueue<Entry>(QUEUE_CAPACITY);
    private static final AtomicLong DROPPED = new AtomicLong();
    private static final Map<String, String> LAST_STATE = new ConcurrentHashMap<String, String>();
    private static volatile File directory;
    private static volatile boolean enabled = false;
    private static volatile boolean started;
    private static long lastLimitEnforcement;

    private static volatile long lastSecond = -1L;
    private static volatile String lastSecondPrefix = "";
    private static final SimpleDateFormat SECOND_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    private DebugLog() { }

    /** May be called when the game starts; first write also initializes lazily. */
    public static synchronized void initialize(File gameDirectory, boolean isEnabled) {
        enabled = isEnabled;
        if (directory == null && gameDirectory != null) {
            directory = new File(new File(gameDirectory, "logs"), "ynyutils");
        }
        startWriter();
        RemoteLogUploader.resume(directory);
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void state(String category, String key, String value) {
        if (!enabled && !"Command".equals(category)) return;
        String stateKey = sanitize(category) + ":" + String.valueOf(key);
        String next = String.valueOf(value);
        String prior = LAST_STATE.put(stateKey, next);
        if (!next.equals(prior)) write(category, "state=" + key + "; value=" + next);
    }

    public static void write(String category, String message) {
        if (!enabled && !"Command".equals(category)) return;
        ensureInitialized();
        String safeCategory = sanitize(category);
        String timestamp = timestampFast();
        Entry entry = new Entry(timestamp, safeCategory, String.valueOf(message));
        if (!RemoteLogUploader.offerLossless(QUEUE, entry)) {
            DROPPED.incrementAndGet();
        }
    }

    private static synchronized String timestampFast() {
        long now = System.currentTimeMillis();
        long sec = now / 1000L;
        if (sec != lastSecond) {
            lastSecond = sec;
            lastSecondPrefix = SECOND_FORMAT.format(new Date(sec * 1000L));
        }
        int ms = (int)(now % 1000L);
        return lastSecondPrefix + String.format(Locale.ROOT, ".%03d", ms);
    }

    private static synchronized void ensureInitialized() {
        if (directory == null) {
            try {
                File mcDir = resolveMinecraftDir();
                if (mcDir == null) return;
                directory = new File(new File(mcDir, "logs"), "ynyutils");
            } catch (Throwable ignored) { return; }
        }
        startWriter();
        RemoteLogUploader.resume(directory);
    }

    private static File resolveMinecraftDir() {
        try {
            Class<?> aveClass = Class.forName("ave");
            Object mc = aveClass.getMethod("A").invoke(null);
            if (mc != null) {
                try {
                    Field f = aveClass.getDeclaredField("v");
                    f.setAccessible(true);
                    return (File) f.get(mc);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        try {
            String mcName = "net." + "minecraft." + "client." + "Minecraft";
            Class<?> mcClass = Class.forName(mcName);
            Object mc = mcClass.getMethod("getMinecraft").invoke(null);
            if (mc != null) {
                Field f = mcClass.getDeclaredField("mcDataDir");
                f.setAccessible(true);
                return (File) f.get(mc);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void startWriter() {
        if (started || directory == null) return;
        started = true;
        Thread writer = new Thread(new Runnable() { public void run() { runWriter(); } }, "YNYUtils-LogWriter");
        writer.setDaemon(true);
        writer.start();
    }

    private static void runWriter() {
        long lastCleanup = 0L;
        java.util.List<Entry> batch = new java.util.ArrayList<Entry>(128);
        for (;;) {
            try {
                Entry first = QUEUE.poll(500L, TimeUnit.MILLISECONDS);
                if (first != null) {
                    batch.add(first);
                    QUEUE.drainTo(batch, 127);
                    appendBatch(batch);
                    batch.clear();
                }
                long now = System.currentTimeMillis();
                if (now - lastCleanup >= CLEANUP_INTERVAL_MS) {
                    cleanup(now);
                    lastCleanup = now;
                }
                long lost = DROPPED.getAndSet(0L);
                if (lost > 0L) {
                    append(new Entry(timestampFast(), "System", "event=log_queue_overflow dropped=" + lost));
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable ignored) {
                // Logging must never interrupt gameplay or terminate the writer.
            }
        }
    }

    private static void append(Entry entry) throws IOException {
        appendBatch(java.util.Collections.singletonList(entry));
    }

    private static void appendBatch(java.util.List<Entry> entries) {
        File dir = directory;
        if (dir == null || entries.isEmpty()) return;
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) return;

        Map<File, StringBuilder> fileBuffers = new LinkedHashMap<File, StringBuilder>();
        for (Entry entry : entries) {
            String day = entry.timestamp.substring(0, 10).replace("-", "");
            File file = new File(dir, fileGroup(entry.category) + "-" + day + ".log");
            StringBuilder sb = fileBuffers.get(file);
            if (sb == null) {
                sb = new StringBuilder();
                fileBuffers.put(file, sb);
            }
            sb.append("[").append(entry.timestamp.substring(11)).append("] [")
              .append(entry.category).append("] ").append(entry.message).append(System.lineSeparator());
            RemoteLogUploader.record(dir, entry.timestamp, entry.category, entry.message);
        }

        for (Map.Entry<File, StringBuilder> fe : fileBuffers.entrySet()) {
            try (BufferedWriter out = Files.newBufferedWriter(fe.getKey().toPath(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                out.write(fe.getValue().toString());
            } catch (IOException ignored) {}
        }
    }

    private static void cleanup(long now) {
        File dir = directory;
        if (dir == null || !dir.isDirectory()) return;
        File[] files = dir.listFiles((d, name) -> name.endsWith(".log"));
        if (files == null) return;
        for (File file : files) {
            if (now - file.lastModified() > RETENTION_MS) {
                try { Files.deleteIfExists(file.toPath()); } catch (IOException ignored) { }
            }
        }
        enforceLimits(null);
        if (enabled) LocalLogRetention.prune(dir);
    }

    private static void enforceLimits(Path current) {
        long now = System.currentTimeMillis();
        if (current != null && now - lastLimitEnforcement < 5000L) return;
        lastLimitEnforcement = now;
        File dir = directory;
        if (dir == null) return;
        File[] files = dir.listFiles((d, name) -> name.endsWith(".log"));
        if (files == null) return;
        long total = 0L;
        for (File file : files) total += file.length();
        if (total <= MAX_TOTAL_BYTES) return;
        java.util.Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (File file : files) {
            if (total <= MAX_TOTAL_BYTES) break;
            // Keep the file currently receiving data unless it alone exceeds the cap.
            if (current != null && file.toPath().equals(current) && file.length() <= MAX_TOTAL_BYTES) continue;
            long size = file.length();
            try { if (Files.deleteIfExists(file.toPath())) total -= size; } catch (IOException ignored) { }
        }
    }

    private static String fileGroup(String category) {
        if (category.equals("AutoArmor")) return "auto-armor";
        if (category.equals("AutoProtect")) return "auto-protect";
        if (category.startsWith("Auto") || category.equals("AutoConsumables")) return "auto-consumables";
        if (category.startsWith("Knockback")) return "knockback";
        if (category.equals("DamageIndicator")) return "damage-indicator";
        if (category.equals("AllyGroundMarker")) return "ally-marker";
        if (category.startsWith("Inventory") || category.equals("InvMove") || category.equals("ArmorPickupFilter")) return "inventory";
        if (category.toLowerCase(Locale.ROOT).contains("clan") || category.equals("ClanPing")) return "clan-ping";
        if (category.toLowerCase(Locale.ROOT).contains("hit") || category.equals("Combat")) return "combat";
        return "system";
    }

    private static String sanitize(String category) {
        if (category == null || category.trim().isEmpty()) return "System";
        return category.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    public static String timestamp() {
        return timestampFast();
    }

    private static final class Entry {
        final String timestamp, category, message;
        Entry(String timestamp, String category, String message) {
            this.timestamp = timestamp; this.category = category; this.message = message;
        }
    }
}
