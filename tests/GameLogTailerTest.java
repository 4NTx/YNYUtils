import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.zip.GZIPOutputStream;

import com.yny.utils.telemetry.GameLogTailer;
import com.yny.utils.telemetry.TelemetryConfig;

public final class GameLogTailerTest {
    public static void main(String[] args) throws Exception {
        File root = new File("build/remote-logs-test-game-" + System.nanoTime());
        File logs = new File(root, "logs");
        File yny = new File(logs, "ynyutils");
        if (!yny.isDirectory() && !yny.mkdirs()) throw new AssertionError("mkdir failed");
        File other = new File(logs, "othermod/diagnostic.log");
        if (!other.getParentFile().isDirectory() && !other.getParentFile().mkdirs()) throw new AssertionError("mkdir other failed");
        Files.write(other.toPath(), "other mod event\n".getBytes(StandardCharsets.UTF_8));
        File latest = new File(logs, "latest.log");
        Files.write(latest.toPath(), "[12:00] token=secretvalue\n[12:01] [CHAT] »Bem vindo de volta. Por favor digite /login suaSenha\n".getBytes(StandardCharsets.UTF_8));
        File archive = new File(logs, "2026-10-14-1.log.gz");
        try (GZIPOutputStream gzip = new GZIPOutputStream(Files.newOutputStream(archive.toPath()))) {
            gzip.write("[11:00] old event\n".getBytes(StandardCharsets.UTF_8));
        }
        TelemetryConfig config = TelemetryConfig.get();
        config.telemetryEnabled = true;
        config.apiEndpoint = "http://127.0.0.1:1/api/telemetry/event";
        GameLogTailer.start(yny);
        Thread.sleep(1500L);
        File pending = new File(yny, "remote-pending");
        assertCount(pending, 0);
        Files.write(latest.toPath(), "[12:01] next event\n".getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
        Thread.sleep(5500L);
        assertCount(pending, 0);
        System.out.println("PASS: game logs stay out of command-only remote telemetry");
    }

    private static void assertCount(File pending, int expected) {
        File[] files = pending.listFiles((dir, name) -> name.endsWith(".json"));
        int actual = files == null ? 0 : files.length;
        if (actual != expected) throw new AssertionError("expected " + expected + " entries, got " + actual);
    }
}


