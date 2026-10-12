import java.io.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Keeps the health callback ahead of a stale immediate entity read. */
public final class FixHealthEventRace {
    private static final String TARGET = "com/yny/targethealth/TargetHealthElement.class";
    private static final String HELPER = "com/yny/targethealth/HealthEventLatch";

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("input.steinmod HealthEventLatch.class output.steinmod");
        byte[] helper;
        try (InputStream stream = new FileInputStream(args[1])) { helper = stream.readAllBytes(); }
        int count = 0;
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(args[0]));
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(args[2]))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                in.transferTo(buffer);
                byte[] bytes = buffer.toByteArray();
                if (entry.getName().equals(TARGET)) { bytes = patch(bytes); count++; }
                ZipEntry copy = new ZipEntry(entry.getName());
                copy.setTime(entry.getTime());
                out.putNextEntry(copy); out.write(bytes); out.closeEntry();
            }
            if (count != 1) throw new IllegalStateException("Damage Indicator class missing");
            out.putNextEntry(new ZipEntry(HELPER + ".class")); out.write(helper); out.closeEntry();
        }
        System.out.println("Health callback precedence installed");
    }

    private static byte[] patch(byte[] bytes) {
        byte[] compatible = bytes.clone();
        compatible[6] = 0; compatible[7] = 60;
        ClassNode node = new ClassNode();
        new ClassReader(compatible).accept(node, 0);
        node.version = ((bytes[6] & 255) << 8) | (bytes[7] & 255);
        int health = 0, absorption = 0, record = 0;
        for (MethodNode method : node.methods) {
            if (method.name.equals("updateTarget") && method.desc.equals("()V")) {
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (!(instruction instanceof MethodInsnNode call)
                            || !call.owner.equals("dev/xavier/stein/loader/api/Entities")) continue;
                    if (call.name.equals("health") || call.name.equals("absorption")) {
                        InsnList override = new InsnList();
                        override.add(new VarInsnNode(Opcodes.ALOAD, 2));
                        override.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, call.name, "(FLjava/lang/Object;)F", false));
                        method.instructions.insert(call, override);
                        if (call.name.equals("health")) health++; else absorption++;
                    }
                }
            }
            if (method.name.equals("onHealthChanged") && method.desc.equals("(Ljava/lang/Object;FFFF)V")) {
                AbstractInsnNode firstReturn = null;
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction.getOpcode() == Opcodes.RETURN) { firstReturn = instruction; break; }
                }
                if (firstReturn == null) throw new IllegalStateException("health guard missing");
                AbstractInsnNode point = firstReturn.getNext();
                while (point != null && point.getOpcode() < 0) point = point.getNext();
                if (!(point instanceof VarInsnNode) || point.getOpcode() != Opcodes.ALOAD)
                    throw new IllegalStateException("health callback insertion site changed");
                InsnList save = new InsnList();
                save.add(new VarInsnNode(Opcodes.ALOAD, 1));
                save.add(new VarInsnNode(Opcodes.FLOAD, 3));
                save.add(new VarInsnNode(Opcodes.FLOAD, 5));
                save.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "record", "(Ljava/lang/Object;FF)V", false));
                method.instructions.insertBefore(point, save);
                record++;
            }
        }
        if (health != 1 || absorption != 1 || record != 1)
            throw new IllegalStateException("health sites: " + health + "/" + absorption + "/" + record);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }
}
