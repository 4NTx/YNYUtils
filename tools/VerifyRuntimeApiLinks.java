import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public final class VerifyRuntimeApiLinks {
    private static final String PREFIX = "dev/xavier/stein/loader/api/";
    private static final Map<String, ClassNode> api = new HashMap<>();
    private static final List<String> missing = new ArrayList<>();
    private static int references;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("loader.jar mod1.steinmod ...");
        try (ZipFile loader = new ZipFile(args[0])) {
            Enumeration<? extends ZipEntry> entries = loader.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().startsWith(PREFIX) || !entry.getName().endsWith(".class")) continue;
                api.put(entry.getName().substring(0, entry.getName().length() - 6),
                        read(loader.getInputStream(entry).readAllBytes()));
            }
        }
        for (int i = 1; i < args.length; i++) {
            try (ZipFile mod = new ZipFile(args[i])) {
                Enumeration<? extends ZipEntry> entries = mod.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (!entry.getName().endsWith(".class")) continue;
                    ClassNode source = read(mod.getInputStream(entry).readAllBytes());
                    for (MethodNode method : source.methods) {
                        for (AbstractInsnNode insn : method.instructions.toArray()) {
                            if (insn instanceof MethodInsnNode) {
                                MethodInsnNode call = (MethodInsnNode) insn;
                                if (!call.owner.startsWith(PREFIX)) continue;
                                references++;
                                if (!hasMethod(call.owner, call.name, call.desc, new HashSet<>()))
                                    missing.add(source.name + "." + method.name + " -> " + call.owner + "." + call.name + call.desc);
                            } else if (insn instanceof FieldInsnNode) {
                                FieldInsnNode field = (FieldInsnNode) insn;
                                if (!field.owner.startsWith(PREFIX)) continue;
                                references++;
                                if (!hasField(field.owner, field.name, field.desc, new HashSet<>()))
                                    missing.add(source.name + "." + method.name + " -> " + field.owner + "." + field.name + ":" + field.desc);
                            }
                        }
                    }
                }
            }
        }
        System.out.println("Checked " + references + " Loader API references against runtime jar.");
        for (String item : missing) System.out.println("MISSING " + item);
        if (!missing.isEmpty()) throw new AssertionError(missing.size() + " missing Loader API links");
    }

    private static ClassNode read(byte[] original) {
        byte[] bytes = original.clone();
        bytes[6] = 0;
        bytes[7] = 60;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }

    private static boolean hasMethod(String owner, String name, String desc, Set<String> seen) {
        if (!seen.add(owner)) return false;
        ClassNode node = api.get(owner);
        if (node == null) return false;
        for (MethodNode method : node.methods)
            if (method.name.equals(name) && method.desc.equals(desc)) return true;
        if ("java/lang/Enum".equals(node.superName)
                && (("name".equals(name) && "()Ljava/lang/String;".equals(desc))
                    || ("ordinal".equals(name) && "()I".equals(desc)))) return true;
        if (node.superName != null && hasMethod(node.superName, name, desc, seen)) return true;
        for (String iface : node.interfaces)
            if (hasMethod(iface, name, desc, seen)) return true;
        return false;
    }

    private static boolean hasField(String owner, String name, String desc, Set<String> seen) {
        if (!seen.add(owner)) return false;
        ClassNode node = api.get(owner);
        if (node == null) return false;
        for (FieldNode field : node.fields)
            if (field.name.equals(name) && field.desc.equals(desc)) return true;
        if (node.superName != null && hasField(node.superName, name, desc, seen)) return true;
        for (String iface : node.interfaces)
            if (hasField(iface, name, desc, seen)) return true;
        return false;
    }
}