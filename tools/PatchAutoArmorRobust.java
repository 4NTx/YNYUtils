import java.io.*;
import java.util.Enumeration;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public final class PatchAutoArmorRobust {
    private static final String AUTO_ARMOR = "com/yny/utils/modules/player/AutoArmor.class";
    private static final String FILTER = "com/yny/utils/modules/player/ArmorPickupFilter.class";
    private static final String POLICY = "com/yny/utils/modules/player/ArmorPickupPolicy.class";
    private static final String OWNER = "com/yny/utils/modules/player/AutoArmor";

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.err.println("Usage: PatchAutoArmorRobust <in.steinmod> <filter.class> <policy.class> <out.steinmod>");
            System.exit(1);
        }

        File inFile = new File(args[0]);
        File filterFile = new File(args[1]);
        File policyFile = new File(args[2]);
        File outFile = new File(args[3]);

        byte[] newFilterBytes = readBytes(filterFile);
        byte[] newPolicyBytes = readBytes(policyFile);

        int armorCount = 0;
        int filterCount = 0;
        int policyCount = 0;

        try (ZipFile zipIn = new ZipFile(inFile);
             ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(outFile))) {

            Enumeration<? extends ZipEntry> entries = zipIn.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                byte[] data = readStream(zipIn.getInputStream(entry));

                if (AUTO_ARMOR.equals(entry.getName())) {
                    data = patchAutoArmor(data);
                    armorCount++;
                } else if (FILTER.equals(entry.getName())) {
                    data = newFilterBytes;
                    filterCount++;
                } else if (POLICY.equals(entry.getName())) {
                    data = newPolicyBytes;
                    policyCount++;
                }

                ZipEntry copy = new ZipEntry(entry.getName());
                copy.setTime(entry.getTime());
                zipOut.putNextEntry(copy);
                zipOut.write(data);
                zipOut.closeEntry();
            }
        }

        if (armorCount != 1 || filterCount != 1 || policyCount != 1) {
            throw new IllegalStateException("Failed to update entries: armor=" + armorCount + ", filter=" + filterCount + ", policy=" + policyCount);
        }

        System.out.println("PASS: Successfully patched AutoArmor, ArmorPickupFilter and ArmorPickupPolicy into steinmod");
    }

    public static byte[] patchAutoArmor(byte[] original) {
        byte[] compatible = original.clone();
        compatible[6] = 0;
        compatible[7] = 60; // Java 16 compatible for ASM 9.0 reader
        ClassNode classNode = new ClassNode();
        new ClassReader(compatible).accept(classNode, ClassReader.EXPAND_FRAMES);
        classNode.version = ((original[6] & 255) << 8) | (original[7] & 255);

        int matchesPatched = 0;
        int timeoutPatched = 0;
        int unenchantedPatched = 0;

        for (MethodNode method : classNode.methods) {
            // 1. Patch matchesEquipState
            if ("matchesEquipState".equals(method.name) && "(Lzx;Lzx;Lcom/yny/utils/modules/player/AutoArmor$Pending;)Z".equals(method.desc)) {
                method.instructions.clear();
                InsnList il = new InsnList();
                LabelNode labelFalse = new LabelNode();
                LabelNode labelTrue = new LabelNode();

                // if (!matchesReplacementArmor(armor, operation)) return false;
                il.add(new VarInsnNode(Opcodes.ALOAD, 1)); // armor
                il.add(new VarInsnNode(Opcodes.ALOAD, 2)); // operation
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, OWNER, "matchesReplacementArmor", "(Lzx;Lcom/yny/utils/modules/player/AutoArmor$Pending;)Z", false));
                il.add(new JumpInsnNode(Opcodes.IFEQ, labelFalse));

                // if (operation.oldArmor == null) return true;
                il.add(new VarInsnNode(Opcodes.ALOAD, 2)); // operation
                il.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/modules/player/AutoArmor$Pending", "oldArmor", "Lzx;"));
                il.add(new JumpInsnNode(Opcodes.IFNULL, labelTrue));

                // if (matchesFormerArmor(source, operation)) return true;
                il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // source
                il.add(new VarInsnNode(Opcodes.ALOAD, 2)); // operation
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, OWNER, "matchesFormerArmor", "(Lzx;Lcom/yny/utils/modules/player/AutoArmor$Pending;)Z", false));
                il.add(new JumpInsnNode(Opcodes.IFNE, labelTrue));

                // if (source == null) return true;
                il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // source
                il.add(new JumpInsnNode(Opcodes.IFNULL, labelTrue));

                // return false;
                il.add(labelFalse);
                il.add(new InsnNode(Opcodes.ICONST_0));
                il.add(new InsnNode(Opcodes.IRETURN));

                // return true;
                il.add(labelTrue);
                il.add(new InsnNode(Opcodes.ICONST_1));
                il.add(new InsnNode(Opcodes.IRETURN));

                method.instructions = il;
                matchesPatched++;
            }

            // 2. Patch advancePending timeout (change 8L to 16L)
            if ("advancePending".equals(method.name)) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof LdcInsnNode) {
                        LdcInsnNode ldc = (LdcInsnNode) insn;
                        if (Long.valueOf(8L).equals(ldc.cst)) {
                            ldc.cst = Long.valueOf(16L);
                            timeoutPatched++;
                        }
                    }
                }
            }

            // 3. Patch bestReplacement to allow unenchanted when empty/broken slot
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
                                    // Check if guard already exists before prev
                                    AbstractInsnNode beforePrev = varInsn.getPrevious();
                                    boolean alreadyGuarded = false;
                                    if (beforePrev instanceof JumpInsnNode && beforePrev.getOpcode() == Opcodes.IFNE) {
                                        alreadyGuarded = true;
                                    }

                                    if (!alreadyGuarded) {
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
                                            unenchantedPatched++;
                                        }
                                    } else {
                                        unenchantedPatched++;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (matchesPatched != 1) {
            throw new IllegalStateException("Expected 1 matchesEquipState patch, got " + matchesPatched);
        }
        if (timeoutPatched < 1) {
            throw new IllegalStateException("Expected at least 1 timeout patch, got " + timeoutPatched);
        }
        if (unenchantedPatched != 2) {
            throw new IllegalStateException("Expected 2 unenchanted patch sites, got " + unenchantedPatched);
        }

        System.out.println("Patched AutoArmor: matchesPatched=" + matchesPatched + ", timeoutPatched=" + timeoutPatched + ", unenchantedPatched=" + unenchantedPatched);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                if (type1.equals(type2)) return type1;
                if ("java/lang/Object".equals(type1) || "java/lang/Object".equals(type2)) return "java/lang/Object";
                try {
                    return super.getCommonSuperClass(type1, type2);
                } catch (Throwable t) {
                    return "java/lang/Object";
                }
            }
        };
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] readBytes(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return readStream(in);
        }
    }

    private static byte[] readStream(InputStream in) throws IOException {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] tmp = new byte[8192];
            int n;
            while ((n = in.read(tmp)) != -1) {
                buffer.write(tmp, 0, n);
            }
            return buffer.toByteArray();
        }
    }
}
