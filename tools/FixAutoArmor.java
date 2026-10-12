import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import org.objectweb.asm.*;
public final class FixAutoArmor implements Opcodes {
 static int defer,settle,retry;
 public static void main(String[] args)throws Exception {
  if(args.length!=2)throw new IllegalArgumentException("input output");
  Path output=Path.of(args[1]);
  try(ZipFile input=new ZipFile(args[0]);ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(output))){
   var entries=input.entries();
   while(entries.hasMoreElements()){
    ZipEntry entry=entries.nextElement();byte[] bytes;
    try(InputStream stream=input.getInputStream(entry)){bytes=stream.readAllBytes();}    if(entry.getName().equals("com/yny/utils/modules/player/AutoArmor.class")){
     ClassWriter writer=new ClassWriter(0);
     new ClassReader(bytes).accept(new ClassVisitor(ASM9,writer){
      public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] exceptions){
       MethodVisitor method=super.visitMethod(access,name,desc,sig,exceptions);
       if(name.equals("deferPreventiveUntilBroken")){
        defer++;method.visitCode();method.visitInsn(RETURN);method.visitMaxs(0,2);method.visitEnd();return null;
       }
       if(name.equals("advancePending"))return new MethodVisitor(ASM9,method){
        boolean afterAge;        public void visitFieldInsn(int op,String owner,String field,String desc){
         super.visitFieldInsn(op,owner,field,desc);
         if(op==GETFIELD&&owner.endsWith("AutoArmor$Pending")&&field.equals("age"))afterAge=true;
        }
        public void visitInsn(int op){
         if(afterAge&&op==ICONST_3){op=ICONST_1;settle++;}
         afterAge=false;super.visitInsn(op);
        }
       };
       if(name.equals("scheduleRetry"))return new MethodVisitor(ASM9,method){
        public void visitLdcInsn(Object value){
         if(Long.valueOf(3L).equals(value)){value=2L;retry++;}
         else if(Long.valueOf(10L).equals(value)){value=4L;retry++;}
         super.visitLdcInsn(value);        }
       };
       return method;
      }
     },0);bytes=writer.toByteArray();
    }
    ZipEntry copy=new ZipEntry(entry.getName());copy.setTime(entry.getTime());
    zip.putNextEntry(copy);zip.write(bytes);zip.closeEntry();
   }
  }
  if(defer!=1||settle!=1||retry!=2){Files.deleteIfExists(output);throw new IllegalStateException("counts "+defer+" "+settle+" "+retry);}
  System.out.println("Auto Armor preventive retry and speed patched");
 }
}
