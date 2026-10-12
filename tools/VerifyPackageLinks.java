import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Detects broken references between classes bundled in a steinmod. */
public final class VerifyPackageLinks {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("package.steinmod");
        Map<String, ClassNode> classes = new HashMap<>();
        try (ZipFile zip = new ZipFile(args[0])) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().startsWith("com/yny/") || !entry.getName().endsWith(".class")) continue;
                byte[] data;
                try (InputStream in = zip.getInputStream(entry)) { data = in.readAllBytes(); }
                data[6] = 0; data[7] = 60;
                ClassNode node = new ClassNode(); new ClassReader(data).accept(node, ClassReader.SKIP_DEBUG);
                classes.put(node.name, node);
            }
        }
        int checked = 0;
        List<String> missing = new ArrayList<>();
        for (ClassNode node : classes.values()) for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof FieldInsnNode) {
                    FieldInsnNode field = (FieldInsnNode)insn;
                    if (!field.owner.startsWith("com/yny/")) continue;
                    checked++;
                    if (!hasField(classes, field.owner, field.name, field.desc))
                        missing.add(node.name + "." + method.name + " -> field " + field.owner + "." + field.name + field.desc);
                } else if (insn instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode)insn;
                    if (!call.owner.startsWith("com/yny/")) continue;
                    checked++;
                    if (!hasMethod(classes, call.owner, call.name, call.desc, call.getOpcode()))
                        missing.add(node.name + "." + method.name + " -> method " + call.owner + "." + call.name + call.desc);
                }
            }
        }
        if (!missing.isEmpty()) throw new AssertionError(String.join("\n", missing));
        System.out.println("PASS: " + classes.size() + " classes, " + checked + " internal links");
    }

    private static boolean hasField(Map<String, ClassNode> classes, String owner, String name, String desc) {
        ClassNode node = classes.get(owner);
        if (node == null) return false;
        for (FieldNode field : node.fields) if (field.name.equals(name) && field.desc.equals(desc)) return true;
        if (node.superName == null) return false;
        if (!node.superName.startsWith("com/yny/")) return !node.superName.equals("java/lang/Object");
        return hasField(classes, node.superName, name, desc);
    }

    private static boolean hasMethod(Map<String, ClassNode> classes, String owner, String name, String desc, int opcode) {
        ClassNode node = classes.get(owner);
        if (node == null) return false;
        for (MethodNode method : node.methods) if (method.name.equals(name) && method.desc.equals(desc)) return true;
        if (node.superName == null) return false;
        if (!node.superName.startsWith("com/yny/"))
            return !node.superName.equals("java/lang/Object") && opcode != Opcodes.INVOKESTATIC;
        return hasMethod(classes, node.superName, name, desc, opcode);
    }
}
