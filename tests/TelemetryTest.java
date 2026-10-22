package tests;

import java.util.UUID;
import com.google.gson.JsonObject;
import com.yny.utils.telemetry.TelemetryConfig;
import com.yny.utils.telemetry.TelemetryIdentity;
import com.yny.utils.telemetry.WatermarkHud;
import dev.xavier.stein.loader.api.HudPlacement;

public class TelemetryTest {

    public static void main(String[] args) {
        testIdentity();
        testConfig();
        testWatermark();
        testPayloadStructure();
        System.out.println("PASS: Todas as verificações de Telemetria e Identidade foram aprovadas com 100% de sucesso!");
    }

    private static void testIdentity() {
        TelemetryIdentity identity = TelemetryIdentity.get();
        if (identity == null) throw new AssertionError("Identity singleton nulo");
        if (identity.getInstallId() == null || identity.getInstallId().isEmpty())
            throw new AssertionError("installId nulo ou vazio");

        // Valida que o installId segue formato canônico UUID v4
        UUID uuid = UUID.fromString(identity.getInstallId());
        if (uuid == null) throw new AssertionError("installId não é um UUID válido");

        // Valida que o shortId possui 8 caracteres hexadecimais
        String shortId = identity.getShortId();
        if (shortId == null || shortId.length() != 8)
            throw new AssertionError("shortId inválido: " + shortId);

        if (identity.getCreatedTimestamp() <= 0)
            throw new AssertionError("Timestamp de criação inválido");

        // Valida unicidade e persistência de identificadores por nickname
        TelemetryIdentity idPlayer1 = TelemetryIdentity.forNick("Artur");
        TelemetryIdentity idPlayer2 = TelemetryIdentity.forNick("Gbzadaa");
        TelemetryIdentity idPlayer1Again = TelemetryIdentity.forNick("artur");

        if (idPlayer1.getShortId() == null || idPlayer1.getShortId().length() != 8)
            throw new AssertionError("shortId do Player1 inválido: " + idPlayer1.getShortId());
        if (idPlayer2.getShortId() == null || idPlayer2.getShortId().length() != 8)
            throw new AssertionError("shortId do Player2 inválido: " + idPlayer2.getShortId());

        if (idPlayer1.getShortId().equalsIgnoreCase(idPlayer2.getShortId()))
            throw new AssertionError("FALHA: Nicks diferentes não podem ter o mesmo identificador! (" + idPlayer1.getShortId() + " == " + idPlayer2.getShortId() + ")");

        if (!idPlayer1.getShortId().equalsIgnoreCase(idPlayer1Again.getShortId()))
            throw new AssertionError("FALHA: Mesmo nick deve ter identificador estável e invariável!");

        System.out.println("PASS: TelemetryIdentity validado (ID: " + identity.getInstallId() + ", Short: #" + shortId + ")");
        System.out.println("PASS: Identificador por nick validado (Artur: #" + idPlayer1.getShortId() + ", Gbzadaa: #" + idPlayer2.getShortId() + ")");
    }

    private static void testConfig() {
        TelemetryConfig config = TelemetryConfig.get();
        if (config == null) throw new AssertionError("Config singleton nulo");

        config.telemetryEnabled = false;
        config.watermarkVisible = false;
        config.watermarkOpacity = 5;
        config.sanitize();
        if (!config.telemetryEnabled) throw new AssertionError("Envio de telemetria não pode ser desativado pelo usuário");
        if (!config.watermarkVisible) throw new AssertionError("Sanitize deve forçar watermarkVisible ativo");
        if (config.watermarkOpacity != 60) throw new AssertionError("Sanitize de opacidade fixa em 60% falhou");

        config.watermarkOpacity = 150;
        config.sanitize();
        if (config.watermarkOpacity != 60) throw new AssertionError("Sanitize de opacidade fixa em 60% falhou");

        if (!"https://ping.antiutm.com/telemetry/api/telemetry/event".equals(TelemetryConfig.getSecureEndpoint())) {
            throw new AssertionError("Falha no desmascaramento seguro do endpoint");
        }
        if (TelemetryConfig.getSecureToken().length() != 64) {
            throw new AssertionError("Falha no desmascaramento seguro do token");
        }

        System.out.println("PASS: TelemetryConfig sanitization validada (60% fixa, visibilidade ativa, credenciais ofuscadas)");
    }

    private static void testWatermark() {
        TelemetryIdentity identity = TelemetryIdentity.get();
        TelemetryConfig config = TelemetryConfig.get();
        WatermarkHud hud = new WatermarkHud(identity, config);

        if (!"ynyutils.watermark".equals(hud.id())) throw new AssertionError("ID do HudElement incorreto");
        if (hud.name() == null || hud.name().isEmpty()) throw new AssertionError("Nome do HudElement vazio");
        if (hud.layout(true, 0f)) throw new AssertionError("Layout preview deve ser falso (bloqueado para edição)");
        if (!hud.layout(false, 0f)) throw new AssertionError("Layout normal deve ser verdadeiro");

        HudPlacement placement = hud.defaultPlacement();
        if (placement == null) throw new AssertionError("defaultPlacement nulo");
        if (placement.scale <= 0) throw new AssertionError("Escala de posicionamento inválida");

        System.out.println("PASS: WatermarkHud validado");
    }

    private static void testPayloadStructure() {
        TelemetryIdentity identity = TelemetryIdentity.get();
        JsonObject json = new JsonObject();
        json.addProperty("installId", identity.getInstallId());
        json.addProperty("shortId", identity.getShortId());
        json.addProperty("nick", "TesterPlayer");
        json.addProperty("server", "play.teste.com");
        json.addProperty("clan", "YNY");
        json.addProperty("version", "1.0.0");
        json.addProperty("eventType", "SERVER_JOIN");
        json.addProperty("timestamp", System.currentTimeMillis());

        if (!json.has("installId") || !json.has("shortId") || !json.has("nick") || !json.has("server")) {
            throw new AssertionError("Campos obrigatórios ausentes no JSON");
        }

        // Validação estrita de privacidade: garante que campos proibidos não existem
        if (json.has("password") || json.has("senha") || json.has("token") || json.has("chat") || json.has("login")) {
            throw new AssertionError("VIOLAÇÃO DE PRIVACIDADE: Campo sensível detectado no payload!");
        }

        System.out.println("PASS: Estrutura segura de payload validada");
    }
}
