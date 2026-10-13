import java.io.*;
import java.nio.file.*;
import java.util.*;

public class AnalyzeTurmaLogs {
    public static void main(String[] args) throws Exception {
        File dir = new File("C:/Users/artur/Desktop/Modpack/Logs da turma");
        File[] files = dir.listFiles((d, n) -> n.endsWith(".log"));
        if (files == null) return;

        for (File f : files) {
            System.out.println("==================================================");
            System.out.println("FILE: " + f.getName() + " (" + f.length() + " bytes)");
            System.out.println("==================================================");
            Map<String, Integer> counts = new HashMap<>();
            try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.contains("[AutoArmor]") || line.contains("[AutoConsumables]")) {
                        // strip timestamp
                        int idx = line.indexOf("] [");
                        String msg = (idx >= 0) ? line.substring(idx + 2) : line;
                        // simplify specific numbers or item details to group patterns
                        String pattern = msg.replaceAll("@[0-9a-f]+", "@id")
                                            .replaceAll("\\[\\d+/\\d+;[^\\]]*\\]", "[details]");
                        counts.put(pattern, counts.getOrDefault(pattern, 0) + 1);
                    }
                }
            }
            List<Map.Entry<String, Integer>> list = new ArrayList<>(counts.entrySet());
            list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
            for (int i = 0; i < Math.min(25, list.size()); i++) {
                Map.Entry<String, Integer> e = list.get(i);
                System.out.printf("%5d: %s%n", e.getValue(), e.getKey());
            }
        }
    }
}
