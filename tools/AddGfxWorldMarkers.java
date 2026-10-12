import java.io.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Routes world markers through the Loader's WorldRender when it is available. */
public final class AddGfxWorldMarkers {
    private static final String MAIN = "com/yny/utils/YNYUtils.class";
    private static final String HELPER = "com/yny/utils/modules/clanping/GfxWorldMarkers";

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("input.steinmod GfxWorldMarkers.class output.steinmod");
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
                if (entry.getName().equals(MAIN)) { bytes = patch(bytes); found++; }
                ZipEntry copy = new ZipEntry(entry.getName());
                copy.setTime(entry.getTime());
                out.putNextEntry(copy); out.write(bytes); out.closeEntry();
            }
            if (found != 1) throw new IllegalStateException("YNYUtils class missing");
            out.putNextEntry(new ZipEntry(HELPER + ".class")); out.write(helper); out.closeEntry();
        }
        System.out.println("WorldRender compatibility path installed");
    }

    private static byte[] patch(byte[] bytes) {
        byte[] compatible = bytes.clone();
        compatible[6] = 0; compatible[7] = 60;
        ClassNode node = new ClassNode();
        new ClassReader(compatible).accept(node, 0);
        node.version = ((bytes[6] & 255) << 8) | (bytes[7] & 255);
        int count = 0;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("onRenderWorld") || !method.desc.equals("(F)V")) continue;
            LabelNode legacy = new LabelNode();
            InsnList choose = new InsnList();
            choose.add(new VarInsnNode(Opcodes.ALOAD, 0));
            choose.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/YNYUtils", "clanPing", "Lcom/yny/utils/modules/clanping/ClanPing;"));
            choose.add(new VarInsnNode(Opcodes.FLOAD, 1));
            choose.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "render", "(Lcom/yny/utils/modules/clanping/ClanPing;F)Z", false));
            choose.add(new JumpInsnNode(Opcodes.IFEQ, legacy));
            choose.add(new InsnNode(Opcodes.RETURN));
            choose.add(legacy);
            choose.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
            method.instructions.insert(choose);
            count++;
        }
        if (count != 1) throw new IllegalStateException("onRenderWorld site count=" + count);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }
}
