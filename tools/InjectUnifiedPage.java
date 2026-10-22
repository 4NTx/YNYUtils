import java.io.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public final class InjectUnifiedPage {
    public static void main(String[] args) throws Exception {
        File inputFile = new File(args.length > 0 ? args[0] : "data/.minecraft/mods/YNYUtils.steinmod");
        File outputFile = new File(args.length > 1 ? args[1] : "C:/Users/artur/.gemini/antigravity-cli/brain/dfb7bbd3-c190-41ff-848d-d74681186529/scratch/YNYUtils_unified.steinmod");
        File teamMod = new File(args.length > 2 ? args[2] : "Sources/COISAS/YNYUtils/team-source/build/libs/YNYUtilsEquipe.steinmod");
        
        System.out.println("Reading base mod: " + inputFile.getAbsolutePath());
        System.out.println("Reading updated team classes from: " + teamMod.getAbsolutePath());
        
        // First, extract all updated team classes and resources from teamMod
        java.util.Map<String, byte[]> teamEntries = new java.util.HashMap<>();
        try (ZipFile tz = new ZipFile(teamMod)) {
            for (ZipEntry ze : java.util.Collections.list(tz.entries())) {
                String name = ze.getName();
                if ((name.startsWith("com/yny/utils/team/") && name.endsWith(".class")) ||
                    (name.startsWith("assets/steincombat/lang/") && name.endsWith(".lang")) ||
                    (name.equals("assets/steincombat/defaults.json"))) {
                    teamEntries.put(name, tz.getInputStream(ze).readAllBytes());
                }
            }
        }
        System.out.println("Loaded " + teamEntries.size() + " updated team entries.");
        
        int patchedYNYUtils = 0;
        
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(inputFile));
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(outputFile))) {
            
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName();
                byte[] bytes = in.readAllBytes();
                
                // If this entry is one of the team entries, replace with updated version!
                if (teamEntries.containsKey(name)) {
                    bytes = teamEntries.remove(name);
                } else if (name.equals("com/yny/utils/YNYUtils.class")) {
                    bytes = patchYNYUtilsOnPage(bytes);
                    patchedYNYUtils++;
                }
                
                ZipEntry outEntry = new ZipEntry(name);
                out.putNextEntry(outEntry);
                out.write(bytes);
                out.closeEntry();
            }
            
            // If any team entries remain, add them
            for (java.util.Map.Entry<String, byte[]> e : teamEntries.entrySet()) {
                ZipEntry outEntry = new ZipEntry(e.getKey());
                out.putNextEntry(outEntry);
                out.write(e.getValue());
                out.closeEntry();
            }
        }
        
        if (patchedYNYUtils != 1) {
            throw new IllegalStateException("Failed to patch YNYUtils.class! Found: " + patchedYNYUtils);
        }
        
        System.out.println("Successfully unified YNYUtils into: " + outputFile.getAbsolutePath() + " (" + outputFile.length() + " bytes)");
    }
    
    private static byte[] patchYNYUtilsOnPage(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        
        int patchedMethods = 0;
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals("onPage") && mn.desc.equals("(Ldev/xavier/stein/loader/api/Page;)V")) {
                for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode min = (MethodInsnNode) insn;
                        if ("com/yny/utils/team/SteinCombat".equals(min.owner) && "addOptions".equals(min.name)) {
                            System.out.println("  SteinCombat.addOptions already present in onPage!");
                            return classBytes;
                        }
                    }
                }
                // Find RETURN instruction
                for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn.getOpcode() == Opcodes.RETURN) {
                        InsnList toAdd = new InsnList();
                        toAdd.add(new VarInsnNode(Opcodes.ALOAD, 1)); // Page page
                        toAdd.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "com/yny/utils/team/SteinCombat",
                            "addOptions",
                            "(Ldev/xavier/stein/loader/api/Page;)V",
                            false
                        ));
                        mn.instructions.insertBefore(insn, toAdd);
                        patchedMethods++;
                        System.out.println("  Injected SteinCombat.addOptions(page) before RETURN in onPage!");
                        break;
                    }
                }
            }
        }
        
        if (patchedMethods != 1) {
            throw new IllegalStateException("Expected 1 onPage method, found: " + patchedMethods);
        }
        
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }
}
