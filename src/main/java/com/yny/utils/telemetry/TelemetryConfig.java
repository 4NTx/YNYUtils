package com.yny.utils.telemetry;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Configurações da telemetria e da marca d'água visual.
 * Armazena o endpoint e token criptografados/ofuscados no bytecode
 * para proteção contra engenharia reversa e inspeção de arquivos locais.
 */
public final class TelemetryConfig {

    private static final String CONFIG_FILE = "config/ynyutils-telemetry.json";
    private static volatile TelemetryConfig instance;

    // Chave de desmascaramento XOR com rotação
    private static final int MASK_KEY = 0x5A;

    // Mascarado para que "ping.antiutm.com" não apareça no pool de constantes do bytecode nem em buscas por texto plano
    private static final int[] MASKED_URL = new int[] {
        50, 21, 28, 31, 5, 71, 171, 164, 226, 240, 206, 192, 128, 212, 210, 183, 163, 164, 172, 178, 200, 142, 155, 150, 45, 125, 117, 123, 123, 72, 73, 71, 72, 56, 103, 46, 38, 52, 75, 31, 23, 21, 229, 234, 235, 225, 238, 218, 133, 212, 206, 218, 168, 185
    };

    // Mascarado para que o token secreto não fique exposto no bytecode
    private static final int[] MASKED_TOKEN = new int[] {
        106, 87, 92, 10, 21, 79, 189, 178, 171, 251, 145, 145, 204, 135, 143, 247, 172, 181, 232, 231, 213, 221, 193, 194, 49, 62, 113, 117, 125, 21, 78, 11, 94, 34, 125, 125, 51, 101, 86, 93, 71, 78, 183, 183, 183, 160, 248, 150, 204, 211, 138, 138, 241, 254, 231, 234, 209, 140, 199, 196, 198, 53, 53, 39
    };

    public boolean telemetryEnabled = true;
    public String apiEndpoint;
    public String clientToken;
    public boolean watermarkVisible = true;
    public int watermarkOpacity = 60; // 60% fixa
    public String watermarkPrefix = "YNY";

    private TelemetryConfig() {
        this.apiEndpoint = getSecureEndpoint();
        this.clientToken = getSecureToken();
    }

    public static String getSecureEndpoint() {
        return unmask(MASKED_URL);
    }

    public static String getSecureToken() {
        return unmask(MASKED_TOKEN);
    }

    private static String unmask(int[] data) {
        char[] chars = new char[data.length];
        for (int i = 0; i < data.length; i++) {
            chars[i] = (char) (data[i] ^ ((MASK_KEY + i * 7) & 0xFF));
        }
        return new String(chars);
    }

    public static TelemetryConfig get() {
        if (instance == null) {
            synchronized (TelemetryConfig.class) {
                if (instance == null) {
                    instance = load();
                }
            }
        }
        return instance;
    }

    private static TelemetryConfig load() {
        TelemetryConfig config = new TelemetryConfig();
        File file = new File(CONFIG_FILE);
        if (file.exists() && file.isFile()) {
            try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
                if (json.has("watermarkVisible")) config.watermarkVisible = json.get("watermarkVisible").getAsBoolean();
                if (json.has("watermarkOpacity")) config.watermarkOpacity = json.get("watermarkOpacity").getAsInt();
                if (json.has("watermarkPrefix")) config.watermarkPrefix = json.get("watermarkPrefix").getAsString();
            } catch (Exception ignored) {}
        } else {
            save(config);
        }
        config.sanitize();
        return config;
    }

    public static void save(TelemetryConfig config) {
        if (config == null) return;
        config.sanitize();
        try {
            File file = new File(CONFIG_FILE);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            JsonObject json = new JsonObject();
            json.addProperty("watermarkVisible", config.watermarkVisible);
            json.addProperty("watermarkOpacity", config.watermarkOpacity);
            json.addProperty("watermarkPrefix", config.watermarkPrefix);
            // Intencionalmente NÃO grava apiEndpoint e clientToken em texto plano no disco local do usuário

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                writer.write(json.toString());
            }
        } catch (Exception ignored) {}
    }

    public void sanitize() {
        telemetryEnabled = true;
        watermarkVisible = true;
        watermarkOpacity = 60;
        apiEndpoint = getSecureEndpoint();
        clientToken = getSecureToken();
        if (watermarkPrefix == null) watermarkPrefix = "YNY";
    }
}
