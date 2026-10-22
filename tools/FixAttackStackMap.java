import java.io.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Restores the missing stack map at the HitThroughWeb early return. */
public final class FixAttackStackMap {
    private static final String ENTRY = "com/yny/utils/YNYUtils.class";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("input.steinmod output.steinmod");
        int count = 0;
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(args[0]));
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(args[1]))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                in.transferTo(data);
                byte[] bytes = data.toByteArray();
                if (ENTRY.equals(entry.getName())) { bytes = patch(bytes); count++; }
                ZipEntry copy = new ZipEntry(entry.getName());
                copy.setTime(entry.getTime());
                out.putNextEntry(copy); out.write(bytes); out.closeEntry();
            }
        }
        if (count != 1) throw new IllegalStateException("YNYUtils.class count=" + count);
    }

    private static byte[] patch(byte[] bytes) {
        byte[] compatible = bytes.clone();
        compatible[6] = 0; compatible[7] = 60;
        ClassNode node = new ClassNode();
        new ClassReader(compatible).accept(node, 0);
        node.version = ((bytes[6] & 255) << 8) | (bytes[7] & 255);
        int patched = 0;
        for (MethodNode method : node.methods) {
            if (!"onAttackEntity".equals(method.name) || !"(Ljava/lang/Object;)Z".equals(method.desc)) continue;
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) insn;
                if (!"com/yny/utils/modules/pvp/HitThroughWeb".equals(call.owner)
                        || !"isWebAttacking".equals(call.name)) continue;
                AbstractInsnNode next = insn.getNext();
                if (!(next instanceof JumpInsnNode) || next.getOpcode() != Opcodes.IFEQ)
                    throw new IllegalStateException("attack branch changed");
                LabelNode target = ((JumpInsnNode) next).label;
                AbstractInsnNode after = target.getNext();
                while (after instanceof LineNumberNode) after = after.getNext();
                if (after instanceof FrameNode) throw new IllegalStateException("attack frame already exists");
                method.instructions.insert(target, new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
                patched++;
            }
        }
        if (patched != 1) throw new IllegalStateException("attack hook count=" + patched);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }
}