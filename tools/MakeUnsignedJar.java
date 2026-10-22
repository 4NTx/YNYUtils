import java.io.*;
import java.util.zip.*;

public class MakeUnsignedJar {
    public static void main(String[] args) throws Exception {
        File inFile = new File("data/.minecraft/versions/1.8.9/1.8.9.jar");
        File outFile = new File("build/1.8.9-unsigned.jar");
        outFile.getParentFile().mkdirs();

        try (ZipInputStream in = new ZipInputStream(new FileInputStream(inFile));
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(outFile))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                String n = e.getName().toUpperCase();
                if (n.startsWith("META-INF/") && (n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".DSA"))) {
                    continue;
                }
                out.putNextEntry(new ZipEntry(e.getName()));
                in.transferTo(out);
                out.closeEntry();
            }
        }
        System.out.println("PASS: created build/1.8.9-unsigned.jar");
    }
}
