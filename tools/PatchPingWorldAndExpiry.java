import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public final class PatchPingWorldAndExpiry {
    private static final String CLAN_PING = "com/yny/utils/modules/clanping/ClanPing.class";
    private static final String CLAN_PING_ACTIVE = "com/yny/utils/modules/clanping/ClanPing$Active.class";
    private static final String CLAN_PING_TRANSPORT = "com/yny/utils/modules/clanping/ClanPingTransport.class";
    private static final String CLAN_PING_HUD = "com/yny/utils/modules/clanping/ClanPingHud.class";
    private static final String GFX_MARKERS = "com/yny/utils/modules/clanping/GfxWorldMarkers.class";
    private static final String PING_WORLD = "com/yny/utils/modules/clanping/PingWorld.class";
    private static final String PING_COMPASS = "com/yny/utils/modules/clanping/PingCompass.class";
    private static final String PING_HUD_MODEL = "com/yny/utils/modules/clanping/PingHudModel.class";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: PatchPingWorldAndExpiry <in.steinmod> <out.steinmod>");
            System.exit(1);
        }

        File inFile = new File(args[0]);
        File outFile = new File(args[1]);

        Map<String, byte[]> overrides = new LinkedHashMap<>();
        overrides.put(PING_WORLD, readBytes(new File("build/compiled/com/yny/utils/modules/clanping/PingWorld.class")));
        overrides.put(CLAN_PING_HUD, readBytes(new File("build/compiled/com/yny/utils/modules/clanping/ClanPingHud.class")));
        overrides.put(GFX_MARKERS, readBytes(new File("build/compiled/com/yny/utils/modules/clanping/GfxWorldMarkers.class")));
        overrides.put(PING_COMPASS, readBytes(new File("build/compiled/com/yny/utils/modules/clanping/PingCompass.class")));
        overrides.put(PING_HUD_MODEL, readBytes(new File("build/compiled/com/yny/utils/modules/clanping/PingHudModel.class")));

        // Telemetry overrides
        File telemetryDir = new File("build/compiled/com/yny/utils/telemetry");
        if (telemetryDir.exists() && telemetryDir.isDirectory()) {
            File[] tfs = telemetryDir.listFiles();
            if (tfs != null) {
                for (File tf : tfs) {
                    if (tf.isFile() && tf.getName().endsWith(".class")) {
                        overrides.put("com/yny/utils/telemetry/" + tf.getName(), readBytes(tf));
                    }
                }
            }
        }

        // Team overrides from team-source build
        File teamMod = new File("team-source/build/libs/YNYUtilsEquipe.steinmod");
        if (teamMod.exists()) {
            try (ZipFile tz = new ZipFile(teamMod)) {
                for (ZipEntry ze : Collections.list(tz.entries())) {
                    String name = ze.getName();
                    if ((name.startsWith("com/yny/utils/team/") && name.endsWith(".class")) ||
                        (name.startsWith("assets/steincombat/lang/") && name.endsWith(".lang")) ||
                        (name.equals("assets/steincombat/defaults.json"))) {
                        overrides.put(name, readStream(tz.getInputStream(ze)));
                    }
                }
            }
        }

        Map<String, byte[]> outEntries = new LinkedHashMap<>();

        int clanPingCount = 0;
        int activeCount = 0;
        int transportCount = 0;

        try (ZipFile zipIn = new ZipFile(inFile)) {
            Enumeration<? extends ZipEntry> entries = zipIn.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                byte[] data = readStream(zipIn.getInputStream(entry));

                if (CLAN_PING.equals(entry.getName())) {
                    data = patchClanPing(data);
                    clanPingCount++;
                } else if (CLAN_PING_ACTIVE.equals(entry.getName())) {
                    data = patchActive(data);
                    activeCount++;
                } else if (CLAN_PING_TRANSPORT.equals(entry.getName())) {
                    data = patchTransport(data);
                    transportCount++;
                } else if (overrides.containsKey(entry.getName())) {
                    data = overrides.remove(entry.getName());
                }

                outEntries.put(entry.getName(), data);
            }
        }

        // Add any remaining overrides (new classes/assets not in base zip)
        outEntries.putAll(overrides);

        if (clanPingCount != 1 || activeCount != 1 || transportCount != 1) {
            throw new IllegalStateException("Missing required classes: clanPing=" + clanPingCount
                    + ", active=" + activeCount + ", transport=" + transportCount);
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

        System.out.println("PASS: Successfully patched ClanPing, ClanPing$Active, ClanPingTransport and injected all overrides into " + outFile.getName());
    }

    private static byte[] patchClanPing(byte[] original) {
        ClassNode node = read(original);

        int canCreatePatched = 0;
        int startCooldownPatched = 0;
        int renderWorldPatched = 0;
        int worldMetaPatched = 0;
        int hudDetailPatched = 0;
        int tickEndPatched = 0;
        int enemyCrosshairPatched = 0;
        int locationCrosshairPatched = 0;

        for (MethodNode method : node.methods) {
            // 1. canCreatePing: remove cooldown wait check (return true immediately after canSend)
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
                canCreatePatched++;
            }

            // 2. startCooldown: clear cooldown logic (0 delay)
            if ("startCooldown".equals(method.name) && "()V".equals(method.desc)) {
                method.instructions.clear();
                method.instructions.add(new InsnNode(Opcodes.RETURN));
                startCooldownPatched++;
            }

            // 3. renderWorld: check if player is in same world as ping; if not, return early
            if ("renderWorld".equals(method.name) && "(F)V".equals(method.desc)) {
                // Find IFNE 8 (after hasActive check)
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn.getOpcode() == Opcodes.IFNE) {
                        JumpInsnNode jinsn = (JumpInsnNode) insn;
                        LabelNode allowLabel = jinsn.label;
                        InsnList worldCheck = new InsnList();
                        LabelNode continueLabel = new LabelNode();
                        worldCheck.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        worldCheck.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/modules/clanping/ClanPing", "active", "Lcom/yny/utils/modules/clanping/ClanPing$Active;"));
                        worldCheck.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/clanping/PingWorld", "isSameWorld", "(Lcom/yny/utils/modules/clanping/ClanPing$Active;)Z", false));
                        worldCheck.add(new JumpInsnNode(Opcodes.IFNE, continueLabel));
                        worldCheck.add(new InsnNode(Opcodes.RETURN));
                        worldCheck.add(continueLabel);
                        worldCheck.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
                        method.instructions.insert(allowLabel, worldCheck);
                        renderWorldPatched++;
                        break;
                    }
                }
            }

            // 4. worldMeta: delegate to PingWorld.worldMeta
            if ("worldMeta".equals(method.name) && "()Ljava/lang/String;".equals(method.desc)) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn.getOpcode() == Opcodes.ISTORE && ((VarInsnNode) insn).var == 6) {
                        // instruction after istore 6: remove all following and replace with PingWorld.worldMeta
                        AbstractInsnNode next = insn.getNext();
                        while (next != null) {
                            AbstractInsnNode toRemove = next;
                            next = next.getNext();
                            method.instructions.remove(toRemove);
                        }
                        InsnList meta = new InsnList();
                        meta.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        meta.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/modules/clanping/ClanPing", "active", "Lcom/yny/utils/modules/clanping/ClanPing$Active;"));
                        meta.add(new VarInsnNode(Opcodes.ILOAD, 6));
                        meta.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/clanping/PingWorld", "worldMeta", "(Lcom/yny/utils/modules/clanping/ClanPing$Active;I)Ljava/lang/String;", false));
                        meta.add(new InsnNode(Opcodes.ARETURN));
                        method.instructions.add(meta);
                        worldMetaPatched++;
                        break;
                    }
                }
            }

            // 5. hudDetail: delegate to PingWorld.hudDetail
            if ("hudDetail".equals(method.name) && "()Ljava/lang/String;".equals(method.desc)) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode min = (MethodInsnNode) insn;
                        if ("repeat".equals(min.name) && "java/lang/String".equals(min.owner)) {
                            // After repeat call, dirString is on stack
                            AbstractInsnNode next = insn.getNext();
                            while (next != null) {
                                AbstractInsnNode toRemove = next;
                                next = next.getNext();
                                method.instructions.remove(toRemove);
                            }
                            InsnList detail = new InsnList();
                            detail.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            detail.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/modules/clanping/ClanPing", "active", "Lcom/yny/utils/modules/clanping/ClanPing$Active;"));
                            detail.add(new InsnNode(Opcodes.SWAP)); // stack: active, dirString
                            detail.add(new VarInsnNode(Opcodes.ILOAD, 6)); // meters
                            detail.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/clanping/PingWorld", "hudDetail", "(Lcom/yny/utils/modules/clanping/ClanPing$Active;Ljava/lang/String;I)Ljava/lang/String;", false));
                            detail.add(new InsnNode(Opcodes.ARETURN));
                            method.instructions.add(detail);
                            hudDetailPatched++;
                            break;
                        }
                    }
                }
            }

            // 6. onTickEnd: add PingWorld.checkWorldTick at method entry
            if ("onTickEnd".equals(method.name) && "()V".equals(method.desc)) {
                InsnList tick = new InsnList();
                tick.add(new VarInsnNode(Opcodes.ALOAD, 0));
                tick.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/clanping/PingWorld", "checkWorldTick", "(Lcom/yny/utils/modules/clanping/ClanPing;)V", false));
                method.instructions.insert(tick);
                tickEndPatched++;
            }

            // 7. createEnemyFromCrosshair: replace UUID with PingWorld.buildPingId and remove queuedPing wait
            if ("createEnemyFromCrosshair".equals(method.name) && "()V".equals(method.desc)) {
                patchCrosshairMethod(method);
                enemyCrosshairPatched++;
            }

            // 8. createLocationFromCrosshair: replace UUID with PingWorld.buildPingId and remove queuedPing wait
            if ("createLocationFromCrosshair".equals(method.name) && "()V".equals(method.desc)) {
                patchCrosshairMethod(method);
                locationCrosshairPatched++;
            }

            // 9. elevate enemy waypoint label: change 0.65d to 1.25d
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn instanceof LdcInsnNode) {
                    LdcInsnNode ldc = (LdcInsnNode) insn;
                    if (ldc.cst instanceof Double && Math.abs((Double) ldc.cst - 0.65D) < 0.001D) {
                        ldc.cst = 1.25D;
                    }
                }
            }
        }

        if (canCreatePatched != 1 || startCooldownPatched != 1 || renderWorldPatched != 1
                || worldMetaPatched != 1 || hudDetailPatched != 1 || tickEndPatched != 1
                || enemyCrosshairPatched != 1 || locationCrosshairPatched != 1) {
            throw new IllegalStateException("ClanPing patch mismatch: canCreate=" + canCreatePatched
                    + ", startCooldown=" + startCooldownPatched + ", renderWorld=" + renderWorldPatched
                    + ", worldMeta=" + worldMetaPatched + ", hudDetail=" + hudDetailPatched
                    + ", tickEnd=" + tickEndPatched + ", enemyCrosshair=" + enemyCrosshairPatched
                    + ", locationCrosshair=" + locationCrosshairPatched);
        }

        return write(node);
    }

    private static void patchCrosshairMethod(MethodNode method) {
        // Step A: Replace UUID.randomUUID().toString() with PingWorld.buildPingId(PingWorld.getCurrentDimension(), PingWorld.getCurrentWorldName())
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode min = (MethodInsnNode) insn;
                if ("java/util/UUID".equals(min.owner) && "randomUUID".equals(min.name)) {
                    AbstractInsnNode toStringNode = min.getNext();
                    if (toStringNode instanceof MethodInsnNode) {
                        MethodInsnNode min2 = (MethodInsnNode) toStringNode;
                        if ("toString".equals(min2.name)) {
                            InsnList idList = new InsnList();
                            idList.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/clanping/PingWorld", "getCurrentDimension", "()I", false));
                            idList.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/clanping/PingWorld", "getCurrentWorldName", "()Ljava/lang/String;", false));
                            idList.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/clanping/PingWorld", "buildPingId", "(ILjava/lang/String;)Ljava/lang/String;", false));
                            method.instructions.insertBefore(min, idList);
                            method.instructions.remove(min);
                            method.instructions.remove(toStringNode);
                            break;
                        }
                    }
                }
            }
        }

        // Step B: Replace queuedPing block (ownPingId != null) with instant cancel and continue
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.IFNULL) {
                JumpInsnNode jinsn = (JumpInsnNode) insn;
                AbstractInsnNode prev = prevReal(jinsn);
                if (prev instanceof FieldInsnNode) {
                    FieldInsnNode finsn = (FieldInsnNode) prev;
                    if ("ownPingId".equals(finsn.name)) {
                        // Between jinsn and jinsn.label is the queuedPing block
                        LabelNode labelNull = jinsn.label;
                        AbstractInsnNode cur = jinsn.getNext();
                        while (cur != null && cur != labelNull) {
                            AbstractInsnNode toRemove = cur;
                            cur = cur.getNext();
                            method.instructions.remove(toRemove);
                        }

                        // Insert instant cancel:
                        InsnList fastCancel = new InsnList();
                        fastCancel.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        fastCancel.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/modules/clanping/ClanPing", "transport", "Lcom/yny/utils/modules/clanping/ClanPingTransport;"));
                        fastCancel.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        fastCancel.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/modules/clanping/ClanPing", "ownPingId", "Ljava/lang/String;"));
                        fastCancel.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "com/yny/utils/modules/clanping/ClanPingTransport", "cancel", "(Ljava/lang/String;)V", false));
                        fastCancel.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        fastCancel.add(new InsnNode(Opcodes.ACONST_NULL));
                        fastCancel.add(new FieldInsnNode(Opcodes.PUTFIELD, "com/yny/utils/modules/clanping/ClanPing", "ownPingId", "Ljava/lang/String;"));
                        method.instructions.insert(jinsn, fastCancel);
                        break;
                    }
                }
            }
        }
    }

    private static byte[] patchActive(byte[] original) {
        ClassNode node = read(original);

        int expiredPatched = 0;
        int fadePatched = 0;

        for (MethodNode method : node.methods) {
            // expired()Z -> always return false (0)
            if ("expired".equals(method.name) && "()Z".equals(method.desc)) {
                method.instructions.clear();
                method.instructions.add(new InsnNode(Opcodes.ICONST_0));
                method.instructions.add(new InsnNode(Opcodes.IRETURN));
                expiredPatched++;
            }
            // fade()F -> always return 1.0F
            if ("fade".equals(method.name) && "()F".equals(method.desc)) {
                method.instructions.clear();
                method.instructions.add(new InsnNode(Opcodes.FCONST_1));
                method.instructions.add(new InsnNode(Opcodes.FRETURN));
                fadePatched++;
            }
        }

        if (expiredPatched != 1 || fadePatched != 1) {
            throw new IllegalStateException("ClanPing$Active patch mismatch: expired=" + expiredPatched + ", fade=" + fadePatched);
        }

        return write(node);
    }

    private static byte[] patchTransport(byte[] original) {
        ClassNode node = read(original);

        int createExpiresPatched = 0;

        for (MethodNode method : node.methods) {
            if ("create".equals(method.name) && "(Lcom/yny/utils/modules/clanping/ClanPing$Active;)V".equals(method.desc)) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof FieldInsnNode) {
                        FieldInsnNode finsn = (FieldInsnNode) insn;
                        if ("expiresAt".equals(finsn.name) && finsn.getOpcode() == Opcodes.GETFIELD) {
                            InsnList futureTime = new InsnList();
                            futureTime.add(new InsnNode(Opcodes.POP)); // pop active instance
                            futureTime.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false));
                            futureTime.add(new LdcInsnNode(31536000000L)); // 1 year in ms
                            futureTime.add(new InsnNode(Opcodes.LADD));
                            method.instructions.insertBefore(finsn, futureTime);
                            method.instructions.remove(finsn);
                            createExpiresPatched++;
                            break;
                        }
                    }
                }
            }
        }

        if (createExpiresPatched != 1) {
            throw new IllegalStateException("ClanPingTransport patch mismatch: createExpires=" + createExpiresPatched);
        }

        return write(node);
    }

    private static AbstractInsnNode prevReal(AbstractInsnNode insn) {
        AbstractInsnNode cur = insn.getPrevious();
        while (cur != null && (cur instanceof LabelNode || cur instanceof LineNumberNode || cur instanceof FrameNode)) {
            cur = cur.getPrevious();
        }
        return cur;
    }

    private static byte[] readBytes(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            return readStream(in);
        }
    }

    private static byte[] readStream(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int r;
        while ((r = in.read(b)) != -1) {
            buffer.write(b, 0, r);
        }
        return buffer.toByteArray();
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
}
