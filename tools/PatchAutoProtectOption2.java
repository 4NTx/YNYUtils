import java.io.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Patches YNYUtils.steinmod to implement AutoProtect Option 2:
 * 1. Updates YNYConfig.class with autoProtectLowHealthEnabled and autoProtectToggleKey.
 * 2. Updates AutoProtect.class with checkToggleKey and autoProtectLowHealthEnabled guard.
 * 3. Updates AutoProtectOptions.class with low-health defense toggle and toggle key.
 * 4. Patches YNYUtils.class updateToggleKey() to call AutoProtect.checkToggleKey().
 */
public final class PatchAutoProtectOption2 {
    private static final String MAIN = "com/yny/utils/YNYUtils.class";
    private static final String CONFIG = "com/yny/utils/config/YNYConfig.class";
    private static final String PROTECT = "com/yny/utils/modules/player/AutoProtect.class";
    private static final String OPTIONS = "com/yny/utils/modules/player/AutoProtectOptions.class";

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            throw new IllegalArgumentException("Usage: PatchAutoProtectOption2 in.steinmod YNYConfig.class AutoProtect.class AutoProtectOptions.class out.steinmod");
        }

        byte[] configBytes = readBytes(args[1]);
        byte[] protectBytes = readBytes(args[2]);
        byte[] optionsBytes = readBytes(args[3]);

        int mainCount = 0;
        int configCount = 0;
        int protectCount = 0;
        int optionsCount = 0;

        try (ZipInputStream in = new ZipInputStream(new FileInputStream(args[0]));
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(args[4]))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                in.transferTo(buffer);
                byte[] data = buffer.toByteArray();

                if (MAIN.equals(entry.getName())) {
                    data = patchYNYUtils(data);
                    mainCount++;
                } else if (CONFIG.equals(entry.getName())) {
                    data = configBytes;
                    configCount++;
                } else if (PROTECT.equals(entry.getName())) {
                    data = protectBytes;
                    protectCount++;
                } else if (OPTIONS.equals(entry.getName())) {
                    data = optionsBytes;
                    optionsCount++;
                }

                ZipEntry copy = new ZipEntry(entry.getName());
                copy.setTime(entry.getTime());
                out.putNextEntry(copy);
                out.write(data);
                out.closeEntry();
            }
        }

        if (mainCount != 1 || configCount != 1 || protectCount != 1 || optionsCount != 1) {
            throw new IllegalStateException("Failed to update entries: main=" + mainCount + ", config=" + configCount
                    + ", protect=" + protectCount + ", options=" + optionsCount);
        }
        System.out.println("PASS: Successfully patched AutoProtect Option 2 into steinmod");
    }

    private static byte[] patchYNYUtils(byte[] bytes) {
        ClassNode node = read(bytes);
        int patched = 0;
        for (MethodNode method : node.methods) {
            if (!"updateToggleKey".equals(method.name) || !"()V".equals(method.desc)) {
                continue;
            }
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn.getOpcode() == Opcodes.RETURN) {
                    InsnList added = new InsnList();
                    added.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/yny/utils/YNYUtils", "config", "Lcom/yny/utils/config/YNYConfig;"));
                    added.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    added.add(new FieldInsnNode(Opcodes.GETFIELD, "com/yny/utils/YNYUtils", "configStore", "Ldev/xavier/stein/loader/api/ModConfig;"));
                    added.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/yny/utils/modules/player/AutoProtect", "checkToggleKey", "(Lcom/yny/utils/config/YNYConfig;Ldev/xavier/stein/loader/api/ModConfig;)V", false));
                    method.instructions.insertBefore(insn, added);
                    patched++;
                }
            }
        }
        if (patched != 1) {
            throw new IllegalStateException("Expected 1 updateToggleKey return site, found: " + patched);
        }
        return write(node);
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
}
