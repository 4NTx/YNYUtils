import java.io.*;
import java.util.zip.*;

public final class UpdateAutoProtectInPack {
    private static final String TARGET = "com/yny/utils/modules/player/AutoProtect.class";

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: UpdateAutoProtectInPack <in.steinmod> <AutoProtect.class> <out.steinmod>");
            System.exit(1);
        }

        File inFile = new File(args[0]);
        File newClassFile = new File(args[1]);
        File outFile = new File(args[2]);

        byte[] newClassBytes;
        try (InputStream in = new FileInputStream(newClassFile)) {
            newClassBytes = in.readAllBytes();
        }

        int count = 0;
        File tempOut = new File(outFile.getAbsolutePath() + ".tmp");
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(inFile));
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(tempOut))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                byte[] data;
                if (TARGET.equals(entry.getName())) {
                    data = newClassBytes;
                    count++;
                } else {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    in.transferTo(buffer);
                    data = buffer.toByteArray();
                }

                ZipEntry copy = new ZipEntry(entry.getName());
                copy.setTime(entry.getTime());
                out.putNextEntry(copy);
                out.write(data);
                out.closeEntry();
            }
        }

        if (count != 1) {
            tempOut.delete();
            throw new IllegalStateException("Expected exactly 1 target entry replaced, but got: " + count);
        }

        if (outFile.exists()) outFile.delete();
        if (!tempOut.renameTo(outFile)) {
            throw new IOException("Failed to rename temp file to " + outFile);
        }
        System.out.println("PASS: Successfully updated AutoProtect.class in " + outFile.getName());
    }
}
