import java.io.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Preserves the HUD target briefly across a missed raycast frame. */
public final class AddTargetGrace {
    private static final String TARGET = "com/yny/targethealth/TargetHealthElement.class";
    private static final String HELPER = "com/yny/targethealth/TargetGrace";

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("input.steinmod TargetGrace.class output.steinmod");
        byte[] helper;
        try (InputStream stream = new FileInputStream(args[1])) { helper = stream.readAllBytes(); }
        int found = 0;
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(args[0]));
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(args[2]))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                in.transferTo(buffer);
                byte[] bytes = buffer.toByteArray();
                if (entry.getName().equals(TARGET)) { bytes = patch(bytes); found++; }
                ZipEntry copy = new ZipEntry(entry.getName());
                copy.setTime(entry.getTime());
                out.putNextEntry(copy); out.write(bytes); out.closeEntry();
            }
            if (found != 1) throw new IllegalStateException("Target HUD class missing");
            out.putNextEntry(new ZipEntry(HELPER + ".class")); out.write(helper); out.closeEntry();
        }
        System.out.println("Target grace period installed");
    }

    private static byte[] patch(byte[] bytes) {
        byte[] compatible = bytes.clone();
        compatible[6] = 0; compatible[7] = 60;
        ClassNode node = new ClassNode();
        new ClassReader(compatible).accept(node, 0);
        node.version = ((bytes[6] & 255) << 8) | (bytes[7] & 255);
        int count = 0;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("updateTarget") || !method.desc.equals("()V")) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals("com/yny/targethealth/TargetHealthElement")
                        && call.name.equals("findTarget")) {
                    InsnList grace = new InsnList();
                    grace.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    grace.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/targethealth/TargetHealthElement", "target", "Lpr;"));
                    grace.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "choose", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false));
                    method.instructions.insert(call, grace);
                    count++;
                }
            }
        }
        if (count != 1) throw new IllegalStateException("findTarget site count=" + count);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }
}
