package tests;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import com.yny.utils.core.DebugLog;
import com.yny.utils.telemetry.ClanIdentityResolver;
import com.yny.utils.telemetry.OutgoingCommandLog;
import com.yny.utils.telemetry.TelemetryConfig;

public final class CommandClanTelemetryTest {
    public static void main(String[] args) throws Exception {
        Path game = Files.createTempDirectory("yny-command-test-");
        TelemetryConfig.get().apiEndpoint = "http://127.0.0.1:1/api/telemetry/event";
        DebugLog.initialize(game.toFile(), true);
        OutgoingCommandLog.record("hello");
        OutgoingCommandLog.record("/tell oi");
        Path logs = game.resolve("logs/ynyutils");
        boolean found = false;
        for (int i = 0; i < 40 && !found; i++) {
            Thread.sleep(50);
            File[] files = logs.toFile().listFiles((dir, name) -> name.startsWith("system-") && name.endsWith(".log"));
            if (files == null) continue;
            for (File file : files) {
                String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                if (content.contains("[Command] outgoing=/tell oi") && !content.contains("outgoing=hello")) found = true;
            }
        }
        if (!found) throw new AssertionError("Outgoing command was not logged exactly");
        if (!"FWs".equals(ClanIdentityResolver.clanFromLabel("§aClã: §fFWs")))
            throw new AssertionError("Sidebar clan label was not parsed");
        System.out.println("PASS: outgoing command and sidebar clan parser");
    }
}
