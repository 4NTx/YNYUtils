import java.io.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Patches the preserved package without rebuilding older source modules. */
public final class ShowClanInEnemyPing {
    private static final String BASE = "com/yny/utils/modules/clanping/";
    private static final String PING = BASE + "ClanPing.class";
    private static final String HUD = BASE + "ClanPingHud.class";
    private static final String HELPER = BASE + "PingClanName";

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("input.steinmod PingClanName.class output.steinmod");
        byte[] helper;
        try (InputStream in = new FileInputStream(args[1])) { helper = in.readAllBytes(); }
        int ping = 0, hud = 0;
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(args[0]));
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(args[2]))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                in.transferTo(bytes);
                byte[] data = bytes.toByteArray();
                if (PING.equals(entry.getName())) { data = patchPing(data); ping++; }
                if (HUD.equals(entry.getName())) { data = patchHud(data); hud++; }
                ZipEntry copy = new ZipEntry(entry.getName());
                copy.setTime(entry.getTime());
                out.putNextEntry(copy); out.write(data); out.closeEntry();
            }
            if (ping != 1 || hud != 1) throw new IllegalStateException("missing ping/HUD class");
            ZipEntry added = new ZipEntry(HELPER + ".class");
            out.putNextEntry(added); out.write(helper); out.closeEntry();
        }
        System.out.println("Enemy ping and HUD now resolve the clan from Players.group");
    }

    private static ClassNode read(byte[] bytes) {
        byte[] compatible = bytes.clone();
        compatible[6] = 0; compatible[7] = 60; // ASM 9.0 reader accepts up to Java 16.
        ClassNode node = new ClassNode();
        new ClassReader(compatible).accept(node, 0);
        node.version = ((bytes[6] & 255) << 8) | (bytes[7] & 255);
        return node;
    }

    private static byte[] write(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] patchPing(byte[] bytes) {
        ClassNode node = read(bytes);
        int count = 0;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("targetDisplayName") || !method.desc.equals("(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/String;")) continue;
            method.instructions.clear(); method.tryCatchBlocks.clear();
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
            method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "display", "(Ljava/lang/String;)Ljava/lang/String;", false));
            method.instructions.add(new InsnNode(Opcodes.ARETURN));
            method.maxLocals = 2; method.maxStack = 1; count++;
        }
        if (count != 1) throw new IllegalStateException("expected targetDisplayName once, found " + count);
        return write(node);
    }

    private static byte[] patchHud(byte[] bytes) {
        ClassNode node = read(bytes);
        int count = 0;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("layout") || !method.desc.equals("(ZF)Z")) continue;
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (!(insn instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.GETFIELD
                        || !field.owner.equals(BASE + "ClanPing$Active") || !field.name.equals("targetName")) continue;
                AbstractInsnNode next = insn.getNext();
                while (next instanceof LabelNode || next instanceof LineNumberNode || next instanceof FrameNode) next = next.getNext();
                if (next != null && next.getOpcode() == Opcodes.GOTO) {
                    method.instructions.insert(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "display", "(Ljava/lang/String;)Ljava/lang/String;", false));
                    count++;
                }
            }
        }
        if (count != 1) throw new IllegalStateException("expected live HUD target once, found " + count);
        return write(node);
    }
}
