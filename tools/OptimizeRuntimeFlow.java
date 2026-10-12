import java.io.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Applies the ally click, InvMove reservation, and target sampling corrections. */
public final class OptimizeRuntimeFlow {
    private static final String MAIN = "com/yny/utils/YNYUtils.class";
    private static final String MOVE = "com/yny/utils/modules/player/InventoryMove.class";
    private static final String INDICATOR = "com/yny/targethealth/TargetHealthElement.class";
    private static final String ALLIES = "com/yny/utils/modules/pvp/HitThroughAllies.class";
    private static final String ALLY_OWNER = "com/yny/utils/modules/pvp/HitThroughAllies";

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("input HitThroughAllies.class output");
        int main = 0, move = 0, indicator = 0, allies = 0;
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(args[0]));
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(args[2]))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream(); in.transferTo(buffer);
                byte[] bytes = buffer.toByteArray();
                if (MAIN.equals(entry.getName())) { bytes = patchMain(bytes); main++; }
                if (MOVE.equals(entry.getName())) { bytes = patchMove(bytes); move++; }
                if (INDICATOR.equals(entry.getName())) { bytes = patchIndicator(bytes); indicator++; }
                if (ALLIES.equals(entry.getName())) {
                    try (InputStream source = new FileInputStream(args[1])) { bytes = source.readAllBytes(); }
                    allies++;
                }
                ZipEntry copy = new ZipEntry(entry.getName()); copy.setTime(entry.getTime());
                out.putNextEntry(copy); out.write(bytes); out.closeEntry();
            }
        }
        if (main != 1 || move != 1 || indicator != 1 || allies != 1)
            throw new IllegalStateException("missing class: " + main + "/" + move + "/" + indicator + "/" + allies);
    }

    private static ClassNode read(byte[] bytes) {
        byte[] compatible = bytes.clone(); compatible[6] = 0; compatible[7] = 60;
        ClassNode node = new ClassNode(); new ClassReader(compatible).accept(node, 0);
        node.version = ((bytes[6] & 255) << 8) | (bytes[7] & 255); return node;
    }
    private static byte[] write(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }

    private static byte[] patchMain(byte[] bytes) {
        ClassNode node = read(bytes); int found = 0;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("onAttackEntity") || !method.desc.equals("(Ljava/lang/Object;)Z")) continue;
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode)insn;
                if (!call.owner.equals("com/yny/utils/modules/ModuleDiagnostics") || !call.name.equals("combatEvent")) continue;
                LabelNode ordinary = new LabelNode(); InsnList added = new InsnList();
                added.add(new VarInsnNode(Opcodes.ALOAD, 0));
                added.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/YNYUtils", "hitThroughAllies", "L" + ALLY_OWNER + ";"));
                added.add(new VarInsnNode(Opcodes.ALOAD, 1));
                added.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ALLY_OWNER, "allowClickedAlly", "(Ljava/lang/Object;)Z", false));
                added.add(new JumpInsnNode(Opcodes.IFEQ, ordinary));
                added.add(new VarInsnNode(Opcodes.ALOAD, 0));
                added.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/YNYUtils", "autoConsumables", "Lcom/yny/utils/modules/player/AutoConsumables;"));
                added.add(new VarInsnNode(Opcodes.ALOAD, 1));
                added.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "com/yny/utils/modules/player/AutoConsumables", "onAttack", "(Ljava/lang/Object;)V", false));
                added.add(new InsnNode(Opcodes.ICONST_0)); added.add(new InsnNode(Opcodes.IRETURN));
                added.add(ordinary); added.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
                method.instructions.insert(insn, added); found++;
            }
        }
        if (found != 1) throw new IllegalStateException("attack hook site=" + found);
        return write(node);
    }

    private static byte[] patchMove(byte[] bytes) {
        ClassNode node = read(bytes); int found = 0;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("onTickEnd") || !method.desc.equals("()V")) continue;
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (!(insn instanceof FieldInsnNode)) continue;
                FieldInsnNode field = (FieldInsnNode)insn;
                if (!field.name.equals("useKeyReserved") || field.getOpcode() != Opcodes.GETFIELD) continue;
                AbstractInsnNode call = insn.getNext();
                if (!(call instanceof MethodInsnNode) || !((MethodInsnNode)call).name.equals("getAsBoolean"))
                    throw new IllegalStateException("reservation call changed");
                InsnList added = new InsnList();
                added.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/player/AutoProtect", "isControllingUseKey", "()Z", false));
                added.add(new InsnNode(Opcodes.IOR));
                method.instructions.insert(call, added); found++;
            }
        }
        if (found != 1) throw new IllegalStateException("InvMove reservation site=" + found);
        return write(node);
    }

    private static byte[] patchIndicator(byte[] bytes) {
        ClassNode node = read(bytes); int found = 0;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("layout") || !method.desc.equals("(ZF)Z")) continue;
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode)insn;
                if (!call.owner.equals("com/yny/targethealth/TargetHealthElement") || !call.name.equals("updateTarget")) continue;
                AbstractInsnNode receiver = insn.getPrevious();
                if (!(receiver instanceof VarInsnNode) || receiver.getOpcode() != Opcodes.ALOAD || ((VarInsnNode)receiver).var != 0)
                    throw new IllegalStateException("layout target call changed");
                method.instructions.remove(receiver); method.instructions.remove(insn); found++;
            }
        }
        if (found != 1) throw new IllegalStateException("duplicate target sample site=" + found);
        return write(node);
    }
}
