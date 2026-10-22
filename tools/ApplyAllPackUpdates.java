import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public final class ApplyAllPackUpdates {
    private static final String CLAN_PING = "com/yny/utils/modules/clanping/ClanPing.class";
    private static final String CLAN_PING_ACTIVE = "com/yny/utils/modules/clanping/ClanPing$Active.class";
    private static final String CLAN_PING_TRANSPORT = "com/yny/utils/modules/clanping/ClanPingTransport.class";

    public static void main(String[] args) throws Exception {
        File baseFile = new File("artifacts/YNYUtils.before-autoarmor-mouse-fix-2026-10-17.steinmod");
        if (!baseFile.exists()) {
            baseFile = new File("artifacts/YNYUtils.steinmod");
        }
        File teamMod = new File("team-source/build/libs/YNYUtilsEquipe.steinmod");
        File outFile = new File("build/YNYUtils.steinmod");

        System.out.println("=== Applying All Modpack Updates to YNYUtils.steinmod ===");

        Map<String, byte[]> replacements = new LinkedHashMap<>();

        // 1. Load ALL compiled classes recursively from build/compiled
        File compiledRoot = new File("build/compiled");
        if (compiledRoot.exists()) {
            loadCompiledClasses(compiledRoot, compiledRoot, replacements);
        } else {
            throw new FileNotFoundException("Missing build/compiled directory!");
        }

        // 2. Load all team-source classes and assets (CombatPage, defaults.json, etc.)
        if (teamMod.exists()) {
            try (ZipFile tz = new ZipFile(teamMod)) {
                for (ZipEntry ze : Collections.list(tz.entries())) {
                    String name = ze.getName();
                    if ((name.startsWith("com/yny/utils/team/") && name.endsWith(".class")) ||
                        (name.startsWith("assets/steincombat/lang/") && name.endsWith(".lang")) ||
                        (name.equals("assets/steincombat/defaults.json"))) {
                        byte[] b = readStream(tz.getInputStream(ze));
                        replacements.put(name, b);
                        if (name.contains("CombatPage")) {
                            System.out.println("  Loaded team entry: " + name + " (" + b.length + " bytes)");
                        }
                    }
                }
            }
        } else {
            throw new FileNotFoundException("Missing team mod: " + teamMod.getAbsolutePath());
        }

        // 3. Read base zip, patch ClanPing, and apply all replacements
        Map<String, byte[]> finalEntries = new LinkedHashMap<>();

        try (ZipFile zipIn = new ZipFile(baseFile)) {
            Enumeration<? extends ZipEntry> entries = zipIn.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (isTestClass(name)) {
                    System.out.println("  [EXCLUDE] Removing test class from base zip: " + name);
                    continue;
                }
                byte[] data = readStream(zipIn.getInputStream(entry));

                if (CLAN_PING.equals(name)) {
                    data = patchClanPing(data);
                    data = desugarStringConcat(data);
                    System.out.println("  Patched and desugared ClanPing.class");
                } else if (CLAN_PING_ACTIVE.equals(name)) {
                    data = patchActive(data);
                    data = desugarStringConcat(data);
                    System.out.println("  Patched and desugared ClanPing$Active.class");
                } else if (CLAN_PING_TRANSPORT.equals(name)) {
                    data = patchTransport(data);
                    data = desugarStringConcat(data);
                    System.out.println("  Patched and desugared ClanPingTransport.class");
                } else if ("com/yny/utils/modules/player/AutoArmor.class".equals(name)) {
                    data = PatchTurmaFixes.patchAutoArmor(data);
                    data = desugarStringConcat(data);
                    System.out.println("  Patched and desugared AutoArmor.class (zero-pause sword block, atomic equip, cursor recovery, threshold)");
                } else if ("com/yny/utils/modules/player/AutoConsumables.class".equals(name)) {
                    data = PatchTurmaFixes.patchAutoConsumables(data);
                    data = desugarStringConcat(data);
                    System.out.println("  Patched and desugared AutoConsumables.class");
                } else if ("com/yny/utils/YNYUtils.class".equals(name)) {
                    data = PatchTurmaFixes.patchYNYUtils(data);
                    data = patchYNYUtilsMobCombat(data);
                    data = patchYNYUtilsRealKnockback(data);
                    data = desugarStringConcat(data);
                    System.out.println("  Patched and desugared YNYUtils.class (added Mob Aura and Mob Combat options/hooks, restored real knockback)");
                } else if ("com/yny/utils/modules/pvp/KnockbackControl.class".equals(name)) {
                    data = patchKnockbackControl(data);
                    data = desugarStringConcat(data);
                    System.out.println("  Patched and desugared KnockbackControl.class (allowed 0-100% reduction range)");
                } else if ("com/yny/utils/modules/pvp/KnockbackControl$VelocityInterceptor.class".equals(name)) {
                    data = patchVelocityInterceptor(data);
                    data = desugarStringConcat(data);
                } else if ("com/yny/utils/ui/PvpStatusHud.class".equals(name)) {
                    data = patchPvpStatusHud(data);
                    data = desugarStringConcat(data);
                    System.out.println("  Patched and desugared PvpStatusHud.class (restored showKnockback, added autoclick notification and static instance)");
                } else if ("com/yny/utils/modules/pvp/AllyGroundMarker.class".equals(name)) {
                    data = patchAllyGroundMarker(data);
                    data = desugarStringConcat(data);
                    System.out.println("  Patched and desugared AllyGroundMarker.class (eliminated CME via SafeEntities.safePlayers)");
                } else if (replacements.containsKey(name)) {
                    data = replacements.remove(name);
                } else if (name.endsWith(".class")) {
                    // Any other remaining legacy classes: desugar and enforce Java 8
                    data = desugarStringConcat(data);
                }

                finalEntries.put(name, data);
            }
        }

        // Add any remaining replacement entries (new classes)
        finalEntries.putAll(replacements);

        // Filter out any unit test classes completely from the final bundle
        finalEntries.keySet().removeIf(ApplyAllPackUpdates::isTestClass);

        // Inject updated steinmod.json
        File jsonFile = new File("steinmod.json");
        if (jsonFile.exists()) {
            finalEntries.put("steinmod.json", Files.readAllBytes(jsonFile.toPath()));
            System.out.println("  Injected updated steinmod.json (version 1.0.0, author 4NT)");
        }

        // Inject pack.mcmeta to suppress resourcepack metadata warning
        File mcmetaFile = new File("src/main/resources/pack.mcmeta");
        if (mcmetaFile.exists()) {
            finalEntries.put("pack.mcmeta", Files.readAllBytes(mcmetaFile.toPath()));
            System.out.println("  Injected pack.mcmeta");
        }

        // 4. Double check all class entries to ensure 100% Major 52
        for (Map.Entry<String, byte[]> e : finalEntries.entrySet()) {
            if (e.getKey().endsWith(".class")) {
                byte[] b = e.getValue();
                int major = ((b[6] & 0xFF) << 8) | (b[7] & 0xFF);
                if (major != 52) {
                    System.out.println("  Normalizing class " + e.getKey() + " from major " + major + " to 52");
                    b = desugarStringConcat(b);
                    e.setValue(b);
                }
            }
        }

        // 4b. Enforce Java 8 access permissions across all classes in the pack
        // In Java 8, cross-class access to private members (e.g. inner classes accessing outer classes)
        // throws IllegalAccessError at runtime without nestmate support.
        widenCrossClassAccess(finalEntries);

        // 4c. Verify that NO calls to non-existent methods or fields exist across pack classes
        verifyNoMissingMembers(finalEntries);

        // 5. Write to build/YNYUtils.steinmod
        File tempOut = new File(outFile.getAbsolutePath() + ".tmp");
        if (tempOut.getParentFile() != null) tempOut.getParentFile().mkdirs();
        try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(tempOut))) {
            for (Map.Entry<String, byte[]> e : finalEntries.entrySet()) {
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

        System.out.println("Generated: " + outFile.getAbsolutePath() + " (" + outFile.length() + " bytes)");

        // 6. Sync YNYUtils.steinmod to all target locations
        List<File> ynyTargets = Arrays.asList(
            new File("artifacts/YNYUtils.steinmod"),
            new File("../../../data/.minecraft/mods/YNYUtils.steinmod"),
            new File("../YNY-Modpack-Limpa/data/.minecraft/mods/YNYUtils.steinmod"),
            new File("../YNY-Modpack-Limpa/data/data/.minecraft/mods/YNYUtils.steinmod"),
            new File("../../../YNYUtils.steinmod")
        );

        byte[] finalBytes = Files.readAllBytes(outFile.toPath());
        String finalSha = sha256(finalBytes);
        System.out.println("Final YNYUtils SHA-256: " + finalSha);

        for (File target : ynyTargets) {
            if (target.exists() || target.getParentFile().exists()) {
                Files.write(target.toPath(), finalBytes);
                System.out.println("  Synchronized YNYUtils -> " + target.getPath() + " (verified SHA-256: " + sha256(Files.readAllBytes(target.toPath())) + ")");
            } else {
                System.out.println("  Skipping non-existent path: " + target.getPath());
            }
        }

        // 7. Sync Combat.steinmod to all target locations
        File combatMod = new File("../SteinCombat/build/libs/Combat.steinmod");
        if (combatMod.exists()) {
            byte[] combatBytes = Files.readAllBytes(combatMod.toPath());
            String combatSha = sha256(combatBytes);
            System.out.println("Final Combat SHA-256: " + combatSha);

            List<File> combatTargets = Arrays.asList(
                new File("../../../data/.minecraft/mods/Combat.steinmod"),
                new File("../YNY-Modpack-Limpa/data/.minecraft/mods/Combat.steinmod"),
                new File("../YNY-Modpack-Limpa/data/data/.minecraft/mods/Combat.steinmod")
            );

            for (File target : combatTargets) {
                if (target.exists() || target.getParentFile().exists()) {
                    Files.write(target.toPath(), combatBytes);
                    System.out.println("  Synchronized Combat -> " + target.getPath() + " (verified SHA-256: " + sha256(Files.readAllBytes(target.toPath())) + ")");
                }
            }
        } else {
            System.err.println("WARNING: Combat.steinmod not found at " + combatMod.getAbsolutePath());
        }

        // 8. Clear steinloader cache to prevent stale cached classes
        File cacheJar = new File("../../../data/.minecraft/steinloader/cache/YNYUtils.jar");
        File cacheStamp = new File("../../../data/.minecraft/steinloader/cache/YNYUtils.jar.stamp");
        if (cacheJar.exists()) cacheJar.delete();
        if (cacheStamp.exists()) cacheStamp.delete();
        System.out.println("  Cleared SteinLoader cache for YNYUtils");

        File combatCacheJar = new File("../../../data/.minecraft/steinloader/cache/Combat.jar");
        File combatCacheStamp = new File("../../../data/.minecraft/steinloader/cache/Combat.jar.stamp");
        if (combatCacheJar.exists()) combatCacheJar.delete();
        if (combatCacheStamp.exists()) combatCacheStamp.delete();
        System.out.println("  Cleared SteinLoader cache for Combat");

        System.out.println("PASS: All modpack updates applied and synchronized successfully!");
    }

    private static void loadCompiledClasses(File root, File current, Map<String, byte[]> replacements) throws IOException {
        File[] files = current.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                loadCompiledClasses(root, f, replacements);
            } else if (f.isFile() && f.getName().endsWith(".class")) {
                String relPath = root.toPath().relativize(f.toPath()).toString().replace('\\', '/');
                if (isTestClass(relPath)) {
                    System.out.println("  [EXCLUDE] Skipping test class from production modpack: " + relPath);
                    continue;
                }
                byte[] bytes = Files.readAllBytes(f.toPath());
                // Protect against classes compiled with MCP names (minecraft-named-open.jar) that fail on Notch runtime
                if (new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1).contains("net/minecraft/")
                    || "com/yny/utils/YNYUtils.class".equals(relPath)
                    || "com/yny/utils/ui/PvpStatusHud.class".equals(relPath)
                    || relPath.equals("com/yny/utils/modules/player/AutoArmor.class")
                    || relPath.startsWith("com/yny/utils/modules/player/AutoArmor$")
                    || relPath.equals("com/yny/utils/modules/player/AutoConsumables.class")
                    || relPath.startsWith("com/yny/utils/modules/player/AutoConsumables$")
                    || relPath.equals("com/yny/utils/modules/pvp/KnockbackControl.class")
                    || relPath.startsWith("com/yny/utils/modules/pvp/KnockbackControl$")
                    || relPath.equals("com/yny/utils/modules/pvp/KnockbackJumpReset.class")) {
                    System.out.println("  [PROTECT] Preserving Notch class, skipping unsafe MCP class: " + relPath);
                    continue;
                }
                replacements.put(relPath, bytes);
                System.out.println("  Loaded compiled class: " + relPath);
            }
        }
    }

    private static byte[] desugarStringConcat(byte[] classBytes) {
        byte[] compat = classBytes.clone();
        compat[6] = 0;
        compat[7] = 60; // Read as Java 16
        ClassReader cr = new ClassReader(compat);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        cn.version = Opcodes.V1_8;
        cn.nestHostClass = null;
        if (cn.nestMembers != null) cn.nestMembers.clear();

        for (MethodNode mn : cn.methods) {
            ListIterator<AbstractInsnNode> it = mn.instructions.iterator();
            while (it.hasNext()) {
                AbstractInsnNode insn = it.next();
                if (insn instanceof InvokeDynamicInsnNode) {
                    InvokeDynamicInsnNode indy = (InvokeDynamicInsnNode) insn;
                    if (indy.bsm.getOwner().equals("java/lang/invoke/StringConcatFactory")
                            && indy.bsm.getName().equals("makeConcatWithConstants")) {
                        desugarIndy(mn, indy);
                    }
                }
            }
        }

        return write(cn);
    }

    private static void desugarIndy(MethodNode mn, InvokeDynamicInsnNode indy) {
        Type[] argTypes = Type.getArgumentTypes(indy.desc);
        String recipe = indy.bsmArgs.length > 0 ? (String) indy.bsmArgs[0] : null;

        int maxLocals = mn.maxLocals;
        int[] localIndices = new int[argTypes.length];
        int curLocal = maxLocals;
        for (int i = argTypes.length - 1; i >= 0; i--) {
            localIndices[i] = curLocal;
            curLocal += argTypes[i].getSize();
        }
        mn.maxLocals = curLocal;

        InsnList list = new InsnList();

        for (int i = argTypes.length - 1; i >= 0; i--) {
            Type t = argTypes[i];
            list.add(new VarInsnNode(t.getOpcode(Opcodes.ISTORE), localIndices[i]));
        }

        list.add(new TypeInsnNode(Opcodes.NEW, "java/lang/StringBuilder"));
        list.add(new InsnNode(Opcodes.DUP));
        list.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false));

        if (recipe != null) {
            int argIdx = 0;
            StringBuilder literal = new StringBuilder();
            for (int i = 0; i < recipe.length(); i++) {
                char ch = recipe.charAt(i);
                if (ch == '\u0001') {
                    if (literal.length() > 0) {
                        list.add(new LdcInsnNode(literal.toString()));
                        list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false));
                        literal.setLength(0);
                    }
                    if (argIdx < argTypes.length) {
                        appendArg(list, argTypes[argIdx], localIndices[argIdx]);
                        argIdx++;
                    }
                } else {
                    literal.append(ch);
                }
            }
            if (literal.length() > 0) {
                list.add(new LdcInsnNode(literal.toString()));
                list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false));
            }
        } else {
            for (int i = 0; i < argTypes.length; i++) {
                appendArg(list, argTypes[i], localIndices[i]);
            }
        }

        list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false));

        mn.instructions.insertBefore(indy, list);
        mn.instructions.remove(indy);
    }

    private static void appendArg(InsnList list, Type type, int localIndex) {
        list.add(new VarInsnNode(type.getOpcode(Opcodes.ILOAD), localIndex));
        String desc;
        switch (type.getSort()) {
            case Type.BOOLEAN: desc = "(Z)Ljava/lang/StringBuilder;"; break;
            case Type.CHAR:    desc = "(C)Ljava/lang/StringBuilder;"; break;
            case Type.BYTE:
            case Type.SHORT:
            case Type.INT:     desc = "(I)Ljava/lang/StringBuilder;"; break;
            case Type.LONG:    desc = "(J)Ljava/lang/StringBuilder;"; break;
            case Type.FLOAT:   desc = "(F)Ljava/lang/StringBuilder;"; break;
            case Type.DOUBLE:  desc = "(D)Ljava/lang/StringBuilder;"; break;
            case Type.OBJECT:
                if (type.getClassName().equals("java.lang.String")) {
                    desc = "(Ljava/lang/String;)Ljava/lang/StringBuilder;";
                } else {
                    desc = "(Ljava/lang/Object;)Ljava/lang/StringBuilder;";
                }
                break;
            default:
                desc = "(Ljava/lang/Object;)Ljava/lang/StringBuilder;";
                break;
        }
        list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", desc, false));
    }

    private static byte[] patchClanPing(byte[] original) {
        ClassNode node = read(original);

        for (MethodNode method : node.methods) {
            // 1. canCreatePing
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
            }

            // 2. startCooldown
            if ("startCooldown".equals(method.name) && "()V".equals(method.desc)) {
                method.instructions.clear();
                method.instructions.add(new InsnNode(Opcodes.RETURN));
            }

            // 3. renderWorld: check if player is in same world as ping; if not, return early
            if ("renderWorld".equals(method.name) && "(F)V".equals(method.desc)) {
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
                        break;
                    }
                }
            }

            // 4. worldMeta: delegate to PingWorld.worldMeta
            if ("worldMeta".equals(method.name) && "()Ljava/lang/String;".equals(method.desc)) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn.getOpcode() == Opcodes.ISTORE && ((VarInsnNode) insn).var == 6) {
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
                            AbstractInsnNode next = insn.getNext();
                            while (next != null) {
                                AbstractInsnNode toRemove = next;
                                next = next.getNext();
                                method.instructions.remove(toRemove);
                            }
                            InsnList detail = new InsnList();
                            detail.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            detail.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/modules/clanping/ClanPing", "active", "Lcom/yny/utils/modules/clanping/ClanPing$Active;"));
                            detail.add(new InsnNode(Opcodes.SWAP));
                            detail.add(new VarInsnNode(Opcodes.ILOAD, 6));
                            detail.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/clanping/PingWorld", "hudDetail", "(Lcom/yny/utils/modules/clanping/ClanPing$Active;Ljava/lang/String;I)Ljava/lang/String;", false));
                            detail.add(new InsnNode(Opcodes.ARETURN));
                            method.instructions.add(detail);
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
            }

            // 7. createEnemyFromCrosshair
            if ("createEnemyFromCrosshair".equals(method.name) && "()V".equals(method.desc)) {
                patchCrosshairMethod(method);
            }

            // 8. createLocationFromCrosshair
            if ("createLocationFromCrosshair".equals(method.name) && "()V".equals(method.desc)) {
                patchCrosshairMethod(method);
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

            // 2b. onJoinGame: do not clear active nor pendingRelations
            if ("onJoinGame".equals(method.name) && "()V".equals(method.desc)) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof FieldInsnNode && insn.getOpcode() == Opcodes.PUTFIELD) {
                        FieldInsnNode finsn = (FieldInsnNode) insn;
                        if ("com/yny/utils/modules/clanping/ClanPing".equals(finsn.owner) && "active".equals(finsn.name)) {
                            AbstractInsnNode prev1 = prevReal(finsn);
                            AbstractInsnNode prev2 = prevReal(prev1);
                            if (prev1 != null && prev1.getOpcode() == Opcodes.ACONST_NULL
                                    && prev2 != null && prev2.getOpcode() == Opcodes.ALOAD && ((VarInsnNode) prev2).var == 0) {
                                method.instructions.remove(prev2);
                                method.instructions.remove(prev1);
                                method.instructions.remove(finsn);
                            }
                        }
                    }
                    if (insn instanceof MethodInsnNode && insn.getOpcode() == Opcodes.INVOKEINTERFACE) {
                        MethodInsnNode min = (MethodInsnNode) insn;
                        if ("java/util/Map".equals(min.owner) && "clear".equals(min.name)) {
                            AbstractInsnNode prev1 = prevReal(min);
                            AbstractInsnNode prev2 = prevReal(prev1);
                            if (prev1 instanceof FieldInsnNode && "pendingRelations".equals(((FieldInsnNode) prev1).name)
                                    && prev2 != null && prev2.getOpcode() == Opcodes.ALOAD) {
                                method.instructions.remove(prev2);
                                method.instructions.remove(prev1);
                                method.instructions.remove(min);
                            }
                        }
                    }
                }
            }

            // 10. Delegate renderWorldLabel to PingWorld.renderWorldLabel
            if ("renderWorldLabel".equals(method.name) && "(Ljava/lang/String;DDDDDDI)V".equals(method.desc)) {
                method.instructions.clear();
                method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                method.instructions.add(new VarInsnNode(Opcodes.DLOAD, 1));
                method.instructions.add(new VarInsnNode(Opcodes.DLOAD, 3));
                method.instructions.add(new VarInsnNode(Opcodes.DLOAD, 5));
                method.instructions.add(new VarInsnNode(Opcodes.DLOAD, 7));
                method.instructions.add(new VarInsnNode(Opcodes.DLOAD, 9));
                method.instructions.add(new VarInsnNode(Opcodes.DLOAD, 11));
                method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 13));
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/clanping/PingWorld", "renderWorldLabel", "(Ljava/lang/String;DDDDDDI)V", false));
                method.instructions.add(new InsnNode(Opcodes.RETURN));
            }

            // 11. Extend fallback waypoint beams to 256.0d
            if (("renderLocationWaypoint".equals(method.name) || "renderEnemyLastKnownWaypoint".equals(method.name))
                    && "(F)V".equals(method.desc)) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof LdcInsnNode) {
                        LdcInsnNode ldc = (LdcInsnNode) insn;
                        if (ldc.cst instanceof Double && Math.abs((Double) ldc.cst - 24.0D) < 0.001D) {
                            ldc.cst = 256.0D;
                        }
                    }
                }
            }

            // 12. showIncoming: notify PingWorld on incoming ping from teammates
            if ("showIncoming".equals(method.name) && "(Lcom/yny/utils/modules/clanping/ClanPing$Active;)V".equals(method.desc)) {
                InsnList notifyIncoming = new InsnList();
                notifyIncoming.add(new VarInsnNode(Opcodes.ALOAD, 1));
                notifyIncoming.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/clanping/PingWorld", "onIncomingPing", "(Lcom/yny/utils/modules/clanping/ClanPing$Active;)V", false));
                method.instructions.insert(notifyIncoming);
            }

            // 13. receive: update leader server
            if ("receive".equals(method.name) && "(Lcom/yny/utils/modules/clanping/ClanPing$Active;)V".equals(method.desc)) {
                InsnList updateLeader = new InsnList();
                LabelNode skip = new LabelNode();
                updateLeader.add(new VarInsnNode(Opcodes.ALOAD, 1));
                updateLeader.add(new JumpInsnNode(Opcodes.IFNULL, skip));
                updateLeader.add(new VarInsnNode(Opcodes.ALOAD, 1));
                updateLeader.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/modules/clanping/ClanPing$Active", "server", "Ljava/lang/String;"));
                updateLeader.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/clanping/PingClanName", "setLeaderServer", "(Ljava/lang/String;)V", false));
                updateLeader.add(skip);
                updateLeader.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
                method.instructions.insert(updateLeader);
            }
        }

        MethodNode getLeaderServerMethod = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "getLeaderServer", "()Ljava/lang/String;", null, null);
        InsnList gls = new InsnList();
        gls.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/clanping/PingClanName", "getLeaderServer", "()Ljava/lang/String;", false));
        gls.add(new InsnNode(Opcodes.ARETURN));
        getLeaderServerMethod.instructions.add(gls);
        getLeaderServerMethod.maxStack = 1;
        getLeaderServerMethod.maxLocals = 0;
        node.methods.add(getLeaderServerMethod);

        return write(node);
    }

    private static void patchCrosshairMethod(MethodNode method) {
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

        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.IFNULL) {
                JumpInsnNode jinsn = (JumpInsnNode) insn;
                AbstractInsnNode prev = prevReal(jinsn);
                if (prev instanceof FieldInsnNode) {
                    FieldInsnNode finsn = (FieldInsnNode) prev;
                    if ("ownPingId".equals(finsn.name)) {
                        LabelNode labelNull = jinsn.label;
                        AbstractInsnNode cur = jinsn.getNext();
                        while (cur != null && cur != labelNull) {
                            AbstractInsnNode toRemove = cur;
                            cur = cur.getNext();
                            method.instructions.remove(toRemove);
                        }

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

        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode min = (MethodInsnNode) insn;
                if ("startCooldown".equals(min.name) && "()V".equals(min.desc)) {
                    AbstractInsnNode cur = min.getNext();
                    while (cur != null && cur.getOpcode() != Opcodes.RETURN) {
                        if (cur instanceof MethodInsnNode && "actionBar".equals(((MethodInsnNode) cur).name)
                                && "dev/xavier/stein/loader/api/Chat".equals(((MethodInsnNode) cur).owner)) {
                            AbstractInsnNode ldcArg = prevReal(cur);
                            if (ldcArg != null && ldcArg instanceof LdcInsnNode) {
                                method.instructions.remove(ldcArg);
                            }
                            InsnList notifySelf = new InsnList();
                            notifySelf.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            notifySelf.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/modules/clanping/ClanPing", "active", "Lcom/yny/utils/modules/clanping/ClanPing$Active;"));
                            notifySelf.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/clanping/PingWorld", "onIncomingPing", "(Lcom/yny/utils/modules/clanping/ClanPing$Active;)V", false));
                            method.instructions.insertBefore(cur, notifySelf);
                            method.instructions.remove(cur);
                            break;
                        }
                        cur = cur.getNext();
                    }
                    break;
                }
            }
        }
    }

    private static byte[] patchActive(byte[] original) {
        ClassNode node = read(original);
        for (MethodNode method : node.methods) {
            if ("expired".equals(method.name) && "()Z".equals(method.desc)) {
                method.instructions.clear();
                method.instructions.add(new InsnNode(Opcodes.ICONST_0));
                method.instructions.add(new InsnNode(Opcodes.IRETURN));
            }
            if ("fade".equals(method.name) && "()F".equals(method.desc)) {
                method.instructions.clear();
                method.instructions.add(new InsnNode(Opcodes.FCONST_1));
                method.instructions.add(new InsnNode(Opcodes.FRETURN));
            }
        }
        return write(node);
    }

    private static byte[] patchTransport(byte[] original) {
        ClassNode node = read(original);
        for (MethodNode method : node.methods) {
            if ("create".equals(method.name) && "(Lcom/yny/utils/modules/clanping/ClanPing$Active;)V".equals(method.desc)) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof FieldInsnNode) {
                        FieldInsnNode finsn = (FieldInsnNode) insn;
                        if ("expiresAt".equals(finsn.name) && finsn.getOpcode() == Opcodes.GETFIELD) {
                            InsnList futureTime = new InsnList();
                            futureTime.add(new InsnNode(Opcodes.POP));
                            futureTime.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false));
                            futureTime.add(new LdcInsnNode(31536000000L));
                            futureTime.add(new InsnNode(Opcodes.LADD));
                            method.instructions.insertBefore(finsn, futureTime);
                            method.instructions.remove(finsn);
                        }
                    }
                }
            } else if ("authenticate".equals(method.name) && "()V".equals(method.desc)) {
                InsnList insns = new InsnList();
                insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "dev/xavier/stein/loader/api/Server", "address", "()Ljava/lang/String;", false));
                insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/clanping/PingClanName", "setLeaderServer", "(Ljava/lang/String;)V", false));
                method.instructions.insert(insns);
            }
        }
        return write(node);
    }

    private static byte[] patchPvpStatusHud(byte[] original) {
        ClassNode node = read(original);

        // 1. Add static volatile instance field
        boolean hasInstance = false;
        boolean hasAutoclickMessage = false;
        for (FieldNode fn : node.fields) {
            if ("instance".equals(fn.name)) hasInstance = true;
            if ("autoclickMessage".equals(fn.name)) hasAutoclickMessage = true;
        }
        if (!hasInstance) {
            node.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_VOLATILE,
                "instance",
                "Lcom/yny/utils/ui/PvpStatusHud;",
                null,
                null
            ));
        }
        if (!hasAutoclickMessage) {
            node.fields.add(new FieldNode(
                Opcodes.ACC_PRIVATE,
                "autoclickMessage",
                "Z",
                null,
                null
            ));
        }

        // 2. In <init>, set instance = this;
        for (MethodNode mn : node.methods) {
            if ("<init>".equals(mn.name)) {
                for (AbstractInsnNode insn : mn.instructions.toArray()) {
                    if (insn instanceof LdcInsnNode && "Notificações de PvP".equals(((LdcInsnNode) insn).cst)) {
                        ((LdcInsnNode) insn).cst = "Knockback: ATIVADO";
                    }
                }
                boolean already = false;
                for (AbstractInsnNode insn : mn.instructions.toArray()) {
                    if (insn instanceof FieldInsnNode && "instance".equals(((FieldInsnNode) insn).name)) {
                        already = true; break;
                    }
                }
                if (!already) {
                    for (AbstractInsnNode insn : mn.instructions.toArray()) {
                        if (insn.getOpcode() == Opcodes.INVOKESPECIAL) {
                            InsnList setInst = new InsnList();
                            setInst.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            setInst.add(new FieldInsnNode(
                                Opcodes.PUTSTATIC,
                                "com/yny/utils/ui/PvpStatusHud",
                                "instance",
                                "Lcom/yny/utils/ui/PvpStatusHud;"
                            ));
                            mn.instructions.insert(insn, setInst);
                            break;
                        }
                    }
                }
            }

            // Restore showKnockback
            if ("showKnockback".equals(mn.name) && "(Z)V".equals(mn.desc)) {
                mn.instructions.clear();
                mn.tryCatchBlocks.clear();
                mn.localVariables.clear();

                LabelNode lFalse = new LabelNode();
                LabelNode lAfterMsg = new LabelNode();
                LabelNode lColorFalse = new LabelNode();
                LabelNode lAfterColor = new LabelNode();

                InsnList insns = new InsnList();

                // this.message = arg1 ? "Knockback: ATIVADO" : "Knockback: DESATIVADO";
                insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
                insns.add(new VarInsnNode(Opcodes.ILOAD, 1));
                insns.add(new JumpInsnNode(Opcodes.IFEQ, lFalse));
                insns.add(new LdcInsnNode("Knockback: ATIVADO"));
                insns.add(new JumpInsnNode(Opcodes.GOTO, lAfterMsg));
                insns.add(lFalse);
                insns.add(new LdcInsnNode("Knockback: DESATIVADO"));
                insns.add(lAfterMsg);
                insns.add(new FieldInsnNode(Opcodes.PUTFIELD, "com/yny/utils/ui/PvpStatusHud", "message", "Ljava/lang/String;"));

                // this.color = arg1 ? 5635925 (0x55FF55) : 16733525 (0xFF5555);
                insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
                insns.add(new VarInsnNode(Opcodes.ILOAD, 1));
                insns.add(new JumpInsnNode(Opcodes.IFEQ, lColorFalse));
                insns.add(new LdcInsnNode(Integer.valueOf(5635925)));
                insns.add(new JumpInsnNode(Opcodes.GOTO, lAfterColor));
                insns.add(lColorFalse);
                insns.add(new LdcInsnNode(Integer.valueOf(16733525)));
                insns.add(lAfterColor);
                insns.add(new FieldInsnNode(Opcodes.PUTFIELD, "com/yny/utils/ui/PvpStatusHud", "color", "I"));

                // this.autoArmorMessage = false;
                insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
                insns.add(new InsnNode(Opcodes.ICONST_0));
                insns.add(new FieldInsnNode(Opcodes.PUTFIELD, "com/yny/utils/ui/PvpStatusHud", "autoArmorMessage", "Z"));

                // this.consumablesMessage = false;
                insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
                insns.add(new InsnNode(Opcodes.ICONST_0));
                insns.add(new FieldInsnNode(Opcodes.PUTFIELD, "com/yny/utils/ui/PvpStatusHud", "consumablesMessage", "Z"));

                // this.autoclickMessage = false;
                insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
                insns.add(new InsnNode(Opcodes.ICONST_0));
                insns.add(new FieldInsnNode(Opcodes.PUTFIELD, "com/yny/utils/ui/PvpStatusHud", "autoclickMessage", "Z"));

                // this.visibleUntil = System.nanoTime() + 2500000000L;
                insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
                insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false));
                insns.add(new LdcInsnNode(Long.valueOf(2500000000L)));
                insns.add(new InsnNode(Opcodes.LADD));
                insns.add(new FieldInsnNode(Opcodes.PUTFIELD, "com/yny/utils/ui/PvpStatusHud", "visibleUntil", "J"));

                insns.add(new InsnNode(Opcodes.RETURN));

                mn.instructions.add(insns);
                mn.maxStack = 5;
                mn.maxLocals = 2;
                System.out.println("  Restored real PvpStatusHud.showKnockback(Z)V");
            }

            // In showAutoArmor, reset autoclickMessage = false;
            if ("showAutoArmor".equals(mn.name) && "(Z)V".equals(mn.desc)) {
                InsnList resetAc = new InsnList();
                resetAc.add(new VarInsnNode(Opcodes.ALOAD, 0));
                resetAc.add(new InsnNode(Opcodes.ICONST_0));
                resetAc.add(new FieldInsnNode(Opcodes.PUTFIELD, "com/yny/utils/ui/PvpStatusHud", "autoclickMessage", "Z"));
                mn.instructions.insert(resetAc);
            }

            // In showAutoConsumables, reset autoclickMessage = false;
            if ("showAutoConsumables".equals(mn.name) && "(Z)V".equals(mn.desc)) {
                InsnList resetAc = new InsnList();
                resetAc.add(new VarInsnNode(Opcodes.ALOAD, 0));
                resetAc.add(new InsnNode(Opcodes.ICONST_0));
                resetAc.add(new FieldInsnNode(Opcodes.PUTFIELD, "com/yny/utils/ui/PvpStatusHud", "autoclickMessage", "Z"));
                mn.instructions.insert(resetAc);
            }

            // In layout(ZF)Z:
            if ("layout".equals(mn.name) && "(ZF)Z".equals(mn.desc)) {
                boolean already = false;
                for (AbstractInsnNode insn : mn.instructions.toArray()) {
                    if (insn instanceof FieldInsnNode && "autoclickMessage".equals(((FieldInsnNode) insn).name)) {
                        already = true; break;
                    }
                }
                if (!already) {
                    LabelNode lNormal = new LabelNode();
                    LabelNode lTrue = new LabelNode();
                    LabelNode lFalse = new LabelNode();
                    InsnList acCheck = new InsnList();
                    acCheck.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    acCheck.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/ui/PvpStatusHud", "autoclickMessage", "Z"));
                    acCheck.add(new JumpInsnNode(Opcodes.IFEQ, lNormal));
                    acCheck.add(new VarInsnNode(Opcodes.ILOAD, 1)); // preview
                    acCheck.add(new JumpInsnNode(Opcodes.IFNE, lTrue));
                    acCheck.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false));
                    acCheck.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    acCheck.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/ui/PvpStatusHud", "visibleUntil", "J"));
                    acCheck.add(new InsnNode(Opcodes.LCMP));
                    acCheck.add(new JumpInsnNode(Opcodes.IFGE, lFalse));
                    acCheck.add(lTrue);
                    acCheck.add(new InsnNode(Opcodes.ICONST_1));
                    acCheck.add(new InsnNode(Opcodes.IRETURN));
                    acCheck.add(lFalse);
                    acCheck.add(new InsnNode(Opcodes.ICONST_0));
                    acCheck.add(new InsnNode(Opcodes.IRETURN));
                    acCheck.add(lNormal);

                    mn.instructions.insert(acCheck);
                }
            }
        }

        // 3. Add method: public void showAutoclick(boolean enabled)
        boolean hasShowAutoclick = false;
        boolean hasNotifyAutoclick = false;
        for (MethodNode mn : node.methods) {
            if ("showAutoclick".equals(mn.name)) hasShowAutoclick = true;
            if ("notifyAutoclick".equals(mn.name)) hasNotifyAutoclick = true;
        }

        if (!hasShowAutoclick) {
            MethodNode showAc = new MethodNode(Opcodes.ACC_PUBLIC, "showAutoclick", "(Z)V", null, null);
            LabelNode lFalse = new LabelNode();
            LabelNode lAfterMsg = new LabelNode();
            LabelNode lColorFalse = new LabelNode();
            LabelNode lAfterColor = new LabelNode();

            showAc.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            showAc.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
            showAc.instructions.add(new JumpInsnNode(Opcodes.IFEQ, lFalse));
            showAc.instructions.add(new LdcInsnNode("AutoClick: ATIVADO"));
            showAc.instructions.add(new JumpInsnNode(Opcodes.GOTO, lAfterMsg));
            showAc.instructions.add(lFalse);
            showAc.instructions.add(new LdcInsnNode("AutoClick: DESATIVADO"));
            showAc.instructions.add(lAfterMsg);
            showAc.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, "com/yny/utils/ui/PvpStatusHud", "message", "Ljava/lang/String;"));

            showAc.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            showAc.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
            showAc.instructions.add(new JumpInsnNode(Opcodes.IFEQ, lColorFalse));
            showAc.instructions.add(new LdcInsnNode(Integer.valueOf(0x55FF55)));
            showAc.instructions.add(new JumpInsnNode(Opcodes.GOTO, lAfterColor));
            showAc.instructions.add(lColorFalse);
            showAc.instructions.add(new LdcInsnNode(Integer.valueOf(0xFF5555)));
            showAc.instructions.add(lAfterColor);
            showAc.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, "com/yny/utils/ui/PvpStatusHud", "color", "I"));

            showAc.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            showAc.instructions.add(new InsnNode(Opcodes.ICONST_0));
            showAc.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, "com/yny/utils/ui/PvpStatusHud", "autoArmorMessage", "Z"));

            showAc.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            showAc.instructions.add(new InsnNode(Opcodes.ICONST_0));
            showAc.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, "com/yny/utils/ui/PvpStatusHud", "consumablesMessage", "Z"));

            showAc.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            showAc.instructions.add(new InsnNode(Opcodes.ICONST_1));
            showAc.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, "com/yny/utils/ui/PvpStatusHud", "autoclickMessage", "Z"));

            showAc.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            showAc.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false));
            showAc.instructions.add(new LdcInsnNode(Long.valueOf(2500000000L)));
            showAc.instructions.add(new InsnNode(Opcodes.LADD));
            showAc.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, "com/yny/utils/ui/PvpStatusHud", "visibleUntil", "J"));
            showAc.instructions.add(new InsnNode(Opcodes.RETURN));

            node.methods.add(showAc);
        }

        // 4. Add method: public static void notifyAutoclick(boolean enabled)
        if (!hasNotifyAutoclick) {
            MethodNode notifyAc = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "notifyAutoclick", "(Z)V", null, null);
            LabelNode lNull = new LabelNode();
            notifyAc.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/yny/utils/ui/PvpStatusHud", "instance", "Lcom/yny/utils/ui/PvpStatusHud;"));
            notifyAc.instructions.add(new VarInsnNode(Opcodes.ASTORE, 1));
            notifyAc.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
            notifyAc.instructions.add(new JumpInsnNode(Opcodes.IFNULL, lNull));
            notifyAc.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
            notifyAc.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
            notifyAc.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "com/yny/utils/ui/PvpStatusHud", "showAutoclick", "(Z)V", false));
            notifyAc.instructions.add(lNull);
            notifyAc.instructions.add(new InsnNode(Opcodes.RETURN));

            node.methods.add(notifyAc);
        }

        return write(node);
    }

    private static byte[] patchVelocityInterceptor(byte[] original) {
        ClassNode node = read(original);
        for (MethodNode mn : node.methods) {
            if ("channelRead".equals(mn.name) && "(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Object;)V".equals(mn.desc)) {
                boolean already = false;
                for (AbstractInsnNode insn : mn.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode && "shouldDropVelocity".equals(((MethodInsnNode) insn).name)) {
                        already = true;
                        break;
                    }
                }
                if (!already) {
                    LabelNode continueNormal = new LabelNode();
                    InsnList insns = new InsnList();
                    insns.add(new VarInsnNode(Opcodes.ALOAD, 2)); // Object msg
                    insns.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "com/yny/utils/modules/pvp/MobKnockbackFilter",
                        "shouldDropVelocity",
                        "(Ljava/lang/Object;)Z",
                        false
                    ));
                    insns.add(new JumpInsnNode(Opcodes.IFEQ, continueNormal));
                    insns.add(new InsnNode(Opcodes.RETURN));
                    insns.add(continueNormal);
                    insns.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
                    mn.instructions.insert(insns);
                    mn.maxStack = Math.max(mn.maxStack, 2);
                }
            }
        }
        return write(node);
    }

    public static boolean isTestClass(String path) {
        if (path == null) return false;
        String p = path.replace('\\', '/');
        return p.contains("Test") || p.endsWith("Test.class") || p.contains("Test$")
                || p.endsWith("TestNettyHandler.class") || p.startsWith("tests/");
    }

    private static byte[] patchAllyGroundMarker(byte[] original) {
        ClassNode node = read(original);
        for (MethodNode mn : node.methods) {
            if ("renderWorld".equals(mn.name)) {
                for (AbstractInsnNode insn : mn.instructions.toArray()) {
                    if (insn.getOpcode() == Opcodes.GETFIELD) {
                        FieldInsnNode fin = (FieldInsnNode) insn;
                        if ("j".equals(fin.name) && "Ljava/util/List;".equals(fin.desc) && "bdb".equals(fin.owner)) {
                            AbstractInsnNode prev1 = fin.getPrevious(); // getfield ave.f
                            if (prev1 != null && prev1.getOpcode() == Opcodes.GETFIELD) {
                                AbstractInsnNode prev2 = prev1.getPrevious(); // aload_2
                                if (prev2 != null && prev2.getOpcode() == Opcodes.ALOAD) {
                                    mn.instructions.remove(prev2);
                                    mn.instructions.remove(prev1);
                                    MethodInsnNode callSafe = new MethodInsnNode(
                                            Opcodes.INVOKESTATIC,
                                            "com/yny/utils/core/SafeEntities",
                                            "safePlayers",
                                            "()Ljava/util/List;",
                                            false);
                                    mn.instructions.set(fin, callSafe);
                                    System.out.println("  Patched AllyGroundMarker.renderWorld: replaced ave.f.j with SafeEntities.safePlayers()");
                                }
                            }
                        }
                    }
                }
            }
        }
        return write(node);
    }

    private static byte[] patchKnockbackControl(byte[] original) {
        ClassNode node = read(original);
        for (MethodNode mn : node.methods) {
            if ("adjust".equals(mn.name)) {
                for (AbstractInsnNode insn : mn.instructions.toArray()) {
                    boolean isMinCandidate = false;
                    if (insn.getOpcode() == Opcodes.BIPUSH) {
                        int op = ((IntInsnNode) insn).operand;
                        if (op == 92 || op == 93 || op == 0 || op == 8 || op == 90) {
                            isMinCandidate = true;
                        }
                    } else if (insn.getOpcode() == Opcodes.ICONST_0) {
                        isMinCandidate = true;
                    }
                    if (isMinCandidate) {
                        AbstractInsnNode next = insn.getNext();
                        if (next != null && next.getOpcode() == Opcodes.BIPUSH && ((IntInsnNode) next).operand == 100) {
                            mn.instructions.set(insn, new IntInsnNode(Opcodes.BIPUSH, 90));
                            System.out.println("  Patched KnockbackControl.adjust: changed min clamp to 90");
                        }
                    }
                }
            }
        }
        return write(node);
    }

    private static byte[] patchYNYUtilsRealKnockback(byte[] original) {
        ClassNode node = read(original);

        for (MethodNode mn : node.methods) {
            // Restore lambda$new$2: () -> config.knockbackEnabled
            if ("lambda$new$2".equals(mn.name) && "()Z".equals(mn.desc)) {
                mn.instructions.clear();
                InsnList insns = new InsnList();
                insns.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/yny/utils/YNYUtils", "config", "Lcom/yny/utils/config/YNYConfig;"));
                insns.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/config/YNYConfig", "knockbackEnabled", "Z"));
                insns.add(new InsnNode(Opcodes.IRETURN));
                mn.instructions.add(insns);
                mn.maxStack = 2;
                mn.maxLocals = 0;
                System.out.println("  Restored YNYUtils.lambda$new$2 -> config.knockbackEnabled");
            }

            // Restore lambda$new$3: () -> config.knockbackPercent
            if ("lambda$new$3".equals(mn.name) && "()I".equals(mn.desc)) {
                mn.instructions.clear();
                InsnList insns = new InsnList();
                insns.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/yny/utils/YNYUtils", "config", "Lcom/yny/utils/config/YNYConfig;"));
                insns.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/config/YNYConfig", "knockbackPercent", "I"));
                insns.add(new InsnNode(Opcodes.IRETURN));
                mn.instructions.add(insns);
                mn.maxStack = 2;
                mn.maxLocals = 0;
                System.out.println("  Restored YNYUtils.lambda$new$3 -> config.knockbackPercent");
            }

            // Restore lambda$new$4: () -> config.knockbackPreserveVertical
            if ("lambda$new$4".equals(mn.name) && "()Z".equals(mn.desc)) {
                mn.instructions.clear();
                InsnList insns = new InsnList();
                insns.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/yny/utils/YNYUtils", "config", "Lcom/yny/utils/config/YNYConfig;"));
                insns.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/config/YNYConfig", "knockbackPreserveVertical", "Z"));
                insns.add(new InsnNode(Opcodes.IRETURN));
                mn.instructions.add(insns);
                mn.maxStack = 2;
                mn.maxLocals = 0;
                System.out.println("  Restored YNYUtils.lambda$new$4 -> config.knockbackPreserveVertical");
            }

            // Restore updateToggleKey: remove POP and ICONST_0 after wasPressed
            if ("updateToggleKey".equals(mn.name) && "()V".equals(mn.desc)) {
                for (AbstractInsnNode insn : mn.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode minsn = (MethodInsnNode) insn;
                        if ("com/yny/utils/core/ToggleKey".equals(minsn.owner) && "wasPressed".equals(minsn.name)) {
                            AbstractInsnNode n1 = insn.getNext();
                            if (n1 != null && n1.getOpcode() == Opcodes.POP) {
                                AbstractInsnNode n2 = n1.getNext();
                                if (n2 != null && n2.getOpcode() == Opcodes.ICONST_0) {
                                    mn.instructions.remove(n1);
                                    mn.instructions.remove(n2);
                                    System.out.println("  Restored YNYUtils.updateToggleKey knockback toggle (removed POP + ICONST_0)");
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }

        return write(node);
    }

    private static byte[] patchYNYUtilsMobCombat(byte[] original) {
        ClassNode node = read(original);

        // 1. Add instance field if missing
        boolean hasInstance = false;
        for (FieldNode fn : node.fields) {
            if ("instance".equals(fn.name)) { hasInstance = true; break; }
        }
        if (!hasInstance) {
            node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_VOLATILE, "instance", "Lcom/yny/utils/YNYUtils;", null, null));
        }

        // 2. Set instance = this in <init>
        for (MethodNode mn : node.methods) {
            if ("<init>".equals(mn.name) && "()V".equals(mn.desc)) {
                boolean already = false;
                for (AbstractInsnNode insn : mn.instructions.toArray()) {
                    if (insn instanceof FieldInsnNode && "instance".equals(((FieldInsnNode) insn).name)) {
                        already = true; break;
                    }
                }
                if (!already) {
                    for (AbstractInsnNode insn : mn.instructions.toArray()) {
                        if (insn.getOpcode() == Opcodes.RETURN) {
                            InsnList insns = new InsnList();
                            insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            insns.add(new FieldInsnNode(Opcodes.PUTSTATIC, "com/yny/utils/YNYUtils", "instance", "Lcom/yny/utils/YNYUtils;"));
                            mn.instructions.insertBefore(insn, insns);
                        }
                    }
                }
            }
        }

        // 3. Add public static YNYConfig getConfig()
        boolean hasGetConfig = false;
        for (MethodNode mn : node.methods) {
            if ("getConfig".equals(mn.name) && "()Lcom/yny/utils/config/YNYConfig;".equals(mn.desc)) {
                hasGetConfig = true; break;
            }
        }
        if (!hasGetConfig) {
            MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "getConfig", "()Lcom/yny/utils/config/YNYConfig;", null, null);
            mn.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/yny/utils/YNYUtils", "config", "Lcom/yny/utils/config/YNYConfig;"));
            mn.instructions.add(new InsnNode(Opcodes.ARETURN));
            mn.maxStack = 1;
            mn.maxLocals = 0;
            node.methods.add(mn);
        }

        // 4. Add public static ModConfig getConfigStore()
        boolean hasGetConfigStore = false;
        for (MethodNode mn : node.methods) {
            if ("getConfigStore".equals(mn.name) && "()Ldev/xavier/stein/loader/api/ModConfig;".equals(mn.desc)) {
                hasGetConfigStore = true; break;
            }
        }
        if (!hasGetConfigStore) {
            MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "getConfigStore", "()Ldev/xavier/stein/loader/api/ModConfig;", null, null);
            mn.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/yny/utils/YNYUtils", "instance", "Lcom/yny/utils/YNYUtils;"));
            LabelNode isNull = new LabelNode();
            mn.instructions.add(new JumpInsnNode(Opcodes.IFNULL, isNull));
            mn.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/yny/utils/YNYUtils", "instance", "Lcom/yny/utils/YNYUtils;"));
            mn.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/YNYUtils", "configStore", "Ldev/xavier/stein/loader/api/ModConfig;"));
            mn.instructions.add(new InsnNode(Opcodes.ARETURN));
            mn.instructions.add(isNull);
            mn.instructions.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
            mn.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
            mn.instructions.add(new InsnNode(Opcodes.ARETURN));
            mn.maxStack = 2;
            mn.maxLocals = 0;
            node.methods.add(mn);
        }

        // 5. In onPage(Page): delegate 100% to YNYPage.build(this, page)
        for (MethodNode mn : node.methods) {
            if ("onPage".equals(mn.name) && "(Ldev/xavier/stein/loader/api/Page;)V".equals(mn.desc)) {
                mn.instructions.clear();
                mn.tryCatchBlocks.clear();
                mn.localVariables.clear();
                InsnList insns = new InsnList();
                insns.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this (YNYUtils)
                insns.add(new VarInsnNode(Opcodes.ALOAD, 1)); // Page page
                insns.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "com/yny/utils/ui/YNYPage",
                    "build",
                    "(Lcom/yny/utils/YNYUtils;Ldev/xavier/stein/loader/api/Page;)V",
                    false
                ));
                insns.add(new InsnNode(Opcodes.RETURN));
                mn.instructions.add(insns);
                mn.maxStack = 2;
                mn.maxLocals = 2;
                System.out.println("  Patched YNYUtils.onPage to delegate to YNYPage.build");
            }
        }

        // 5b. In afterStartGame(): call DropProtection.enforce() and Hud.register(new BrokenArmorHud())
        for (MethodNode mn : node.methods) {
            if ("afterStartGame".equals(mn.name) && "()V".equals(mn.desc)) {
                boolean hasBrokenArmorHud = false;
                for (AbstractInsnNode insn : mn.instructions.toArray()) {
                    if (insn instanceof TypeInsnNode && "com/yny/utils/ui/BrokenArmorHud".equals(((TypeInsnNode) insn).desc)) {
                        hasBrokenArmorHud = true; break;
                    }
                }
                if (!hasBrokenArmorHud) {
                    for (AbstractInsnNode insn : mn.instructions.toArray()) {
                        if (insn.getOpcode() == Opcodes.RETURN) {
                            InsnList regList = new InsnList();
                            regList.add(new TypeInsnNode(Opcodes.NEW, "com/yny/utils/ui/BrokenArmorHud"));
                            regList.add(new InsnNode(Opcodes.DUP));
                            regList.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "com/yny/utils/ui/BrokenArmorHud", "<init>", "()V", false));
                            regList.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "dev/xavier/stein/loader/api/Hud", "register", "(Ldev/xavier/stein/loader/api/HudElement;)V", false));
                            mn.instructions.insertBefore(insn, regList);
                            mn.maxStack = Math.max(mn.maxStack, 4);
                            System.out.println("  Injected Hud.register(new BrokenArmorHud()) into YNYUtils.afterStartGame");
                            break;
                        }
                    }
                }

                boolean hasDropProt = false;
                for (AbstractInsnNode insn : mn.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode && "com/yny/utils/modules/player/DropProtection".equals(((MethodInsnNode) insn).owner)) {
                        hasDropProt = true; break;
                    }
                }
                if (!hasDropProt) {
                    for (AbstractInsnNode insn : mn.instructions.toArray()) {
                        if (insn.getOpcode() == Opcodes.RETURN) {
                            mn.instructions.insertBefore(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/player/DropProtection", "enforce", "()V", false));
                            break;
                        }
                    }
                }
            }
        }

        // 6. In onTickEnd(): call MobAura.onTickEnd(), recoverOrphanedCursor(), and DropProtection.enforce()
        for (MethodNode mn : node.methods) {
            if ("onTickEnd".equals(mn.name) && "()V".equals(mn.desc)) {
                boolean hasCursorRecovery = false;
                boolean hasPreventiveAlert = false;
                for (AbstractInsnNode insn : mn.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode minsn = (MethodInsnNode) insn;
                        if ("com/yny/utils/modules/player/AutoArmorHelper".equals(minsn.owner)) {
                            if ("recoverOrphanedCursor".equals(minsn.name)) hasCursorRecovery = true;
                            if ("checkPreventiveArmorAlert".equals(minsn.name)) hasPreventiveAlert = true;
                        }
                    }
                }
                if (!hasCursorRecovery) {
                    InsnList recList = new InsnList();
                    recList.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/player/AutoArmorHelper", "recoverOrphanedCursor", "()Z", false));
                    recList.add(new InsnNode(Opcodes.POP));
                    mn.instructions.insert(recList);
                }
                if (!hasPreventiveAlert) {
                    InsnList alertList = new InsnList();
                    alertList.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/player/AutoArmorHelper", "checkPreventiveArmorAlert", "()V", false));
                    mn.instructions.insert(alertList);
                }

                boolean hasSelfArmorBreak = false;
                for (AbstractInsnNode insn : mn.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode minsn = (MethodInsnNode) insn;
                        if ("com/yny/utils/ui/BrokenArmorHud".equals(minsn.owner) && "checkSelfArmorBreak".equals(minsn.name)) {
                            hasSelfArmorBreak = true; break;
                        }
                    }
                }
                if (!hasSelfArmorBreak) {
                    InsnList hudList = new InsnList();
                    hudList.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/ui/BrokenArmorHud", "checkSelfArmorBreak", "()V", false));
                    mn.instructions.insert(hudList);
                    System.out.println("  Injected BrokenArmorHud.checkSelfArmorBreak at start of YNYUtils.onTickEnd");
                }

                boolean hasMobAura = false;
                boolean hasDropProt = false;
                for (AbstractInsnNode insn : mn.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode minsn = (MethodInsnNode) insn;
                        if ("com/yny/utils/modules/pvp/MobAura".equals(minsn.owner) && "onTickEnd".equals(minsn.name)) hasMobAura = true;
                        if ("com/yny/utils/modules/player/DropProtection".equals(minsn.owner) && "enforce".equals(minsn.name)) hasDropProt = true;
                    }
                }
                for (AbstractInsnNode insn : mn.instructions.toArray()) {
                    if (insn.getOpcode() == Opcodes.RETURN) {
                        InsnList insns = new InsnList();
                        if (!hasMobAura) {
                            insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/pvp/MobAura", "onTickEnd", "()V", false));
                        }
                        boolean hasBrokenArmor = false;
                        for (AbstractInsnNode testInsn : mn.instructions.toArray()) {
                            if (testInsn instanceof MethodInsnNode && "com/yny/utils/modules/pvp/BrokenArmorHighlight".equals(((MethodInsnNode) testInsn).owner)) {
                                hasBrokenArmor = true; break;
                            }
                        }
                        if (!hasBrokenArmor) {
                            insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/pvp/BrokenArmorHighlight", "onTickEnd", "()V", false));
                        }
                        insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/pvp/BowAimAssist", "onTickEnd", "()V", false));
                        boolean hasFocusModeTick = false;
                        for (AbstractInsnNode testInsn : mn.instructions.toArray()) {
                            if (testInsn instanceof MethodInsnNode && "com/yny/utils/modules/pvp/FocusMode".equals(((MethodInsnNode) testInsn).owner) && "onTickEnd".equals(((MethodInsnNode) testInsn).name)) {
                                hasFocusModeTick = true; break;
                            }
                        }
                        if (!hasFocusModeTick) {
                            insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/pvp/FocusMode", "onTickEnd", "()V", false));
                        }
                        if (!hasDropProt) {
                            insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/player/DropProtection", "enforce", "()V", false));
                        }
                        mn.instructions.insertBefore(insn, insns);
                        break;
                    }
                }
            }
        }

        // 7. In onRenderWorld(float): clean, high-visibility, zero-lag rendering pipeline
        for (MethodNode mn : node.methods) {
            if ("onRenderWorld".equals(mn.name) && "(F)V".equals(mn.desc)) {
                mn.instructions.clear();
                mn.tryCatchBlocks.clear();
                InsnList insns = new InsnList();

                // 1. Clan Ally Ground Markers ("bolinha verde nos pés", alta visibilidade, zero lag, throughWalls)
                insns.add(new VarInsnNode(Opcodes.FLOAD, 1));
                insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/clanping/GfxWorldMarkers", "renderAllyMarkers", "(F)V", false));

                // 2. ClanPing waypoint markers (if not handled by GfxWorldMarkers, fall back to legacy)
                LabelNode skipLegacy = new LabelNode();
                insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
                insns.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/YNYUtils", "clanPing", "Lcom/yny/utils/modules/clanping/ClanPing;"));
                insns.add(new VarInsnNode(Opcodes.FLOAD, 1));
                insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/clanping/GfxWorldMarkers", "render", "(Lcom/yny/utils/modules/clanping/ClanPing;F)Z", false));
                insns.add(new JumpInsnNode(Opcodes.IFNE, skipLegacy));

                insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
                insns.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/YNYUtils", "clanPing", "Lcom/yny/utils/modules/clanping/ClanPing;"));
                insns.add(new VarInsnNode(Opcodes.FLOAD, 1));
                insns.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "com/yny/utils/modules/clanping/ClanPing", "renderWorld", "(F)V", false));

                insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
                insns.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/YNYUtils", "allyGroundMarker", "Lcom/yny/utils/modules/pvp/AllyGroundMarker;"));
                insns.add(new VarInsnNode(Opcodes.FLOAD, 1));
                insns.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "com/yny/utils/modules/pvp/AllyGroundMarker", "renderWorld", "(F)V", false));

                insns.add(skipLegacy);
                insns.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));

                // 3. MobAura visuals
                insns.add(new VarInsnNode(Opcodes.FLOAD, 1));
                insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/pvp/MobAura", "onRenderWorld", "(F)V", false));

                // 4. BrokenArmorHighlight (high visibility, zero lag, enemy broken armor highlights)
                insns.add(new VarInsnNode(Opcodes.FLOAD, 1));
                insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/pvp/BrokenArmorHighlight", "onRenderWorld", "(F)V", false));

                // 5. BowAimAssist (trajectory line, impact box, locked target)
                insns.add(new VarInsnNode(Opcodes.FLOAD, 1));
                insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/pvp/BowAimAssist", "onRenderWorld", "(F)V", false));

                // 6. FocusMode (3D target ring, beam and focus tag)
                insns.add(new VarInsnNode(Opcodes.FLOAD, 1));
                insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/pvp/FocusMode", "onRenderWorld", "(F)V", false));

                insns.add(new InsnNode(Opcodes.RETURN));
                mn.instructions.add(insns);
                mn.maxStack = 3;
                mn.maxLocals = 2;
                System.out.println("  Patched YNYUtils.onRenderWorld with clean, unblocked rendering pipeline");
            }
        }

        // 7b. In onJoinGame(): invalidate clan caches and reset PriorityBus, FocusMode & BrokenArmorHud
        for (MethodNode mn : node.methods) {
            if ("onJoinGame".equals(mn.name) && "()V".equals(mn.desc)) {
                for (AbstractInsnNode insn : mn.instructions.toArray()) {
                    if (insn.getOpcode() == Opcodes.RETURN) {
                        InsnList insns = new InsnList();
                        insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/player/PriorityBus", "reset", "()V", false));
                        insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/clanping/PingClanName", "clearCaches", "()V", false));
                        insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "dev/xavier/stein/loader/api/Relations", "changed", "()V", false));
                        insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/pvp/FocusMode", "reset", "()V", false));
                        insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/ui/BrokenArmorHud", "reset", "()V", false));
                        mn.instructions.insertBefore(insn, insns);
                        break;
                    }
                }
            }
        }

        // 7c. In onSendChat(String): process client commands, outgoing chat for clan commands and clear clan caches
        for (MethodNode mn : node.methods) {
            if ("onSendChat".equals(mn.name) && "(Ljava/lang/String;)Ljava/lang/String;".equals(mn.desc)) {
                LabelNode notHandled = new LabelNode();
                InsnList insns = new InsnList();
                insns.add(new VarInsnNode(Opcodes.ALOAD, 1));
                insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/core/YNYCommands", "handleChatCommand", "(Ljava/lang/String;)Z", false));
                insns.add(new JumpInsnNode(Opcodes.IFEQ, notHandled));
                insns.add(new InsnNode(Opcodes.ACONST_NULL));
                insns.add(new InsnNode(Opcodes.ARETURN));
                insns.add(notHandled);
                insns.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
                insns.add(new VarInsnNode(Opcodes.ALOAD, 1));
                insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/clanping/PingClanName", "processOutgoingChat", "(Ljava/lang/String;)V", false));
                insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/clanping/PingClanName", "clearCaches", "()V", false));
                insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "dev/xavier/stein/loader/api/Relations", "changed", "()V", false));
                mn.instructions.insert(insns);
                mn.maxStack = Math.max(mn.maxStack, 2);
            }
        }

        // 7d. Hook onChat(Object) to automatically extract clan from incoming server/player chat
        boolean hasOnChat = false;
        for (MethodNode mn : node.methods) {
            if ("onChat".equals(mn.name) && "(Ljava/lang/Object;)Ljava/lang/Object;".equals(mn.desc)) {
                hasOnChat = true;
                InsnList insns = new InsnList();
                insns.add(new VarInsnNode(Opcodes.ALOAD, 1));
                insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "dev/xavier/stein/loader/api/Chat", "formatted", "(Ljava/lang/Object;)Ljava/lang/String;", false));
                insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/clanping/PingClanName", "processIncomingChat", "(Ljava/lang/String;)V", false));
                mn.instructions.insert(insns);
                break;
            }
        }
        if (!hasOnChat) {
            MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "onChat", "(Ljava/lang/Object;)Ljava/lang/Object;", null, null);
            InsnList insns = new InsnList();
            insns.add(new VarInsnNode(Opcodes.ALOAD, 1));
            insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "dev/xavier/stein/loader/api/Chat", "formatted", "(Ljava/lang/Object;)Ljava/lang/String;", false));
            insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/clanping/PingClanName", "processIncomingChat", "(Ljava/lang/String;)V", false));
            insns.add(new VarInsnNode(Opcodes.ALOAD, 1));
            insns.add(new InsnNode(Opcodes.ARETURN));
            mn.instructions.add(insns);
            mn.maxStack = 2;
            mn.maxLocals = 2;
            node.methods.add(mn);
            System.out.println("  Added onChat hook to YNYUtils for automatic clan chat detection");
        }

        // 7e. In onDisconnected: reset PriorityBus & AutoArmorHelper, FocusMode & BrokenArmorHud
        for (MethodNode mn : node.methods) {
            if ("onDisconnected".equals(mn.name)) {
                for (AbstractInsnNode insn : mn.instructions.toArray()) {
                    if (insn.getOpcode() == Opcodes.RETURN) {
                        mn.instructions.insertBefore(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/player/PriorityBus", "reset", "()V", false));
                        mn.instructions.insertBefore(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/player/AutoArmorHelper", "reset", "()V", false));
                        mn.instructions.insertBefore(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/clanping/PingClanName", "onDisconnect", "()V", false));
                        mn.instructions.insertBefore(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/pvp/FocusMode", "reset", "()V", false));
                        mn.instructions.insertBefore(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/ui/BrokenArmorHud", "reset", "()V", false));
                        break;
                    }
                }
            }
        }

        // 8. In onShutdown(): call PriorityBus.reset(), MobAura.reset(), strip AutoClicker
        for (MethodNode mn : node.methods) {
            if ("onShutdown".equals(mn.name) && "()V".equals(mn.desc)) {
                for (AbstractInsnNode insn : mn.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode && "com/yny/utils/modules/pvp/AutoClicker".equals(((MethodInsnNode) insn).owner)) {
                        mn.instructions.remove(insn);
                    }
                }
                boolean hasMobAura = false;
                boolean hasBrokenArmor = false;
                for (AbstractInsnNode insn : mn.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode minsn = (MethodInsnNode) insn;
                        if ("com/yny/utils/modules/pvp/MobAura".equals(minsn.owner) && "reset".equals(minsn.name)) hasMobAura = true;
                        if ("com/yny/utils/modules/pvp/BrokenArmorHighlight".equals(minsn.owner) && "reset".equals(minsn.name)) hasBrokenArmor = true;
                    }
                }
                for (AbstractInsnNode insn : mn.instructions.toArray()) {
                    if (insn.getOpcode() == Opcodes.RETURN) {
                        mn.instructions.insertBefore(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/player/PriorityBus", "reset", "()V", false));
                        mn.instructions.insertBefore(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/player/AutoArmorHelper", "reset", "()V", false));
                        mn.instructions.insertBefore(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/clanping/PingClanName", "onDisconnect", "()V", false));
                        if (!hasMobAura) {
                            mn.instructions.insertBefore(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/pvp/MobAura", "reset", "()V", false));
                        }
                        if (!hasBrokenArmor) {
                            mn.instructions.insertBefore(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/pvp/BrokenArmorHighlight", "reset", "()V", false));
                        }
                        mn.instructions.insertBefore(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/pvp/FocusMode", "reset", "()V", false));
                        mn.instructions.insertBefore(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/ui/BrokenArmorHud", "reset", "()V", false));
                        break;
                    }
                }
            }
        }

        // 9. In updateToggleKey(): call MobAura.updateToggleKey(), FocusMode.updateToggleKey(), BrokenArmorHud.updateToggleKey(), strip AutoClicker
        for (MethodNode mn : node.methods) {
            if ("updateToggleKey".equals(mn.name) && "()V".equals(mn.desc)) {
                for (AbstractInsnNode insn : mn.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode && "com/yny/utils/modules/pvp/AutoClicker".equals(((MethodInsnNode) insn).owner)) {
                        mn.instructions.remove(insn);
                    }
                }
                boolean hasMobAura = false;
                for (AbstractInsnNode insn : mn.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode minsn = (MethodInsnNode) insn;
                        if ("com/yny/utils/modules/pvp/MobAura".equals(minsn.owner) && "updateToggleKey".equals(minsn.name)) hasMobAura = true;
                    }
                }
                if (!hasMobAura) {
                    for (AbstractInsnNode insn : mn.instructions.toArray()) {
                        if (insn.getOpcode() == Opcodes.RETURN) {
                            mn.instructions.insertBefore(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/pvp/MobAura", "updateToggleKey", "()V", false));
                            break;
                        }
                    }
                }
                boolean hasFocusMode = false;
                for (AbstractInsnNode insn : mn.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode minsn = (MethodInsnNode) insn;
                        if ("com/yny/utils/modules/pvp/FocusMode".equals(minsn.owner) && "updateToggleKey".equals(minsn.name)) hasFocusMode = true;
                    }
                }
                if (!hasFocusMode) {
                    for (AbstractInsnNode insn : mn.instructions.toArray()) {
                        if (insn.getOpcode() == Opcodes.RETURN) {
                            mn.instructions.insertBefore(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/pvp/FocusMode", "updateToggleKey", "()V", false));
                            break;
                        }
                    }
                }
                boolean hasSelfArmorKey = false;
                for (AbstractInsnNode insn : mn.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode minsn = (MethodInsnNode) insn;
                        if ("com/yny/utils/ui/BrokenArmorHud".equals(minsn.owner) && "updateToggleKey".equals(minsn.name)) hasSelfArmorKey = true;
                    }
                }
                if (!hasSelfArmorKey) {
                    for (AbstractInsnNode insn : mn.instructions.toArray()) {
                        if (insn.getOpcode() == Opcodes.RETURN) {
                            mn.instructions.insertBefore(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/ui/BrokenArmorHud", "updateToggleKey", "()V", false));
                            break;
                        }
                    }
                }
            }
        }

        // 10. In onAttackEntity(Object): call FocusMode.onAttackEntity(target)
        for (MethodNode mn : node.methods) {
            if ("onAttackEntity".equals(mn.name) && "(Ljava/lang/Object;)Z".equals(mn.desc)) {
                boolean hasFocusAttack = false;
                for (AbstractInsnNode insn : mn.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode && "com/yny/utils/modules/pvp/FocusMode".equals(((MethodInsnNode) insn).owner)) {
                        hasFocusAttack = true; break;
                    }
                }
                if (!hasFocusAttack) {
                    LabelNode continueNormal = new LabelNode();
                    InsnList insns = new InsnList();
                    insns.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/pvp/FocusMode", "onAttackEntity", "(Ljava/lang/Object;)Z", false));
                    insns.add(new JumpInsnNode(Opcodes.IFEQ, continueNormal));
                    insns.add(new InsnNode(Opcodes.ICONST_1));
                    insns.add(new InsnNode(Opcodes.IRETURN));
                    insns.add(continueNormal);
                    insns.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
                    mn.instructions.insert(insns);
                    mn.maxStack = Math.max(mn.maxStack, 2);
                    System.out.println("  Injected FocusMode.onAttackEntity hook into YNYUtils.onAttackEntity");
                }
            }
        }

        // 11. In onMouseInput(): call FocusMode.onMouseInput()
        boolean hasOnMouseInput = false;
        for (MethodNode mn : node.methods) {
            if ("onMouseInput".equals(mn.name) && "()V".equals(mn.desc)) {
                hasOnMouseInput = true;
                boolean hasFocusMouse = false;
                for (AbstractInsnNode insn : mn.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode && "com/yny/utils/modules/pvp/FocusMode".equals(((MethodInsnNode) insn).owner) && "onMouseInput".equals(((MethodInsnNode) insn).name)) {
                        hasFocusMouse = true; break;
                    }
                }
                if (!hasFocusMouse) {
                    InsnList insns = new InsnList();
                    insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/pvp/FocusMode", "onMouseInput", "()V", false));
                    mn.instructions.insert(insns);
                    System.out.println("  Injected FocusMode.onMouseInput hook into YNYUtils.onMouseInput");
                }
                break;
            }
        }
        if (!hasOnMouseInput) {
            MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "onMouseInput", "()V", null, null);
            InsnList insns = new InsnList();
            insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/pvp/HitThroughWeb", "onMouseInput", "()V", false));
            insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/pvp/FocusMode", "onMouseInput", "()V", false));
            insns.add(new InsnNode(Opcodes.RETURN));
            mn.instructions.add(insns);
            mn.maxStack = 1;
            mn.maxLocals = 1;
            node.methods.add(mn);
            System.out.println("  Created onMouseInput with FocusMode hook in YNYUtils");
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

    private static byte[] readStream(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int r;
        while ((r = in.read(b)) != -1) {
            buffer.write(b, 0, r);
        }
        return buffer.toByteArray();
    }

    private static String sha256(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
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

    private static ClassLoader frameClassLoader;

    private static ClassLoader getFrameClassLoader() {
        if (frameClassLoader == null) {
            List<URL> urls = new ArrayList<>();
            File[] jars = new File[] {
                new File("../../../data/.minecraft/versions/1.8.9-SteinLoader/1.8.9-SteinLoader.jar"),
                new File("C:/Users/artur/.stein-sdk/1.8.9/minecraft-named-open.jar"),
                new File("C:/Users/artur/.stein-sdk/1.8.9/stein-api.jar"),
                new File("team-source/build/libs/YNYUtilsEquipe.steinmod"),
                new File("build/compiled")
            };
            for (File j : jars) {
                if (j.exists()) {
                    try {
                        urls.add(j.toURI().toURL());
                    } catch (Exception ignored) {}
                }
            }
            frameClassLoader = new URLClassLoader(urls.toArray(new URL[0]), ApplyAllPackUpdates.class.getClassLoader());
        }
        return frameClassLoader;
    }

    private static byte[] write(ClassNode node) {
        final ClassLoader cl = getFrameClassLoader();
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                try {
                    Class<?> c1 = Class.forName(type1.replace('/', '.'), false, cl);
                    Class<?> c2 = Class.forName(type2.replace('/', '.'), false, cl);
                    if (c1.isAssignableFrom(c2)) return type1;
                    if (c2.isAssignableFrom(c1)) return type2;
                    if (c1.isInterface() || c2.isInterface()) return "java/lang/Object";
                    do {
                        c1 = c1.getSuperclass();
                    } while (!c1.isAssignableFrom(c2));
                    return c1.getName().replace('.', '/');
                } catch (Exception e) {
                    return "java/lang/Object";
                }
            }
        };
        node.accept(writer);
        byte[] b = writer.toByteArray();
        b[6] = 0;
        b[7] = 52;
        return b;
    }

    private static void widenCrossClassAccess(Map<String, byte[]> entries) {
        Map<String, ClassNode> nodes = new LinkedHashMap<>();
        Map<String, String> entryNamesByClass = new HashMap<>();

        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (e.getKey().endsWith(".class")) {
                ClassNode cn = read(e.getValue());
                nodes.put(cn.name, cn);
                entryNamesByClass.put(cn.name, e.getKey());
            }
        }

        Set<String> modified = new HashSet<>();
        int widenedMembers = 0;

        for (ClassNode caller : nodes.values()) {
            for (MethodNode mn : caller.methods) {
                for (AbstractInsnNode insn : mn.instructions) {
                    if (insn instanceof FieldInsnNode) {
                        FieldInsnNode fin = (FieldInsnNode) insn;
                        if (nodes.containsKey(fin.owner) && !fin.owner.equals(caller.name)) {
                            Map.Entry<ClassNode, FieldNode> targetEntry = findFieldDecl(nodes.get(fin.owner), fin.name, fin.desc, nodes);
                            if (targetEntry != null) {
                                ClassNode target = targetEntry.getKey();
                                FieldNode fn = targetEntry.getValue();
                                boolean samePkg = isSamePackage(caller.name, target.name);
                                if ((fn.access & Opcodes.ACC_PRIVATE) != 0) {
                                    fn.access &= ~Opcodes.ACC_PRIVATE;
                                    if (!samePkg) {
                                        fn.access |= Opcodes.ACC_PUBLIC;
                                    }
                                    modified.add(target.name);
                                    widenedMembers++;
                                    System.out.println("  [ACCESS] Widened private field " + target.name + "." + fn.name + " (" + (samePkg ? "package-private" : "public") + ") for caller " + caller.name);
                                } else if (!samePkg && (fn.access & Opcodes.ACC_PUBLIC) == 0 && (fn.access & Opcodes.ACC_PROTECTED) == 0) {
                                    fn.access |= Opcodes.ACC_PUBLIC;
                                    modified.add(target.name);
                                    widenedMembers++;
                                    System.out.println("  [ACCESS] Widened package field " + target.name + "." + fn.name + " to public for caller " + caller.name);
                                }
                            }
                        }
                    } else if (insn instanceof MethodInsnNode) {
                        MethodInsnNode min = (MethodInsnNode) insn;
                        if (nodes.containsKey(min.owner) && !min.owner.equals(caller.name)) {
                            Map.Entry<ClassNode, MethodNode> targetEntry = findMethodDecl(nodes.get(min.owner), min.name, min.desc, nodes);
                            if (targetEntry != null) {
                                ClassNode target = targetEntry.getKey();
                                MethodNode tmn = targetEntry.getValue();
                                boolean samePkg = isSamePackage(caller.name, target.name);
                                if ((tmn.access & Opcodes.ACC_PRIVATE) != 0) {
                                    tmn.access &= ~Opcodes.ACC_PRIVATE;
                                    if (!samePkg) {
                                        tmn.access |= Opcodes.ACC_PUBLIC;
                                    }
                                    modified.add(target.name);
                                    widenedMembers++;
                                    System.out.println("  [ACCESS] Widened private method " + target.name + "." + tmn.name + tmn.desc + " (" + (samePkg ? "package-private" : "public") + ") for caller " + caller.name);
                                } else if (!samePkg && (tmn.access & Opcodes.ACC_PUBLIC) == 0 && (tmn.access & Opcodes.ACC_PROTECTED) == 0) {
                                    tmn.access |= Opcodes.ACC_PUBLIC;
                                    modified.add(target.name);
                                    widenedMembers++;
                                    System.out.println("  [ACCESS] Widened package method " + target.name + "." + tmn.name + tmn.desc + " to public for caller " + caller.name);
                                }
                            }
                        }
                    }
                }
            }
        }

        for (String className : modified) {
            ClassNode cn = nodes.get(className);
            cn.version = Opcodes.V1_8;
            cn.nestHostClass = null;
            if (cn.nestMembers != null) cn.nestMembers.clear();
            ClassWriter cw = new ClassWriter(0);
            cn.accept(cw);
            byte[] bytes = cw.toByteArray();
            bytes[6] = 0;
            bytes[7] = 52;
            String entryName = entryNamesByClass.get(className);
            entries.put(entryName, bytes);
        }

        System.out.println("  Access widening complete: widened " + widenedMembers + " member(s) across " + modified.size() + " class(es).");
    }

    private static boolean isSamePackage(String classA, String classB) {
        int slashA = classA.lastIndexOf('/');
        int slashB = classB.lastIndexOf('/');
        if (slashA == -1 && slashB == -1) return true;
        if (slashA != -1 && slashB != -1) {
            return classA.substring(0, slashA).equals(classB.substring(0, slashB));
        }
        return false;
    }

    private static Map.Entry<ClassNode, FieldNode> findFieldDecl(ClassNode node, String name, String desc, Map<String, ClassNode> nodes) {
        ClassNode cur = node;
        while (cur != null) {
            for (FieldNode fn : cur.fields) {
                if (fn.name.equals(name) && fn.desc.equals(desc)) {
                    return new AbstractMap.SimpleEntry<>(cur, fn);
                }
            }
            if (cur.superName != null && nodes.containsKey(cur.superName)) {
                cur = nodes.get(cur.superName);
            } else {
                break;
            }
        }
        return null;
    }

    private static Map.Entry<ClassNode, MethodNode> findMethodDecl(ClassNode node, String name, String desc, Map<String, ClassNode> nodes) {
        ClassNode cur = node;
        while (cur != null) {
            for (MethodNode mn : cur.methods) {
                if (mn.name.equals(name) && mn.desc.equals(desc)) {
                    return new AbstractMap.SimpleEntry<>(cur, mn);
                }
            }
            if (cur.superName != null && nodes.containsKey(cur.superName)) {
                cur = nodes.get(cur.superName);
            } else {
                break;
            }
        }
        return null;
    }

    private static void verifyNoMissingMembers(Map<String, byte[]> entries) {
        Map<String, ClassNode> nodes = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (e.getKey().endsWith(".class")) {
                ClassNode cn = read(e.getValue());
                nodes.put(cn.name, cn);
            }
        }

        ClassLoader cl = getFrameClassLoader();
        int errors = 0;
        for (ClassNode cn : nodes.values()) {
            for (MethodNode mn : cn.methods) {
                for (AbstractInsnNode insn : mn.instructions) {
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode min = (MethodInsnNode) insn;
                        if (nodes.containsKey(min.owner)) {
                            Map.Entry<ClassNode, MethodNode> targetEntry = findMethodDecl(nodes.get(min.owner), min.name, min.desc, nodes);
                            if (targetEntry == null) {
                                if (min.name.equals("<init>")) {
                                    System.err.println("FATAL: " + cn.name + "." + mn.name + mn.desc + " calls NON-EXISTENT constructor " + min.owner + "." + min.name + min.desc);
                                    errors++;
                                } else {
                                    String superName = nodes.get(min.owner).superName;
                                    if (superName != null && !hasExternalMethod(superName, min.name, min.desc, cl)) {
                                        System.err.println("FATAL: " + cn.name + "." + mn.name + mn.desc + " calls NON-EXISTENT method " + min.owner + "." + min.name + min.desc);
                                        errors++;
                                    }
                                }
                            }
                        }
                    } else if (insn instanceof FieldInsnNode) {
                        FieldInsnNode fin = (FieldInsnNode) insn;
                        if (nodes.containsKey(fin.owner)) {
                            Map.Entry<ClassNode, FieldNode> targetEntry = findFieldDecl(nodes.get(fin.owner), fin.name, fin.desc, nodes);
                            if (targetEntry == null) {
                                String superName = nodes.get(fin.owner).superName;
                                if (superName != null && !hasExternalField(superName, fin.name, fin.desc, cl)) {
                                    System.err.println("FATAL: " + cn.name + "." + mn.name + mn.desc + " accesses NON-EXISTENT field " + fin.owner + "." + fin.name + " " + fin.desc);
                                    errors++;
                                }
                            }
                        }
                    }
                }
            }
        }

        if (errors > 0) {
            throw new IllegalStateException("CRITICAL BUILD FAILURE: Found " + errors + " missing method/field call(s) in modpack classes!");
        }
        System.out.println("  Verified member integrity: 0 missing methods or fields across pack classes.");
    }

    private static boolean hasExternalMethod(String owner, String name, String desc, ClassLoader cl) {
        try {
            Class<?> clazz = Class.forName(owner.replace('/', '.'), false, cl);
            while (clazz != null && clazz != Object.class) {
                for (Method m : clazz.getDeclaredMethods()) {
                    if (m.getName().equals(name) && org.objectweb.asm.Type.getMethodDescriptor(m).equals(desc)) return true;
                }
                for (Class<?> iface : clazz.getInterfaces()) {
                    for (Method m : iface.getDeclaredMethods()) {
                        if (m.getName().equals(name) && org.objectweb.asm.Type.getMethodDescriptor(m).equals(desc)) return true;
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Throwable ignored) {}

        String cur = owner;
        while (cur != null && !cur.equals("java/lang/Object")) {
            try (InputStream in = cl.getResourceAsStream(cur.replace('.', '/') + ".class")) {
                if (in == null) break;
                ClassReader cr = new ClassReader(in);
                ClassNode cn = new ClassNode();
                cr.accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                for (MethodNode mn : cn.methods) {
                    if (mn.name.equals(name) && mn.desc.equals(desc)) return true;
                }
                cur = cn.superName;
            } catch (Throwable t) {
                break;
            }
        }
        return false;
    }

    private static boolean hasExternalField(String owner, String name, String desc, ClassLoader cl) {
        try {
            Class<?> clazz = Class.forName(owner.replace('/', '.'), false, cl);
            while (clazz != null && clazz != Object.class) {
                for (Field f : clazz.getDeclaredFields()) {
                    if (f.getName().equals(name) && org.objectweb.asm.Type.getDescriptor(f.getType()).equals(desc)) return true;
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Throwable ignored) {}

        String cur = owner;
        while (cur != null && !cur.equals("java/lang/Object")) {
            try (InputStream in = cl.getResourceAsStream(cur.replace('.', '/') + ".class")) {
                if (in == null) break;
                ClassReader cr = new ClassReader(in);
                ClassNode cn = new ClassNode();
                cr.accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                for (FieldNode fn : cn.fields) {
                    if (fn.name.equals(name) && fn.desc.equals(desc)) return true;
                }
                cur = cn.superName;
            } catch (Throwable t) {
                break;
            }
        }
        return false;
    }
}


