import java.io.*;import java.nio.file.*;import java.util.zip.*;import org.objectweb.asm.*;
public class FixAllyChain implements Opcodes {
static final String C="com/yny/utils/modules/pvp/HitThroughAllies.class", R="dev/xavier/stein/loader/api/Targeting$Ray", H="dev/xavier/stein/loader/api/Targeting$Hit";
static int count;
public static void main(String[] a)throws Exception {
if(a.length!=2)throw new IllegalArgumentException("input output");
try(ZipFile in=new ZipFile(a[0]);ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(Path.of(a[1])))){
var es=in.entries();while(es.hasMoreElements()){ZipEntry e=es.nextElement();byte[] b;try(InputStream s=in.getInputStream(e)){b=s.readAllBytes();}
if(e.getName().equals(C)){ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS){protected String getCommonSuperClass(String a,String b){return "java/lang/Object";}};
new ClassReader(b).accept(new ClassVisitor(ASM9,w){public MethodVisitor visitMethod(int x,String n,String d,String s,String[] e){
MethodVisitor m=super.visitMethod(x,n,d,s,e);
if(n.equals("firstNonAllyBeforeBlock")){count++;write(m);return null;}return m;}},0);b=w.toByteArray();}
ZipEntry copy=new ZipEntry(e.getName());copy.setTime(e.getTime());out.putNextEntry(copy);out.write(b);out.closeEntry();}}
if(count!=1){Files.deleteIfExists(Path.of(a[1]));throw new IllegalStateException("method count "+count);}
System.out.println("Patched one method");
}
static void write(MethodVisitor m){Label loop=new Label(),end=new Label();m.visitCode();
m.visitVarInsn(ALOAD,0);m.visitFieldInsn(GETFIELD,R,"hits","Ljava/util/List;");m.visitMethodInsn(INVOKEINTERFACE,"java/util/List","iterator","()Ljava/util/Iterator;",true);m.visitVarInsn(ASTORE,3);
m.visitLabel(loop);m.visitVarInsn(ALOAD,3);m.visitMethodInsn(INVOKEINTERFACE,"java/util/Iterator","hasNext","()Z",true);m.visitJumpInsn(IFEQ,end);
m.visitVarInsn(ALOAD,3);m.visitMethodInsn(INVOKEINTERFACE,"java/util/Iterator","next","()Ljava/lang/Object;",true);m.visitTypeInsn(CHECKCAST,H);m.visitVarInsn(ASTORE,4);
m.visitVarInsn(ALOAD,4);m.visitFieldInsn(GETFIELD,H,"distance","D");m.visitVarInsn(ALOAD,0);m.visitFieldInsn(GETFIELD,R,"blockDistance","D");m.visitInsn(DCMPL);m.visitJumpInsn(IFGE,end);
m.visitVarInsn(ALOAD,2);m.visitVarInsn(ALOAD,4);m.visitFieldInsn(GETFIELD,H,"entity","Ljava/lang/Object;");m.visitMethodInsn(INVOKEINTERFACE,"java/util/function/Predicate","test","(Ljava/lang/Object;)Z",true);m.visitJumpInsn(IFNE,loop);
m.visitVarInsn(ALOAD,4);m.visitFieldInsn(GETFIELD,H,"entity","Ljava/lang/Object;");m.visitInsn(ARETURN);
m.visitLabel(end);m.visitInsn(ACONST_NULL);m.visitInsn(ARETURN);m.visitMaxs(0,0);m.visitEnd();}
}
