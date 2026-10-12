import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;

/** Rebuilds the compact target HUD methods inside an already mapped .steinmod. */
public final class CompactDamageIndicator {
    private static final String OWNER = "com/yny/targethealth/TargetHealthElement";
    private static final String SETTINGS = "com/yny/targethealth/Settings";
    private static final String TARGET_MOD = "com/yny/targethealth/TargetHealthMod";
    private static final String RENDER = "dev/xavier/stein/loader/api/Render";
    private static final String ITEM_CELL = "(Ljava/lang/Object;II)V";
    private CompactDamageIndicator() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("usage: CompactDamageIndicator input.steinmod output.steinmod");
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(args[0]));
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(args[1]))) {
            ZipEntry entry;
            boolean patched = false;
            while ((entry = in.getNextEntry()) != null) {
                byte[] bytes = readAll(in);
                if (entry.getName().equals(OWNER + ".class")) {
                    bytes = patch(bytes);
                    patched = true;
                }
                ZipEntry copy = new ZipEntry(entry.getName());
                copy.setTime(entry.getTime());
                out.putNextEntry(copy);
                out.write(bytes);
                out.closeEntry();
            }
            if (!patched) throw new IllegalArgumentException("TargetHealthElement.class not found");
        }
    }

    private static byte[] readAll(ZipInputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
        return out.toByteArray();
    }

    private static byte[] patch(byte[] bytes) {
        ClassNode type = new ClassNode();
        new ClassReader(bytes).accept(type, 0);
        String armorDesc = fieldDesc(type, "armor");
        String heldDesc = fieldDesc(type, "heldItem");
        MethodNode width = method(type, "width", "()I");
        clear(width); width.instructions.add(new IntInsnNode(Opcodes.SIPUSH, 250)); width.instructions.add(new InsnNode(Opcodes.IRETURN));
        MethodNode height = method(type, "height", "()I");
        clear(height); height.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 60)); height.instructions.add(new InsnNode(Opcodes.IRETURN));
        MethodNode draw = method(type, "draw", "(ZF)V");
        for (AbstractInsnNode insn : draw.instructions.toArray()) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)insn;
                if (call.owner.equals(OWNER) && call.desc.equals("(II)V")
                        && (call.name.equals("drawHearts") || call.name.equals("drawCompact") || call.name.equals("drawMinimal"))) {
                    call.name = "drawClassic";
                }
            }
        }
        replaceClassic(method(type, "drawClassic", "(II)V"));
        replaceEquipment(method(type, "drawEquipment", "(II)V"), armorDesc, heldDesc);
        addCellRenderer(type);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        type.accept(writer);
        return writer.toByteArray();
    }

    private static String fieldDesc(ClassNode type, String name) {
        for (FieldNode field : type.fields) if (field.name.equals(name)) return field.desc;
        throw new IllegalStateException("field not found: " + name);
    }

    private static MethodNode method(ClassNode type, String name, String desc) {
        for (MethodNode method : type.methods) if (method.name.equals(name) && method.desc.equals(desc)) return method;
        throw new IllegalStateException("method not found: " + name + desc);
    }

    private static void clear(MethodNode method) {
        method.instructions.clear();
        method.tryCatchBlocks.clear();
        method.localVariables = null;
        method.maxStack = 0;
        method.maxLocals = 0;
    }

    private static void replaceClassic(MethodNode m) {
        clear(m);
        InsnList i = m.instructions;
        LabelNode noName = new LabelNode(), noDistance = new LabelNode(), noNumbers = new LabelNode();
        settingCheck(i, "showName", noName);
        // Small neutral avatar tile in place of the oversized text-first panel.
        intConst(i, 3); i.add(new VarInsnNode(Opcodes.ILOAD, 1)); i.add(new InsnNode(Opcodes.IADD)); i.add(new InsnNode(Opcodes.I2F));
        ldc(i, 2.0F); ldc(i, 18.0F); ldc(i, 18.0F); ldc(i, 2.0F); intConst(i, 0xFF9C6848);
        i.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RENDER, "roundRect", "(FFFFFI)V", false));
        for (int eyeX : new int[]{8, 14}) {
            i.add(new VarInsnNode(Opcodes.ILOAD, 1)); intConst(i, eyeX); i.add(new InsnNode(Opcodes.IADD)); i.add(new InsnNode(Opcodes.I2F));
            ldc(i, 8.0F); ldc(i, 2.0F); ldc(i, 2.0F); intConst(i, 0xFF33251C);
            i.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RENDER, "rect", "(FFFFI)V", false));
        }
        i.add(new VarInsnNode(Opcodes.ILOAD, 1)); intConst(i, 9); i.add(new InsnNode(Opcodes.IADD)); i.add(new InsnNode(Opcodes.I2F));
        ldc(i, 14.0F); ldc(i, 6.0F); ldc(i, 1.0F); intConst(i, 0xFF633C2B);
        i.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RENDER, "rect", "(FFFFI)V", false));
        // Name beside the avatar.
        i.add(new VarInsnNode(Opcodes.ALOAD, 0)); i.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "name", "Ljava/lang/String;"));
        i.add(new IntInsnNode(Opcodes.SIPUSH, 150)); i.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RENDER, "trim", "(Ljava/lang/String;I)Ljava/lang/String;", false));
        i.add(new VarInsnNode(Opcodes.ILOAD, 1)); intConst(i, 25); i.add(new InsnNode(Opcodes.IADD)); i.add(new InsnNode(Opcodes.I2F));
        ldc(i, 5.0F); intConst(i, 0xFFFFFFFF); intConst(i, 1);
        i.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RENDER, "text", "(Ljava/lang/String;FFIZ)I", false)); i.add(new InsnNode(Opcodes.POP));
        i.add(noName); i.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        // Distance shares the header row, aligned to the right edge.
        settingCheck(i, "showDistance", noDistance);
        i.add(new VarInsnNode(Opcodes.ALOAD, 0)); i.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "distanceText", "Ljava/lang/String;"));
        i.add(new VarInsnNode(Opcodes.ILOAD, 1)); i.add(new VarInsnNode(Opcodes.ILOAD, 2)); i.add(new InsnNode(Opcodes.IADD));
        i.add(new VarInsnNode(Opcodes.ALOAD, 0)); i.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "distanceText", "Ljava/lang/String;"));
        i.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RENDER, "textWidth", "(Ljava/lang/String;)I", false));
        i.add(new InsnNode(Opcodes.ISUB)); intConst(i, 4); i.add(new InsnNode(Opcodes.ISUB)); i.add(new InsnNode(Opcodes.I2F));
        ldc(i, 5.0F); intConst(i, 0xFFFFDA80); intConst(i, 1);
        i.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RENDER, "text", "(Ljava/lang/String;FFIZ)I", false)); i.add(new InsnNode(Opcodes.POP));
        i.add(noDistance); i.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        // Numeric health sits beside a short, animated bar.
        settingCheck(i, "showNumbers", noNumbers);
        i.add(new VarInsnNode(Opcodes.ALOAD, 0)); i.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "healthText", "Ljava/lang/String;"));
        i.add(new VarInsnNode(Opcodes.ILOAD, 1)); i.add(new VarInsnNode(Opcodes.ILOAD, 2)); i.add(new InsnNode(Opcodes.IADD));
        i.add(new VarInsnNode(Opcodes.ALOAD, 0)); i.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "healthText", "Ljava/lang/String;"));
        i.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RENDER, "textWidth", "(Ljava/lang/String;)I", false));
        i.add(new InsnNode(Opcodes.ISUB)); intConst(i, 4); i.add(new InsnNode(Opcodes.ISUB)); i.add(new InsnNode(Opcodes.I2F));
        ldc(i, 17.0F);
        i.add(new VarInsnNode(Opcodes.ALOAD, 0));
        i.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "health", "F")); i.add(new VarInsnNode(Opcodes.ALOAD, 0));
        i.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "maximum", "F")); i.add(new InsnNode(Opcodes.FDIV));
        i.add(new MethodInsnNode(Opcodes.INVOKESTATIC, OWNER, "barColor", "(F)I", false)); intConst(i, 1);
        i.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RENDER, "text", "(Ljava/lang/String;FFIZ)I", false)); i.add(new InsnNode(Opcodes.POP));
        i.add(noNumbers); i.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        i.add(new VarInsnNode(Opcodes.ALOAD, 0)); i.add(new VarInsnNode(Opcodes.ILOAD, 1)); intConst(i, 3);
        i.add(new InsnNode(Opcodes.IADD)); intConst(i, 20); i.add(new VarInsnNode(Opcodes.ILOAD, 2));
        intConst(i, 56); i.add(new InsnNode(Opcodes.ISUB)); intConst(i, 5);
        i.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, OWNER, "drawBar", "(IIII)V", false));
        i.add(new InsnNode(Opcodes.RETURN));
    }

    private static void replaceEquipment(MethodNode m, String armorDesc, String heldDesc) {
        clear(m);
        InsnList i = m.instructions;
        LabelNode skipArmor = new LabelNode(), skipHand = new LabelNode();
        settingCheck(i, "showArmorDurability", skipArmor);
        for (int slot = 0; slot < 4; slot++) {
            i.add(new VarInsnNode(Opcodes.ALOAD, 0)); i.add(new VarInsnNode(Opcodes.ALOAD, 0));
            i.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "armor", armorDesc)); intConst(i, slot); i.add(new InsnNode(Opcodes.AALOAD));
            i.add(new VarInsnNode(Opcodes.ILOAD, 1)); i.add(new VarInsnNode(Opcodes.ILOAD, 2)); intConst(i, 210);
            i.add(new InsnNode(Opcodes.ISUB)); intConst(i, 2); i.add(new InsnNode(Opcodes.IDIV)); i.add(new InsnNode(Opcodes.IADD));
            intConst(i, slot * 42); i.add(new InsnNode(Opcodes.IADD)); intConst(i, 31);
            i.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, OWNER, "drawItemCell", ITEM_CELL, false));
        }
        i.add(skipArmor); i.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        settingCheck(i, "showHeldItem", skipHand);
        i.add(new VarInsnNode(Opcodes.ALOAD, 0)); i.add(new VarInsnNode(Opcodes.ALOAD, 0));
        i.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "heldItem", heldDesc));
        i.add(new VarInsnNode(Opcodes.ILOAD, 1)); i.add(new VarInsnNode(Opcodes.ILOAD, 2)); intConst(i, 210);
        i.add(new InsnNode(Opcodes.ISUB)); intConst(i, 2); i.add(new InsnNode(Opcodes.IDIV)); i.add(new InsnNode(Opcodes.IADD));
        intConst(i, 168); i.add(new InsnNode(Opcodes.IADD)); intConst(i, 31);
        i.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, OWNER, "drawItemCell", ITEM_CELL, false));
        i.add(skipHand); i.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        i.add(new InsnNode(Opcodes.RETURN));
    }

    private static void addCellRenderer(ClassNode type) {
        for (int index = type.methods.size() - 1; index >= 0; index--) {
            MethodNode existing = type.methods.get(index);
            if (existing.name.equals("drawItemCell") && existing.desc.equals(ITEM_CELL)) type.methods.remove(index);
        }
        MethodNode m = new MethodNode(Opcodes.ACC_PRIVATE, "drawItemCell", ITEM_CELL, null, null);
        InsnList i = m.instructions;
        LabelNode hasItem = new LabelNode();
        i.add(new VarInsnNode(Opcodes.ALOAD, 1)); i.add(new JumpInsnNode(Opcodes.IFNONNULL, hasItem));
        ldc(i, "—"); i.add(new VarInsnNode(Opcodes.ILOAD, 2)); intConst(i, 15); i.add(new InsnNode(Opcodes.IADD)); i.add(new InsnNode(Opcodes.I2F));
        i.add(new VarInsnNode(Opcodes.ILOAD, 3)); intConst(i, 19); i.add(new InsnNode(Opcodes.IADD)); i.add(new InsnNode(Opcodes.I2F));
        intConst(i, 0xFF728194); i.add(new InsnNode(Opcodes.ICONST_0));
        i.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RENDER, "text", "(Ljava/lang/String;FFIZ)I", false)); i.add(new InsnNode(Opcodes.POP));
        i.add(new InsnNode(Opcodes.RETURN));
        i.add(hasItem); i.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        i.add(new VarInsnNode(Opcodes.ALOAD, 1)); i.add(new VarInsnNode(Opcodes.ILOAD, 2)); intConst(i, 13); i.add(new InsnNode(Opcodes.IADD));
        i.add(new VarInsnNode(Opcodes.ILOAD, 3)); i.add(new InsnNode(Opcodes.ICONST_1));
        i.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RENDER, "item", "(Ljava/lang/Object;IIZ)V", false));
        i.add(new VarInsnNode(Opcodes.ALOAD, 1));
        i.add(new MethodInsnNode(Opcodes.INVOKESTATIC, OWNER, "durability", "(Ljava/lang/Object;)Ljava/lang/String;", false));
        intConst(i, 40); i.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RENDER, "trim", "(Ljava/lang/String;I)Ljava/lang/String;", false));
        i.add(new VarInsnNode(Opcodes.ILOAD, 2)); intConst(i, 1); i.add(new InsnNode(Opcodes.IADD)); i.add(new InsnNode(Opcodes.I2F));
        i.add(new VarInsnNode(Opcodes.ILOAD, 3)); intConst(i, 19); i.add(new InsnNode(Opcodes.IADD)); i.add(new InsnNode(Opcodes.I2F));
        intConst(i, 0xFFB7C5D4); i.add(new InsnNode(Opcodes.ICONST_0));
        i.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RENDER, "text", "(Ljava/lang/String;FFIZ)I", false)); i.add(new InsnNode(Opcodes.POP));
        i.add(new InsnNode(Opcodes.RETURN));
        type.methods.add(m);
    }

    private static void settingCheck(InsnList i, String field, LabelNode skip) {
        i.add(new FieldInsnNode(Opcodes.GETSTATIC, TARGET_MOD, "settings", "L" + SETTINGS + ";"));
        i.add(new FieldInsnNode(Opcodes.GETFIELD, SETTINGS, field, "Z"));
        i.add(new JumpInsnNode(Opcodes.IFEQ, skip));
    }
    private static void intConst(InsnList i, int value) {
        if (value >= -1 && value <= 5) i.add(new InsnNode(Opcodes.ICONST_0 + value));
        else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) i.add(new IntInsnNode(Opcodes.BIPUSH, value));
        else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) i.add(new IntInsnNode(Opcodes.SIPUSH, value));
        else i.add(new LdcInsnNode(value));
    }
    private static void ldc(InsnList i, Object value) { i.add(new LdcInsnNode(value)); }
}
