import java.io.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Groups distance diagnostics without changing what the target HUD displays. */
public final class CompactDamageLogs {
    private static final String TARGET = "com/yny/targethealth/TargetHealthElement.class";
    private static final String HELPER = "com/yny/targethealth/DistanceLog";

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("input.steinmod DistanceLog.class output.steinmod");
        byte[] helper;
        try (InputStream stream = new FileInputStream(args[1])) { helper = stream.readAllBytes(); }
        int count = 0;
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(args[0]));
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(args[2]))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                in.transferTo(buffer);
                byte[] bytes = buffer.toByteArray();
                if (entry.getName().equals(TARGET)) { bytes = patch(bytes); count++; }
                ZipEntry copy = new ZipEntry(entry.getName());
                copy.setTime(entry.getTime());
                out.putNextEntry(copy); out.write(bytes); out.closeEntry();
            }
            if (count != 1) throw new IllegalStateException("target HUD class missing");
            out.putNextEntry(new ZipEntry(HELPER + ".class"));
            out.write(helper); out.closeEntry();
        }
        System.out.println("Damage Indicator diagnostics grouped by metre");
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
                if (!(instruction instanceof MethodInsnNode call) || !call.owner.equals("com/yny/utils/core/DebugLog")
                        || !call.name.equals("state")) continue;
                AbstractInsnNode previous = instruction.getPrevious();
                while (previous instanceof LabelNode || previous instanceof LineNumberNode || previous instanceof FrameNode) previous = previous.getPrevious();
                if (previous instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD
                        && field.name.equals("distanceText")) {
                    method.instructions.insert(previous, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "bucket", "(Ljava/lang/String;)Ljava/lang/String;", false));
                    count++;
                }
            }
        }
        if (count != 1) throw new IllegalStateException("expected one distance log, found " + count);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }
}
