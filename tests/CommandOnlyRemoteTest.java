package tests;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import com.yny.utils.config.YNYConfig;
import com.yny.utils.modules.player.ArmorPickupFilter;
import com.yny.utils.telemetry.RemoteLogUploader;

public final class CommandOnlyRemoteTest {
    public static void main(String[] args) throws Exception {
        Path logDir = Files.createTempDirectory("yny-priority-test-");
        if (!RemoteLogUploader.recordWithId(logDir.toFile(), "2026-10-15 00:00:00.000", "Command", "outgoing=/tell oi", null))
            throw new AssertionError("Command was not spooled");
        RemoteLogUploader.recordWithId(logDir.toFile(), "2026-10-15 00:00:00.000", "AutoArmor", "diagnostic", null);
        Path spool = logDir.resolve("remote-pending");
        File[] priority = spool.toFile().listFiles((dir, name) -> name.startsWith("!") && name.endsWith(".json"));
        if (priority == null || priority.length != 1) throw new AssertionError("New command has no priority file");
        Path legacy = spool.resolve("0000000000001-legacy-0001.json");
        byte[] content = "{\"id\":\"legacy\",\"category\":\"Command\",\"message\":\"outgoing=/home\"}".getBytes(StandardCharsets.UTF_8);
        Files.write(legacy, content);
        Method promote = RemoteLogUploader.class.getDeclaredMethod("promoteExistingCommands", File.class);
        promote.setAccessible(true);
        Path oldDiagnostic = spool.resolve("0000000000002-diagnostic-0001.json");
        Files.write(oldDiagnostic, "{\"category\":\"AutoArmor\",\"message\":\"diagnostic\"}".getBytes(StandardCharsets.UTF_8));
        promote.invoke(null, spool.toFile());
        if (Files.exists(legacy) || !Files.exists(spool.resolve("!" + legacy.getFileName())))
            throw new AssertionError("Old command was not prioritized");
        if (Files.exists(oldDiagnostic)) throw new AssertionError("Non-command backlog was retained");
        if (!java.util.Arrays.equals(content, Files.readAllBytes(spool.resolve("!" + legacy.getFileName()))))
            throw new AssertionError("Old command content changed");
        System.out.println("PASS: only commands are queued; old commands are promoted and diagnostics removed");
    }
}


