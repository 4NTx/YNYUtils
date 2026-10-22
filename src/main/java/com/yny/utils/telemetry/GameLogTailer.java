package com.yny.utils.telemetry;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Properties;
import java.util.zip.GZIPInputStream;

/** Mirrors Minecraft and other top-level game logs, including rotated archives. */
public final class GameLogTailer {
    private static volatile boolean started;
    private static File logsDirectory;
    private static File moduleDirectory;
    private static final Properties CHECKPOINTS = new Properties();

    private GameLogTailer() { }

    public static synchronized void start(File ynyLogDirectory) {
        if (started || ynyLogDirectory == null || !TelemetryConfig.get().telemetryEnabled) return;
        moduleDirectory = ynyLogDirectory;
        logsDirectory = ynyLogDirectory.getParentFile();
        if (logsDirectory == null) return;
        File checkpoint = new File(moduleDirectory, "game-log-checkpoints.properties");
        if (checkpoint.isFile()) {
            try (InputStream in = Files.newInputStream(checkpoint.toPath())) { CHECKPOINTS.load(in); }
            catch (Exception ignored) { }
        }
        started = true;
        Thread worker = new Thread(new Runnable() { public void run() { loop(); } }, "YNY-GameLogTailer");
        worker.setDaemon(true);
        worker.start();
    }

    private static void loop() {
        for (;;) {
            try {
                scan();
                Thread.sleep(5000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable ignored) {
                try { Thread.sleep(5000L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            }
        }
    }

    private static void scan() throws Exception {
        java.util.List<File> files = new java.util.ArrayList<File>();
        try (java.util.stream.Stream<java.nio.file.Path> paths = Files.walk(logsDirectory.toPath())) {
            paths.filter(path -> !path.startsWith(moduleDirectory.toPath()))
                .filter(path -> !Files.isSymbolicLink(path) && Files.isRegularFile(path))
                .filter(path -> path.getFileName().toString().endsWith(".log")
                        || path.getFileName().toString().endsWith(".log.gz"))
                .forEach(path -> files.add(path.toFile()));
        }
        files.sort((a, b) -> relativeName(a).compareTo(relativeName(b)));
        for (File file : files) {
            if (file.getName().endsWith(".gz")) scanArchive(file);
            else scanPlain(file);
        }
    }

    private static String relativeName(File file) {
        return logsDirectory.toPath().relativize(file.toPath()).toString().replace('\\', '/');
    }
    private static void scanPlain(File file) throws Exception {
        String generation = generation(file);
        String key = "plain." + relativeName(file);
        String prior = CHECKPOINTS.getProperty(key, "");
        long offset = 0L;
        if (prior.startsWith(generation + ":")) {
            try { offset = Long.parseLong(prior.substring(generation.length() + 1)); }
            catch (NumberFormatException ignored) { offset = 0L; }
        }
        if (offset > file.length()) offset = 0L;
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            in.seek(offset);
            for (;;) {
                long lineStart = in.getFilePointer();
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                int value;
                while ((value = in.read()) != -1 && value != '\n') bytes.write(value);
                if (value == -1) break; // wait for a complete line
                byte[] data = bytes.toByteArray();
                int length = data.length > 0 && data[data.length - 1] == '\r' ? data.length - 1 : data.length;
                String line = new String(data, 0, length, StandardCharsets.UTF_8);
                if (!spool(file, generation, lineStart, line)) break;
                offset = in.getFilePointer();
            }
        }
        String next = generation + ":" + offset;
        if (!next.equals(prior)) {
            CHECKPOINTS.setProperty(key, next);
            save();
        }
    }

    private static void scanArchive(File file) throws Exception {
        String generation = generation(file);
        String key = "gzip." + relativeName(file);
        if (generation.equals(CHECKPOINTS.getProperty(key))) return;
        long lineNumber = 0L;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(file.toPath())), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (!spool(file, generation, lineNumber, line)) return;
                lineNumber++;
            }
        }
        CHECKPOINTS.setProperty(key, generation);
        save();
    }

    private static boolean spool(File file, String generation, long position, String line) throws Exception {
        String id = String.format("%013d-%s", position, hash(relativeName(file) + generation + position).substring(0, 24));
        String time = eventTime(file, line);
        return RemoteLogUploader.recordWithId(moduleDirectory, time, "GameLog", "[" + relativeName(file) + "] " + line, id);
    }

    private static String eventTime(File file, String line) {
        String date = file.getName().matches("^\\d{4}-\\d{2}-\\d{2}.*")
                ? file.getName().substring(0, 10)
                : new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date(file.lastModified()));
        if (line.matches("^\\[\\d{2}:\\d{2}:\\d{2}\\].*")) return date + " " + line.substring(1, 9) + ".000";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).format(new Date(file.lastModified()));
    }
    private static String generation(File file) throws Exception {
        if (file.getName().endsWith(".gz")) return hash(relativeName(file) + ":" + file.length() + ":" + file.lastModified()).substring(0, 24);
        Object fileKey = Files.readAttributes(file.toPath(), java.nio.file.attribute.BasicFileAttributes.class).fileKey();
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            ByteArrayOutputStream first = new ByteArrayOutputStream();
            int value;
            while ((value = in.read()) != -1 && value != '\n' && first.size() < 1024) first.write(value);
            return hash(relativeName(file) + ":" + String.valueOf(fileKey) + ":" + new String(first.toByteArray(), StandardCharsets.UTF_8)).substring(0, 24);
        }
    }

    private static String hash(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) hex.append(String.format("%02x", b & 0xff));
        return hex.toString();
    }

    private static void save() throws Exception {
        if (!moduleDirectory.isDirectory() && !moduleDirectory.mkdirs() && !moduleDirectory.isDirectory()) return;
        File temp = new File(moduleDirectory, "game-log-checkpoints.tmp");
        File target = new File(moduleDirectory, "game-log-checkpoints.properties");
        try (java.io.OutputStream out = Files.newOutputStream(temp.toPath())) { CHECKPOINTS.store(out, "YNY game log offsets"); }
        try { Files.move(temp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}






