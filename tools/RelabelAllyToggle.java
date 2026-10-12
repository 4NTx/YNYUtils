import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public final class RelabelAllyToggle {
    private static final String CLASS_NAME = "com/yny/utils/YNYUtils.class";
    private static final String OLD_LABEL = "Ignorar aliados (não bater)";
    private static final String NEW_LABEL = "Ignorar aliados e atacar inimigo atrás";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("input.steinmod output.steinmod");
        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);
        AtomicInteger replacements = new AtomicInteger();
        try (ZipFile source = new ZipFile(input.toFile());
             ZipOutputStream target = new ZipOutputStream(Files.newOutputStream(output))) {
            var entries = source.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                byte[] bytes;
                try (InputStream stream = source.getInputStream(entry)) {
                    bytes = stream.readAllBytes();
                }
                if (CLASS_NAME.equals(entry.getName())) {
                    ClassReader reader = new ClassReader(bytes);
                    ClassWriter writer = new ClassWriter(0);
                    reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                        @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                String signature, String[] exceptions) {
                            MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
                            return new MethodVisitor(Opcodes.ASM9, delegate) {
                                @Override public void visitLdcInsn(Object value) {
                                    if (OLD_LABEL.equals(value)) {
                                        replacements.incrementAndGet();
                                        super.visitLdcInsn(NEW_LABEL);
                                    } else {
                                        super.visitLdcInsn(value);
                                    }
                                }
                            };
                        }
                    }, 0);
                    bytes = writer.toByteArray();
                }
                ZipEntry copy = new ZipEntry(entry.getName());
                copy.setTime(entry.getTime());
                target.putNextEntry(copy);
                target.write(bytes);
                target.closeEntry();
            }
        }
        if (replacements.get() != 1) {
            Files.deleteIfExists(output);
            throw new IllegalStateException("Expected exactly one toggle label; found " + replacements.get());
        }
        System.out.println("Updated one ally toggle label");
    }
}
