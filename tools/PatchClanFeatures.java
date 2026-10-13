import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Applies clan ping through-walls, persistence, rival ping protection,
 * ally ground marker through-walls and clan filtering, and repackages YNYUtils.steinmod.
 */
public final class PatchClanFeatures {
    private static final String ALLY_GROUND_MARKER = "com/yny/utils/modules/pvp/AllyGroundMarker.class";
    private static final String CLAN_PING = "com/yny/utils/modules/clanping/ClanPing.class";
    private static final String HIT_THROUGH = "com/yny/utils/modules/pvp/HitThroughAllies.class";
    private static final String PING_CLAN_NAME = "com/yny/utils/modules/clanping/PingClanName.class";
    private static final String TARGET_HEALTH = "com/yny/targethealth/TargetHealthElement.class";
    private static final String TARGET_HUD = "com/yny/targethealth/TargetHudRenderer.class";
    private static final String GFX_MARKERS = "com/yny/utils/modules/clanping/GfxWorldMarkers.class";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: PatchClanFeatures <in.steinmod> <out.steinmod>");
            System.exit(1);
        }

        File inFile = new File(args[0]);
        File outFile = new File(args[1]);

        byte[] hitThroughBytes = readBytes(new File("build/compiled/com/yny/utils/modules/pvp/HitThroughAllies.class"));
        byte[] pingClanNameBytes = readBytes(new File("build/compiled/com/yny/utils/modules/clanping/PingClanName.class"));
        byte[] targetHealthBytes = readBytes(new File("build/compiled/com/yny/targethealth/TargetHealthElement.class"));
        byte[] targetHudBytes = readBytes(new File("build/compiled/com/yny/targethealth/TargetHudRenderer.class"));
        byte[] gfxMarkersBytes = readBytes(new File("build/compiled/com/yny/utils/modules/clanping/GfxWorldMarkers.class"));

        byte[] patchedMarkerBytes = null;
        byte[] patchedClanPingBytes = null;

        Map<String, byte[]> outEntries = new LinkedHashMap<>();

        try (ZipFile zipIn = new ZipFile(inFile)) {
            Enumeration<? extends ZipEntry> entries = zipIn.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                byte[] data = readStream(zipIn.getInputStream(entry));

                if (ALLY_GROUND_MARKER.equals(entry.getName())) {
                    data = patchAllyGroundMarker(data);
                    patchedMarkerBytes = data;
                } else if (CLAN_PING.equals(entry.getName())) {
                    data = patchClanPing(data);
                    patchedClanPingBytes = data;
                } else if (HIT_THROUGH.equals(entry.getName())) {
                    data = hitThroughBytes;
                } else if (PING_CLAN_NAME.equals(entry.getName())) {
                    data = pingClanNameBytes;
                } else if (TARGET_HEALTH.equals(entry.getName())) {
                    data = targetHealthBytes;
                } else if (TARGET_HUD.equals(entry.getName())) {
                    data = targetHudBytes;
                } else if (GFX_MARKERS.equals(entry.getName())) {
                    data = gfxMarkersBytes;
                }

                outEntries.put(entry.getName(), data);
            }
        }

        if (patchedMarkerBytes == null || patchedClanPingBytes == null) {
            throw new IllegalStateException("Failed to patch entries: marker=" + (patchedMarkerBytes != null)
                    + ", clanPing=" + (patchedClanPingBytes != null));
        }

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

        System.out.println("PASS: Successfully patched ClanPing, AllyGroundMarker, HitThroughAllies, TargetHealthElement, and TargetHudRenderer into " + outFile.getName());
    }

    public static byte[] patchAllyGroundMarker(byte[] original) {
        ClassNode node = read(original);

        int depthPatched = 0;
        int groupPatched = 0;

        for (MethodNode method : node.methods) {
            if ("renderWorld".equals(method.name) && "(F)V".equals(method.desc)) {
                // 1. Change bfl.j() to bfl.i() right after bfl.a(false) to disable depth test
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode min && "bfl".equals(min.owner) && "j".equals(min.name) && "()V".equals(min.desc)) {
                        AbstractInsnNode prev = prevReal(min);
                        if (prev instanceof MethodInsnNode pmin && "bfl".equals(pmin.owner) && "a".equals(pmin.name) && "(Z)V".equals(pmin.desc)) {
                            AbstractInsnNode pprev = prevReal(pmin);
                            if (pprev != null && pprev.getOpcode() == Opcodes.ICONST_0) {
                                min.name = "i"; // Disable depth test!
                                depthPatched++;
                            }
                        }
                    }

                    // 2. Expand Relation.GROUP check to also check PingClanName.isSameClan
                    if (insn instanceof FieldInsnNode finsn && finsn.getOpcode() == Opcodes.GETSTATIC
                            && "dev/xavier/stein/loader/api/Relation".equals(finsn.owner)
                            && "GROUP".equals(finsn.name)) {
                        AbstractInsnNode next = nextReal(finsn);
                        if (next instanceof MethodInsnNode min && "equals".equals(min.name)) {
                            AbstractInsnNode jump = nextReal(min);
                            if (jump instanceof JumpInsnNode jinsn && jinsn.getOpcode() == Opcodes.IFNE) {
                                LabelNode drawLabel = jinsn.label;
                                InsnList extra = new InsnList();
                                extra.add(new VarInsnNode(Opcodes.ALOAD, 12)); // player
                                extra.add(new VarInsnNode(Opcodes.ALOAD, 12));
                                extra.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "wn", "e_", "()Ljava/lang/String;", false));
                                extra.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/clanping/PingClanName", "isSameClan", "(Ljava/lang/Object;Ljava/lang/String;)Z", false));
                                extra.add(new JumpInsnNode(Opcodes.IFNE, drawLabel)); // IF same clan, draw!
                                method.instructions.insert(jinsn, extra);
                                groupPatched++;
                            }
                        }
                    }
                }
            }
        }

        if (depthPatched != 1 || groupPatched != 1) {
            throw new IllegalStateException("AllyGroundMarker patch mismatch: depth=" + depthPatched + ", group=" + groupPatched);
        }

        return write(node);
    }

    public static byte[] patchClanPing(byte[] original) {
        ClassNode node = read(original);

        int joinActivePatched = 0;
        int joinPendingPatched = 0;
        int disconnectPatched = 0;
        int rivalPingPatched = 0;
        int quickPingPatched = 0;
        int findTargetPatched = 0;
        int receiveClanPatched = 0;
        int resolvePendingPatched = 0;
        int waypointHeightPatched = 0;

        for (MethodNode method : node.methods) {
            // 1. onJoinGame: do not clear active nor pendingRelations on dimension / world switch
            if ("onJoinGame".equals(method.name) && "()V".equals(method.desc)) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof FieldInsnNode finsn && finsn.getOpcode() == Opcodes.PUTFIELD
                            && "com/yny/utils/modules/clanping/ClanPing".equals(finsn.owner)
                            && "active".equals(finsn.name)) {
                        AbstractInsnNode prev1 = prevReal(finsn);
                        AbstractInsnNode prev2 = prevReal(prev1);
                        if (prev1 != null && prev1.getOpcode() == Opcodes.ACONST_NULL
                                && prev2 != null && prev2.getOpcode() == Opcodes.ALOAD && ((VarInsnNode) prev2).var == 0) {
                            method.instructions.remove(prev2);
                            method.instructions.remove(prev1);
                            method.instructions.remove(finsn);
                            joinActivePatched++;
                        }
                    }
                    if (insn instanceof MethodInsnNode min && min.getOpcode() == Opcodes.INVOKEINTERFACE
                            && "java/util/Map".equals(min.owner) && "clear".equals(min.name)) {
                        AbstractInsnNode prev1 = prevReal(min);
                        AbstractInsnNode prev2 = prevReal(prev1);
                        if (prev1 instanceof FieldInsnNode finsn && "pendingRelations".equals(finsn.name)
                                && prev2 != null && prev2.getOpcode() == Opcodes.ALOAD) {
                            method.instructions.remove(prev2);
                            method.instructions.remove(prev1);
                            method.instructions.remove(min);
                            joinPendingPatched++;
                        }
                    }
                }
            }

            // 2. onDisconnected: do not wipe active if not expired
            if ("onDisconnected".equals(method.name) && "()V".equals(method.desc)) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof FieldInsnNode finsn && finsn.getOpcode() == Opcodes.PUTFIELD
                            && "com/yny/utils/modules/clanping/ClanPing".equals(finsn.owner)
                            && "active".equals(finsn.name)) {
                        AbstractInsnNode prev1 = prevReal(finsn);
                        AbstractInsnNode prev2 = prevReal(prev1);
                        if (prev1 != null && prev1.getOpcode() == Opcodes.ACONST_NULL
                                && prev2 != null && prev2.getOpcode() == Opcodes.ALOAD && ((VarInsnNode) prev2).var == 0) {
                            method.instructions.remove(prev2);
                            method.instructions.remove(prev1);
                            method.instructions.remove(finsn);
                            disconnectPatched++;
                        }
                    }
                }
            }

            // 3. createEnemyFromCrosshair: prevent pinging same clan as rival
            if ("createEnemyFromCrosshair".equals(method.name) && "()V".equals(method.desc)) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode min && "dev/xavier/stein/loader/api/Relation".equals(min.owner)
                            && "friendly".equals(min.name)) {
                        AbstractInsnNode jump = nextReal(min);
                        if (jump instanceof JumpInsnNode jinsn && jinsn.getOpcode() == Opcodes.IFEQ) {
                            LabelNode allowLabel = jinsn.label;
                            LabelNode checkClan = new LabelNode();
                            jinsn.label = checkClan;
                            InsnList clanCheck = new InsnList();
                            clanCheck.add(checkClan);
                            clanCheck.add(new VarInsnNode(Opcodes.ALOAD, 2)); // wn var2
                            clanCheck.add(new VarInsnNode(Opcodes.ALOAD, 2));
                            clanCheck.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "wn", "e_", "()Ljava/lang/String;", false));
                            clanCheck.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/clanping/PingClanName", "isSameClan", "(Ljava/lang/Object;Ljava/lang/String;)Z", false));
                            clanCheck.add(new JumpInsnNode(Opcodes.IFEQ, allowLabel)); // not same clan -> allow
                            method.instructions.insert(jinsn, clanCheck);
                            rivalPingPatched++;
                        }
                    }
                    if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String s && s.contains("não é possível pingar")) {
                        ldc.cst = "§eClan Ping: não é possível pingar membro do mesmo clan.";
                    }
                }
            }

            // 4. createQuickPing: if crosshair target is same clan/friendly, fallback to location ping
            if ("createQuickPing".equals(method.name) && "()V".equals(method.desc)) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode min && "crosshairPlayer".equals(min.name)) {
                        AbstractInsnNode jump = nextReal(min);
                        if (jump instanceof JumpInsnNode jinsn && jinsn.getOpcode() == Opcodes.IFNULL) {
                            LabelNode locationLabel = jinsn.label;
                            InsnList checkFriendly = new InsnList();
                            checkFriendly.add(new InsnNode(Opcodes.DUP));
                            checkFriendly.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "dev/xavier/stein/loader/api/Relations", "of", "(Ljava/lang/Object;)Ldev/xavier/stein/loader/api/Relation;", false));
                            checkFriendly.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "dev/xavier/stein/loader/api/Relation", "friendly", "()Z", false));
                            checkFriendly.add(new JumpInsnNode(Opcodes.IFNE, locationLabel)); // friendly -> location
                            checkFriendly.add(new InsnNode(Opcodes.DUP));
                            checkFriendly.add(new InsnNode(Opcodes.DUP));
                            checkFriendly.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "wn", "e_", "()Ljava/lang/String;", false));
                            checkFriendly.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/clanping/PingClanName", "isSameClan", "(Ljava/lang/Object;Ljava/lang/String;)Z", false));
                            checkFriendly.add(new JumpInsnNode(Opcodes.IFNE, locationLabel)); // same clan -> location
                            method.instructions.insert(jinsn, checkFriendly);
                            quickPingPatched++;
                        }
                    }
                }
            }

            // 5. findLoadedTarget: match by UUID or nickname (targetName.equalsIgnoreCase(wn.e_()))
            if ("findLoadedTarget".equals(method.name) && "(Lave;)Lwn;".equals(method.desc)) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode min && "java/lang/String".equals(min.owner) && "equals".equals(min.name)) {
                        AbstractInsnNode jump = nextReal(min);
                        if (jump instanceof JumpInsnNode jinsn && jinsn.getOpcode() == Opcodes.IFEQ) {
                            LabelNode matchedLabel = new LabelNode();
                            LabelNode skipLabel = jinsn.label;
                            InsnList fallback = new InsnList();
                            fallback.add(new JumpInsnNode(Opcodes.IFNE, matchedLabel)); // UUID matched!
                            fallback.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            fallback.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/modules/clanping/ClanPing", "active", "Lcom/yny/utils/modules/clanping/ClanPing$Active;"));
                            fallback.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/modules/clanping/ClanPing$Active", "targetName", "Ljava/lang/String;"));
                            fallback.add(new VarInsnNode(Opcodes.ALOAD, 3));
                            fallback.add(new TypeInsnNode(Opcodes.CHECKCAST, "wn"));
                            fallback.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "wn", "e_", "()Ljava/lang/String;", false));
                            fallback.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equalsIgnoreCase", "(Ljava/lang/String;)Z", false));
                            fallback.add(new JumpInsnNode(Opcodes.IFEQ, skipLabel)); // Neither matched -> skip
                            fallback.add(matchedLabel);
                            method.instructions.insertBefore(jinsn, fallback);
                            method.instructions.remove(jinsn);
                            findTargetPatched++;
                        }
                    }
                }
            }

            // 6. receive: allow incoming if author is same clan
            if ("receive".equals(method.name) && "(Lcom/yny/utils/modules/clanping/ClanPing$Active;)V".equals(method.desc)) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof FieldInsnNode finsn && finsn.getOpcode() == Opcodes.GETSTATIC
                            && "dev/xavier/stein/loader/api/Relation".equals(finsn.owner)
                            && "GROUP".equals(finsn.name)) {
                        AbstractInsnNode jump = nextReal(finsn);
                        if (jump instanceof JumpInsnNode jinsn && jinsn.getOpcode() == Opcodes.IF_ACMPEQ) {
                            LabelNode showLabel = jinsn.label;
                            InsnList clanCheck = new InsnList();
                            clanCheck.add(new InsnNode(Opcodes.ACONST_NULL));
                            clanCheck.add(new VarInsnNode(Opcodes.ALOAD, 1));
                            clanCheck.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/modules/clanping/ClanPing$Active", "author", "Ljava/lang/String;"));
                            clanCheck.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/clanping/PingClanName", "isSameClan", "(Ljava/lang/Object;Ljava/lang/String;)Z", false));
                            clanCheck.add(new JumpInsnNode(Opcodes.IFNE, showLabel)); // same clan -> show
                            method.instructions.insert(jinsn, clanCheck);
                            receiveClanPatched++;
                        }
                    }
                }
            }

            // 7. resolvePendingRelations: allow if author is same clan
            if ("resolvePendingRelations".equals(method.name) && "()V".equals(method.desc)) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof FieldInsnNode finsn && finsn.getOpcode() == Opcodes.GETSTATIC
                            && "dev/xavier/stein/loader/api/Relation".equals(finsn.owner)
                            && "GROUP".equals(finsn.name)) {
                        AbstractInsnNode jump = nextReal(finsn);
                        if (jump instanceof JumpInsnNode jinsn && jinsn.getOpcode() == Opcodes.IF_ACMPNE) {
                            LabelNode skipLabel = jinsn.label;
                            LabelNode showLabel = new LabelNode();
                            jinsn.label = showLabel; // if not GROUP, check clan
                            InsnList clanCheck = new InsnList();
                            clanCheck.add(showLabel);
                            clanCheck.add(new InsnNode(Opcodes.ACONST_NULL));
                            clanCheck.add(new VarInsnNode(Opcodes.ALOAD, 2));
                            clanCheck.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/modules/clanping/ClanPing$Active", "author", "Ljava/lang/String;"));
                            clanCheck.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/clanping/PingClanName", "isSameClan", "(Ljava/lang/Object;Ljava/lang/String;)Z", false));
                            clanCheck.add(new JumpInsnNode(Opcodes.IFEQ, skipLabel)); // if not same clan -> skip
                            method.instructions.insert(jinsn, clanCheck);
                            resolvePendingPatched++;
                        }
                    }
                }
            }

            // 8. Increase beacon pillar height in renderLocationWaypoint and renderEnemyLastKnownWaypoint
            if ("renderLocationWaypoint".equals(method.name) || "renderEnemyLastKnownWaypoint".equals(method.name)) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof Double d && d == 24.0) {
                        ldc.cst = 96.0;
                        waypointHeightPatched++;
                    }
                }
            }
        }

        // 9. Refine renderWorldLabel for depthMask false, disableDepth, blend, dynamic scale
        patchRenderWorldLabelMethod(node);

        if (joinActivePatched != 1 || disconnectPatched != 1 || rivalPingPatched != 1
                || quickPingPatched != 1 || findTargetPatched != 1 || receiveClanPatched != 1
                || resolvePendingPatched != 1) {
            throw new IllegalStateException("ClanPing patch mismatch: joinActive=" + joinActivePatched
                    + ", disconnect=" + disconnectPatched + ", rivalPing=" + rivalPingPatched
                    + ", quickPing=" + quickPingPatched + ", findTarget=" + findTargetPatched
                    + ", receiveClan=" + receiveClanPatched + ", resolvePending=" + resolvePendingPatched);
        }

        return write(node);
    }

    private static void patchRenderWorldLabelMethod(ClassNode node) {
        for (MethodNode method : node.methods) {
            if ("renderWorldLabel".equals(method.name)) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode min && "bfl".equals(min.owner) && "i".equals(min.name) && "()V".equals(min.desc)) {
                        InsnList pre = new InsnList();
                        pre.add(new InsnNode(Opcodes.ICONST_0));
                        pre.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "bfl", "a", "(Z)V", false)); // depthMask(false)
                        method.instructions.insertBefore(min, pre);

                        InsnList post = new InsnList();
                        post.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "bfl", "l", "()V", false)); // enableBlend()
                        post.add(new IntInsnNode(Opcodes.SIPUSH, 770));
                        post.add(new IntInsnNode(Opcodes.SIPUSH, 771));
                        post.add(new InsnNode(Opcodes.ICONST_1));
                        post.add(new InsnNode(Opcodes.ICONST_0));
                        post.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "bfl", "a", "(IIII)V", false)); // tryBlendFuncSeparate
                        method.instructions.insert(min, post);
                    }
                    if (insn instanceof MethodInsnNode min && "bfl".equals(min.owner) && "j".equals(min.name) && "()V".equals(min.desc)) {
                        InsnList restore = new InsnList();
                        restore.add(new InsnNode(Opcodes.ICONST_1));
                        restore.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "bfl", "a", "(Z)V", false)); // depthMask(true)
                        method.instructions.insertBefore(min, restore);

                        InsnList disableBlend = new InsnList();
                        disableBlend.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "bfl", "k", "()V", false)); // disableBlend()
                        method.instructions.insert(min, disableBlend);
                    }
                    if (insn instanceof MethodInsnNode min && "bfl".equals(min.owner) && "a".equals(min.name) && "(FFF)V".equals(min.desc)) {
                        AbstractInsnNode p1 = prevReal(insn); // ldc 0.025f
                        AbstractInsnNode p2 = prevReal(p1);   // ldc -0.025f
                        AbstractInsnNode p3 = prevReal(p2);   // ldc -0.025f
                        if (p1 instanceof LdcInsnNode && p2 instanceof LdcInsnNode && p3 instanceof LdcInsnNode) {
                            method.instructions.remove(p3);
                            method.instructions.remove(p2);
                            method.instructions.remove(p1);

                            InsnList calcScale = new InsnList();
                            calcScale.add(new VarInsnNode(Opcodes.DLOAD, 1));
                            calcScale.add(new VarInsnNode(Opcodes.DLOAD, 7));
                            calcScale.add(new InsnNode(Opcodes.DSUB));
                            calcScale.add(new InsnNode(Opcodes.DUP2));
                            calcScale.add(new InsnNode(Opcodes.DMUL)); // dx*dx

                            calcScale.add(new VarInsnNode(Opcodes.DLOAD, 3));
                            calcScale.add(new VarInsnNode(Opcodes.DLOAD, 9));
                            calcScale.add(new InsnNode(Opcodes.DSUB));
                            calcScale.add(new InsnNode(Opcodes.DUP2));
                            calcScale.add(new InsnNode(Opcodes.DMUL)); // dy*dy
                            calcScale.add(new InsnNode(Opcodes.DADD)); // dx*dx + dy*dy

                            calcScale.add(new VarInsnNode(Opcodes.DLOAD, 5));
                            calcScale.add(new VarInsnNode(Opcodes.DLOAD, 11));
                            calcScale.add(new InsnNode(Opcodes.DSUB));
                            calcScale.add(new InsnNode(Opcodes.DUP2));
                            calcScale.add(new InsnNode(Opcodes.DMUL)); // dz*dz
                            calcScale.add(new InsnNode(Opcodes.DADD)); // dx*dx + dy*dy + dz*dz

                            calcScale.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "sqrt", "(D)D", false)); // dist
                            calcScale.add(new LdcInsnNode(14.0D));
                            calcScale.add(new InsnNode(Opcodes.DDIV)); // dist / 14.0
                            calcScale.add(new LdcInsnNode(6.0D));
                            calcScale.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "min", "(DD)D", false));
                            calcScale.add(new LdcInsnNode(0.025D));
                            calcScale.add(new InsnNode(Opcodes.DMUL));
                            calcScale.add(new LdcInsnNode(0.025D));
                            calcScale.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "max", "(DD)D", false));
                            calcScale.add(new InsnNode(Opcodes.D2F)); // float scale

                            calcScale.add(new InsnNode(Opcodes.DUP));
                            calcScale.add(new InsnNode(Opcodes.FNEG));
                            calcScale.add(new InsnNode(Opcodes.SWAP));
                            calcScale.add(new InsnNode(Opcodes.DUP));
                            calcScale.add(new InsnNode(Opcodes.FNEG));
                            calcScale.add(new InsnNode(Opcodes.SWAP));

                            method.instructions.insertBefore(insn, calcScale);
                        }
                    }
                }
                method.maxStack = Math.max(method.maxStack, 16);
            }
        }
    }

    private static AbstractInsnNode prevReal(AbstractInsnNode insn) {
        AbstractInsnNode p = insn != null ? insn.getPrevious() : null;
        while (p instanceof LineNumberNode || p instanceof LabelNode || p instanceof FrameNode) {
            p = p.getPrevious();
        }
        return p;
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
        AbstractInsnNode n = insn != null ? insn.getNext() : null;
        while (n instanceof LineNumberNode || n instanceof LabelNode || n instanceof FrameNode) {
            n = n.getNext();
        }
        return n;
    }

    private static ClassNode read(byte[] bytes) {
        byte[] compatible = bytes.clone();
        compatible[6] = 0;
        compatible[7] = 60;
        ClassNode node = new ClassNode();
        new ClassReader(compatible).accept(node, ClassReader.EXPAND_FRAMES);
        node.version = ((bytes[6] & 255) << 8) | (bytes[7] & 255);
        return node;
    }

    private static byte[] write(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] readBytes(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return in.readAllBytes();
        }
    }

    private static byte[] readStream(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) != -1) {
            out.write(buf, 0, r);
        }
        return out.toByteArray();
    }
}
