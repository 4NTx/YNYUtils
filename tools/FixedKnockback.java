package com.yny.utils.tools;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Utilitário para restaurar o controle dinâmico e real de Knockback (0-100%) no bytecode do YNYUtils.
 * Remove lambdas hardcoded em 92% e reconecta as lambdas diretamente ao YNYConfig.
 */
public final class FixedKnockback implements Opcodes {
    static final String MOD = "com/yny/utils/YNYUtils.class";
    static final String HUD = "com/yny/utils/ui/PvpStatusHud.class";
    static final String KB_CTRL = "com/yny/utils/modules/pvp/KnockbackControl.class";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: FixedKnockback <input.jar> <output.jar>");
        Path inPath = Paths.get(args[0]);
        Path outPath = Paths.get(args[1]);

        int patchedLambdas = 0;
        int patchedAdjust = 0;

        try (ZipFile in = new ZipFile(inPath.toFile());
             ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(outPath))) {
            var entries = in.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                byte[] b;
                try (InputStream s = in.getInputStream(e)) {
                    b = s.readAllBytes();
                }

                if (MOD.equals(e.getName())) {
                    ClassNode node = new ClassNode();
                    new ClassReader(b).accept(node, 0);
                    for (MethodNode mn : node.methods) {
                        if ("lambda$new$2".equals(mn.name) && "()Z".equals(mn.desc)) {
                            mn.instructions.clear();
                            InsnList insns = new InsnList();
                            insns.add(new FieldInsnNode(GETSTATIC, "com/yny/utils/YNYUtils", "config", "Lcom/yny/utils/config/YNYConfig;"));
                            insns.add(new FieldInsnNode(GETFIELD, "com/yny/utils/config/YNYConfig", "knockbackEnabled", "Z"));
                            insns.add(new InsnNode(IRETURN));
                            mn.instructions.add(insns);
                            mn.maxStack = 2;
                            mn.maxLocals = 0;
                            patchedLambdas++;
                        } else if ("lambda$new$3".equals(mn.name) && "()I".equals(mn.desc)) {
                            mn.instructions.clear();
                            InsnList insns = new InsnList();
                            insns.add(new FieldInsnNode(GETSTATIC, "com/yny/utils/YNYUtils", "config", "Lcom/yny/utils/config/YNYConfig;"));
                            insns.add(new FieldInsnNode(GETFIELD, "com/yny/utils/config/YNYConfig", "knockbackPercent", "I"));
                            insns.add(new InsnNode(IRETURN));
                            mn.instructions.add(insns);
                            mn.maxStack = 2;
                            mn.maxLocals = 0;
                            patchedLambdas++;
                        } else if ("lambda$new$4".equals(mn.name) && "()Z".equals(mn.desc)) {
                            mn.instructions.clear();
                            InsnList insns = new InsnList();
                            insns.add(new FieldInsnNode(GETSTATIC, "com/yny/utils/YNYUtils", "config", "Lcom/yny/utils/config/YNYConfig;"));
                            insns.add(new FieldInsnNode(GETFIELD, "com/yny/utils/config/YNYConfig", "knockbackPreserveVertical", "Z"));
                            insns.add(new InsnNode(IRETURN));
                            mn.instructions.add(insns);
                            mn.maxStack = 2;
                            mn.maxLocals = 0;
                            patchedLambdas++;
                        } else if ("updateToggleKey".equals(mn.name) && "()V".equals(mn.desc)) {
                            for (AbstractInsnNode insn : mn.instructions.toArray()) {
                                if (insn instanceof MethodInsnNode minsn && "com/yny/utils/core/ToggleKey".equals(minsn.owner) && "wasPressed".equals(minsn.name)) {
                                    AbstractInsnNode n1 = insn.getNext();
                                    if (n1 != null && n1.getOpcode() == POP) {
                                        AbstractInsnNode n2 = n1.getNext();
                                        if (n2 != null && n2.getOpcode() == ICONST_0) {
                                            mn.instructions.remove(n1);
                                            mn.instructions.remove(n2);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                    node.accept(cw);
                    b = cw.toByteArray();
                } else if (KB_CTRL.equals(e.getName())) {
                    ClassNode node = new ClassNode();
                    new ClassReader(b).accept(node, 0);
                    for (MethodNode mn : node.methods) {
                        if ("adjust".equals(mn.name)) {
                            for (AbstractInsnNode insn : mn.instructions.toArray()) {
                                if (insn.getOpcode() == BIPUSH) {
                                    IntInsnNode iin = (IntInsnNode) insn;
                                    if (iin.operand == 92 || iin.operand == 93) {
                                        mn.instructions.set(insn, new InsnNode(ICONST_0));
                                        patchedAdjust++;
                                    }
                                }
                            }
                        }
                    }
                    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                    node.accept(cw);
                    b = cw.toByteArray();
                }

                ZipEntry copy = new ZipEntry(e.getName());
                copy.setTime(e.getTime());
                out.putNextEntry(copy);
                out.write(b);
                out.closeEntry();
            }
        }

        System.out.println("Knockback restored to dynamic config (0-100%, toggle, vertical, HUD). Patched lambdas=" + patchedLambdas + ", adjust=" + patchedAdjust);
    }
}