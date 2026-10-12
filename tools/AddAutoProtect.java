import java.io.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Adds YNYUtils-owned sword protection to the recovered production package. */
public final class AddAutoProtect {
    private static final String MAIN = "com/yny/utils/YNYUtils";
    private static final String PROTECT = "com/yny/utils/modules/player/AutoProtect";
    private static final String OPTIONS = "com/yny/utils/modules/player/AutoProtectOptions";
    private static final String CONFIG = "com/yny/utils/config/YNYConfig";
    private static final String LOG = "com/yny/utils/core/DebugLog";

    public static void main(String[] args) throws Exception {
        if (args.length != 5) throw new IllegalArgumentException("input config.class protect.class options.class output");
        int main = 0, config = 0, log = 0;
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(args[0]));
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(args[4]))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                in.transferTo(buffer);
                byte[] bytes = buffer.toByteArray();
                if (entry.getName().equals(MAIN + ".class")) { bytes = patch(bytes); main++; }
                if (entry.getName().equals(CONFIG + ".class")) { bytes = read(args[1]); config++; }
                if (entry.getName().equals(LOG + ".class")) { bytes = patchLog(bytes); log++; }
                ZipEntry copy = new ZipEntry(entry.getName()); copy.setTime(entry.getTime());
                out.putNextEntry(copy); out.write(bytes); out.closeEntry();
            }
            if (main != 1 || config != 1 || log != 1) throw new IllegalStateException("missing class: " + main + "/" + config + "/" + log);
            out.putNextEntry(new ZipEntry(PROTECT + ".class")); out.write(read(args[2])); out.closeEntry();
            out.putNextEntry(new ZipEntry(OPTIONS + ".class")); out.write(read(args[3])); out.closeEntry();
        }
    }

    private static byte[] read(String name) throws IOException {
        try (InputStream in = new FileInputStream(name)) { return in.readAllBytes(); }
    }

    private static byte[] patch(byte[] bytes) {
        byte[] compatible = bytes.clone(); compatible[6] = 0; compatible[7] = 60;
        ClassNode node = new ClassNode(); new ClassReader(compatible).accept(node, 0);
        node.version = ((bytes[6] & 255) << 8) | (bytes[7] & 255);
        int ticks = 0, pages = 0, resets = 0;
        for (MethodNode method : node.methods) {
            if (method.name.equals("onTickEnd") && method.desc.equals("()V")) {
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (!(insn instanceof MethodInsnNode)) continue;
                    MethodInsnNode call = (MethodInsnNode) insn;
                    if (!call.owner.equals("com/yny/utils/modules/player/InventoryMove") || !call.name.equals("onTickEnd")) continue;
                    InsnList added = new InsnList();
                    added.add(new FieldInsnNode(Opcodes.GETSTATIC, MAIN, "config", "L" + CONFIG + ";"));
                    added.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    added.add(new FieldInsnNode(Opcodes.GETFIELD, MAIN, "autoConsumables", "Lcom/yny/utils/modules/player/AutoConsumables;"));
                    added.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "com/yny/utils/modules/player/AutoConsumables", "isControllingUseKey", "()Z", false));
                    added.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PROTECT, "onTick", "(L" + CONFIG + ";Z)V", false));
                    method.instructions.insert(insn, added); ticks++;
                }
            }
            if (method.name.equals("onPage") && method.desc.equals("(Ldev/xavier/stein/loader/api/Page;)V")) {
                InsnList added = new InsnList();
                added.add(new VarInsnNode(Opcodes.ALOAD, 1));
                added.add(new FieldInsnNode(Opcodes.GETSTATIC, MAIN, "config", "L" + CONFIG + ";"));
                added.add(new VarInsnNode(Opcodes.ALOAD, 0));
                added.add(new FieldInsnNode(Opcodes.GETFIELD, MAIN, "configStore", "Ldev/xavier/stein/loader/api/ModConfig;"));
                added.add(new MethodInsnNode(Opcodes.INVOKESTATIC, OPTIONS, "add", "(Ldev/xavier/stein/loader/api/Page;L" + CONFIG + ";Ldev/xavier/stein/loader/api/ModConfig;)V", false));
                method.instructions.insert(added); pages++;
            }
            if ((method.name.equals("onShutdown") && method.desc.equals("()V")) ||
                (method.name.equals("onJoinGame") && method.desc.equals("()V")) ||
                (method.name.equals("onDisconnected") && method.desc.equals("(Ljava/lang/String;Ljava/lang/String;)V"))) {
                InsnList added = new InsnList();
                added.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PROTECT, "reset", "()V", false));
                method.instructions.insert(added); resets++;
            }
        }
        if (ticks != 1 || pages != 1 || resets != 3) throw new IllegalStateException("patch sites: " + ticks + "/" + pages + "/" + resets);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }

    private static byte[] patchLog(byte[] bytes) {
        byte[] compatible = bytes.clone(); compatible[6] = 0; compatible[7] = 60;
        ClassNode node = new ClassNode(); new ClassReader(compatible).accept(node, 0);
        node.version = ((bytes[6] & 255) << 8) | (bytes[7] & 255);
        int found = 0;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("fileGroup") || !method.desc.equals("(Ljava/lang/String;)Ljava/lang/String;")) continue;
            LabelNode ordinary = new LabelNode();
            InsnList added = new InsnList();
            added.add(new LdcInsnNode("AutoProtect"));
            added.add(new VarInsnNode(Opcodes.ALOAD, 0));
            added.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false));
            added.add(new JumpInsnNode(Opcodes.IFEQ, ordinary));
            added.add(new LdcInsnNode("auto-protect"));
            added.add(new InsnNode(Opcodes.ARETURN));
            added.add(ordinary);
            added.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
            method.instructions.insert(added); found++;
        }
        if (found != 1) throw new IllegalStateException("DebugLog.fileGroup missing");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }
}
