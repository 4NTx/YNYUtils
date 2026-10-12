import java.io.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Patches ClanPing.class to eliminate stutter/lag when moving by interpolating
 * the target entity position and bounding box using partialTicks,
 * and updates targetDisplayName to display the enemy clan tag alongside the nickname.
 */
public final class PatchEnemyPingFluidity {
    private static final String CLAN_PING = "com/yny/utils/modules/clanping/ClanPing.class";
    private static final String PING_CLAN_NAME = "com/yny/utils/modules/clanping/PingClanName.class";
    private static final String GFX_MARKERS = "com/yny/utils/modules/clanping/GfxWorldMarkers.class";
    private static final String HIT_THROUGH = "com/yny/utils/modules/pvp/HitThroughAllies.class";

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            throw new IllegalArgumentException("Usage: PatchEnemyPingFluidity in.steinmod PingClanName.class GfxWorldMarkers.class HitThroughAllies.class out.steinmod");
        }

        byte[] pingClanNameBytes = readBytes(args[1]);
        byte[] gfxMarkersBytes = readBytes(args[2]);
        byte[] hitThroughBytes = readBytes(args[3]);

        int clanPingCount = 0;
        int pingClanNameCount = 0;
        int gfxMarkersCount = 0;
        int hitThroughCount = 0;

        try (ZipInputStream in = new ZipInputStream(new FileInputStream(args[0]));
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(args[4]))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                in.transferTo(buffer);
                byte[] data = buffer.toByteArray();

                if (CLAN_PING.equals(entry.getName())) {
                    data = patchClanPing(data);
                    clanPingCount++;
                } else if (PING_CLAN_NAME.equals(entry.getName())) {
                    data = pingClanNameBytes;
                    pingClanNameCount++;
                } else if (GFX_MARKERS.equals(entry.getName())) {
                    data = gfxMarkersBytes;
                    gfxMarkersCount++;
                } else if (HIT_THROUGH.equals(entry.getName())) {
                    data = hitThroughBytes;
                    hitThroughCount++;
                }

                ZipEntry copy = new ZipEntry(entry.getName());
                copy.setTime(entry.getTime());
                out.putNextEntry(copy);
                out.write(data);
                out.closeEntry();
            }

            if (pingClanNameCount == 0) {
                out.putNextEntry(new ZipEntry(PING_CLAN_NAME));
                out.write(pingClanNameBytes);
                out.closeEntry();
                pingClanNameCount++;
            }
            if (gfxMarkersCount == 0) {
                out.putNextEntry(new ZipEntry(GFX_MARKERS));
                out.write(gfxMarkersBytes);
                out.closeEntry();
                gfxMarkersCount++;
            }
        }

        if (clanPingCount != 1 || hitThroughCount != 1) {
            throw new IllegalStateException("Failed to patch entries: clanPing=" + clanPingCount + ", hitThrough=" + hitThroughCount);
        }
        System.out.println("PASS: Patched ClanPing, PingClanName, GfxWorldMarkers, and HitThroughAllies");
    }

    private static byte[] readBytes(String path) throws IOException {
        try (InputStream in = new FileInputStream(path)) {
            return in.readAllBytes();
        }
    }

    private static ClassNode read(byte[] bytes) {
        byte[] compatible = bytes.clone();
        compatible[6] = 0;
        compatible[7] = 60;
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

    private static byte[] patchClanPing(byte[] bytes) {
        ClassNode node = read(bytes);
        int patchedRenderWorld = 0;
        int patchedTargetDisplayName = 0;
        int patchedLastKnown = 0;

        for (MethodNode method : node.methods) {
            if (method.name.equals("renderWorld") && method.desc.equals("(F)V")) {
                patchRenderWorldMethod(method);
                patchedRenderWorld++;
            } else if (method.name.equals("targetDisplayName") && method.desc.equals("(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/String;")) {
                patchTargetDisplayNameMethod(method);
                patchedTargetDisplayName++;
            } else if (method.name.equals("renderEnemyLastKnownWaypoint") && method.desc.equals("(F)V")) {
                patchLastKnownMethod(method);
                patchedLastKnown++;
            }
        }

        if (patchedRenderWorld != 1 || patchedTargetDisplayName != 1 || patchedLastKnown != 1) {
            throw new IllegalStateException("ClanPing patch mismatch: renderWorld=" + patchedRenderWorld
                    + ", targetDisplayName=" + patchedTargetDisplayName + ", lastKnown=" + patchedLastKnown);
        }

        return write(node);
    }

    private static void patchRenderWorldMethod(MethodNode method) {
        // Step 1: Insert target interpolation right after visualScale (DSTORE 11)
        VarInsnNode dstore11 = null;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.DSTORE && ((VarInsnNode) insn).var == 11) {
                dstore11 = (VarInsnNode) insn;
                break;
            }
        }
        if (dstore11 == null) throw new IllegalStateException("dstore 11 (visualScale) not found");

        InsnList interp = new InsnList();
        // targetX = var3.P + (var3.s - var3.P) * var1 -> store in double local 15
        interp.add(new VarInsnNode(Opcodes.ALOAD, 3));
        interp.add(new FieldInsnNode(Opcodes.GETFIELD, "pk", "P", "D"));
        interp.add(new VarInsnNode(Opcodes.ALOAD, 3));
        interp.add(new FieldInsnNode(Opcodes.GETFIELD, "pk", "s", "D"));
        interp.add(new VarInsnNode(Opcodes.ALOAD, 3));
        interp.add(new FieldInsnNode(Opcodes.GETFIELD, "pk", "P", "D"));
        interp.add(new InsnNode(Opcodes.DSUB));
        interp.add(new VarInsnNode(Opcodes.FLOAD, 1));
        interp.add(new InsnNode(Opcodes.F2D));
        interp.add(new InsnNode(Opcodes.DMUL));
        interp.add(new InsnNode(Opcodes.DADD));
        interp.add(new VarInsnNode(Opcodes.DSTORE, 15));

        // targetY = var3.Q + (var3.t - var3.Q) * var1 -> store in double local 17
        interp.add(new VarInsnNode(Opcodes.ALOAD, 3));
        interp.add(new FieldInsnNode(Opcodes.GETFIELD, "pk", "Q", "D"));
        interp.add(new VarInsnNode(Opcodes.ALOAD, 3));
        interp.add(new FieldInsnNode(Opcodes.GETFIELD, "pk", "t", "D"));
        interp.add(new VarInsnNode(Opcodes.ALOAD, 3));
        interp.add(new FieldInsnNode(Opcodes.GETFIELD, "pk", "Q", "D"));
        interp.add(new InsnNode(Opcodes.DSUB));
        interp.add(new VarInsnNode(Opcodes.FLOAD, 1));
        interp.add(new InsnNode(Opcodes.F2D));
        interp.add(new InsnNode(Opcodes.DMUL));
        interp.add(new InsnNode(Opcodes.DADD));
        interp.add(new VarInsnNode(Opcodes.DSTORE, 17));

        // targetZ = var3.R + (var3.u - var3.R) * var1 -> store in double local 19
        interp.add(new VarInsnNode(Opcodes.ALOAD, 3));
        interp.add(new FieldInsnNode(Opcodes.GETFIELD, "pk", "R", "D"));
        interp.add(new VarInsnNode(Opcodes.ALOAD, 3));
        interp.add(new FieldInsnNode(Opcodes.GETFIELD, "pk", "u", "D"));
        interp.add(new VarInsnNode(Opcodes.ALOAD, 3));
        interp.add(new FieldInsnNode(Opcodes.GETFIELD, "pk", "R", "D"));
        interp.add(new InsnNode(Opcodes.DSUB));
        interp.add(new VarInsnNode(Opcodes.FLOAD, 1));
        interp.add(new InsnNode(Opcodes.F2D));
        interp.add(new InsnNode(Opcodes.DMUL));
        interp.add(new InsnNode(Opcodes.DADD));
        interp.add(new VarInsnNode(Opcodes.DSTORE, 19));

        // Sync active.x, active.y, active.z
        interp.add(new VarInsnNode(Opcodes.ALOAD, 0));
        interp.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/modules/clanping/ClanPing", "active", "Lcom/yny/utils/modules/clanping/ClanPing$Active;"));
        interp.add(new VarInsnNode(Opcodes.DLOAD, 15));
        interp.add(new FieldInsnNode(Opcodes.PUTFIELD, "com/yny/utils/modules/clanping/ClanPing$Active", "x", "D"));

        interp.add(new VarInsnNode(Opcodes.ALOAD, 0));
        interp.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/modules/clanping/ClanPing", "active", "Lcom/yny/utils/modules/clanping/ClanPing$Active;"));
        interp.add(new VarInsnNode(Opcodes.DLOAD, 17));
        interp.add(new FieldInsnNode(Opcodes.PUTFIELD, "com/yny/utils/modules/clanping/ClanPing$Active", "y", "D"));

        interp.add(new VarInsnNode(Opcodes.ALOAD, 0));
        interp.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/modules/clanping/ClanPing", "active", "Lcom/yny/utils/modules/clanping/ClanPing$Active;"));
        interp.add(new VarInsnNode(Opcodes.DLOAD, 19));
        interp.add(new FieldInsnNode(Opcodes.PUTFIELD, "com/yny/utils/modules/clanping/ClanPing$Active", "z", "D"));

        method.instructions.insert(dstore11, interp);

        // Step 2: Smooth bounding box offset: replace (dload 5, dneg, dload 7, dneg, dload 9, dneg) before aug.c
        MethodInsnNode augCCall = null;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof MethodInsnNode minsn && minsn.owner.equals("aug") && minsn.name.equals("c") && minsn.desc.equals("(DDD)Laug;")) {
                augCCall = minsn;
                break;
            }
        }
        if (augCCall == null) throw new IllegalStateException("aug.c call not found in renderWorld");

        // The 6 instructions preceding augCCall are: dload 5, dneg, dload 7, dneg, dload 9, dneg
        AbstractInsnNode p1 = augCCall.getPrevious(); // dneg (9)
        AbstractInsnNode p2 = p1.getPrevious();       // dload 9
        AbstractInsnNode p3 = p2.getPrevious();       // dneg (7)
        AbstractInsnNode p4 = p3.getPrevious();       // dload 7
        AbstractInsnNode p5 = p4.getPrevious();       // dneg (5)
        AbstractInsnNode p6 = p5.getPrevious();       // dload 5

        method.instructions.remove(p6);
        method.instructions.remove(p5);
        method.instructions.remove(p4);
        method.instructions.remove(p3);
        method.instructions.remove(p2);
        method.instructions.remove(p1);

        InsnList boxOffset = new InsnList();
        // offsetX = targetX (local 15) - var3.s - camX (local 5)
        boxOffset.add(new VarInsnNode(Opcodes.DLOAD, 15));
        boxOffset.add(new VarInsnNode(Opcodes.ALOAD, 3));
        boxOffset.add(new FieldInsnNode(Opcodes.GETFIELD, "pk", "s", "D"));
        boxOffset.add(new InsnNode(Opcodes.DSUB));
        boxOffset.add(new VarInsnNode(Opcodes.DLOAD, 5));
        boxOffset.add(new InsnNode(Opcodes.DSUB));

        // offsetY = targetY (local 17) - var3.t - camY (local 7)
        boxOffset.add(new VarInsnNode(Opcodes.DLOAD, 17));
        boxOffset.add(new VarInsnNode(Opcodes.ALOAD, 3));
        boxOffset.add(new FieldInsnNode(Opcodes.GETFIELD, "pk", "t", "D"));
        boxOffset.add(new InsnNode(Opcodes.DSUB));
        boxOffset.add(new VarInsnNode(Opcodes.DLOAD, 7));
        boxOffset.add(new InsnNode(Opcodes.DSUB));

        // offsetZ = targetZ (local 19) - var3.u - camZ (local 9)
        boxOffset.add(new VarInsnNode(Opcodes.DLOAD, 19));
        boxOffset.add(new VarInsnNode(Opcodes.ALOAD, 3));
        boxOffset.add(new FieldInsnNode(Opcodes.GETFIELD, "pk", "u", "D"));
        boxOffset.add(new InsnNode(Opcodes.DSUB));
        boxOffset.add(new VarInsnNode(Opcodes.DLOAD, 9));
        boxOffset.add(new InsnNode(Opcodes.DSUB));

        method.instructions.insertBefore(augCCall, boxOffset);

        // Step 3: Interpolate coordinates passed to renderWorldLabel: replace wn.s, wn.t, wn.u with dload 15, 17, 19
        int replacedCoords = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof FieldInsnNode finsn && finsn.owner.equals("wn") && finsn.desc.equals("D")) {
                AbstractInsnNode prev = finsn.getPrevious();
                if (finsn.name.equals("s")) {
                    method.instructions.set(finsn, new VarInsnNode(Opcodes.DLOAD, 15));
                    if (prev != null && prev.getOpcode() == Opcodes.ALOAD) method.instructions.remove(prev);
                    replacedCoords++;
                } else if (finsn.name.equals("t")) {
                    method.instructions.set(finsn, new VarInsnNode(Opcodes.DLOAD, 17));
                    if (prev != null && prev.getOpcode() == Opcodes.ALOAD) method.instructions.remove(prev);
                    replacedCoords++;
                } else if (finsn.name.equals("u")) {
                    method.instructions.set(finsn, new VarInsnNode(Opcodes.DLOAD, 19));
                    if (prev != null && prev.getOpcode() == Opcodes.ALOAD) method.instructions.remove(prev);
                    replacedCoords++;
                }
            }
        }
        if (replacedCoords != 3) {
            throw new IllegalStateException("Expected 3 coordinate replacements in renderWorld, got " + replacedCoords);
        }

        method.maxLocals = Math.max(method.maxLocals, 22);
        method.maxStack = Math.max(method.maxStack, 18);
    }

    private static void patchTargetDisplayNameMethod(MethodNode method) {
        method.instructions.clear();
        method.tryCatchBlocks.clear();
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "com/yny/utils/modules/clanping/PingClanName", "display",
                "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/String;", false));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxLocals = 2;
        method.maxStack = 2;
    }

    private static void patchLastKnownMethod(MethodNode method) {
        int patched = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof FieldInsnNode finsn
                    && finsn.owner.equals("com/yny/utils/modules/clanping/ClanPing$Active")
                    && finsn.name.equals("targetName")
                    && finsn.desc.equals("Ljava/lang/String;")) {
                method.instructions.insert(finsn, new MethodInsnNode(Opcodes.INVOKESTATIC,
                        "com/yny/utils/modules/clanping/PingClanName", "display",
                        "(Ljava/lang/String;)Ljava/lang/String;", false));
                patched++;
                break;
            }
        }
        if (patched != 1) {
            throw new IllegalStateException("Failed to patch last known waypoint targetName, patched=" + patched);
        }
    }
}
