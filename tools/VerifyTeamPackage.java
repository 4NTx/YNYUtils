import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class VerifyTeamPackage {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("package");
        int count = 0;
        try (ZipFile zip = new ZipFile(args[0])) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (!name.startsWith("com/yny/utils/team/") || !name.endsWith(".class")) continue;
                Class.forName(name.substring(0, name.length() - 6).replace("/", "."), false,
                        ClassLoader.getSystemClassLoader());
                count++;
            }
        }
        if (count < 20) throw new AssertionError("team classes missing: " + count);
        System.out.println("PASS: " + count + " team classes load and verify");
    }
}