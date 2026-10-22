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
    private static final String THRESHOLD = "com/yny/utils/modules/player/ArmorThreshold.class";
    private static final String YNY_UTILS = "com/yny/utils/YNYUtils.class";

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
        byte[] thresholdBytes = readBytes(new File("tools/com/yny/utils/modules/player/ArmorThreshold.class"));

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
                } else if (YNY_UTILS.equals(entry.getName())) {
                    data = patchYNYUtils(data);
                } else if (FILTER.equals(entry.getName())) {
                    data = filterBytes;
                } else if (POLICY.equals(entry.getName())) {
                    data = policyBytes;
                } else if (THRESHOLD.equals(entry.getName())) {
                    data = thresholdBytes;
                } else if (ARMOR_HELPER.equals(entry.getName()) || CONSUMABLE_HELPER.equals(entry.getName())) {
                    continue; // will add below
                }

                outEntries.put(entry.getName(), data);
            }
        }

        outEntries.put(ARMOR_HELPER, armorHelperBytes);
        outEntries.put(CONSUMABLE_HELPER, consumableHelperBytes);
        outEntries.put(THRESHOLD, thresholdBytes);

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

        System.out.println("PASS: Successfully patched AutoArmor, AutoConsumables, helpers, threshold, and filter into " + outFile.getName());
    }

    public static byte[] patchYNYUtils(byte[] original) {
        byte[] compatible = original.clone();
        compatible[6] = 0;
        compatible[7] = 60;
        ClassNode classNode = new ClassNode();
        new ClassReader(compatible).accept(classNode, ClassReader.EXPAND_FRAMES);
        classNode.version = ((original[6] & 255) << 8) | (original[7] & 255);

        int sliderPatched = 0;
        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn instanceof LdcInsnNode) {
                    LdcInsnNode ldc = (LdcInsnNode) insn;
                    if ("Repor armadura abaixo desta durabilidade".equals(ldc.cst)
                            || "Troca preventiva abaixo de".equals(ldc.cst)) {
                        AbstractInsnNode next = ldc.getNext();
                        while (next != null && next.getOpcode() != Opcodes.INVOKESTATIC) {
                            if (next instanceof LdcInsnNode) {
                                LdcInsnNode nextLdc = (LdcInsnNode) next;
                                if (Double.valueOf(8.0).equals(nextLdc.cst) || Double.valueOf(50.0).equals(nextLdc.cst)) {
                                    nextLdc.cst = Double.valueOf(7.0);
                                    sliderPatched++;
                                    break;
                                } else if (Double.valueOf(7.0).equals(nextLdc.cst)) {
                                    sliderPatched++;
                                    break;
                                }
                            }
                            next = next.getNext();
                        }
                    }
                }
            }
        }
        System.out.printf("Patched YNYUtils sliderMax: count=%d%n", sliderPatched);

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

    public static byte[] patchAutoArmor(byte[] original) {
        byte[] compatible = original.clone();
        compatible[6] = 0;
        compatible[7] = 60;
        ClassNode classNode = new ClassNode();
        new ClassReader(compatible).accept(classNode, ClassReader.EXPAND_FRAMES);
        classNode.version = ((original[6] & 255) << 8) | (original[7] & 255);

        int eatingGuardPatched = 0;
        int cursorRecoveryPatched = 0;
        int thresholdPatched = 0;
        int screenPausePatched = 0;
        int fastConfirmPatched = 0;
        int hotbarEquipPatched = 0;
        int comparePatched = 0;
        int preventiveUpgradePatched = 0;
        int fallbackStorePatched = 0;

        for (MethodNode method : classNode.methods) {
            // 1. In onTickEnd: pause AutoArmor safely (never block for sword-blocking) + recover orphaned cursor + normalize threshold
            if ("onTickEnd".equals(method.name) && "()V".equals(method.desc)) {
                // Check if bew.bS is already present, or if shouldPauseForUsingItem is already called
                boolean foundGuard = false;
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode min = (MethodInsnNode) insn;
                        if ("bew".equals(min.owner) && "bS".equals(min.name) && "()Z".equals(min.desc)) {
                            // Replace bew.bS() with AutoArmorHelper.shouldPauseForUsingItem(Object)
                            MethodInsnNode helperCall = new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                "com/yny/utils/modules/player/AutoArmorHelper",
                                "shouldPauseForUsingItem",
                                "(Ljava/lang/Object;)Z",
                                false
                            );
                            method.instructions.set(min, helperCall);
                            eatingGuardPatched++;
                            foundGuard = true;
                            break;
                        }
                        if ("com/yny/utils/modules/player/AutoArmorHelper".equals(min.owner)
                                && "shouldPauseForUsingItem".equals(min.name)) {
                            eatingGuardPatched++;
                            foundGuard = true;
                            break;
                        }
                    }
                }
                if (!foundGuard) {
                    for (AbstractInsnNode insn : method.instructions.toArray()) {
                        if (insn instanceof MethodInsnNode) {
                            MethodInsnNode min = (MethodInsnNode) insn;
                            if ("bew".equals(min.owner) && "ai".equals(min.name) && "()Z".equals(min.desc)) {
                                AbstractInsnNode next = insn.getNext();
                                while (next != null && !(next instanceof JumpInsnNode)) {
                                    next = next.getNext();
                                }
                                if (next instanceof JumpInsnNode) {
                                    JumpInsnNode jin = (JumpInsnNode) next;
                                    LabelNode labelPaused = jin.label;

                                    InsnList guard = new InsnList();
                                    guard.add(new VarInsnNode(Opcodes.ALOAD, 1));
                                    guard.add(new FieldInsnNode(Opcodes.GETFIELD, "ave", "h", "Lbew;"));
                                    guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/player/AutoArmorHelper", "shouldPauseForUsingItem", "(Ljava/lang/Object;)Z", false));
                                    guard.add(new JumpInsnNode(Opcodes.IFNE, labelPaused));

                                    method.instructions.insert(jin, guard);
                                    eatingGuardPatched++;
                                    break;
                                }
                            }
                        }
                    }
                }

                // Cursor recovery at the very top of onTickEnd (before pending check)
                FieldInsnNode firstPendingCheck = null;
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof FieldInsnNode) {
                        FieldInsnNode fin = (FieldInsnNode) insn;
                        if (fin.getOpcode() == Opcodes.GETFIELD && "pending".equals(fin.name) && "com/yny/utils/modules/player/AutoArmor$Pending".equals(fin.owner)) {
                            firstPendingCheck = fin;
                            break;
                        }
                    }
                }

                boolean topRecoveryFound = false;
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode min = (MethodInsnNode) insn;
                        if ("com/yny/utils/modules/player/AutoArmorHelper".equals(min.owner)
                                && "recoverOrphanedCursor".equals(min.name)) {
                            if (firstPendingCheck != null && method.instructions.indexOf(min) < method.instructions.indexOf(firstPendingCheck)) {
                                topRecoveryFound = true;
                                cursorRecoveryPatched++;
                            } else {
                                // Old placement after pending check; remove it and POP
                                AbstractInsnNode next = min.getNext();
                                if (next instanceof InsnNode && next.getOpcode() == Opcodes.POP) {
                                    method.instructions.remove(next);
                                }
                                method.instructions.remove(min);
                            }
                        }
                    }
                }
                if (!topRecoveryFound) {
                    for (AbstractInsnNode insn : method.instructions.toArray()) {
                        if (insn instanceof FieldInsnNode) {
                            FieldInsnNode fin = (FieldInsnNode) insn;
                            if (fin.getOpcode() == Opcodes.PUTFIELD && "tick".equals(fin.name) && "J".equals(fin.desc)) {
                                InsnList recovery = new InsnList();
                                recovery.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/player/AutoArmorHelper", "recoverOrphanedCursor", "()Z", false));
                                recovery.add(new InsnNode(Opcodes.POP));
                                method.instructions.insert(fin, recovery);
                                cursorRecoveryPatched++;
                                break;
                            }
                        }
                    }
                }

                // Threshold normalization in onTickEnd: normalize threshold to enforce 20% min or break-only (0)
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode min = (MethodInsnNode) insn;
                        if ("com/yny/utils/modules/player/ArmorThreshold".equals(min.owner)
                                && "normalize".equals(min.name) && "(I)I".equals(min.desc)) {
                            thresholdPatched++;
                            break;
                        }
                    }
                }
                if (thresholdPatched == 0) {
                    for (AbstractInsnNode insn : method.instructions.toArray()) {
                        if (insn instanceof MethodInsnNode) {
                            MethodInsnNode min = (MethodInsnNode) insn;
                            if ("java/util/function/IntSupplier".equals(min.owner)
                                    && "getAsInt".equals(min.name) && "()I".equals(min.desc)) {
                                AbstractInsnNode cur = min.getNext();
                                while (cur != null && !(cur instanceof VarInsnNode && cur.getOpcode() == Opcodes.ISTORE)) {
                                    cur = cur.getNext();
                                }
                                if (cur != null) {
                                    MethodInsnNode normCall = new MethodInsnNode(
                                        Opcodes.INVOKESTATIC,
                                        "com/yny/utils/modules/player/ArmorThreshold",
                                        "normalize",
                                        "(I)I",
                                        false
                                    );
                                    method.instructions.insertBefore(cur, normCall);
                                    thresholdPatched++;
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            // 2. In advancePending: freeze age on GUI screen & 2-tick fast confirm
            if ("advancePending".equals(method.name) && "()V".equals(method.desc)) {
                // A. Freeze age increment when screen is open
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode && "hasUnsafeInventoryUi".equals(((MethodInsnNode)insn).name)) {
                        screenPausePatched++;
                        break;
                    }
                }
                if (screenPausePatched == 0) {
                    for (AbstractInsnNode insn : method.instructions.toArray()) {
                        if (insn instanceof FieldInsnNode) {
                            FieldInsnNode fin = (FieldInsnNode) insn;
                            if (fin.getOpcode() == Opcodes.PUTFIELD && "age".equals(fin.name) && "com/yny/utils/modules/player/AutoArmor$Pending".equals(fin.owner)) {
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
                }

                // B. 2-Tick fast confirm on localApplied
                AbstractInsnNode localAppliedNode = null;
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof FieldInsnNode) {
                        FieldInsnNode fin = (FieldInsnNode) insn;
                        if (fin.getOpcode() == Opcodes.GETFIELD && "localApplied".equals(fin.name) && "com/yny/utils/modules/player/AutoArmor$Pending".equals(fin.owner)) {
                            localAppliedNode = fin;
                            break;
                        }
                    }
                }
                if (localAppliedNode != null) {
                    AbstractInsnNode cur = localAppliedNode;
                    while (cur != null) {
                        if (cur.getOpcode() == Opcodes.BIPUSH && ((IntInsnNode)cur).operand == 16) {
                            AbstractInsnNode prev = cur.getPrevious();
                            if (prev instanceof FieldInsnNode && "age".equals(((FieldInsnNode)prev).name)) {
                                method.instructions.set(cur, new InsnNode(Opcodes.ICONST_2));
                                fastConfirmPatched++;
                                break;
                            }
                        } else if (cur.getOpcode() == Opcodes.ICONST_2) {
                            AbstractInsnNode prev = cur.getPrevious();
                            if (prev instanceof FieldInsnNode && "age".equals(((FieldInsnNode)prev).name)) {
                                fastConfirmPatched++;
                                break;
                            }
                        }
                        cur = cur.getNext();
                    }
                }
            }

            // 3. In beginEquip: use AutoArmorHelper.performEquip
            if ("beginEquip".equals(method.name)) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode min = (MethodInsnNode) insn;
                        if ("com/yny/utils/modules/player/AutoArmorHelper".equals(min.owner) && "performEquip".equals(min.name)) {
                            hotbarEquipPatched++;
                            break;
                        }
                        if ("dev/xavier/stein/loader/api/Inventory".equals(min.owner) && "equip".equals(min.name) && "(I)Z".equals(min.desc)) {
                            AbstractInsnNode choiceLoad = min.getPrevious();
                            AbstractInsnNode choiceVar = choiceLoad != null ? choiceLoad.getPrevious() : null;

                            InsnList call = new InsnList();
                            call.add(new VarInsnNode(Opcodes.ILOAD, 1));
                            call.add(new VarInsnNode(Opcodes.ALOAD, 2));
                            call.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/modules/player/AutoArmor$Choice", "inventoryIndex", "I"));
                            call.add(new VarInsnNode(Opcodes.ALOAD, 3));
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
                        if ("com/yny/utils/modules/player/AutoArmorHelper".equals(min.owner) && "compareSlots".equals(min.name)) {
                            comparePatched++;
                            break;
                        }
                        if ("java/lang/Integer".equals(min.owner) && "compare".equals(min.name) && "(II)I".equals(min.desc)) {
                            InsnList call = new InsnList();
                            call.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            call.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/modules/player/AutoArmor$Choice", "inventoryIndex", "I"));
                            call.add(new VarInsnNode(Opcodes.ALOAD, 1));
                            call.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/modules/player/AutoArmor$Choice", "inventoryIndex", "I"));
                            call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/player/AutoArmorHelper", "compareSlots", "(II)I", false));

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

            // 5. In isPreventiveUpgrade: do not require candidate protection level/score >= worn!
            // Require armorValue(candidate) >= armorValue(worn) and durabilityPercent(candidate) > durabilityPercent(worn).
            // This prevents P10/P5 armor from breaking when having P5/P4 spares in inventory.
            if ("isPreventiveUpgrade".equals(method.name) && "(Lzx;Lzx;)Z".equals(method.desc)) {
                InsnList il = new InsnList();
                LabelNode falseLabel = new LabelNode();
                LabelNode endLabel = new LabelNode();

                // Inventory.armorValue(candidate) >= Inventory.armorValue(worn)
                il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "dev/xavier/stein/loader/api/Inventory", "armorValue", "(Ljava/lang/Object;)I", false));
                il.add(new VarInsnNode(Opcodes.ALOAD, 1));
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "dev/xavier/stein/loader/api/Inventory", "armorValue", "(Ljava/lang/Object;)I", false));
                il.add(new JumpInsnNode(Opcodes.IF_ICMPLT, falseLabel));

                // durabilityPercent(candidate) > durabilityPercent(worn)
                il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/player/AutoArmor", "durabilityPercent", "(Lzx;)I", false));
                il.add(new VarInsnNode(Opcodes.ALOAD, 1));
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/player/AutoArmor", "durabilityPercent", "(Lzx;)I", false));
                il.add(new JumpInsnNode(Opcodes.IF_ICMPLE, falseLabel));

                // return true
                il.add(new InsnNode(Opcodes.ICONST_1));
                il.add(new JumpInsnNode(Opcodes.GOTO, endLabel));

                // falseLabel: return false
                il.add(falseLabel);
                il.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
                il.add(new InsnNode(Opcodes.ICONST_0));

                // endLabel: ireturn
                il.add(endLabel);
                il.add(new FrameNode(Opcodes.F_SAME1, 0, null, 1, new Object[]{Opcodes.INTEGER}));
                il.add(new InsnNode(Opcodes.IRETURN));

                method.instructions.clear();
                method.instructions.add(il);
                preventiveUpgradePatched++;
            }

            // 6. In bestReplacement: ensure preventiveFallback (var5) actually stores choice (var14) instead of POP!
            if ("bestReplacement".equals(method.name)) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn.getOpcode() == Opcodes.POP) {
                        AbstractInsnNode prev = insn.getPrevious();
                        if (prev instanceof VarInsnNode && prev.getOpcode() == Opcodes.ALOAD && ((VarInsnNode) prev).var == 14) {
                            method.instructions.set(insn, new VarInsnNode(Opcodes.ASTORE, 5));
                            fallbackStorePatched++;
                            break;
                        }
                    }
                }
            }
        }

        if (eatingGuardPatched != 1) throw new IllegalStateException("eatingGuardPatched=" + eatingGuardPatched);
        if (cursorRecoveryPatched != 1) throw new IllegalStateException("cursorRecoveryPatched=" + cursorRecoveryPatched);
        if (thresholdPatched != 1) throw new IllegalStateException("thresholdPatched=" + thresholdPatched);
        if (screenPausePatched != 1) throw new IllegalStateException("screenPausePatched=" + screenPausePatched);
        if (fastConfirmPatched != 1) throw new IllegalStateException("fastConfirmPatched=" + fastConfirmPatched);
        if (hotbarEquipPatched != 1) throw new IllegalStateException("hotbarEquipPatched=" + hotbarEquipPatched);
        if (comparePatched != 1) throw new IllegalStateException("comparePatched=" + comparePatched);
        if (preventiveUpgradePatched != 1) throw new IllegalStateException("preventiveUpgradePatched=" + preventiveUpgradePatched);
        if (fallbackStorePatched != 1) throw new IllegalStateException("fallbackStorePatched=" + fallbackStorePatched);

        System.out.printf("Patched AutoArmor: eatingGuard=%d, cursorRecovery=%d, threshold=%d, screenPause=%d, fastConfirm=%d, hotbarEquip=%d, compare=%d, preventiveUpgrade=%d, fallbackStore=%d%n",
                eatingGuardPatched, cursorRecoveryPatched, thresholdPatched, screenPausePatched, fastConfirmPatched, hotbarEquipPatched, comparePatched, preventiveUpgradePatched, fallbackStorePatched);

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
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode && "resolveRestoreSlot".equals(((MethodInsnNode)insn).name)) {
                        restoreSlotPatched++;
                        break;
                    }
                }
                if (restoreSlotPatched == 0) {
                    for (AbstractInsnNode insn : method.instructions.toArray()) {
                        if (insn instanceof VarInsnNode && insn.getOpcode() == Opcodes.ISTORE && ((VarInsnNode)insn).var == 4) {
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
                }

                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode && "holdUseKey".equals(((MethodInsnNode)insn).name)) {
                        beginUseHoldPatched++;
                        break;
                    }
                }
                if (beginUseHoldPatched == 0) {
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
            }

            // 2. In restoreUseKey()V: release key
            if ("restoreUseKey".equals(method.name) && "()V".equals(method.desc)) {
                boolean hasHold = false;
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode && "holdUseKey".equals(((MethodInsnNode)insn).name)) {
                        hasHold = true;
                        break;
                    }
                }
                if (!hasHold) {
                    InsnList releaseCall = new InsnList();
                    releaseCall.add(new InsnNode(Opcodes.ICONST_0));
                    releaseCall.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/player/ConsumableHelper", "holdUseKey", "(Z)V", false));
                    method.instructions.insert(releaseCall);
                }
                restoreKeyPatched++;
            }

            // 3. In cancelPendingUse(Z)V: release key
            if ("cancelPendingUse".equals(method.name) && "(Z)V".equals(method.desc)) {
                boolean hasHold = false;
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode && "holdUseKey".equals(((MethodInsnNode)insn).name)) {
                        hasHold = true;
                        break;
                    }
                }
                if (!hasHold) {
                    InsnList releaseCall = new InsnList();
                    releaseCall.add(new InsnNode(Opcodes.ICONST_0));
                    releaseCall.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/player/ConsumableHelper", "holdUseKey", "(Z)V", false));
                    method.instructions.insert(releaseCall);
                }
                cancelHoldPatched++;
            }

            // 4. In resetSession()V: release key
            if ("resetSession".equals(method.name) && "()V".equals(method.desc)) {
                boolean hasHold = false;
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode && "holdUseKey".equals(((MethodInsnNode)insn).name)) {
                        hasHold = true;
                        break;
                    }
                }
                if (!hasHold) {
                    InsnList releaseCall = new InsnList();
                    releaseCall.add(new InsnNode(Opcodes.ICONST_0));
                    releaseCall.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/player/ConsumableHelper", "holdUseKey", "(Z)V", false));
                    method.instructions.insert(releaseCall);
                }
                resetHoldPatched++;
            }

            // 5. In finishOrContinueUse: ensure key held while eating
            if ("finishOrContinueUse".equals(method.name) && "(Lwn;)V".equals(method.desc)) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode && "holdUseKey".equals(((MethodInsnNode)insn).name)) {
                        continueHoldPatched++;
                        break;
                    }
                }
                if (continueHoldPatched == 0) {
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
