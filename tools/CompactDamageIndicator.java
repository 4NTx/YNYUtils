import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Rebuilds TargetHealthElement inside YNYUtils.steinmod to delegate
 * layout, responsive height and rendering to TargetHudRenderer.
 */
public final class CompactDamageIndicator {
    private static final String OWNER = "com/yny/targethealth/TargetHealthElement";
    private static final String RENDERER = "com/yny/targethealth/TargetHudRenderer";
    private static final String TARGET_MOD = "com/yny/targethealth/TargetHealthMod";

    private CompactDamageIndicator() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("usage: CompactDamageIndicator input.steinmod output.steinmod");

        byte[] rendererBytes = Files.readAllBytes(new File("build/classes/com/yny/targethealth/TargetHudRenderer.class").toPath());
        byte[] modBytes = Files.readAllBytes(new File("build/classes/com/yny/targethealth/TargetHealthMod.class").toPath());

        try (ZipInputStream in = new ZipInputStream(new FileInputStream(args[0]));
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(args[1]))) {
            ZipEntry entry;
            boolean patched = false;
            boolean hadRenderer = false;
            boolean hadMod = false;
            while ((entry = in.getNextEntry()) != null) {
                byte[] bytes = readAll(in);
                if (entry.getName().equals(OWNER + ".class")) {
                    bytes = patch(bytes);
                    patched = true;
                } else if (entry.getName().equals(RENDERER + ".class")) {
                    bytes = rendererBytes;
                    hadRenderer = true;
                } else if (entry.getName().equals(TARGET_MOD + ".class")) {
                    bytes = modBytes;
                    hadMod = true;
                }
                ZipEntry copy = new ZipEntry(entry.getName());
                copy.setTime(entry.getTime());
                out.putNextEntry(copy);
                out.write(bytes);
                out.closeEntry();
            }
            if (!patched) throw new IllegalArgumentException("TargetHealthElement.class not found");
            if (!hadRenderer) {
                ZipEntry e = new ZipEntry(RENDERER + ".class");
                out.putNextEntry(e);
                out.write(rendererBytes);
                out.closeEntry();
            }
            if (!hadMod) {
                ZipEntry e = new ZipEntry(TARGET_MOD + ".class");
                out.putNextEntry(e);
                out.write(modBytes);
                out.closeEntry();
            }
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
        byte[] compatible = bytes.clone();
        compatible[6] = 0;
        compatible[7] = 60;
        ClassNode type = new ClassNode();
        new ClassReader(compatible).accept(type, 0);
        type.version = ((bytes[6] & 255) << 8) | (bytes[7] & 255);

        String targetDesc = fieldDesc(type, "target");
        String armorDesc = fieldDesc(type, "armor");
        String heldDesc = fieldDesc(type, "heldItem");

        // width() -> TargetHudRenderer.getWidth()
        MethodNode width = method(type, "width", "()I");
        clear(width);
        width.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RENDERER, "getWidth", "()I", false));
        width.instructions.add(new InsnNode(Opcodes.IRETURN));

        // height() -> TargetHudRenderer.getHeight(target, armor, heldItem)
        MethodNode height = method(type, "height", "()I");
        clear(height);
        height.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        height.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "target", targetDesc));
        height.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        height.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "armor", armorDesc));
        height.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        height.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "heldItem", heldDesc));
        height.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RENDERER, "getHeight",
                "(Ljava/lang/Object;[Ljava/lang/Object;Ljava/lang/Object;)I", false));
        height.instructions.add(new InsnNode(Opcodes.IRETURN));

        // draw(ZF)V -> TargetHudRenderer.draw(...)
        MethodNode draw = method(type, "draw", "(ZF)V");
        clear(draw);
        draw.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        draw.instructions.add(new VarInsnNode(Opcodes.FLOAD, 2));
        draw.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        draw.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "target", targetDesc));
        draw.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        draw.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "name", "Ljava/lang/String;"));
        draw.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        draw.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "distanceText", "Ljava/lang/String;"));
        draw.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        draw.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "healthText", "Ljava/lang/String;"));
        draw.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        draw.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "health", "F"));
        draw.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        draw.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "maximum", "F"));
        draw.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        draw.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "absorption", "F"));
        draw.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        draw.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "animatedHealth", "F"));
        draw.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        draw.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "damageTrailHealth", "F"));
        draw.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        draw.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "armor", armorDesc));
        draw.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        draw.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "heldItem", heldDesc));
        draw.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RENDERER, "draw",
                "(ZFLjava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;FFFFF[Ljava/lang/Object;Ljava/lang/Object;)V", false));
        draw.instructions.add(new InsnNode(Opcodes.RETURN));

        // Clear unused legacy drawing methods so they have no stale bytecode
        for (String deadMethod : new String[]{"drawClassic", "drawHearts", "drawCompact", "drawMinimal", "drawEquipment", "drawBar", "drawItemCell"}) {
            for (MethodNode m : type.methods) {
                if (m.name.equals(deadMethod)) {
                    clear(m);
                    m.instructions.add(new InsnNode(Opcodes.RETURN));
                }
            }
        }

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
}
