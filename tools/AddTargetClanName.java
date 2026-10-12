import java.io.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public final class AddTargetClanName {
    private static final String TARGET = "com/yny/targethealth/TargetHealthElement.class";
    private static final String HELPER = "com/yny/targethealth/TargetClanName";

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("input helper.class output");
        int found = 0;
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(args[0]));
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(args[2]))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream(); in.transferTo(buffer);
                byte[] bytes = buffer.toByteArray();
                if (TARGET.equals(entry.getName())) { bytes = patch(bytes); found++; }
                ZipEntry copy = new ZipEntry(entry.getName()); copy.setTime(entry.getTime());
                out.putNextEntry(copy); out.write(bytes); out.closeEntry();
            }
            if (found != 1) throw new IllegalStateException("TargetHealthElement missing");
            out.putNextEntry(new ZipEntry(HELPER + ".class"));
            try (InputStream helper = new FileInputStream(args[1])) { helper.transferTo(out); }
            out.closeEntry();
        }
    }

    private static byte[] patch(byte[] bytes) {
        byte[] compatible = bytes.clone(); compatible[6] = 0; compatible[7] = 60;
        ClassNode node = new ClassNode(); new ClassReader(compatible).accept(node, 0);
        node.version = ((bytes[6] & 255) << 8) | (bytes[7] & 255);
        int found = 0;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("safeDisplayName") || !method.desc.equals("(Lpr;)Ljava/lang/String;")) continue;
            method.instructions.clear(); method.tryCatchBlocks.clear();
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER,
                    "display", "(Ljava/lang/Object;)Ljava/lang/String;", false));
            method.instructions.add(new InsnNode(Opcodes.ARETURN));
            method.maxLocals = 1; method.maxStack = 1; found++;
        }
        if (found != 1) throw new IllegalStateException("safeDisplayName missing");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer);
        return writer.toByteArray();
    }
}
