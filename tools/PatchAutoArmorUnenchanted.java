import java.io.*;
import java.util.Enumeration;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public final class PatchAutoArmorUnenchanted {
    private static final String OWNER = "com/yny/utils/modules/player/AutoArmor";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: PatchAutoArmorUnenchanted <input.steinmod> <output.steinmod>");
            System.exit(1);
        }

        File inputFile = new File(args[0]);
        File outputFile = new File(args[1]);

        try (ZipFile zipIn = new ZipFile(inputFile);
             ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(outputFile))) {

            Enumeration<? extends ZipEntry> entries = zipIn.entries();
            int patched = 0;

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                byte[] bytes = readStream(zipIn.getInputStream(entry));

                if (entry.getName().equals(OWNER + ".class")) {
                    bytes = patch(bytes);
                    patched++;
                }

                ZipEntry copy = new ZipEntry(entry.getName());
                copy.setTime(entry.getTime());
                zipOut.putNextEntry(copy);
                zipOut.write(bytes);
                zipOut.closeEntry();
            }

            if (patched != 1) {
                throw new IllegalStateException("AutoArmor.class not found or patched: " + patched);
            }
        }
        System.out.println("PatchAutoArmorUnenchanted SUCCESS");
    }

    public static byte[] patch(byte[] original) {
        byte[] compatible = original.clone();
        compatible[6] = 0;
        compatible[7] = 60; // Java 16 compatible for ASM 9.0 reader
        ClassNode classNode = new ClassNode();
        new ClassReader(compatible).accept(classNode, 0);
        classNode.version = ((original[6] & 255) << 8) | (original[7] & 255);

        int patchSites = 0;
        for (MethodNode method : classNode.methods) {
            if ("bestReplacement".equals(method.name)) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof FieldInsnNode) {
                        FieldInsnNode field = (FieldInsnNode) insn;
                        if (field.getOpcode() == Opcodes.GETFIELD &&
                            "ignoreUnenchanted".equals(field.name) &&
                            OWNER.equals(field.owner)) {

                            AbstractInsnNode prev = insn.getPrevious();
                            if (prev instanceof VarInsnNode) {
                                VarInsnNode varInsn = (VarInsnNode) prev;
                                if (varInsn.getOpcode() == Opcodes.ALOAD && varInsn.var == 0) {
                                    // Find the JumpInsnNode (IFEQ) following the getAsBoolean call
                                    AbstractInsnNode next = insn.getNext();
                                    while (next != null && !(next instanceof JumpInsnNode && next.getOpcode() == Opcodes.IFEQ)) {
                                        next = next.getNext();
                                    }
                                    if (next instanceof JumpInsnNode) {
                                        JumpInsnNode ifeq = (JumpInsnNode) next;
                                        LabelNode targetLabel = ifeq.label;
                                        InsnList guard = new InsnList();
                                        // if (var2 == null || isBroken(var2)) goto targetLabel
                                        guard.add(new VarInsnNode(Opcodes.ALOAD, 2));
                                        guard.add(new JumpInsnNode(Opcodes.IFNULL, targetLabel));
                                        guard.add(new VarInsnNode(Opcodes.ALOAD, 2));
                                        guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, OWNER, "isBroken", "(Lzx;)Z", false));
                                        guard.add(new JumpInsnNode(Opcodes.IFNE, targetLabel));

                                        method.instructions.insertBefore(prev, guard);
                                        patchSites++;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (patchSites != 2) {
            throw new IllegalStateException("Expected 2 patch sites in bestReplacement, found " + patchSites);
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] readStream(InputStream is) throws IOException {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] tmp = new byte[8192];
            int n;
            while ((n = is.read(tmp)) != -1) {
                buffer.write(tmp, 0, n);
            }
            return buffer.toByteArray();
        }
    }
}
