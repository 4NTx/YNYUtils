import java.io.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Repairs the equipment render call and bundles the health latch inner class. */
public final class FixDamageRuntimeLinks {
    private static final String TARGET = "com/yny/targethealth/TargetHealthElement.class";
    private static final String SAMPLE = "com/yny/targethealth/HealthEventLatch$Sample.class";

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("input Sample.class output");
        int found = 0;
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(args[0]));
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(args[2]))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream(); in.transferTo(buffer);
                byte[] data = buffer.toByteArray();
                if (TARGET.equals(entry.getName())) { data = patch(data); found++; }
                if (SAMPLE.equals(entry.getName())) throw new IllegalStateException("Sample already bundled");
                ZipEntry copy = new ZipEntry(entry.getName()); copy.setTime(entry.getTime());
                out.putNextEntry(copy); out.write(data); out.closeEntry();
            }
            if (found != 1) throw new IllegalStateException("TargetHealthElement missing");
            out.putNextEntry(new ZipEntry(SAMPLE));
            try (InputStream sample = new FileInputStream(args[1])) { sample.transferTo(out); }
            out.closeEntry();
        }
    }

    private static byte[] patch(byte[] bytes) {
        byte[] compatible = bytes.clone(); compatible[6] = 0; compatible[7] = 60;
        ClassNode node = new ClassNode(); new ClassReader(compatible).accept(node, 0);
        node.version = ((bytes[6] & 255) << 8) | (bytes[7] & 255);
        int found = 0;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("drawItemCell") || !method.desc.equals("(Ljava/lang/Object;II)V")) continue;
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode)insn;
                if (call.owner.equals("com/yny/targethealth/TargetHealthElement") &&
                    call.name.equals("durability") && call.desc.equals("(Ljava/lang/Object;)Ljava/lang/String;")) {
                    method.instructions.insertBefore(insn, new TypeInsnNode(Opcodes.CHECKCAST, "zx"));
                    call.desc = "(Lzx;)Ljava/lang/String;";
                    found++;
                }
            }
        }
        if (found != 1) throw new IllegalStateException("durability call site count=" + found);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer);
        return writer.toByteArray();
    }
}
