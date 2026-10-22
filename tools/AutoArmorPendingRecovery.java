import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Lets a locally completed armor swap recover its old cursor after a missing slot callback. */
public final class AutoArmorPendingRecovery implements Opcodes {
    private static final String CLASS = "com/yny/utils/modules/player/AutoArmor.class";
    private static final String OWNER = "com/yny/utils/modules/player/AutoArmor";
    private static final String PENDING = OWNER + "$Pending";
    private static final String PENDING_DESC = "L" + PENDING + ";";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("input.steinmod output.steinmod");
        Path output = Path.of(args[1]);
        int patched = 0;
        try (ZipFile input = new ZipFile(args[0]);
             ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(output))) {
            Enumeration<? extends ZipEntry> entries = input.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                byte[] bytes;
                try (InputStream stream = input.getInputStream(entry)) {
                    bytes = stream.readAllBytes();
                }
                if (CLASS.equals(entry.getName())) {
                    bytes = patch(bytes);
                    patched++;
                }
                ZipEntry copy = new ZipEntry(entry.getName());
                copy.setTime(entry.getTime());
                zip.putNextEntry(copy);
                zip.write(bytes);
                zip.closeEntry();
            }
        }
        if (patched != 1) {
            Files.deleteIfExists(output);
            throw new IllegalStateException("AutoArmor.class count: " + patched);
        }
        System.out.println("AutoArmor pending recovery patched");
    }

    static byte[] patch(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, ClassReader.EXPAND_FRAMES);
        int recoveryGuards = 0;
        int dropGuards = 0;
        for (MethodNode method : node.methods) {
            if (!"advancePending".equals(method.name)) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof FieldInsnNode)) continue;
                FieldInsnNode field = (FieldInsnNode) instruction;
                if (field.getOpcode() == GETFIELD && PENDING.equals(field.owner)
                        && "serverConfirmed".equals(field.name)) {
                    AbstractInsnNode jump = next(instruction);
                    if (!(jump instanceof JumpInsnNode) || jump.getOpcode() != IFEQ) continue;
                    AbstractInsnNode previous = previous(instruction);
                    // Select the gate immediately after localApplied; another gate handles dropping.
                    if (!(previous instanceof FieldInsnNode)
                            || !"pending".equals(((FieldInsnNode) previous).name)) continue;
                    AbstractInsnNode before = previous(previous);
                    AbstractInsnNode priorJump = before == null ? null : previous(before);
                    if (!(priorJump instanceof JumpInsnNode) || priorJump.getOpcode() != IFEQ) continue;
                    JumpInsnNode oldGate = (JumpInsnNode) jump;
                    LabelNode proceed = new LabelNode();
                    InsnList recovery = new InsnList();
                    recovery.add(new JumpInsnNode(IFNE, proceed));
                    recovery.add(new VarInsnNode(ALOAD, 0));
                    recovery.add(new FieldInsnNode(GETFIELD, OWNER, "pending", PENDING_DESC));
                    recovery.add(new FieldInsnNode(GETFIELD, PENDING, "age", "I"));
                    recovery.add(new IntInsnNode(BIPUSH, 16));
                    recovery.add(new JumpInsnNode(IF_ICMPLT, oldGate.label));
                    recovery.add(new VarInsnNode(ALOAD, 0));
                    recovery.add(new FieldInsnNode(GETFIELD, OWNER, "pending", PENDING_DESC));
                    recovery.add(new FieldInsnNode(GETFIELD, PENDING, "piece", "I"));
                    recovery.add(new MethodInsnNode(INVOKESTATIC,
                            "dev/xavier/stein/loader/api/Inventory", "armor", "(I)Ljava/lang/Object;", false));
                    recovery.add(new MethodInsnNode(INVOKESTATIC, OWNER, "asStack", "(Ljava/lang/Object;)Lzx;", false));
                    recovery.add(new VarInsnNode(ALOAD, 0));
                    recovery.add(new FieldInsnNode(GETFIELD, OWNER, "pending", PENDING_DESC));
                    recovery.add(new MethodInsnNode(INVOKESTATIC, OWNER, "matchesReplacementArmor",
                            "(Lzx;" + PENDING_DESC + ")Z", false));
                    recovery.add(new JumpInsnNode(IFEQ, oldGate.label));
                    recovery.add(proceed);
                    method.instructions.insertBefore(oldGate, recovery);
                    method.instructions.remove(oldGate);
                    recoveryGuards++;
                }
                if (field.getOpcode() == GETFIELD && PENDING.equals(field.owner)
                        && "dropOld".equals(field.name)) {
                    AbstractInsnNode jump = next(instruction);
                    if (!(jump instanceof JumpInsnNode) || jump.getOpcode() != IFEQ) continue;
                    InsnList guard = new InsnList();
                    guard.add(new VarInsnNode(ALOAD, 0));
                    guard.add(new FieldInsnNode(GETFIELD, OWNER, "pending", PENDING_DESC));
                    guard.add(new FieldInsnNode(GETFIELD, PENDING, "serverConfirmed", "Z"));
                    guard.add(new JumpInsnNode(IFEQ, ((JumpInsnNode) jump).label));
                    // Insert before the ALOAD 0 that loads pending for dropOld.
                    AbstractInsnNode pendingLoad = previous(previous(instruction));
                    method.instructions.insertBefore(pendingLoad, guard);
                    dropGuards++;
                }
            }
        }
        if (recoveryGuards != 1 || dropGuards != 1) {
            throw new IllegalStateException("Expected one recovery and one drop guard, found "
                    + recoveryGuards + " and " + dropGuards);
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override protected String getCommonSuperClass(String left, String right) {
                return left.equals(right) ? left : "java/lang/Object";
            }
        };
        node.accept(writer);
        return writer.toByteArray();
    }

    private static AbstractInsnNode next(AbstractInsnNode node) {
        for (node = node.getNext(); node != null && node.getOpcode() < 0; node = node.getNext()) { }
        return node;
    }

    private static AbstractInsnNode previous(AbstractInsnNode node) {
        if (node == null) return null;
        for (node = node.getPrevious(); node != null && node.getOpcode() < 0; node = node.getPrevious()) { }
        return node;
    }
}
