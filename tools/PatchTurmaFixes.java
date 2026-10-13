import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public final class PatchTurmaFixes {
    private static final String AUTO_ARMOR = "com/yny/utils/modules/player/AutoArmor.class";
    private static final String AUTO_CONSUMABLES = "com/yny/utils/modules/player/AutoConsumables.class";
    private static final String ARMOR_HELPER = "com/yny/utils/modules/player/AutoArmorHelper.class";
    private static final String CONSUMABLE_HELPER = "com/yny/utils/modules/player/ConsumableHelper.class";
    private static final String FILTER = "com/yny/utils/modules/player/ArmorPickupFilter.class";
    private static final String POLICY = "com/yny/utils/modules/player/ArmorPickupPolicy.class";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: PatchTurmaFixes <in.steinmod> <out.steinmod>");
            System.exit(1);
        }

        File inFile = new File(args[0]);
        File outFile = new File(args[1]);

        byte[] armorHelperBytes = readBytes(new File("tools/com/yny/utils/modules/player/AutoArmorHelper.class"));
        byte[] consumableHelperBytes = readBytes(new File("tools/com/yny/utils/modules/player/ConsumableHelper.class"));
        byte[] filterBytes = readBytes(new File("tools/com/yny/utils/modules/player/ArmorPickupFilter.class"));
        byte[] policyBytes = readBytes(new File("tools/com/yny/utils/modules/player/ArmorPickupPolicy.class"));

        byte[] patchedArmorBytes = null;
        byte[] patchedConsumablesBytes = null;

        Map<String, byte[]> outEntries = new LinkedHashMap<>();

        try (ZipFile zipIn = new ZipFile(inFile)) {
            Enumeration<? extends ZipEntry> entries = zipIn.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                byte[] data = readStream(zipIn.getInputStream(entry));

                if (AUTO_ARMOR.equals(entry.getName())) {
                    data = patchAutoArmor(data);
                    patchedArmorBytes = data;
                } else if (AUTO_CONSUMABLES.equals(entry.getName())) {
                    data = patchAutoConsumables(data);
                    patchedConsumablesBytes = data;
                } else if (FILTER.equals(entry.getName())) {
                    data = filterBytes;
                } else if (POLICY.equals(entry.getName())) {
                    data = policyBytes;
                } else if (ARMOR_HELPER.equals(entry.getName()) || CONSUMABLE_HELPER.equals(entry.getName())) {
                    continue; // will add below
                }

                outEntries.put(entry.getName(), data);
            }
        }

        outEntries.put(ARMOR_HELPER, armorHelperBytes);
        outEntries.put(CONSUMABLE_HELPER, consumableHelperBytes);

        // Write out
        File tempOut = new File(outFile.getAbsolutePath() + ".tmp");
        try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(tempOut))) {
            for (Map.Entry<String, byte[]> e : outEntries.entrySet()) {
                ZipEntry entry = new ZipEntry(e.getKey());
                zipOut.putNextEntry(entry);
                zipOut.write(e.getValue());
                zipOut.closeEntry();
            }
        }

        if (outFile.exists()) outFile.delete();
        if (!tempOut.renameTo(outFile)) {
            throw new IOException("Failed to rename temp file to " + outFile);
        }

        System.out.println("PASS: Successfully patched AutoArmor, AutoConsumables, helpers, and filter into " + outFile.getName());
    }

    public static byte[] patchAutoArmor(byte[] original) {
        byte[] compatible = original.clone();
        compatible[6] = 0;
        compatible[7] = 60;
        ClassNode classNode = new ClassNode();
        new ClassReader(compatible).accept(classNode, ClassReader.EXPAND_FRAMES);
        classNode.version = ((original[6] & 255) << 8) | (original[7] & 255);

        int eatingGuardPatched = 0;
        int screenPausePatched = 0;
        int fastConfirmPatched = 0;
        int hotbarEquipPatched = 0;
        int comparePatched = 0;

        for (MethodNode method : classNode.methods) {
            // 1. In onTickEnd: pause AutoArmor while player is eating/drinking
            if ("onTickEnd".equals(method.name) && "()V".equals(method.desc)) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode min = (MethodInsnNode) insn;
                        if ("bew".equals(min.owner) && "ai".equals(min.name) && "()Z".equals(min.desc)) {
                            // Find following jump instruction
                            AbstractInsnNode next = insn.getNext();
                            while (next != null && !(next instanceof JumpInsnNode)) {
                                next = next.getNext();
                            }
                            if (next instanceof JumpInsnNode) {
                                JumpInsnNode jin = (JumpInsnNode) next;
                                LabelNode labelPaused = jin.label;

                                InsnList guard = new InsnList();
                                // if (mc.h.bS()) goto labelPaused;
                                guard.add(new VarInsnNode(Opcodes.ALOAD, 1));
                                guard.add(new FieldInsnNode(Opcodes.GETFIELD, "ave", "h", "Lbew;"));
                                guard.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "bew", "bS", "()Z", false));
                                guard.add(new JumpInsnNode(Opcodes.IFNE, labelPaused));

                                method.instructions.insert(jin, guard);
                                eatingGuardPatched++;
                                break;
                            }
                        }
                    }
                }
            }

            // 2. In advancePending: freeze age on GUI screen & 2-tick fast confirm
            if ("advancePending".equals(method.name) && "()V".equals(method.desc)) {
                // A. Freeze age increment when screen is open
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof FieldInsnNode) {
                        FieldInsnNode fin = (FieldInsnNode) insn;
                        if (fin.getOpcode() == Opcodes.PUTFIELD && "age".equals(fin.name) && "com/yny/utils/modules/player/AutoArmor$Pending".equals(fin.owner)) {
                            // We found the age increment putfield
                            // Find the starting ALOAD 0 before GETFIELD pending
                            AbstractInsnNode prev = fin.getPrevious();
                            while (prev != null) {
                                if (prev instanceof VarInsnNode && prev.getOpcode() == Opcodes.ALOAD && ((VarInsnNode)prev).var == 0) {
                                    AbstractInsnNode nextCheck = prev.getNext();
                                    if (nextCheck instanceof FieldInsnNode && "pending".equals(((FieldInsnNode)nextCheck).name)) {
                                        break;
                                    }
                                }
                                prev = prev.getPrevious();
                            }
                            if (prev != null) {
                                LabelNode skipAge = new LabelNode();
                                InsnList guard = new InsnList();
                                guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "ave", "A", "()Lave;", false));
                                guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/player/AutoArmor", "hasUnsafeInventoryUi", "(Lave;)Z", false));
                                guard.add(new JumpInsnNode(Opcodes.IFNE, skipAge));

                                method.instructions.insertBefore(prev, guard);
                                method.instructions.insert(fin, skipAge);
                                screenPausePatched++;
                                break;
                            }
                        }
                    }
                }

                // B. 2-Tick fast confirm on localApplied
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof FieldInsnNode) {
                        FieldInsnNode fin = (FieldInsnNode) insn;
                        if (fin.getOpcode() == Opcodes.GETFIELD && "serverConfirmed".equals(fin.name) && "com/yny/utils/modules/player/AutoArmor$Pending".equals(fin.owner)) {
                            // Preceding must be GETFIELD pending and ALOAD 0
                            AbstractInsnNode prev = fin.getPrevious();
                            if (prev instanceof FieldInsnNode && "pending".equals(((FieldInsnNode)prev).name)) {
                                AbstractInsnNode prevPrev = prev.getPrevious();
                                if (prevPrev instanceof VarInsnNode && ((VarInsnNode)prevPrev).var == 0) {
                                    AbstractInsnNode next = fin.getNext();
                                    if (next instanceof JumpInsnNode && next.getOpcode() == Opcodes.IFEQ) {
                                        JumpInsnNode ifeq = (JumpInsnNode) next;
                                        // Next node after ifeq is the start of settleOldArmorCursor block
                                        AbstractInsnNode afterIfeq = ifeq.getNext();
                                        while (afterIfeq != null && !(afterIfeq instanceof LabelNode)) {
                                            afterIfeq = afterIfeq.getNext();
                                        }
                                        LabelNode labelProceed = (LabelNode) afterIfeq;

                                        InsnList replacement = new InsnList();
                                        // if (this.pending.serverConfirmed) goto labelProceed;
                                        replacement.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                        replacement.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/modules/player/AutoArmor", "pending", "Lcom/yny/utils/modules/player/AutoArmor$Pending;"));
                                        replacement.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/modules/player/AutoArmor$Pending", "serverConfirmed", "Z"));
                                        replacement.add(new JumpInsnNode(Opcodes.IFNE, labelProceed));

                                        // if (this.pending.age >= 2) goto labelProceed;
                                        replacement.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                        replacement.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/modules/player/AutoArmor", "pending", "Lcom/yny/utils/modules/player/AutoArmor$Pending;"));
                                        replacement.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/modules/player/AutoArmor$Pending", "age", "I"));
                                        replacement.add(new InsnNode(Opcodes.ICONST_2));
                                        replacement.add(new JumpInsnNode(Opcodes.IF_ICMPGE, labelProceed));

                                        // return;
                                        replacement.add(new InsnNode(Opcodes.RETURN));

                                        // Remove prevPrev, prev, fin, ifeq
                                        method.instructions.remove(prevPrev);
                                        method.instructions.remove(prev);
                                        method.instructions.remove(fin);
                                        method.instructions.insertBefore(ifeq, replacement);
                                        method.instructions.remove(ifeq);
                                        fastConfirmPatched++;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. In beginEquip: use AutoArmorHelper.performEquip
            if ("beginEquip".equals(method.name)) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode min = (MethodInsnNode) insn;
                        if ("dev/xavier/stein/loader/api/Inventory".equals(min.owner) && "equip".equals(min.name) && "(I)Z".equals(min.desc)) {
                            // Preceding loads choice.inventoryIndex
                            // We need: (piece, choice.inventoryIndex, worn)
                            // piece is iload 1, worn is aload 3
                            AbstractInsnNode choiceLoad = min.getPrevious(); // GETFIELD inventoryIndex
                            AbstractInsnNode choiceVar = choiceLoad != null ? choiceLoad.getPrevious() : null; // ALOAD 2

                            InsnList call = new InsnList();
                            call.add(new VarInsnNode(Opcodes.ILOAD, 1)); // piece
                            call.add(new VarInsnNode(Opcodes.ALOAD, 2));
                            call.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/modules/player/AutoArmor$Choice", "inventoryIndex", "I"));
                            call.add(new VarInsnNode(Opcodes.ALOAD, 3)); // worn
                            call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/player/AutoArmorHelper", "performEquip", "(IILjava/lang/Object;)Z", false));

                            if (choiceVar != null) method.instructions.remove(choiceVar);
                            if (choiceLoad != null) method.instructions.remove(choiceLoad);
                            method.instructions.insertBefore(min, call);
                            method.instructions.remove(min);
                            hotbarEquipPatched++;
                            break;
                        }
                    }
                }
            }

            // 4. In compare: use AutoArmorHelper.compareSlots
            if ("compare".equals(method.name)) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode min = (MethodInsnNode) insn;
                        if ("java/lang/Integer".equals(min.owner) && "compare".equals(min.name) && "(II)I".equals(min.desc)) {
                            InsnList call = new InsnList();
                            call.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            call.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/modules/player/AutoArmor$Choice", "inventoryIndex", "I"));
                            call.add(new VarInsnNode(Opcodes.ALOAD, 1));
                            call.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/modules/player/AutoArmor$Choice", "inventoryIndex", "I"));
                            call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/player/AutoArmorHelper", "compareSlots", "(II)I", false));

                            // Remove previous 4 instructions: aload 1, getfield, aload 0, getfield
                            AbstractInsnNode cur = min.getPrevious();
                            for (int k = 0; k < 4 && cur != null; k++) {
                                AbstractInsnNode p = cur.getPrevious();
                                method.instructions.remove(cur);
                                cur = p;
                            }
                            method.instructions.insertBefore(min, call);
                            method.instructions.remove(min);
                            comparePatched++;
                            break;
                        }
                    }
                }
            }
        }

        if (eatingGuardPatched != 1) throw new IllegalStateException("eatingGuardPatched=" + eatingGuardPatched);
        if (screenPausePatched != 1) throw new IllegalStateException("screenPausePatched=" + screenPausePatched);
        if (fastConfirmPatched != 1) throw new IllegalStateException("fastConfirmPatched=" + fastConfirmPatched);
        if (hotbarEquipPatched != 1) throw new IllegalStateException("hotbarEquipPatched=" + hotbarEquipPatched);
        if (comparePatched != 1) throw new IllegalStateException("comparePatched=" + comparePatched);

        System.out.printf("Patched AutoArmor: eatingGuard=%d, screenPause=%d, fastConfirm=%d, hotbarEquip=%d, compare=%d%n",
                eatingGuardPatched, screenPausePatched, fastConfirmPatched, hotbarEquipPatched, comparePatched);

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

    public static byte[] patchAutoConsumables(byte[] original) {
        byte[] compatible = original.clone();
        compatible[6] = 0;
        compatible[7] = 60;
        ClassNode classNode = new ClassNode();
        new ClassReader(compatible).accept(classNode, ClassReader.EXPAND_FRAMES);
        classNode.version = ((original[6] & 255) << 8) | (original[7] & 255);

        int beginUseHoldPatched = 0;
        int restoreSlotPatched = 0;
        int restoreKeyPatched = 0;
        int cancelHoldPatched = 0;
        int resetHoldPatched = 0;
        int continueHoldPatched = 0;

        for (MethodNode method : classNode.methods) {
            // 1. In beginUse(ILwn;I)Z
            if ("beginUse".equals(method.name) && "(ILwn;I)Z".equals(method.desc)) {
                // A. restoreSlot patch: after var 4 is set
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof VarInsnNode && insn.getOpcode() == Opcodes.ISTORE && ((VarInsnNode)insn).var == 4) {
                        // Find the label/frame right after this
                        AbstractInsnNode next = insn.getNext();
                        while (next != null && !(next instanceof FrameNode || next instanceof LabelNode)) {
                            next = next.getNext();
                        }
                        if (next != null) {
                            InsnList slotFix = new InsnList();
                            slotFix.add(new VarInsnNode(Opcodes.ILOAD, 4));
                            slotFix.add(new VarInsnNode(Opcodes.ILOAD, 1));
                            slotFix.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/player/ConsumableHelper", "resolveRestoreSlot", "(II)I", false));
                            slotFix.add(new VarInsnNode(Opcodes.ISTORE, 4));
                            method.instructions.insert(next, slotFix);
                            restoreSlotPatched++;
                            break;
                        }
                    }
                }

                // B. hold key on beginUse: after putfield avb.h Z
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof FieldInsnNode) {
                        FieldInsnNode fin = (FieldInsnNode) insn;
                        if (fin.getOpcode() == Opcodes.PUTFIELD && "avb".equals(fin.owner) && "h".equals(fin.name) && "Z".equals(fin.desc)) {
                            InsnList holdCall = new InsnList();
                            holdCall.add(new InsnNode(Opcodes.ICONST_1));
                            holdCall.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/player/ConsumableHelper", "holdUseKey", "(Z)V", false));
                            method.instructions.insert(fin, holdCall);
                            beginUseHoldPatched++;
                            break;
                        }
                    }
                }
            }

            // 2. In restoreUseKey()V: release key
            if ("restoreUseKey".equals(method.name) && "()V".equals(method.desc)) {
                InsnList releaseCall = new InsnList();
                releaseCall.add(new InsnNode(Opcodes.ICONST_0));
                releaseCall.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/player/ConsumableHelper", "holdUseKey", "(Z)V", false));
                method.instructions.insert(releaseCall);
                restoreKeyPatched++;
            }

            // 3. In cancelPendingUse(Z)V: release key
            if ("cancelPendingUse".equals(method.name) && "(Z)V".equals(method.desc)) {
                InsnList releaseCall = new InsnList();
                releaseCall.add(new InsnNode(Opcodes.ICONST_0));
                releaseCall.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/player/ConsumableHelper", "holdUseKey", "(Z)V", false));
                method.instructions.insert(releaseCall);
                cancelHoldPatched++;
            }

            // 4. In resetSession()V: release key
            if ("resetSession".equals(method.name) && "()V".equals(method.desc)) {
                InsnList releaseCall = new InsnList();
                releaseCall.add(new InsnNode(Opcodes.ICONST_0));
                releaseCall.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/player/ConsumableHelper", "holdUseKey", "(Z)V", false));
                method.instructions.insert(releaseCall);
                resetHoldPatched++;
            }

            // 5. In finishOrContinueUse: ensure key held while eating
            if ("finishOrContinueUse".equals(method.name) && "(Lwn;)V".equals(method.desc)) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof FieldInsnNode) {
                        FieldInsnNode fin = (FieldInsnNode) insn;
                        if (fin.getOpcode() == Opcodes.PUTFIELD && "sawUsing".equals(fin.name) && "com/yny/utils/modules/player/AutoConsumables$PendingUse".equals(fin.owner)) {
                            InsnList holdCall = new InsnList();
                            holdCall.add(new InsnNode(Opcodes.ICONST_1));
                            holdCall.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/player/ConsumableHelper", "holdUseKey", "(Z)V", false));
                            method.instructions.insert(fin, holdCall);
                            continueHoldPatched++;
                            break;
                        }
                    }
                }
            }
        }

        if (beginUseHoldPatched != 1) throw new IllegalStateException("beginUseHoldPatched=" + beginUseHoldPatched);
        if (restoreSlotPatched != 1) throw new IllegalStateException("restoreSlotPatched=" + restoreSlotPatched);
        if (restoreKeyPatched != 1) throw new IllegalStateException("restoreKeyPatched=" + restoreKeyPatched);
        if (cancelHoldPatched != 1) throw new IllegalStateException("cancelHoldPatched=" + cancelHoldPatched);
        if (resetHoldPatched != 1) throw new IllegalStateException("resetHoldPatched=" + resetHoldPatched);
        if (continueHoldPatched != 1) throw new IllegalStateException("continueHoldPatched=" + continueHoldPatched);

        System.out.printf("Patched AutoConsumables: beginUseHold=%d, restoreSlot=%d, restoreKey=%d, cancelHold=%d, resetHold=%d, continueHold=%d%n",
                beginUseHoldPatched, restoreSlotPatched, restoreKeyPatched, cancelHoldPatched, resetHoldPatched, continueHoldPatched);

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
