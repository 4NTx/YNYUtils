import com.yny.utils.telemetry.TelemetryConfig;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class RemoteLogAuthProbe {
    public static void main(String[] args) throws Exception {
        String endpoint = TelemetryConfig.getSecureEndpoint().replaceFirst("/event/?$", "/logs");
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("X-Client-Token", TelemetryConfig.getSecureToken());
            connection.getOutputStream().write("{}".getBytes(StandardCharsets.UTF_8));
            int status = connection.getResponseCode();
            if (status != 400) throw new AssertionError("Expected authenticated validation error 400, got " + status);
            System.out.println("PASS: packaged client token reached live log endpoint (HTTP 400 for empty batch)");
        } finally {
            connection.disconnect();
        }
    }
}
