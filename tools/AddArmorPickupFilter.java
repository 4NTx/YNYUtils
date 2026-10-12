import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import org.objectweb.asm.*;
public final class AddArmorPickupFilter implements Opcodes {
 static final String M="com/yny/utils/YNYUtils";
 static final String C="com/yny/utils/config/YNYConfig";
 static final String F="com/yny/utils/modules/player/ArmorPickupFilter";
 static final String O="com/yny/utils/modules/player/ArmorPickupOptions";
 static int page,tick,slot,items,reset;
 public static void main(String[] args)throws Exception {
  if(args.length!=3)throw new IllegalArgumentException("input output classes-dir");
  Path classes=Path.of(args[2]);
  try(ZipFile input=new ZipFile(args[0]);ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(Path.of(args[1])))){
   var entries=input.entries();
   while(entries.hasMoreElements()){
    ZipEntry e=entries.nextElement();String name=e.getName();byte[] bytes;
    try(InputStream s=input.getInputStream(e)){bytes=s.readAllBytes();}
    if(name.equals(M+".class")){
     ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
     new ClassReader(bytes).accept(new ClassVisitor(ASM9,w){
      public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] exceptions){
       MethodVisitor mv=super.visitMethod(access,name,desc,sig,exceptions);
       return new MethodVisitor(ASM9,mv){
        boolean threshold,sliderCalled;
        public void visitLdcInsn(Object value){
         if(name.equals("onPage")&&"Troca preventiva abaixo de".equals(value))threshold=true;
         super.visitLdcInsn(value);
        }
        public void visitMethodInsn(int op,String owner,String method,String descriptor,boolean itf){
         if(name.equals("onTickEnd")&&owner.equals("com/yny/utils/modules/player/AutoArmor")&&method.equals("onTickEnd")){
          super.visitFieldInsn(GETSTATIC,M,"config","L"+C+";");
          super.visitMethodInsn(INVOKESTATIC,F,"runAutoArmor","(Lcom/yny/utils/modules/player/AutoArmor;L"+C+";)V",false);
          tick++;return;
         }
         super.visitMethodInsn(op,owner,method,descriptor,itf);
         if(name.equals("onPage")&&threshold&&owner.equals("dev/xavier/stein/loader/api/Option")&&method.equals("slider"))sliderCalled=true;
         else if(name.equals("onPage")&&sliderCalled&&owner.equals("dev/xavier/stein/loader/api/Page")&&method.equals("option")){
          super.visitFieldInsn(GETSTATIC,M,"config","L"+C+";");
          super.visitVarInsn(ALOAD,0);
          super.visitFieldInsn(GETFIELD,M,"configStore","Ldev/xavier/stein/loader/api/ModConfig;");
          super.visitMethodInsn(INVOKESTATIC,O,"add","(Ldev/xavier/stein/loader/api/Page;L"+C+";Ldev/xavier/stein/loader/api/ModConfig;)Ldev/xavier/stein/loader/api/Page;",false);
          page++;threshold=false;sliderCalled=false;
         }
        }
        public void visitInsn(int op){
         if(op==RETURN){
          if(name.equals("onSlotChanged")){
           super.visitVarInsn(ILOAD,1);super.visitVarInsn(ILOAD,2);super.visitVarInsn(ALOAD,3);
           super.visitFieldInsn(GETSTATIC,M,"config","L"+C+";");
           super.visitMethodInsn(INVOKESTATIC,F,"onSlotChanged","(IILjava/lang/Object;L"+C+";)V",false);slot++;
          }else if(name.equals("onWindowItems")){
           super.visitVarInsn(ILOAD,1);super.visitMethodInsn(INVOKESTATIC,F,"onWindowItems","(I)V",false);items++;
          }else if(name.equals("onJoinGame")||name.equals("onDisconnected")){
           super.visitMethodInsn(INVOKESTATIC,F,"reset","()V",false);reset++;
          }
         }
         super.visitInsn(op);
        }
       };
      }
     },0);bytes=w.toByteArray();
    }else if(name.equals(C+".class")||name.equals("com/yny/utils/modules/player/ArmorThreshold.class")){
     bytes=Files.readAllBytes(classes.resolve(name));
    }
    ZipEntry copy=new ZipEntry(name);copy.setTime(e.getTime());out.putNextEntry(copy);out.write(bytes);out.closeEntry();
   }
   for(String name:new String[]{F+".class",O+".class","com/yny/utils/modules/player/ArmorPickupPolicy.class"}){
    ZipEntry extra=new ZipEntry(name);out.putNextEntry(extra);out.write(Files.readAllBytes(classes.resolve(name)));out.closeEntry();
   }
  }
  if(page!=1||tick!=1||slot!=1||items!=1||reset!=2)throw new IllegalStateException("counts "+page+" "+tick+" "+slot+" "+items+" "+reset);
  System.out.println("Armor pickup filter hooks installed");
 }
}
