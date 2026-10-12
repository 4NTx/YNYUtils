import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import org.objectweb.asm.*;
public final class ArmorThresholdPatch implements Opcodes {
 static final String H="com/yny/utils/modules/player/ArmorThreshold";
 static final String C="com/yny/utils/config/YNYConfig";
 static int slider,format,supplier,sanitize,getter,setter;
 public static void main(String[] args)throws Exception {
  if(args.length!=3)throw new IllegalArgumentException("input output helper.class");
  try(ZipFile input=new ZipFile(args[0]);ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(Path.of(args[1])))){
   var entries=input.entries();
   while(entries.hasMoreElements()){
    ZipEntry e=entries.nextElement();byte[] bytes;
    try(InputStream s=input.getInputStream(e)){bytes=s.readAllBytes();}
    if(e.getName().equals("com/yny/utils/YNYUtils.class")||e.getName().equals(C+".class")){
     String entry=e.getName();ClassWriter w=new ClassWriter(0);
     new ClassReader(bytes).accept(new ClassVisitor(ASM9,w){
      public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] exceptions){
       MethodVisitor mv=super.visitMethod(access,name,desc,sig,exceptions);
       if(entry.equals("com/yny/utils/YNYUtils.class")&&name.equals("lambda$onPage$35")){
        getter++;mv.visitCode();mv.visitFieldInsn(GETSTATIC,"com/yny/utils/YNYUtils","config","L"+C+";");
        mv.visitFieldInsn(GETFIELD,C,"autoArmorPreventiveThreshold","I");
        mv.visitMethodInsn(INVOKESTATIC,H,"toSlider","(I)D",false);mv.visitInsn(DRETURN);mv.visitMaxs(2,0);mv.visitEnd();return null;
       }
       if(entry.equals("com/yny/utils/YNYUtils.class")&&name.equals("lambda$onPage$36")){
        setter++;mv.visitCode();mv.visitFieldInsn(GETSTATIC,"com/yny/utils/YNYUtils","config","L"+C+";");
        mv.visitVarInsn(DLOAD,1);mv.visitInsn(D2I);mv.visitMethodInsn(INVOKESTATIC,H,"fromSlider","(I)I",false);
        mv.visitFieldInsn(PUTFIELD,C,"autoArmorPreventiveThreshold","I");mv.visitVarInsn(ALOAD,0);
        mv.visitMethodInsn(INVOKEVIRTUAL,"com/yny/utils/YNYUtils","markConfigChanged","()V",false);
        mv.visitInsn(RETURN);mv.visitMaxs(3,3);mv.visitEnd();return null;
       }
       return new MethodVisitor(ASM9,mv){
        boolean inSlider;int d2i;
        public void visitLdcInsn(Object value){
         if(name.equals("onPage")&&entry.equals("com/yny/utils/YNYUtils.class")){
          if("Troca preventiva abaixo de".equals(value)){inSlider=true;}
          else if(inSlider&&Double.valueOf(50).equals(value)){value=8.0;slider++;}
          else if(inSlider&&Double.valueOf(5).equals(value)){value=1.0;slider++;}
         }
         super.visitLdcInsn(value);
        }
        public void visitInsn(int op){
         super.visitInsn(op);
         if(name.equals("lambda$onPage$37")&&op==D2I&&++d2i==2){
          super.visitMethodInsn(INVOKESTATIC,H,"fromSlider","(I)I",false);format++;
         }
        }
        public void visitFieldInsn(int op,String owner,String field,String descriptor){
         if(entry.equals(C+".class")&&name.equals("sanitize")&&op==PUTFIELD&&field.equals("autoArmorPreventiveThreshold")){
          super.visitMethodInsn(INVOKESTATIC,H,"normalize","(I)I",false);sanitize++;
         }
         super.visitFieldInsn(op,owner,field,descriptor);
         if(entry.equals("com/yny/utils/YNYUtils.class")&&name.equals("lambda$new$14")&&op==GETFIELD&&field.equals("autoArmorPreventiveThreshold")){
          super.visitMethodInsn(INVOKESTATIC,H,"normalize","(I)I",false);supplier++;
         }
        }
        public void visitMethodInsn(int op,String owner,String method,String descriptor,boolean itf){
         if(name.equals("onPage")&&owner.equals("dev/xavier/stein/loader/api/Option")&&method.equals("slider"))inSlider=false;
         super.visitMethodInsn(op,owner,method,descriptor,itf);
        }
       };
      }
     },0);bytes=w.toByteArray();
    }
    ZipEntry copy=new ZipEntry(e.getName());copy.setTime(e.getTime());out.putNextEntry(copy);out.write(bytes);out.closeEntry();
   }
   ZipEntry helper=new ZipEntry(H+".class");out.putNextEntry(helper);out.write(Files.readAllBytes(Path.of(args[2])));out.closeEntry();
  }
  if(slider!=2||format!=1||supplier!=1||sanitize!=1||getter!=1||setter!=1)throw new IllegalStateException("counts "+slider+" "+format+" "+supplier+" "+sanitize+" "+getter+" "+setter);
  System.out.println("Armor threshold slider: break only or 15-50%");
 }
}
