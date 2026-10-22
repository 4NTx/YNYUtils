import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public class InspectBytecode69 {
    public static void main(String[] args) throws Exception {
        ZipFile zip = new ZipFile("artifacts/YNYUtils.steinmod");
        Enumeration<? extends ZipEntry> en = zip.entries();
        int indyCount = 0;
        int nestCount = 0;
        int major69Count = 0;
        while (en.hasMoreElements()) {
            ZipEntry ze = en.nextElement();
            if (ze.getName().endsWith(".class")) {
                InputStream is = zip.getInputStream(ze);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = is.read(buf)) != -1) {
                    baos.write(buf, 0, n);
                }
                is.close();
                byte[] b = baos.toByteArray();
                int major = ((b[6] & 0xFF) << 8) | (b[7] & 0xFF);
                if (major == 69 && !ze.getName().startsWith("com/yny/utils/team/")) {
                    major69Count++;
                    // Read with ASM
                    byte[] compat = b.clone();
                    compat[6] = 0;
                    compat[7] = 60; // Java 16
                    ClassReader cr = new ClassReader(compat);
                    ClassNode cn = new ClassNode();
                    cr.accept(cn, 0);
                    if (cn.nestHostClass != null || (cn.nestMembers != null && !cn.nestMembers.isEmpty())) {
                        nestCount++;
                        System.out.println("Nest found in: " + ze.getName());
                    }
                    for (MethodNode mn : cn.methods) {
                        for (AbstractInsnNode insn : mn.instructions.toArray()) {
                            if (insn instanceof InvokeDynamicInsnNode) {
                                indyCount++;
                                InvokeDynamicInsnNode indy = (InvokeDynamicInsnNode) insn;
                                System.out.println("Indy found in: " + ze.getName() + " -> " + indy.bsm.getOwner() + "." + indy.bsm.getName());
                            }
                        }
                    }
                }
            }
        }
        zip.close();
        System.out.println("Total major 69 classes: " + major69Count);
        System.out.println("Total Indy instructions: " + indyCount);
        System.out.println("Total Nest classes: " + nestCount);
    }
}
