import com.yny.utils.telemetry.LocalLogRetention;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class LocalLogRetentionTest {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("yny-retention-");
        try {
            long now = System.currentTimeMillis();
            Date old = new Date(now - 35L * 60L * 1000L);
            Date fresh = new Date(now - 5L * 60L * 1000L);
            SimpleDateFormat day = new SimpleDateFormat("yyyyMMdd", Locale.ROOT);
            SimpleDateFormat time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT);
            Path oldFile = dir.resolve("combat-" + day.format(old) + ".log");
            Path freshFile = dir.resolve("combat-" + day.format(fresh) + ".log");
            String oldLine = "[" + time.format(old) + "] [Combat] old";
            String freshLine = "[" + time.format(fresh) + "] [Combat] fresh";
            Files.write(oldFile, Arrays.asList(oldLine), StandardCharsets.UTF_8);
            if (oldFile.equals(freshFile)) {
                Files.write(oldFile, Arrays.asList(freshLine), StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.APPEND);
            } else {
                Files.write(freshFile, Arrays.asList(freshLine), StandardCharsets.UTF_8);
            }
            Path spool = Files.createDirectories(dir.resolve("remote-pending"));
            Path pending = spool.resolve("entry.json");
            Files.write(pending, Arrays.asList("pending"), StandardCharsets.UTF_8);
            LocalLogRetention.prune(dir.toFile());
            List<String> kept = Files.readAllLines(freshFile, StandardCharsets.UTF_8);
            if (kept.size() != 1 || !freshLine.equals(kept.get(0))) throw new AssertionError("fresh log missing");
            if (!oldFile.equals(freshFile) && Files.exists(oldFile)) throw new AssertionError("old log remains");
            if (!Files.exists(pending)) throw new AssertionError("unconfirmed spool was deleted");
            System.out.println("PASS: local 30-minute retention preserves fresh and pending logs");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(dir)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (Exception ignored) { }
                });
            }
        }
    }
}
