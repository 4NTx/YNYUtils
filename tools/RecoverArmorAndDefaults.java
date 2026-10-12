import java.io.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Repairs a transaction that the server accepted without sending slot callbacks. */
public final class RecoverArmorAndDefaults {
    private static final String OWNER = "com/yny/utils/modules/player/AutoArmor";
    private static final String ARMOR = OWNER + ".class";
    private static final String PENDING = OWNER + "$Pending";
    private static final String CONFIG = "com/yny/utils/config/YNYConfig.class";
    private static final String INVENTORY = "dev/xavier/stein/loader/api/Inventory";

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("input.steinmod YNYConfig.class output.steinmod");
        byte[] config;
        try (InputStream stream = new FileInputStream(args[1])) { config = stream.readAllBytes(); }
        int armorCount = 0, configCount = 0;
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(args[0]));
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(args[2]))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                in.transferTo(buffer);
                byte[] bytes = buffer.toByteArray();
                if (entry.getName().equals(ARMOR)) { bytes = patchArmor(bytes); armorCount++; }
                if (entry.getName().equals(CONFIG)) { bytes = config; configCount++; }
                ZipEntry copy = new ZipEntry(entry.getName());
                copy.setTime(entry.getTime());
                out.putNextEntry(copy); out.write(bytes); out.closeEntry();
            }
        }
        if (armorCount != 1 || configCount != 1) throw new IllegalStateException("missing AutoArmor or YNYConfig");
        System.out.println("AutoArmor timeout and config defaults patched");
    }

    private static byte[] patchArmor(byte[] bytes) {
        byte[] compatible = bytes.clone();
        compatible[6] = 0; compatible[7] = 60; // ASM 9.0 reads through Java 16.
        ClassNode node = new ClassNode();
        new ClassReader(compatible).accept(node, 0);
        node.version = ((bytes[6] & 255) << 8) | (bytes[7] & 255);
        int timeoutCount = 0, cursorCount = 0, fallbackCount = 0, upgradeCount = 0;
        for (MethodNode method : node.methods) {
            if (method.name.equals("bestReplacement") && method.desc.equals("(ILzx;I)L" + OWNER + "$Choice;")) {
                int stores = 0;
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction.getOpcode() == Opcodes.ASTORE && ((VarInsnNode) instruction).var == 5) {
                        if (++stores == 2) { method.instructions.set(instruction, new InsnNode(Opcodes.POP)); fallbackCount++; }
                    }
                }
            }
            if (method.name.equals("isPreventiveUpgrade") && method.desc.equals("(Lzx;Lzx;)Z")) {
                LabelNode reject = null;
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction.getOpcode() == Opcodes.IF_ICMPLT) { reject = ((JumpInsnNode) instruction).label; break; }
                }
                if (reject == null) throw new IllegalStateException("preventive rejection label missing");
                InsnList quality = new InsnList();
                quality.add(new FieldInsnNode(Opcodes.GETSTATIC, "aci", "c", "Laci;"));
                quality.add(new VarInsnNode(Opcodes.ALOAD, 0));
                quality.add(new MethodInsnNode(Opcodes.INVOKESTATIC, OWNER, "level", "(Laci;Lzx;)I", false));
                quality.add(new FieldInsnNode(Opcodes.GETSTATIC, "aci", "c", "Laci;"));
                quality.add(new VarInsnNode(Opcodes.ALOAD, 1));
                quality.add(new MethodInsnNode(Opcodes.INVOKESTATIC, OWNER, "level", "(Laci;Lzx;)I", false));
                quality.add(new JumpInsnNode(Opcodes.IF_ICMPLT, reject));
                quality.add(new VarInsnNode(Opcodes.ALOAD, 0));
                quality.add(new MethodInsnNode(Opcodes.INVOKESTATIC, OWNER, "protectionScore", "(Lzx;)J", false));
                quality.add(new VarInsnNode(Opcodes.ALOAD, 1));
                quality.add(new MethodInsnNode(Opcodes.INVOKESTATIC, OWNER, "protectionScore", "(Lzx;)J", false));
                quality.add(new InsnNode(Opcodes.LCMP));
                quality.add(new JumpInsnNode(Opcodes.IFLT, reject));
                method.instructions.insert(quality);
                upgradeCount++;
            }
            if (!method.name.equals("advancePending") || !method.desc.equals("()V")) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof LdcInsnNode ldc) || !Long.valueOf(Long.MAX_VALUE).equals(ldc.cst)) continue;
                ldc.cst = 8L; timeoutCount++;
                for (AbstractInsnNode before = instruction.getPrevious(); before != null; before = before.getPrevious()) {
                    if (!(before instanceof JumpInsnNode jump) || jump.getOpcode() != Opcodes.IFNULL) continue;
                    AbstractInsnNode previous = before.getPrevious();
                    if (!(previous instanceof MethodInsnNode call) || !call.owner.equals(INVENTORY) || !call.name.equals("cursor")) continue;
                    InsnList allowUnrelatedCursor = new InsnList();
                    allowUnrelatedCursor.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    allowUnrelatedCursor.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "pending", "L" + PENDING + ";"));
                    allowUnrelatedCursor.add(new FieldInsnNode(Opcodes.GETFIELD, PENDING, "oldArmor", "Lzx;"));
                    allowUnrelatedCursor.add(new JumpInsnNode(Opcodes.IFNULL, jump.label));
                    method.instructions.insert(jump, allowUnrelatedCursor);
                    cursorCount++;
                    break;
                }
            }
        }
        if (timeoutCount != 1 || cursorCount != 1 || fallbackCount != 1 || upgradeCount != 1) throw new IllegalStateException("unexpected armor sites: " + timeoutCount + "/" + cursorCount + "/" + fallbackCount + "/" + upgradeCount);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }
}
