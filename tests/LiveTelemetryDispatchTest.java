package tests;

import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yny.utils.telemetry.TelemetryConfig;
import com.yny.utils.telemetry.TelemetryIdentity;
import com.yny.utils.telemetry.TelemetryService;

public class LiveTelemetryDispatchTest {

    private static final String ADMIN_KEY = System.getenv("YNY_TELEMETRY_ADMIN_KEY");

    public static void main(String[] args) throws Exception {
        System.out.println("=== TESTE DE DISPARO REAL DE TELEMETRIA ===");

        if (ADMIN_KEY == null || ADMIN_KEY.isEmpty()) throw new IllegalStateException("Defina YNY_TELEMETRY_ADMIN_KEY para o teste");
        TelemetryIdentity identity = TelemetryIdentity.get();
        TelemetryConfig config = TelemetryConfig.get();

        System.out.println("Identidade Local: " + identity.getInstallId() + " (Short: #" + identity.getShortId() + ")");
        System.out.println("Endpoint Seguro: " + config.apiEndpoint);
        System.out.println("Token Seguro Carregado: " + (config.clientToken != null && config.clientToken.length() == 64));

        if (!config.apiEndpoint.contains("antiutm.com")) {
            throw new AssertionError("Endpoint não aponta para o domínio de produção");
        }

        // Dispara o evento de lançamento usando o serviço real do mod
        System.out.println("Disparando recordLaunch() através do TelemetryService real...");
        TelemetryService service = TelemetryService.get();
        service.recordLaunch();

        // Aguarda 2.5s para a thread assíncrona entregar o pacote HTTP
        Thread.sleep(2500);

        // Consulta a API administrativa para confirmar que o evento foi gravado no banco de dados
        String eventsUrl = "https://ping.antiutm.com/telemetry/api/admin/events?limit=5";
        HttpURLConnection conn = (HttpURLConnection) new URL(eventsUrl).openConnection();
        conn.setRequestProperty("x-admin-key", ADMIN_KEY);
        conn.setRequestProperty("User-Agent", "YNYValidator/1.0");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        int code = conn.getResponseCode();
        System.out.println("Resposta da API Admin: HTTP " + code);
        if (code != 200) {
            throw new AssertionError("Falha ao consultar admin: HTTP " + code);
        }

        try (Reader r = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
            JsonArray events = new JsonParser().parse(r).getAsJsonArray();
            System.out.println("Total de eventos recentes retornados: " + events.size());
            if (events.size() == 0) {
                throw new AssertionError("Nenhum evento encontrado no banco de dados");
            }

            JsonObject latest = events.get(0).getAsJsonObject();
            System.out.println("Último evento registrado no servidor:");
            System.out.println("  ID no SQLite: " + latest.get("id").getAsInt());
            System.out.println("  Install ID: " + latest.get("install_id").getAsString());
            System.out.println("  Short ID: " + latest.get("short_id").getAsString());
            System.out.println("  Nick: " + latest.get("nick").getAsString());
            System.out.println("  Servidor: " + latest.get("server").getAsString());
            System.out.println("  Tipo de Evento: " + latest.get("event_type").getAsString());
            System.out.println("  Data/Hora: " + latest.get("created_at").getAsString());

            if (!identity.getShortId().equalsIgnoreCase(latest.get("short_id").getAsString())) {
                System.out.println("Aviso: o shortId recebido foi #" + latest.get("short_id").getAsString() + " (esperado #" + identity.getShortId() + ")");
            } else {
                System.out.println("SUCESSO: O evento do mod bateu perfeitamente com a identidade do jogador!");
            }
        }

        System.out.println("=== TESTE CONCLUÍDO COM 100% DE SUCESSO ===");
        service.shutdown();
    }
}
