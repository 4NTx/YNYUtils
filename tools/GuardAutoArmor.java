import java.io.*; import java.nio.file.*; import java.util.zip.*; import org.objectweb.asm.*;
public final class GuardAutoArmor implements Opcodes {
 static int timeout,guard;
 public static void main(String[] a)throws Exception{
 if(a.length!=2)throw new IllegalArgumentException("input output");
 Path out=Path.of(a[1]);
 try(ZipFile in=new ZipFile(a[0]);ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(out))){
 var entries=in.entries();
 while(entries.hasMoreElements()){
 ZipEntry e=entries.nextElement();byte[] b;
 try(InputStream s=in.getInputStream(e)){b=s.readAllBytes();}
 if(e.getName().equals("com/yny/utils/modules/player/AutoArmor.class")){
 ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
 new ClassReader(b).accept(new ClassVisitor(ASM9,w){
 public MethodVisitor visitMethod(int ac,String n,String d,String sg,String[] ex){
 MethodVisitor m=super.visitMethod(ac,n,d,sg,ex);
 if(n.equals("advancePending"))return new MethodVisitor(ASM9,m){
 int count;
 public void visitLdcInsn(Object v){
 if(Long.valueOf(8L).equals(v)&&++count==2){v=Long.MAX_VALUE;timeout++;}
 super.visitLdcInsn(v);
 }};
 if(n.equals("beginEquip"))return new MethodVisitor(ASM9,m){
 public void visitCode(){
 super.visitCode();Label ok=new Label();
 super.visitVarInsn(ALOAD,0);
 super.visitFieldInsn(GETFIELD,"com/yny/utils/modules/player/AutoArmor","tick","J");
 super.visitVarInsn(ALOAD,0);
 super.visitFieldInsn(GETFIELD,"com/yny/utils/modules/player/AutoArmor","retryAt","[J");
 super.visitVarInsn(ILOAD,1);super.visitInsn(LALOAD);super.visitInsn(LCMP);
 super.visitJumpInsn(IFGE,ok);super.visitInsn(RETURN);super.visitLabel(ok);guard++;
 }};
 return m;
 }},ClassReader.EXPAND_FRAMES);b=w.toByteArray();}
 ZipEntry copy=new ZipEntry(e.getName());copy.setTime(e.getTime());
 zip.putNextEntry(copy);zip.write(b);zip.closeEntry();
 }}
 if(timeout!=1||guard!=1){Files.deleteIfExists(out);throw new IllegalStateException("counts "+timeout+" "+guard);}
 System.out.println("AutoArmor guard patched");
 }}