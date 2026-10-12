import java.io.*; import java.nio.file.*; import java.util.zip.*; import org.objectweb.asm.*;
public final class FixInvMoveDefense implements Opcodes {
 static int label,removed,constant,guard;
 public static void main(String[] a)throws Exception {
  if(a.length!=2)throw new IllegalArgumentException("input output");
  Path out=Path.of(a[1]);
  try(ZipFile in=new ZipFile(a[0]);ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(out))){
   var entries=in.entries();
   while(entries.hasMoreElements()){
    ZipEntry e=entries.nextElement(); byte[] b;
    try(InputStream s=in.getInputStream(e)){b=s.readAllBytes();}
    String name=e.getName();
    if(name.equals("com/yny/utils/YNYUtils.class")){
     ClassWriter w=new ClassWriter(0);
     new ClassReader(b).accept(new ClassVisitor(ASM9,w){
      public MethodVisitor visitMethod(int access,String method,String desc,String sig,String[] ex){
       MethodVisitor mv=super.visitMethod(access,method,desc,sig,ex);
       if(method.equals("lambda$new$31")&&desc.equals("()Z")){
        constant++;mv.visitCode();mv.visitInsn(ICONST_1);mv.visitInsn(IRETURN);mv.visitMaxs(1,0);mv.visitEnd();return null;
       }
       if(method.equals("onPage"))return new MethodVisitor(ASM9,mv){
        boolean pending;
        public void visitLdcInsn(Object value){
         if("Soltar defesa ao mover (InvMove)".equals(value)){pending=true;label++;}
         super.visitLdcInsn(value);
        }
        public void visitMethodInsn(int op,String owner,String m,String d,boolean itf){
         if(pending&&owner.equals("dev/xavier/stein/loader/api/Page")&&m.equals("option")){
          removed++;pending=false;super.visitInsn(POP);return;
         }

         super.visitMethodInsn(op,owner,m,d,itf);
        }
       };
       return mv;
      }
     },0);b=w.toByteArray();
    } else if(name.equals("com/yny/utils/modules/player/InventoryMove.class")){
     ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
     new ClassReader(b).accept(new ClassVisitor(ASM9,w){
      public MethodVisitor visitMethod(int access,String method,String desc,String sig,String[] ex){
       MethodVisitor mv=super.visitMethod(access,method,desc,sig,ex);
       if(method.equals("onTickEnd"))return new MethodVisitor(ASM9,mv){
        boolean ad,synced;
        public void visitFieldInsn(int op,String owner,String field,String d){
         if(op==GETFIELD&&owner.equals("avh")&&field.equals("ad"))ad=true;
         super.visitFieldInsn(op,owner,field,d);
        }
        public void visitMethodInsn(int op,String owner,String m,String d,boolean itf){
         if(ad&&op==INVOKESTATIC&&owner.equals("com/yny/utils/modules/player/InventoryMove")&&m.equals("sync")){synced=true;ad=false;}
         if(owner.equals("com/yny/utils/modules/player/InventoryMove")&&m.equals("stopSwordBlocking")){super.visitInsn(POP);return;}
         super.visitMethodInsn(op,owner,m,d,itf);
        }
        public void visitInsn(int op){
         super.visitInsn(op);
         if(synced&&op==POP){
          Label done=new Label();
          super.visitVarInsn(ILOAD,2);
          super.visitJumpInsn(IFEQ,done);
          super.visitVarInsn(ALOAD,1);
          super.visitMethodInsn(INVOKESTATIC,"com/yny/utils/modules/player/InventoryMove","stopSwordBlocking","(Lave;)V",false);
          super.visitLabel(done);
          synced=false;guard++;
         }
        }
       };
       return mv;
      }
     },ClassReader.EXPAND_FRAMES);b=w.toByteArray();
    }
    ZipEntry copy=new ZipEntry(name);copy.setTime(e.getTime());zip.putNextEntry(copy);zip.write(b);zip.closeEntry();
   }
  }
  if(label!=1||removed!=1||constant!=1||guard!=1){Files.deleteIfExists(out);throw new IllegalStateException("counts "+label+" "+removed+" "+constant+" "+guard);}
  System.out.println("InvMove defense forced and control removed");
 }
}