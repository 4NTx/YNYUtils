import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public class TestAccessFix {
    public static void main(String[] args) throws Exception {
        File f = new File("../../../data/.minecraft/mods/YNYUtils.steinmod");
        if (!f.exists()) f = new File("artifacts/YNYUtils.steinmod");
        Map<String, ClassNode> nodes = new LinkedHashMap<>();

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

        int fixedFields = 0;
        int fixedMethods = 0;

        for (ClassNode cn : nodes.values()) {
            for (MethodNode mn : cn.methods) {
                for (AbstractInsnNode insn : mn.instructions) {
                    if (insn instanceof FieldInsnNode) {
                        FieldInsnNode fin = (FieldInsnNode) insn;
                        if (nodes.containsKey(fin.owner) && !fin.owner.equals(cn.name)) {
                            ClassNode target = nodes.get(fin.owner);
                            for (FieldNode fn : target.fields) {
                                if (fn.name.equals(fin.name) && fn.desc.equals(fin.desc)) {
                                    if ((fn.access & Opcodes.ACC_PRIVATE) != 0) {
                                        System.out.println("Fixing field: " + fin.owner + "." + fin.name + " accessed by " + cn.name);
                                        fn.access &= ~Opcodes.ACC_PRIVATE;
                                        fixedFields++;
                                    }
                                }
                            }
                        }
                    } else if (insn instanceof MethodInsnNode) {
                        MethodInsnNode min = (MethodInsnNode) insn;
                        if (nodes.containsKey(min.owner) && !min.owner.equals(cn.name)) {
                            ClassNode target = nodes.get(min.owner);
                            for (MethodNode tmn : target.methods) {
                                if (tmn.name.equals(min.name) && tmn.desc.equals(min.desc)) {
                                    if ((tmn.access & Opcodes.ACC_PRIVATE) != 0) {
                                        System.out.println("Fixing method: " + min.owner + "." + min.name + " called by " + cn.name);
                                        tmn.access &= ~Opcodes.ACC_PRIVATE;
                                        fixedMethods++;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        System.out.println("Fixed fields: " + fixedFields + ", fixed methods: " + fixedMethods);
    }
}
