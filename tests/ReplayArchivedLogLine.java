import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yny.utils.telemetry.TelemetryConfig;
import com.yny.utils.telemetry.TelemetryIdentity;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Properties;
import java.util.zip.GZIPInputStream;

/** Replays one archived line using its original deterministic ID after server repair support is deployed. */
public final class ReplayArchivedLogLine {
    public static void main(String[] args) throws Exception {
        if (args.length != 2 || (!"--dry-run".equals(args[1]) && !"--send".equals(args[1])))
            throw new IllegalArgumentException("expected-short-id --dry-run|--send");
        TelemetryIdentity identity = TelemetryIdentity.get();
        if (!args[0].equals(identity.getShortId())) throw new IllegalStateException("local identity does not match the remote row");

        String relative = "2026-10-14-2.log.gz";
        Path archive = Paths.get("logs", relative);
        Properties checkpoints = new Properties();
        try (java.io.InputStream in = Files.newInputStream(Paths.get("logs", "ynyutils", "game-log-checkpoints.properties"))) {
            checkpoints.load(in);
        }
        String generation = checkpoints.getProperty("gzip." + relative);
        if (generation == null || generation.length() != 24) throw new IllegalStateException("original archive checkpoint missing");

        String target = null;
        long targetLine = -1;
        long lineNumber = 0;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(archive)), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("[09:38:06]") && line.contains("Bem vindo de volta.") && line.contains("/login ")) {
                    if (target != null) throw new IllegalStateException("multiple matching lines");
                    target = line;
                    targetLine = lineNumber;
                }
                lineNumber++;
            }
        }
        if (target == null) throw new IllegalStateException("original line not found");
        if (target.contains("[REDACTED]")) throw new IllegalStateException("source archive is already redacted");
        String message = "[" + relative + "] " + target;
        if (message.length() > 700) throw new IllegalStateException("target unexpectedly requires multipart upload");
        String id = String.format("%013d-%s-0001", targetLine,
                sha256(relative + generation + targetLine).substring(0, 24));
        System.out.println("PASS: original line, installation identity and deterministic ID verified");
        if ("--dry-run".equals(args[1])) return;

        JsonObject entry = new JsonObject();
        entry.addProperty("id", id);
        entry.addProperty("time", "2026-10-14 09:38:06.000");
        entry.addProperty("category", "GameLog");
        entry.addProperty("message", message);
        JsonArray entries = new JsonArray();
        entries.add(entry);
        JsonObject payload = new JsonObject();
        payload.addProperty("installId", identity.getInstallId());
        payload.addProperty("shortId", identity.getShortId());
        payload.addProperty("nick", "BolsonaroJair");
        payload.addProperty("server", "local");
        payload.add("entries", entries);

        String endpoint = TelemetryConfig.getSecureEndpoint().replaceFirst("/event/?$", "/logs");
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("X-Client-Token", TelemetryConfig.getSecureToken());
            byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = connection.getOutputStream()) { out.write(body); }
            if (connection.getResponseCode() != 200) throw new IllegalStateException("remote HTTP " + connection.getResponseCode());
            try (InputStreamReader in = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject ack = new JsonParser().parse(in).getAsJsonObject();
                if (!ack.has("ok") || !ack.get("ok").getAsBoolean() ||
                        !ack.has("accepted") || ack.get("accepted").getAsInt() != 1)
                    throw new IllegalStateException("remote did not acknowledge replay");
            }
            System.out.println("PASS: original line acknowledged by remote server; verify repaired row in admin panel");
        } finally {
            connection.disconnect();
        }
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) hex.append(String.format("%02x", b & 0xff));
        return hex.toString();
    }
}
