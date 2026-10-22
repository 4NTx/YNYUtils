package com.yny.utils.telemetry;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import com.google.gson.JsonObject;
import com.yny.utils.modules.clanping.PingClanName;

import dev.xavier.stein.loader.api.Account;
import dev.xavier.stein.loader.api.Log;
import dev.xavier.stein.loader.api.Players;
import dev.xavier.stein.loader.api.Server;

/**
 * Serviço de auditoria e registro de utilização legítima da modpack.
 * Executa requisições de forma estritamente assíncrona em segundo plano,
 * garantindo taxa de quadros (FPS) inalterada e zero interrupção na jogabilidade.
 *
 * <p>Eventos de identidade usam ID da instalação, nick, servidor e clã.
 * Os logs de diagnóstico e comandos são enviados pelo RemoteLogUploader.
 */
public final class TelemetryService {

    private static final String MODPACK_VERSION = "1.0.0";
    private static final int TIMEOUT_MS = 5000;
    private static final Log LOG = Log.of("ynyutils-telemetry");

    private static volatile TelemetryService instance;

    private final TelemetryIdentity identity;
    private final TelemetryConfig config;
    private final ExecutorService executor;

    private volatile String currentServer = "local";
    private volatile String currentNick = "Jogador";
    private volatile String currentClan = "";
    private volatile long nextClanCheck;
    private String lastSentServer = "";
    private String lastSentNick = "";
    private String lastSentEvent = "";
    private long lastSentTime = 0L;

    private TelemetryService(TelemetryIdentity identity, TelemetryConfig config) {
        this.identity = identity;
        this.config = config;
        this.executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "YNY-Telemetry-Worker");
                t.setDaemon(true);
                return t;
            }
        });
    }

    public static TelemetryService get() {
        if (instance == null) {
            synchronized (TelemetryService.class) {
                if (instance == null) {
                    instance = new TelemetryService(TelemetryIdentity.get(), TelemetryConfig.get());
                }
            }
        }
        return instance;
    }

    public void recordLaunch() {
        if (!config.telemetryEnabled) return;
        String nick = resolveCurrentNick();
        currentNick = nick;
        dispatchAsync("LAUNCH", nick, "local", null);
    }

    public void recordServerJoin() {
        if (!config.telemetryEnabled) return;
        String nick = resolveCurrentNick();
        String server = resolveServerAddress();
        currentNick = nick;
        currentServer = server;
        String clan = resolveClanTag(nick);
        currentClan = clan == null ? "" : clan;
        nextClanCheck = 0L;
        dispatchAsync("SERVER_JOIN", nick, server, clan);
    }

    public void recordServerLeave() {
        if (!config.telemetryEnabled) return;
        String nick = resolveCurrentNick();
        String server = resolveServerAddress();
        dispatchAsync("SERVER_LEAVE", nick, server, null);
        currentServer = "local";
        currentClan = "";
    }

    private void dispatchAsync(final String eventType, final String nick, final String server, final String clan) {
        final long now = System.currentTimeMillis();

        // Evita envio duplicado de eventos idênticos num intervalo curto (< 10 segundos)
        synchronized (this) {
            if (eventType.equals(lastSentEvent)
                    && nick.equalsIgnoreCase(lastSentNick)
                    && server.equalsIgnoreCase(lastSentServer)
                    && (now - lastSentTime < 10000L)) {
                return;
            }
            lastSentEvent = eventType;
            lastSentNick = nick;
            lastSentServer = server;
            lastSentTime = now;
        }

        executor.submit(new Runnable() {
            @Override
            public void run() {
                sendPayload(eventType, nick, server, clan, now);
            }
        });
    }

    private void sendPayload(String eventType, String nick, String server, String clan, long timestamp) {
        String endpoint = config.apiEndpoint;
        if (endpoint == null || endpoint.trim().isEmpty()) return;

        HttpURLConnection conn = null;
        try {
            JsonObject json = new JsonObject();
            json.addProperty("installId", identity.getInstallId(nick));
            json.addProperty("shortId", identity.getShortId(nick));
            json.addProperty("nick", nick != null ? nick : "Desconhecido");
            json.addProperty("server", server != null ? server : "offline");
            json.addProperty("clan", clan != null ? clan : "");
            json.addProperty("version", MODPACK_VERSION);
            json.addProperty("eventType", eventType);
            json.addProperty("timestamp", timestamp);

            byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);

            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("User-Agent", "YNYModpack/" + MODPACK_VERSION);
            if (config.clientToken != null && !config.clientToken.trim().isEmpty()) {
                conn.setRequestProperty("X-Client-Token", config.clientToken.trim());
            }

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
                os.flush();
            }

            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                // Sucesso na entrega do evento de telemetria
            } else {
                LOG.debug("Telemetria respondeu com status: " + code);
            }
        } catch (Exception e) {
            // Falha silenciosa para nunca interromper a jogabilidade do usuário
            LOG.debug("Falha na entrega da telemetria: " + e.getMessage());
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Exception ignored) {}
            }
        }
    }

    private String resolveCurrentNick() {
        try {
            String nick = Players.self();
            if (nick != null && !nick.trim().isEmpty()) return nick.trim();
        } catch (Throwable ignored) {}
        try {
            String account = Account.name();
            if (account != null && !account.trim().isEmpty()) return account.trim();
        } catch (Throwable ignored) {}
        return "Jogador";
    }

    private String resolveServerAddress() {
        try {
            if (Server.isMultiplayer()) {
                String addr = Server.address();
                if (addr != null && !addr.trim().isEmpty()) return addr.trim();
            }
        } catch (Throwable ignored) {}
        return "singleplayer";
    }

    /** The scoreboard and player list often arrive after the join callback. */
    public void refreshClan() {
        long now = System.currentTimeMillis();
        if (now < nextClanCheck) return;
        nextClanCheck = now + 2000L;
        if (!config.telemetryEnabled || "local".equals(currentServer)) return;
        String nick = resolveCurrentNick();
        String clan = ClanIdentityResolver.resolve(nick);
        if (clan == null || clan.isEmpty() || clan.equalsIgnoreCase(currentClan)) return;
        currentClan = clan;
        currentNick = nick;
        dispatchAsync("CLAN_UPDATE", nick, currentServer, clan);
    }

    private String resolveClanTag(String nick) {
        return ClanIdentityResolver.resolve(nick);
    }
    public String currentNick() { return currentNick; }
    public String currentServer() { return currentServer; }

    public void shutdown() {
        try {
            executor.shutdownNow();
        } catch (Exception ignored) {}
    }
}





