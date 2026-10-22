import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Adds remote delivery hooks to the already-remapped playable mod. */
public final class PatchRemoteLogs {
    private static final String ENTRYPOINT = "com/yny/utils/YNYUtils";
    private static final String COMMAND_CAPTURE = "com/yny/utils/telemetry/OutgoingCommandLog";
    private static final String BRIDGE = "com/yny/utils/telemetry/TelemetryBridge";
    private static final String LOGGER = "com/yny/utils/core/DebugLog";
    private static final String UPLOADER = "com/yny/utils/telemetry/RemoteLogUploader";
    private static final String RETENTION = "com/yny/utils/telemetry/LocalLogRetention";

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("input.steinmod compiled-dir output.steinmod");
        Path input = Paths.get(args[0]);
        Path classes = Paths.get(args[1]);
        Path output = Paths.get(args[2]);
        Set<String> replacements = new HashSet<String>();
        replacements.add("com/yny/utils/modules/player/ArmorPickupFilter.class");
        replacements.add("com/yny/utils/telemetry/TelemetryService.class");
        replacements.add("com/yny/utils/telemetry/TelemetryService$1.class");
        replacements.add("com/yny/utils/telemetry/TelemetryService$2.class");
        replacements.add("com/yny/utils/telemetry/TelemetryBridge.class");
        replacements.add("com/yny/utils/telemetry/ClanIdentityResolver.class");
        replacements.add("com/yny/utils/telemetry/OutgoingCommandLog.class");
        replacements.add("com/yny/utils/telemetry/TelemetryConfig.class");
        replacements.add("com/yny/utils/telemetry/LocalLogRetention.class");
        replacements.add("com/yny/utils/telemetry/RemoteLogUploader.class");
        replacements.add("com/yny/utils/telemetry/RemoteLogUploader$1.class");
        replacements.add("com/yny/utils/telemetry/GameLogTailer.class");
        replacements.add("com/yny/utils/telemetry/GameLogTailer$1.class");
        Set<String> written = new HashSet<String>();
        int patched = 0;
        int entryPatched = 0;
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(input));
             ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(output))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName();
                byte[] bytes = readAll(in);
                if (name.equals(ENTRYPOINT + ".class")) {
                    bytes = patchEntrypoint(bytes);
                    entryPatched++;
                } else if (name.equals(LOGGER + ".class")) {
                    bytes = patch(bytes);
                    patched++;
                } else if (replacements.contains(name)) {
                    bytes = Files.readAllBytes(classes.resolve(name));
                    written.add(name);
                }
                ZipEntry copy = new ZipEntry(name);
                copy.setTime(entry.getTime());
                out.putNextEntry(copy);
                out.write(bytes);
                out.closeEntry();
            }
            for (String name : replacements) {
                if (written.contains(name)) continue;
                ZipEntry fresh = new ZipEntry(name);
                out.putNextEntry(fresh);
                out.write(Files.readAllBytes(classes.resolve(name)));
                out.closeEntry();
            }
        }
        if (patched != 1) throw new IllegalStateException("DebugLog missing or duplicated: " + patched);
        if (entryPatched != 1) throw new IllegalStateException("YNYUtils missing or duplicated: " + entryPatched);
        System.out.println("PASS: remote log hooks and classes added to " + output);
    }

    private static byte[] patchEntrypoint(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int found = 0;
        for (MethodNode method : node.methods) {
            if (method.name.equals("onSendChat") && method.desc.equals("(Ljava/lang/String;)Ljava/lang/String;")) {
                InsnList hook = new InsnList();
                hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, COMMAND_CAPTURE,
                    "record", "(Ljava/lang/String;)V", false));
                method.instructions.insert(hook);
                found++;
            } else if (method.name.equals("onTickEnd") && method.desc.equals("()V")) {
                InsnList hook = new InsnList();
                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "onTickEnd", "()V", false));
                method.instructions.insert(hook);
                found++;
            }
        }
        if (found != 2) throw new IllegalStateException("Expected chat and tick callbacks; found " + found);
        ClassWriter out = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(out);
        return out.toByteArray();
    }
    private static byte[] patch(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int found = 0;
        for (MethodNode method : node.methods) {
            if (method.name.equals("write") && method.desc.equals("(Ljava/lang/String;Ljava/lang/String;)V")) {
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (!(insn instanceof MethodInsnNode)) continue;
                    MethodInsnNode call = (MethodInsnNode) insn;
                    if (call.owner.equals("java/util/concurrent/ArrayBlockingQueue") && call.name.equals("offer")
                            && call.desc.equals("(Ljava/lang/Object;)Z")) {
                        method.instructions.set(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, UPLOADER,
                            "offerLossless", "(Ljava/util/concurrent/ArrayBlockingQueue;Ljava/lang/Object;)Z", false));
                        found++;
                        break;
                    }
                }
                continue;
            }
            if (hasHook(method)) continue;
            if (method.name.equals("initialize") && method.desc.equals("(Ljava/io/File;Z)V") ||
                method.name.equals("ensureInitialized") && method.desc.equals("()V")) {
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn.getOpcode() != Opcodes.RETURN) continue;
                    InsnList hook = new InsnList();
                    hook.add(new FieldInsnNode(Opcodes.GETSTATIC, LOGGER, "directory", "Ljava/io/File;"));
                    hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, UPLOADER, "resume", "(Ljava/io/File;)V", false));
                    method.instructions.insertBefore(insn, hook);
                }
                found++;
            } else if (method.name.equals("cleanup") && method.desc.equals("(J)V")) {
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn.getOpcode() != Opcodes.RETURN) continue;
                    InsnList hook = new InsnList();
                    hook.add(new FieldInsnNode(Opcodes.GETSTATIC, LOGGER, "directory", "Ljava/io/File;"));
                    hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RETENTION, "prune", "(Ljava/io/File;)V", false));
                    method.instructions.insertBefore(insn, hook);
                }
                found++;
            } else if (method.name.equals("append") && method.desc.equals("(L" + LOGGER + "$Entry;)V")) {
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn.getOpcode() != Opcodes.RETURN) continue;
                    InsnList hook = new InsnList();
                    hook.add(new FieldInsnNode(Opcodes.GETSTATIC, LOGGER, "directory", "Ljava/io/File;"));
                    for (String field : new String[] { "timestamp", "category", "message" }) {
                        hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        hook.add(new FieldInsnNode(Opcodes.GETFIELD, LOGGER + "$Entry", field, "Ljava/lang/String;"));
                    }
                    hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, UPLOADER, "record",
                        "(Ljava/io/File;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", false));
                    method.instructions.insertBefore(insn, hook);
                }
                found++;
            }
        }
        if (found != 5) throw new IllegalStateException("Expected 5 logger hooks; found " + found);
        ClassWriter out = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(out);
        return out.toByteArray();
    }

    private static boolean hasHook(MethodNode method) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode && ((MethodInsnNode) insn).owner.equals(UPLOADER)) return true;
        }
        return false;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        return out.toByteArray();
    }
}





