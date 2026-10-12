import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import org.objectweb.asm.*;
public final class ImprovePingHud implements Opcodes {
  static final String PING="com/yny/utils/modules/clanping/ClanPing.class";
  static final String MOD="com/yny/utils/YNYUtils.class";
  static final String OWNER="com/yny/utils/modules/clanping/ClanPing";
  static final String[] OLD={"↑","↗","→","↘","↓","↙","←","↖"};
  static final String[] NEW={"§e↑ Frente§f","§e↗ Direita à frente§f","§e→ Direita§f","§e↘ Direita atrás§f","§e↓ Atrás§f","§e↙ Esquerda atrás§f","§e← Esquerda§f","§e↖ Esquerda à frente§f"};
  static int arrows,angle,detail,removed,label;
  public static void main(String[] args)throws Exception {
    if(args.length!=2)throw new IllegalArgumentException("input output");
    try(ZipFile in=new ZipFile(args[0]); ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(Path.of(args[1])))){
      var entries=in.entries(); while(entries.hasMoreElements()){
        ZipEntry e=entries.nextElement(); byte[] bytes;        try(InputStream s=in.getInputStream(e)){bytes=s.readAllBytes();}
        if(e.getName().equals(PING)||e.getName().equals(MOD)){
          ClassWriter writer=new ClassWriter(0);
          new ClassReader(bytes).accept(new ClassVisitor(ASM9,writer){
            public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] ex){
              MethodVisitor mv=super.visitMethod(access,name,desc,sig,ex);
              if(e.getName().equals(PING)&&name.equals("drawDistantIndicator")&&desc.equals("()V")){
                removed++; mv.visitCode(); mv.visitInsn(RETURN); mv.visitMaxs(0,1); mv.visitEnd(); return null;
              }
              return new MethodVisitor(ASM9,mv){
                public void visitLdcInsn(Object value){
                  if(e.getName().equals(PING)&&name.equals("direction")){
                    if(value instanceof Double && ((Double)value)==90.0d){angle++;value=-90.0d;}                    else if(value instanceof String){for(int i=0;i<OLD.length;i++)if(OLD[i].equals(value)){arrows++;value=NEW[i];break;}}
                  }
                  if(e.getName().equals(MOD)&&"Seta para inimigo distante".equals(value)){label++;value="Mostrar direção na HUD do ping";}
                  super.visitLdcInsn(value);
                }
                public void visitMethodInsn(int opcode,String owner,String method,String descriptor,boolean itf){
                  super.visitMethodInsn(opcode,owner,method,descriptor,itf);
                  if(e.getName().equals(PING)&&name.equals("hudDetail")&&owner.equals(OWNER)&&method.equals("direction")){
                    detail++; super.visitVarInsn(ALOAD,0);
                    super.visitFieldInsn(GETFIELD,OWNER,"distantIndicatorEnabled","Ljava/util/function/BooleanSupplier;");
                    super.visitMethodInsn(INVOKEINTERFACE,"java/util/function/BooleanSupplier","getAsBoolean","()Z",true);                    super.visitMethodInsn(INVOKEVIRTUAL,"java/lang/String","repeat","(I)Ljava/lang/String;",false);
                  }
                }
              };
            }
          },0); bytes=writer.toByteArray();
        }
        ZipEntry copy=new ZipEntry(e.getName()); copy.setTime(e.getTime()); out.putNextEntry(copy); out.write(bytes); out.closeEntry();
      }
    }
    if(arrows!=8||angle!=1||detail!=1||removed!=1||label!=1){Files.deleteIfExists(Path.of(args[1]));throw new IllegalStateException("counts "+arrows+" "+angle+" "+detail+" "+removed+" "+label);}
    System.out.println("Ping HUD patched: eight directions, one angle, one toggle, floating arrow removed");
  }
}