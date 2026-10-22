import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public class CheckIllegalAccess {
    public static void main(String[] args) throws Exception {
        File f = new File("../../../data/.minecraft/mods/YNYUtils.steinmod");
        if (!f.exists()) f = new File("artifacts/YNYUtils.steinmod");
        Map<String, ClassNode> nodes = new HashMap<>();
        try (ZipFile zf = new ZipFile(f)) {
            for (ZipEntry ze : Collections.list(zf.entries())) {
                if (ze.getName().endsWith(".class")) {
                    ClassReader cr = new ClassReader(zf.getInputStream(ze));
                    ClassNode cn = new ClassNode();
                    cr.accept(cn, 0);
                    nodes.put(cn.name, cn);
                }
            }
        }

        System.out.println("Loaded " + nodes.size() + " classes. Scanning for illegal access, missing methods, and missing fields...");
        int accessViolations = 0;
        int missingMethods = 0;
        int missingFields = 0;

        for (ClassNode cn : nodes.values()) {
            for (MethodNode mn : cn.methods) {
                for (AbstractInsnNode insn : mn.instructions) {
                    if (insn instanceof FieldInsnNode) {
                        FieldInsnNode fin = (FieldInsnNode) insn;
                        if (nodes.containsKey(fin.owner)) {
                            FieldNode fn = findField(nodes.get(fin.owner), fin.name, fin.desc, nodes);
                            if (fn == null) {
                                System.out.println("MISSING FIELD: " + cn.name + "." + mn.name + mn.desc + " accesses NON-EXISTENT field " + fin.owner + "." + fin.name + " " + fin.desc);
                                missingFields++;
                            } else if (!fin.owner.equals(cn.name) && (fn.access & Opcodes.ACC_PRIVATE) != 0) {
                                System.out.println("ACCESS VIOLATION: " + cn.name + "." + mn.name + mn.desc + " accesses PRIVATE field " + fin.owner + "." + fin.name);
                                accessViolations++;
                            }
                        }
                    } else if (insn instanceof MethodInsnNode) {
                        MethodInsnNode min = (MethodInsnNode) insn;
                        if (nodes.containsKey(min.owner)) {
                            MethodNode tmn = findMethod(nodes.get(min.owner), min.name, min.desc, nodes);
                            if (tmn == null) {
                                System.out.println("MISSING METHOD: " + cn.name + "." + mn.name + mn.desc + " calls NON-EXISTENT method " + min.owner + "." + min.name + min.desc);
                                missingMethods++;
                            } else if (!min.owner.equals(cn.name) && (tmn.access & Opcodes.ACC_PRIVATE) != 0) {
                                System.out.println("ACCESS VIOLATION: " + cn.name + "." + mn.name + mn.desc + " calls PRIVATE method " + min.owner + "." + min.name + min.desc);
                                accessViolations++;
                            }
                        }
                    }
                }
            }
        }
        System.out.println("Scan complete:");
        System.out.println("  Access violations: " + accessViolations);
        System.out.println("  Missing methods:   " + missingMethods);
        System.out.println("  Missing fields:    " + missingFields);
    }

    private static FieldNode findField(ClassNode node, String name, String desc, Map<String, ClassNode> nodes) {
        ClassNode cur = node;
        while (cur != null) {
            for (FieldNode fn : cur.fields) {
                if (fn.name.equals(name) && fn.desc.equals(desc)) return fn;
            }
            cur = (cur.superName != null && nodes.containsKey(cur.superName)) ? nodes.get(cur.superName) : null;
        }
        return null;
    }

    private static MethodNode findMethod(ClassNode node, String name, String desc, Map<String, ClassNode> nodes) {
        ClassNode cur = node;
        while (cur != null) {
            for (MethodNode mn : cur.methods) {
                if (mn.name.equals(name) && mn.desc.equals(desc)) return mn;
            }
            cur = (cur.superName != null && nodes.containsKey(cur.superName)) ? nodes.get(cur.superName) : null;
        }
        return null;
    }
}
