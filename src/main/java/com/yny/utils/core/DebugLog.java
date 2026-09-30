package com.yny.utils.core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;

import net.minecraft.client.Minecraft;

/** Rotating diagnostic log shared by utilities. */
public final class DebugLog {
    private static final String FILE_NAME = "ynyutils-debug.log";
    private static final String PREVIOUS_FILE_NAME = "ynyutils-debug.1.log";
    // Um teste de PvP gera muitos eventos de armadura e velocity; dois arquivos
    // de 4 MiB preservam a sessão curta inteira sem crescimento ilimitado.
    private static final long MAX_FILE_BYTES = 4L * 1024L * 1024L;

    private DebugLog() {
    }

    public static synchronized void write(String category, String message) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            File logFile = new File(new File(mc.mcDataDir, "logs"), FILE_NAME);
            File parent = logFile.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                return;
            }
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
            String line = "[" + timestamp + "] [" + category + "] " + message + System.lineSeparator();
            byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
            Path current = logFile.toPath();
            Path previous = new File(parent, PREVIOUS_FILE_NAME).toPath();
            if (Files.exists(previous) && Files.size(previous) > MAX_FILE_BYTES) {
                Files.delete(previous);
            }
            if (Files.exists(current) && Files.size(current) + bytes.length > MAX_FILE_BYTES) {
                if (Files.size(current) <= MAX_FILE_BYTES) {
                    Files.move(current, previous, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    // Logs antigos sem limite não devem permanecer ocupando dezenas de MB.
                    Files.delete(current);
                }
            }
            Files.write(current, bytes, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (Exception exception) {
            System.out.println("[YNYUtils][" + category + "] log write failed: " + exception.getMessage());
        }
    }
}
