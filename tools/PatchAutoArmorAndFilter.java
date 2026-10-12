import java.io.*;
import java.util.Enumeration;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public final class PatchAutoArmorAndFilter {
    private static final String AUTO_ARMOR = "com/yny/utils/modules/player/AutoArmor.class";
    private static final String FILTER = "com/yny/utils/modules/player/ArmorPickupFilter.class";

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: PatchAutoArmorAndFilter <in.steinmod> <new-filter.class> <out.steinmod>");
            System.exit(1);
        }

        File inFile = new File(args[0]);
        File filterFile = new File(args[1]);
        File outFile = new File(args[2]);

        byte[] newFilterBytes = readBytes(filterFile);

        int armorCount = 0;
        int filterCount = 0;

        try (ZipFile zipIn = new ZipFile(inFile);
             ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(outFile))) {

            Enumeration<? extends ZipEntry> entries = zipIn.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                byte[] data = readStream(zipIn.getInputStream(entry));

                if (AUTO_ARMOR.equals(entry.getName())) {
                    data = patchAutoArmor(data);
                    armorCount++;
                } else if (FILTER.equals(entry.getName())) {
                    data = newFilterBytes;
                    filterCount++;
                }

                ZipEntry copy = new ZipEntry(entry.getName());
                copy.setTime(entry.getTime());
                zipOut.putNextEntry(copy);
                zipOut.write(data);
                zipOut.closeEntry();
            }
        }

        if (armorCount != 1 || filterCount != 1) {
            throw new IllegalStateException("Failed to update entries: armor=" + armorCount + ", filter=" + filterCount);
        }

        System.out.println("PASS: Successfully patched AutoArmor and ArmorPickupFilter into steinmod");
    }

    private static byte[] patchAutoArmor(byte[] original) {
        return PatchAutoArmorUnenchanted.patch(original);
    }

    private static byte[] readBytes(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return readStream(in);
        }
    }

    private static byte[] readStream(InputStream in) throws IOException {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] tmp = new byte[8192];
            int n;
            while ((n = in.read(tmp)) != -1) {
                buffer.write(tmp, 0, n);
            }
            return buffer.toByteArray();
        }
    }
}
