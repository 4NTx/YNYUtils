import java.io.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Adds a safe build/config marker to the mod's world-join event. */
public final class AddStartupAudit {
    private static final String MAIN = "com/yny/utils/YNYUtils.class";
    private static final String AUDIT = "com/yny/utils/modules/StartupAudit.class";

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("input.steinmod StartupAudit.class output.steinmod");
        byte[] audit;
        try (InputStream stream = new FileInputStream(args[1])) { audit = stream.readAllBytes(); }
        int found = 0;
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(args[0]));
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(args[2]))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                in.transferTo(buffer);
                byte[] bytes = buffer.toByteArray();
                if (entry.getName().equals(MAIN)) { bytes = patch(bytes); found++; }
                ZipEntry copy = new ZipEntry(entry.getName());
                copy.setTime(entry.getTime());
                out.putNextEntry(copy); out.write(bytes); out.closeEntry();
            }
            if (found != 1) throw new IllegalStateException("YNYUtils class missing");
            out.putNextEntry(new ZipEntry(AUDIT)); out.write(audit); out.closeEntry();
        }
        System.out.println("Startup audit marker installed");
    }

    private static byte[] patch(byte[] bytes) {
        byte[] compatible = bytes.clone();
        compatible[6] = 0; compatible[7] = 60;
        ClassNode node = new ClassNode();
        new ClassReader(compatible).accept(node, 0);
        node.version = ((bytes[6] & 255) << 8) | (bytes[7] & 255);
        int count = 0;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("onJoinGame") || !method.desc.equals("()V")) continue;
            InsnList call = new InsnList();
            call.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/yny/utils/YNYUtils", "config", "Lcom/yny/utils/config/YNYConfig;"));
            call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/StartupAudit", "log", "(Lcom/yny/utils/config/YNYConfig;)V", false));
            method.instructions.insert(call);
            count++;
        }
        if (count != 1) throw new IllegalStateException("onJoinGame site count=" + count);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }
}
