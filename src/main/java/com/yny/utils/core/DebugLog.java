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
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.client.Minecraft;

/** Bounded asynchronous logger. Game callbacks only enqueue small records. */
public final class DebugLog {
    private static final int QUEUE_CAPACITY = 4096;
    private static final long RETENTION_MS = 24L * 60L * 60L * 1000L;
    private static final long MAX_TOTAL_BYTES = 32L * 1024L * 1024L;
    private static final long CLEANUP_INTERVAL_MS = 60L * 1000L;
    private static final ArrayBlockingQueue<Entry> QUEUE = new ArrayBlockingQueue<Entry>(QUEUE_CAPACITY);
    private static final AtomicLong DROPPED = new AtomicLong();
    private static final Map<String, String> LAST_STATE = new ConcurrentHashMap<String, String>();
    private static volatile File directory;
    private static volatile boolean enabled = true;
    private static volatile boolean started;
    private static long lastLimitEnforcement;

    private DebugLog() { }

    /** May be called when the game starts; first write also initializes lazily. */
    public static synchronized void initialize(File gameDirectory, boolean isEnabled) {
        enabled = isEnabled;
        if (directory == null && gameDirectory != null) {
            directory = new File(new File(gameDirectory, "logs"), "ynyutils");
        }
        startWriter();
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static void state(String category, String key, String value) {
        String stateKey = sanitize(category) + ":" + String.valueOf(key);
        String next = String.valueOf(value);
        String prior = LAST_STATE.put(stateKey, next);
        if (!next.equals(prior)) write(category, "state=" + key + "; value=" + next);
    }

    public static void write(String category, String message) {
        if (!enabled) return;
        ensureInitialized();
        String safeCategory = sanitize(category);
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).format(new Date());
        Entry entry = new Entry(timestamp, safeCategory, String.valueOf(message));
        if (!QUEUE.offer(entry)) DROPPED.incrementAndGet();
    }

    private static synchronized void ensureInitialized() {
        if (directory == null) {
            try {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc == null || mc.mcDataDir == null) return;
                directory = new File(new File(mc.mcDataDir, "logs"), "ynyutils");
            } catch (Throwable ignored) { return; }
        }
        startWriter();
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
        for (;;) {
            try {
                Entry entry = QUEUE.poll(1L, TimeUnit.SECONDS);
                if (entry != null) append(entry);
                long now = System.currentTimeMillis();
                if (now - lastCleanup >= CLEANUP_INTERVAL_MS) {
                    cleanup(now);
                    lastCleanup = now;
                }
                long lost = DROPPED.getAndSet(0L);
                if (lost > 0L) append(new Entry(timestamp(), "System", "event=log_queue_overflow dropped=" + lost));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable ignored) {
                // Logging must never interrupt gameplay or terminate the writer.
            }
        }
    }

    private static void append(Entry entry) throws IOException {
        File dir = directory;
        if (dir == null) return;
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) return;
        String day = entry.timestamp.substring(0, 10).replace("-", "");
        File file = new File(dir, fileGroup(entry.category) + "-" + day + ".log");
        String line = "[" + entry.timestamp.substring(11) + "] [" + entry.category + "] " + entry.message + System.lineSeparator();
        try (BufferedWriter out = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            out.write(line);
        }
        enforceLimits(file.toPath());
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

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).format(new Date());
    }

    private static final class Entry {
        final String timestamp, category, message;
        Entry(String timestamp, String category, String message) {
            this.timestamp = timestamp; this.category = category; this.message = message;
        }
    }
}








