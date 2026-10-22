package com.yny.utils.telemetry;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Gerencia a identidade única persistente da modpack por nickname/instalação.
 * Gera e mantém UUIDs e identificadores curtos (shortId) exclusivos por nick,
 * permitindo auditoria visual consistente e dinâmica conforme a conta em uso.
 */
public final class TelemetryIdentity {

    private static final String IDENTITY_FILE = "config/ynyutils-identity.json";
    private static final Map<String, TelemetryIdentity> NICK_CACHE = new ConcurrentHashMap<>();
    private static volatile TelemetryIdentity defaultInstance;

    private final String nick;
    private final String installId;
    private final String shortId;
    private final long createdTimestamp;

    private TelemetryIdentity(String nick, String installId, String shortId, long createdTimestamp) {
        this.nick = nick;
        this.installId = installId;
        this.shortId = shortId;
        this.createdTimestamp = createdTimestamp;
    }

    public static TelemetryIdentity get() {
        String current = resolveCurrentNick();
        if (current != null && !current.trim().isEmpty() && !"Jogador".equalsIgnoreCase(current.trim())) {
            return forNick(current);
        }
        if (defaultInstance == null) {
            synchronized (TelemetryIdentity.class) {
                if (defaultInstance == null) {
                    defaultInstance = loadOrCreateDefault();
                }
            }
        }
        return defaultInstance;
    }

    public static TelemetryIdentity forNick(String playerNick) {
        if (playerNick == null || playerNick.trim().isEmpty() || "Jogador".equalsIgnoreCase(playerNick.trim())) {
            return get();
        }
        String clean = playerNick.trim();
        String key = clean.toLowerCase();
        TelemetryIdentity cached = NICK_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        TelemetryIdentity created = loadOrCreateForNick(clean);
        NICK_CACHE.put(key, created);
        return created;
    }

    public static String resolveCurrentNick() {
        try {
            String nick = dev.xavier.stein.loader.api.Players.self();
            if (nick != null && !nick.trim().isEmpty()) return nick.trim();
        } catch (Throwable ignored) {}
        try {
            String account = dev.xavier.stein.loader.api.Account.name();
            if (account != null && !account.trim().isEmpty()) return account.trim();
        } catch (Throwable ignored) {}
        try {
            Class<?> aveClass = Class.forName("ave");
            Object mc = aveClass.getMethod("A").invoke(null);
            if (mc != null) {
                for (java.lang.reflect.Method m : aveClass.getMethods()) {
                    if (m.getParameterTypes().length == 0 && "avh".equals(m.getReturnType().getName())) {
                        Object session = m.invoke(mc);
                        if (session != null) {
                            for (java.lang.reflect.Method sm : session.getClass().getMethods()) {
                                if (sm.getParameterTypes().length == 0 && String.class.equals(sm.getReturnType()) && !"getId".equals(sm.getName())) {
                                    String val = (String) sm.invoke(session);
                                    if (val != null && !val.trim().isEmpty() && !val.contains("-")) return val.trim();
                                }
                            }
                        }
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static String generateInstallId(String nick) {
        String clean = (nick != null && !nick.trim().isEmpty()) ? nick.trim().toLowerCase() : "default";
        UUID uuid = UUID.nameUUIDFromBytes(("YNYModpack:Player:" + clean).getBytes(StandardCharsets.UTF_8));
        return uuid.toString();
    }

    public static String generateShortId(String nick) {
        String clean = (nick != null && !nick.trim().isEmpty()) ? nick.trim().toLowerCase() : "default";
        UUID uuid = UUID.nameUUIDFromBytes(("YNYModpack:Player:" + clean).getBytes(StandardCharsets.UTF_8));
        long hash = (long) uuid.hashCode() & 0xFFFFFFFFL;
        return String.format("%08X", hash);
    }

    private static TelemetryIdentity loadOrCreateForNick(String cleanNick) {
        String key = cleanNick.toLowerCase();
        File file = new File(IDENTITY_FILE);
        JsonObject rootJson = readFile(file);

        if (rootJson != null && rootJson.has("profiles") && rootJson.get("profiles").isJsonObject()) {
            JsonObject profiles = rootJson.getAsJsonObject("profiles");
            if (profiles.has(key) && profiles.get(key).isJsonObject()) {
                JsonObject p = profiles.getAsJsonObject(key);
                if (p.has("installId") && p.has("shortId")) {
                    String id = p.get("installId").getAsString();
                    String shortCode = p.get("shortId").getAsString();
                    long created = p.has("created") ? p.get("created").getAsLong() : System.currentTimeMillis();
                    return new TelemetryIdentity(cleanNick, id, shortCode, created);
                }
            }
        }

        // Generate deterministic identity per nick
        String newInstallId = generateInstallId(cleanNick);
        String newShortId = generateShortId(cleanNick);
        long now = System.currentTimeMillis();
        TelemetryIdentity identity = new TelemetryIdentity(cleanNick, newInstallId, newShortId, now);

        saveProfile(file, rootJson, cleanNick, identity);
        return identity;
    }

    private static TelemetryIdentity loadOrCreateDefault() {
        File file = new File(IDENTITY_FILE);
        JsonObject rootJson = readFile(file);
        if (rootJson != null && rootJson.has("installId") && rootJson.has("shortId")) {
            String id = rootJson.get("installId").getAsString();
            String shortCode = rootJson.get("shortId").getAsString();
            long created = rootJson.has("created") ? rootJson.get("created").getAsLong() : System.currentTimeMillis();
            return new TelemetryIdentity(null, id, shortCode, created);
        }

        String newInstallId = UUID.randomUUID().toString();
        long hash = (long) newInstallId.hashCode() & 0xFFFFFFFFL;
        String newShortId = String.format("%08X", hash);
        long now = System.currentTimeMillis();

        TelemetryIdentity created = new TelemetryIdentity(null, newInstallId, newShortId, now);
        saveDefault(file, rootJson, created);
        return created;
    }

    private static JsonObject readFile(File file) {
        if (file.exists() && file.isFile()) {
            try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                return new JsonParser().parse(reader).getAsJsonObject();
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static synchronized void saveProfile(File file, JsonObject existing, String nick, TelemetryIdentity identity) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            JsonObject root = existing != null ? existing : new JsonObject();
            JsonObject profiles = root.has("profiles") && root.get("profiles").isJsonObject()
                    ? root.getAsJsonObject("profiles")
                    : new JsonObject();

            JsonObject p = new JsonObject();
            p.addProperty("installId", identity.installId);
            p.addProperty("shortId", identity.shortId);
            p.addProperty("created", identity.createdTimestamp);
            profiles.add(nick.toLowerCase(), p);
            root.add("profiles", profiles);

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                writer.write(root.toString());
            }
        } catch (Exception ignored) {}
    }

    private static synchronized void saveDefault(File file, JsonObject existing, TelemetryIdentity identity) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            JsonObject root = existing != null ? existing : new JsonObject();
            root.addProperty("installId", identity.installId);
            root.addProperty("shortId", identity.shortId);
            root.addProperty("created", identity.createdTimestamp);

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                writer.write(root.toString());
            }
        } catch (Exception ignored) {}
    }

    public String getNick() {
        return nick;
    }

    public String getInstallId() {
        if (this.nick != null) {
            return this.installId;
        }
        String current = resolveCurrentNick();
        if (current != null && !current.trim().isEmpty() && !"Jogador".equalsIgnoreCase(current.trim())) {
            return forNick(current).installId;
        }
        return this.installId;
    }

    public String getInstallId(String queryNick) {
        if (queryNick != null && !queryNick.trim().isEmpty() && !"Jogador".equalsIgnoreCase(queryNick.trim())) {
            return forNick(queryNick).installId;
        }
        return getInstallId();
    }

    public String getShortId() {
        if (this.nick != null) {
            return this.shortId;
        }
        String current = resolveCurrentNick();
        if (current != null && !current.trim().isEmpty() && !"Jogador".equalsIgnoreCase(current.trim())) {
            return forNick(current).shortId;
        }
        return this.shortId;
    }

    public String getShortId(String queryNick) {
        if (queryNick != null && !queryNick.trim().isEmpty() && !"Jogador".equalsIgnoreCase(queryNick.trim())) {
            return forNick(queryNick).shortId;
        }
        return getShortId();
    }

    public long getCreatedTimestamp() {
        return createdTimestamp;
    }
}
