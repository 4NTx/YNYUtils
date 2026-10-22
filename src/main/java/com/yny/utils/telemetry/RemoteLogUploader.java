package com.yny.utils.telemetry;

import java.io.File;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.Comparator;
import java.util.UUID;
import java.nio.file.AtomicMoveNotSupportedException;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Durable, asynchronous mirror of the YNY module log. Each record is spooled before upload. */
public final class RemoteLogUploader {
    private static volatile File pending;
    private static volatile boolean started;
    private static volatile boolean commandsPromoted;

    public static final int MAX_PENDING_FILES = 30;
    public static final long MAX_PENDING_BYTES = 2 * 1024 * 1024L; // 2 MB

    private RemoteLogUploader() { }

    /** Non-blocking queue offering so logging never stalls or reduces FPS on the client. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean offerLossless(ArrayBlockingQueue queue, Object entry) {
        if (queue == null) return false;
        return queue.offer(entry);
    }
    public static void resume(File logDirectory) {
        if (logDirectory == null || !TelemetryConfig.get().telemetryEnabled) return;
        pending = new File(logDirectory, "remote-pending");
        enforceDiskQuota(pending);
        start();
    }

    public static void record(File logDirectory, String timestamp, String category, String message) {
        recordWithId(logDirectory, timestamp, category, message, null);
    }

    /** Returns true when a command is committed or a non-command is intentionally ignored. */
    public static boolean recordWithId(File logDirectory, String timestamp, String category, String message, String idPrefix) {
        if (!"Command".equals(category)) return true;
        if (logDirectory == null || !TelemetryConfig.get().telemetryEnabled) return false;
        try {
            File dir = new File(logDirectory, "remote-pending");
            if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) return false;
            pending = dir;
            enforceDiskQuota(dir);
            String clean = String.valueOf(message);
            clean = clean.replace('\r', ' ').replace('\n', ' ');
            int parts = Math.max(1, (clean.length() + 699) / 700);
            String group = idPrefix == null
                    ? ("Command".equals(category) ? "!" : "") + String.format("%013d-%s", System.currentTimeMillis(), UUID.randomUUID().toString().replace("-", "").substring(0, 24))
                    : idPrefix;
            for (int part = 0; part < parts; part++) {
                JsonObject entry = new JsonObject();
                entry.addProperty("time", timestamp);
                entry.addProperty("category", category);
                entry.addProperty("nick", TelemetryService.get().currentNick());
                entry.addProperty("server", TelemetryService.get().currentServer());
                String section = clean.substring(part * 700, Math.min(clean.length(), (part + 1) * 700));
                entry.addProperty("message", parts == 1 ? section : "[parte " + (part + 1) + "/" + parts + "] " + section);
                String name = group + String.format("-%04d", part + 1);
                entry.addProperty("id", name);
                File temp = new File(dir, name + ".tmp");
                File ready = new File(dir, name + ".json");
                if (!ready.isFile()) {
                    Files.write(temp.toPath(), entry.toString().getBytes(StandardCharsets.UTF_8));
                    try {
                        Files.move(temp.toPath(), ready.toPath(), StandardCopyOption.ATOMIC_MOVE);
                    } catch (AtomicMoveNotSupportedException e) {
                        Files.move(temp.toPath(), ready.toPath());
                    }
                }
            }
            start();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void enforceDiskQuota(File dir) {
        if (dir == null || !dir.isDirectory()) return;
        try {
            File[] files = dir.listFiles((d, name) -> name.endsWith(".json") || name.endsWith(".tmp"));
            if (files == null || files.length == 0) return;

            long now = System.currentTimeMillis();
            for (File f : files) {
                if (f.getName().endsWith(".tmp") && (now - f.lastModified() > 10000L)) {
                    f.delete();
                }
            }

            files = dir.listFiles((d, name) -> name.endsWith(".json"));
            if (files == null || files.length == 0) return;

            Arrays.sort(files, Comparator.comparingLong(File::lastModified));

            long totalBytes = 0;
            for (File f : files) {
                totalBytes += f.length();
            }

            int index = 0;
            int fileCount = files.length;
            while (index < files.length && (fileCount >= MAX_PENDING_FILES || totalBytes > MAX_PENDING_BYTES)) {
                File toDelete = files[index++];
                long len = toDelete.length();
                if (toDelete.delete()) {
                    fileCount--;
                    totalBytes -= len;
                }
            }
        } catch (Throwable ignored) {}
    }

    private static synchronized void start() {
        if (started) return;
        started = true;
        Thread worker = new Thread(new Runnable() { public void run() { loop(); } }, "YNY-RemoteLogs");
        worker.setDaemon(true);
        worker.start();
    }

    private static void loop() {
        long delay = 2000L;
        for (;;) {
            try {
                Thread.sleep(delay);
                if (uploadBatch()) delay = 2000L;
                else delay = Math.min(delay * 2L, 60000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable ignored) {
                delay = Math.min(delay * 2L, 60000L);
            }
        }
    }

    /** Moves old queued commands to the front without changing their entry IDs or content. */
    private static void promoteExistingCommands(File dir) {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json") && !name.startsWith("!"));
        if (files == null) return;
        for (File file : files) {
            try {
                String data = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                if (!data.contains("\"category\":\"Command\"")) {
                    Files.deleteIfExists(file.toPath());
                    continue;
                }
                File priority = new File(dir, "!" + file.getName());
                Files.move(file.toPath(), priority.toPath());
            } catch (Exception ignored) { }
        }
        commandsPromoted = true;
    }

    private static boolean uploadBatch() throws Exception {
        File dir = pending;
        if (dir == null || !TelemetryConfig.get().telemetryEnabled) return true;
        enforceDiskQuota(dir);
        if (!commandsPromoted) promoteExistingCommands(dir);
        File[] files = dir.listFiles((d, name) -> name.startsWith("!") && name.endsWith(".json"));
        if (files == null || files.length == 0) return true;
        Arrays.sort(files, Comparator.comparing(File::getName));
        JsonArray entries = new JsonArray();
        int count = Math.min(files.length, 25);
        for (int i = 0; i < count; i++) {
            String data = new String(Files.readAllBytes(files[i].toPath()), StandardCharsets.UTF_8);
            entries.add(new JsonParser().parse(data));
        }
        TelemetryConfig config = TelemetryConfig.get();
        TelemetryIdentity identity = TelemetryIdentity.get();
        String currentNick = TelemetryService.get().currentNick();
        JsonObject payload = new JsonObject();
        payload.addProperty("installId", identity.getInstallId(currentNick));
        payload.addProperty("shortId", identity.getShortId(currentNick));
        payload.addProperty("nick", currentNick);
        payload.addProperty("server", TelemetryService.get().currentServer());
        payload.add("entries", entries);
        String endpoint = config.apiEndpoint.replaceFirst("/event/?$", "/logs");
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("X-Client-Token", config.clientToken);
            byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = conn.getOutputStream()) { out.write(body); }
            if (conn.getResponseCode() < 200 || conn.getResponseCode() >= 300) return false;
            try (Reader response = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject ack = new JsonParser().parse(response).getAsJsonObject();
                if (!ack.has("ok") || !ack.get("ok").getAsBoolean()
                        || !ack.has("accepted") || ack.get("accepted").getAsInt() != count) return false;
            }
            for (int i = 0; i < count; i++) Files.deleteIfExists(files[i].toPath());
            return true;
        } finally {
            conn.disconnect();
        }
    }
}
