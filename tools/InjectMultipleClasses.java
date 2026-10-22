import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class InjectMultipleClasses {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: InjectMultipleClasses <in.steinmod> <classesDir> <out.steinmod> [targetClassRelativePath...]");
            System.exit(1);
        }

        File inFile = new File(args[0]);
        File classesDir = new File(args[1]);
        File outFile = new File(args[2]);

        Map<String, byte[]> replacements = new HashMap<>();

        if (args.length > 3) {
            for (int i = 3; i < args.length; i++) {
                String path = args[i].replace('\\', '/');
                File f = new File(classesDir, path);
                if (!f.exists()) {
                    throw new FileNotFoundException("Replacement class file not found: " + f.getAbsolutePath());
                }
                replacements.put(path, Files.readAllBytes(f.toPath()));
            }
        } else {
            // Scan classesDir for all .class files
            Path root = classesDir.toPath();
            Files.walk(root).filter(p -> p.toString().endsWith(".class")).forEach(p -> {
                String rel = root.relativize(p).toString().replace('\\', '/');
                try {
                    replacements.put(rel, Files.readAllBytes(p));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }

        System.out.println("Injecting " + replacements.size() + " classes into " + inFile.getName() + " -> " + outFile.getName());

        Set<String> replaced = new HashSet<>();
        File tempOut = new File(outFile.getAbsolutePath() + ".tmp");
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(inFile));
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(tempOut))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                byte[] data;
                String name = entry.getName();
                if (replacements.containsKey(name)) {
                    data = replacements.get(name);
                    replaced.add(name);
                    System.out.println("  Replaced: " + name + " (" + data.length + " bytes)");
                } else {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    in.transferTo(buffer);
                    data = buffer.toByteArray();
                }

                ZipEntry copy = new ZipEntry(name);
                copy.setTime(entry.getTime());
                out.putNextEntry(copy);
                out.write(data);
                out.closeEntry();
            }

            // If any replacement class was newly added (did not exist in in.steinmod)
            for (Map.Entry<String, byte[]> e : replacements.entrySet()) {
                if (!replaced.contains(e.getKey())) {
                    ZipEntry brandNew = new ZipEntry(e.getKey());
                    brandNew.setTime(System.currentTimeMillis());
                    out.putNextEntry(brandNew);
                    out.write(e.getValue());
                    out.closeEntry();
                    System.out.println("  Added new: " + e.getKey() + " (" + e.getValue().length + " bytes)");
                }
            }
        }

        if (outFile.exists()) outFile.delete();
        if (!tempOut.renameTo(outFile)) {
            throw new IOException("Failed to rename temp file to " + outFile);
        }
        System.out.println("PASS: Successfully injected " + replacements.size() + " classes into " + outFile.getName());
    }
}
