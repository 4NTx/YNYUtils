import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Injeta os hooks de telemetria e marca d'água no YNYUtils.class e embute as classes de telemetria no .steinmod.
 */
public final class InjectTelemetryFeatures {

    public static void main(String[] args) throws Exception {
        File inputFile = new File(args.length > 0 ? args[0] : "artifacts/YNYUtils.steinmod");
        File telemetryDir = new File(args.length > 1 ? args[1] : "build/compiled");
        File outputFile = new File(args.length > 2 ? args[2] : "build/YNYUtils.steinmod");

        System.out.println("Input: " + inputFile.getAbsolutePath());
        System.out.println("Telemetry Classes Dir: " + telemetryDir.getAbsolutePath());
        System.out.println("Output: " + outputFile.getAbsolutePath());

        // Carrega todas as classes compiladas de telemetria
        Map<String, byte[]> newClasses = new HashMap<>();
        Path root = telemetryDir.toPath();
        Files.walk(root)
            .filter(p -> p.toString().endsWith(".class") && p.toString().contains("telemetry"))
            .forEach(p -> {
                String rel = root.relativize(p).toString().replace('\\', '/');
                try {
                    newClasses.put(rel, Files.readAllBytes(p));
                    System.out.println("  Loaded telemetry class: " + rel);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });

        int patchedYNY = 0;
        File tempOut = new File(outputFile.getAbsolutePath() + ".tmp");

        try (ZipInputStream in = new ZipInputStream(new FileInputStream(inputFile));
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(tempOut))) {

            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName();
                byte[] bytes;

                if (newClasses.containsKey(name)) {
                    bytes = newClasses.remove(name);
                    System.out.println("  Replaced existing: " + name);
                } else if ("com/yny/utils/YNYUtils.class".equals(name)) {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    in.transferTo(buffer);
                    bytes = patchYNYUtils(buffer.toByteArray());
                    patchedYNY++;
                } else {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    in.transferTo(buffer);
                    bytes = buffer.toByteArray();
                }

                ZipEntry copy = new ZipEntry(name);
                copy.setTime(entry.getTime());
                out.putNextEntry(copy);
                out.write(bytes);
                out.closeEntry();
            }

            // Adiciona novas classes de telemetria
            for (Map.Entry<String, byte[]> e : newClasses.entrySet()) {
                ZipEntry brandNew = new ZipEntry(e.getKey());
                brandNew.setTime(System.currentTimeMillis());
                out.putNextEntry(brandNew);
                out.write(e.getValue());
                out.closeEntry();
                System.out.println("  Added new: " + e.getKey() + " (" + e.getValue().length + " bytes)");
            }
        }

        if (patchedYNY != 1) {
            throw new IllegalStateException("Falha ao localizar e injetar com/yny/utils/YNYUtils.class! Encontrados: " + patchedYNY);
        }

        if (outputFile.exists()) outputFile.delete();
        if (!tempOut.renameTo(outputFile)) {
            throw new IOException("Falha ao renomear arquivo temporário para " + outputFile);
        }

        System.out.println("PASS: Telemetria e marca d'água injetadas com sucesso em " + outputFile.getName());
    }

    private static byte[] patchYNYUtils(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        int patchedMethods = 0;

        for (MethodNode mn : cn.methods) {
            if ("afterStartGame".equals(mn.name) && "()V".equals(mn.desc)) {
                if (!alreadyInjected(mn)) {
                    injectBeforeReturn(mn, new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "com/yny/utils/telemetry/TelemetryBridge",
                        "afterStartGame",
                        "()V",
                        false
                    ));
                    System.out.println("  Injected TelemetryBridge.afterStartGame()");
                } else {
                    System.out.println("  Already present: TelemetryBridge.afterStartGame()");
                }
                patchedMethods++;
            } else if ("onJoinGame".equals(mn.name) && "()V".equals(mn.desc)) {
                if (!alreadyInjected(mn)) {
                    injectBeforeReturn(mn, new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "com/yny/utils/telemetry/TelemetryBridge",
                        "onJoinGame",
                        "()V",
                        false
                    ));
                    System.out.println("  Injected TelemetryBridge.onJoinGame()");
                } else {
                    System.out.println("  Already present: TelemetryBridge.onJoinGame()");
                }
                patchedMethods++;
            } else if ("onDisconnected".equals(mn.name) && "(Ljava/lang/String;Ljava/lang/String;)V".equals(mn.desc)) {
                if (!alreadyInjected(mn)) {
                    injectBeforeReturn(mn, new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "com/yny/utils/telemetry/TelemetryBridge",
                        "onDisconnected",
                        "()V",
                        false
                    ));
                    System.out.println("  Injected TelemetryBridge.onDisconnected()");
                } else {
                    System.out.println("  Already present: TelemetryBridge.onDisconnected()");
                }
                patchedMethods++;
            } else if ("onShutdown".equals(mn.name) && "()V".equals(mn.desc)) {
                if (!alreadyInjected(mn)) {
                    injectBeforeReturn(mn, new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "com/yny/utils/telemetry/TelemetryBridge",
                        "onShutdown",
                        "()V",
                        false
                    ));
                    System.out.println("  Injected TelemetryBridge.onShutdown()");
                } else {
                    System.out.println("  Already present: TelemetryBridge.onShutdown()");
                }
                patchedMethods++;
            } else if ("onPage".equals(mn.name) && "(Ldev/xavier/stein/loader/api/Page;)V".equals(mn.desc)) {
                if (!alreadyInjected(mn)) {
                    InsnList insns = new InsnList();
                    insns.add(new VarInsnNode(Opcodes.ALOAD, 1)); // Page page
                    insns.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "com/yny/utils/telemetry/TelemetryBridge",
                        "addOptions",
                        "(Ldev/xavier/stein/loader/api/Page;)V",
                        false
                    ));
                    injectBeforeReturn(mn, insns);
                    System.out.println("  Injected TelemetryBridge.addOptions(page)");
                } else {
                    System.out.println("  Already present: TelemetryBridge.addOptions(page)");
                }
                patchedMethods++;
            }
        }

        if (patchedMethods != 5) {
            throw new IllegalStateException("Esperados 5 métodos no ciclo de vida de YNYUtils, injetados: " + patchedMethods);
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private static boolean alreadyInjected(MethodNode mn) {
        for (AbstractInsnNode node = mn.instructions.getFirst(); node != null; node = node.getNext()) {
            if (node instanceof MethodInsnNode) {
                MethodInsnNode min = (MethodInsnNode) node;
                if ("com/yny/utils/telemetry/TelemetryBridge".equals(min.owner)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void injectBeforeReturn(MethodNode mn, AbstractInsnNode insn) {
        InsnList list = new InsnList();
        list.add(insn);
        injectBeforeReturn(mn, list);
    }

    private static void injectBeforeReturn(MethodNode mn, InsnList insns) {
        for (AbstractInsnNode node = mn.instructions.getLast(); node != null; node = node.getPrevious()) {
            if (node.getOpcode() == Opcodes.RETURN) {
                mn.instructions.insertBefore(node, insns);
                return;
            }
        }
        throw new IllegalStateException("Nenhum RETURN encontrado em " + mn.name);
    }
}
