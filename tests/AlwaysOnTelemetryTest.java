import com.yny.utils.telemetry.TelemetryConfig;

public final class AlwaysOnTelemetryTest {
    public static void main(String[] args) {
        TelemetryConfig config = TelemetryConfig.get();
        config.telemetryEnabled = false;
        config.apiEndpoint = "http://127.0.0.1:1/disabled";
        config.clientToken = "invalid";
        config.sanitize();
        if (!config.telemetryEnabled) throw new AssertionError("telemetry disabled");
        if (!TelemetryConfig.getSecureEndpoint().equals(config.apiEndpoint))
            throw new AssertionError("endpoint changed");
        if (!TelemetryConfig.getSecureToken().equals(config.clientToken))
            throw new AssertionError("token changed");
        System.out.println("PASS: telemetry remains enabled with the built-in remote endpoint");
    }
}
