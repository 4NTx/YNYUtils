import java.io.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public class StripHostClinit {
    public static void main(String[] args) throws Exception {
        File apiJar = new File("C:/Users/artur/.stein-sdk/1.8.9/stein-api.jar");
        byte[] b = null;
        try (ZipFile zf = new ZipFile(apiJar)) {
            ZipEntry ze = zf.getEntry("dev/xavier/stein/loader/api/spi/Host.class");
            try (InputStream in = zf.getInputStream(ze)) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) != -1) baos.write(buf, 0, r);
                b = baos.toByteArray();
            }
        }
        ClassReader cr = new ClassReader(b);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        cn.methods.removeIf(m -> m.name.equals("<clinit>"));
        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        File out = new File("tools/dev/xavier/stein/loader/api/spi/Host.class");
        out.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(cw.toByteArray());
        }
        System.out.println("Stripped <clinit> from Host.class and saved to " + out.getPath());
    }
}
