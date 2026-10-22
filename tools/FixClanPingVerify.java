import java.io.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public final class FixClanPingVerify {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: FixClanPingVerify <in.steinmod> <out.steinmod>");
            System.exit(1);
        }

        File inFile = new File(args[0]);
        File outFile = new File(args[1]);
        File tempOut = new File(outFile.getAbsolutePath() + ".tmp");

        int patched = 0;
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(inFile));
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(tempOut))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                in.transferTo(buffer);
                byte[] bytes = buffer.toByteArray();

                if ("com/yny/utils/modules/clanping/ClanPing.class".equals(entry.getName())) {
                    bytes = patchClanPing(bytes);
                    patched++;
                }

                ZipEntry copy = new ZipEntry(entry.getName());
                copy.setTime(entry.getTime());
                out.putNextEntry(copy);
                out.write(bytes);
                out.closeEntry();
            }
        }

        if (patched != 1) {
            tempOut.delete();
            throw new IllegalStateException("ClanPing.class not found/patched: " + patched);
        }

        if (outFile.exists()) outFile.delete();
        if (!tempOut.renameTo(outFile)) {
            throw new IOException("Failed to rename temp file to " + outFile);
        }
        System.out.println("PASS: Successfully patched ClanPing.class stack frames!");
    }

    private static byte[] patchClanPing(byte[] original) {
        byte[] compatible = original.clone();
        compatible[6] = 0;
        compatible[7] = 60;
        ClassNode node = new ClassNode();
        new ClassReader(compatible).accept(node, 0);
        node.version = ((original[6] & 255) << 8) | (original[7] & 255);

        boolean found = false;
        for (MethodNode method : node.methods) {
            if ("canCreatePing".equals(method.name) && "()Z".equals(method.desc)) {
                method.instructions.clear();
                LabelNode allow = new LabelNode();
                method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/modules/clanping/ClanPing", "transport", "Lcom/yny/utils/modules/clanping/ClanPingTransport;"));
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "com/yny/utils/modules/clanping/ClanPingTransport", "canSend", "()Z", false));
                method.instructions.add(new JumpInsnNode(Opcodes.IFNE, allow));
                method.instructions.add(new LdcInsnNode("§eClan Ping: somente líder autorizado pode pingar."));
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "dev/xavier/stein/loader/api/Chat", "actionBar", "(Ljava/lang/String;)V", false));
                method.instructions.add(new InsnNode(Opcodes.ICONST_0));
                method.instructions.add(new InsnNode(Opcodes.IRETURN));
                method.instructions.add(allow);
                method.instructions.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
                method.instructions.add(new InsnNode(Opcodes.ICONST_1));
                method.instructions.add(new InsnNode(Opcodes.IRETURN));
                found = true;
                break;
            }
        }

        if (!found) {
            throw new IllegalStateException("canCreatePing method not found");
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }
}
