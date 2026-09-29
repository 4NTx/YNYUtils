package com.yny.targethealth;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.client.Minecraft;

final class Settings {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    boolean enabled = true;
    boolean showName = true;
    boolean showNumbers = true;
    boolean showAbsorption = true;
    boolean showDistantTargets = true;
    boolean ignoreLeaves = true;
    boolean dynamicHealthColor = false;
    int rangeIndex = 2;
    int opacityIndex = 2;
    int templateIndex;
    String accentColor = "c";
    String healthColor = "a";

    static Settings load() {
        File file = file();
        if (!file.isFile()) return new Settings();
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            Settings settings = GSON.fromJson(reader, Settings.class);
            return settings == null ? new Settings() : settings;
        } catch (Exception ignored) {
            return new Settings();
        }
    }

    static void save(Settings settings) {
        File file = file();
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            GSON.toJson(settings, writer);
        } catch (Exception ignored) {
        }
    }

    private static File file() {
        return new File(Minecraft.getMinecraft().mcDataDir, "config/" + TargetHealthMod.ID + ".json");
    }
}
