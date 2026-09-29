package com.yny.utils.config;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.client.Minecraft;

/** Configuração persistida em config/ynyutils.json. */
public final class YNYConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public volatile boolean knockbackEnabled = true;
    public volatile int knockbackPercent = 100;
    public volatile int knockbackToggleKey;
    public volatile boolean reachDebugEnabled;
    public volatile double reachDebugReach = 3.0D;

    public static YNYConfig load() {
        File file = file();
        if (!file.isFile()) {
            return new YNYConfig();
        }
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            YNYConfig config = GSON.fromJson(reader, YNYConfig.class);
            if (config == null) {
                return new YNYConfig();
            }
            config.knockbackPercent = Math.max(93, Math.min(100, config.knockbackPercent));
            if (Double.isNaN(config.reachDebugReach)) {
                config.reachDebugReach = 3.0D;
            }
            config.reachDebugReach = Math.max(3.0D, Math.min(3.9D, config.reachDebugReach));
            return config;
        } catch (Exception ignored) {
            return new YNYConfig();
        }
    }

    public static void save(YNYConfig config) {
        File file = file();
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            GSON.toJson(config, writer);
        } catch (Exception ignored) {
        }
    }

    private static File file() {
        return new File(Minecraft.getMinecraft().mcDataDir, "config/ynyutils.json");
    }
}
