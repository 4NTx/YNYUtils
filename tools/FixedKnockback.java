import java.io.*;
import java.nio.file.*;
import java.util.Set;
import java.util.zip.*;
import org.objectweb.asm.*;
public final class FixedKnockback implements Opcodes {
  static final String MOD="com/yny/utils/YNYUtils.class";
  static final String HUD="com/yny/utils/ui/PvpStatusHud.class";
  static final Set<String> REMOVE=Set.of("Ativar redução de knockback","Knockback recebido","Manter impulso vertical normal","Tecla para alternar knockback","Mostrar status do knockback na HUD");
  static int labels,options,key,constants,hudDefault,hudMethod,section;
  public static void main(String[] args)throws Exception {
    if(args.length!=2)throw new IllegalArgumentException("input output");
    try(ZipFile in=new ZipFile(args[0]);ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(Path.of(args[1])))){
      var entries=in.entries();while(entries.hasMoreElements()){
        ZipEntry e=entries.nextElement();byte[] b;
        try(InputStream s=in.getInputStream(e)){b=s.readAllBytes();}        if(e.getName().equals(MOD)||e.getName().equals(HUD)){
          ClassWriter w=new ClassWriter(0);
          new ClassReader(b).accept(new ClassVisitor(ASM9,w){
            public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] ex){
              MethodVisitor mv=super.visitMethod(access,name,desc,sig,ex);
              if(e.getName().equals(MOD)&&name.startsWith("lambda$new$")&&
                 (name.equals("lambda$new$2")||name.equals("lambda$new$3")||name.equals("lambda$new$4"))){
                constants++;mv.visitCode();
                if(name.equals("lambda$new$3"))mv.visitIntInsn(BIPUSH,92);else mv.visitInsn(name.equals("lambda$new$4")?ICONST_0:ICONST_1);
                mv.visitInsn(IRETURN);mv.visitMaxs(1,0);mv.visitEnd();return null;
              }
              if(e.getName().equals(HUD)&&name.equals("showKnockback")&&desc.equals("(Z)V")){
                hudMethod++;mv.visitCode();mv.visitInsn(RETURN);mv.visitMaxs(0,2);mv.visitEnd();return null;
              }
              return new MethodVisitor(ASM9,mv){                boolean pending,firstKey=true;
                public void visitLdcInsn(Object value){
                  if(e.getName().equals(MOD)&&name.equals("onPage")){
                    if(REMOVE.contains(value)){pending=true;labels++;}
                    if("PvP - Knockback e aliados".equals(value)){value="PvP - Aliados";section++;}
                  }
                  if(e.getName().equals(HUD)&&name.equals("<init>")&&"Knockback: ATIVADO".equals(value)){
                    value="Notificações de PvP";hudDefault++;
                  }
                  super.visitLdcInsn(value);
                }
                public void visitMethodInsn(int opcode,String owner,String method,String descriptor,boolean itf){
                  if(e.getName().equals(MOD)&&name.equals("onPage")&&pending&&owner.equals("dev/xavier/stein/loader/api/Page")&&method.equals("option")){
                    options++;pending=false;super.visitInsn(POP);return;
                  }                  super.visitMethodInsn(opcode,owner,method,descriptor,itf);
                  if(e.getName().equals(MOD)&&name.equals("updateToggleKey")&&firstKey&&owner.equals("com/yny/utils/core/ToggleKey")&&method.equals("wasPressed")){
                    firstKey=false;key++;super.visitInsn(POP);super.visitInsn(ICONST_0);
                  }
                }
              };
            }
          },0);b=w.toByteArray();
        }
        ZipEntry copy=new ZipEntry(e.getName());copy.setTime(e.getTime());out.putNextEntry(copy);out.write(b);out.closeEntry();
      }
    }
    if(labels!=5||options!=5||key!=1||constants!=3||hudDefault!=1||hudMethod!=1||section!=1){
      Files.deleteIfExists(Path.of(args[1]));throw new IllegalStateException("counts "+labels+" "+options+" "+key+" "+constants+" "+hudDefault+" "+hudMethod+" "+section);
    }
    System.out.println("Knockback fixed at 92% X/Y/Z; controls and visual notice removed");
  }
}