import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import org.objectweb.asm.*;
public final class AddPingCompass implements Opcodes {
  static final String HUD="com/yny/utils/modules/clanping/ClanPingHud.class";
  static final String OWNER="com/yny/utils/modules/clanping/ClanPingHud";
  static final String COMPASS="com/yny/utils/modules/clanping/PingCompass.class";
  static int width,text,draw,added;
  public static void main(String[] args)throws Exception {
    if(args.length!=3)throw new IllegalArgumentException("input output compass.class");
    try(ZipFile in=new ZipFile(args[0]);ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(Path.of(args[1])))){
      var entries=in.entries();while(entries.hasMoreElements()){
        ZipEntry e=entries.nextElement();byte[] b;
        try(InputStream s=in.getInputStream(e)){b=s.readAllBytes();}
        if(e.getName().equals(HUD)){
          ClassWriter w=new ClassWriter(0);
          new ClassReader(b).accept(new ClassVisitor(ASM9,w){            public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] ex){
              MethodVisitor mv=super.visitMethod(access,name,desc,sig,ex);
              return new MethodVisitor(ASM9,mv){
                public void visitIntInsn(int opcode,int operand){
                  if(opcode==BIPUSH&&name.equals("width")&&operand==12){width++;operand=40;}
                  if(opcode==BIPUSH&&name.equals("draw")&&operand==7){text++;operand=35;}
                  super.visitIntInsn(opcode,operand);
                }
                public void visitMethodInsn(int opcode,String owner,String method,String descriptor,boolean itf){
                  super.visitMethodInsn(opcode,owner,method,descriptor,itf);
                  if(name.equals("draw")&&owner.equals("dev/xavier/stein/loader/api/Render")&&method.equals("rect")){
                    draw++;
                    super.visitVarInsn(ALOAD,0);super.visitFieldInsn(GETFIELD,OWNER,"title","Ljava/lang/String;");                    super.visitVarInsn(ALOAD,0);super.visitFieldInsn(GETFIELD,OWNER,"detail","Ljava/lang/String;");
                    super.visitVarInsn(ILOAD,4);super.visitInsn(I2F);
                    super.visitVarInsn(ALOAD,0);super.visitMethodInsn(INVOKEVIRTUAL,OWNER,"height","()I",false);super.visitInsn(I2F);
                    super.visitMethodInsn(INVOKESTATIC,"com/yny/utils/modules/clanping/PingCompass","draw","(Ljava/lang/String;Ljava/lang/String;FF)V",false);
                  }
                }
              };
            }
          },0);b=w.toByteArray();
        }
        if(e.getName().equals(COMPASS))throw new IllegalStateException("Compass already included");
        ZipEntry copy=new ZipEntry(e.getName());copy.setTime(e.getTime());out.putNextEntry(copy);out.write(b);out.closeEntry();
      }
      ZipEntry e=new ZipEntry(COMPASS);out.putNextEntry(e);out.write(Files.readAllBytes(Path.of(args[2])));out.closeEntry();added++;
    }    if(width!=1||text!=2||draw!=1||added!=1){Files.deleteIfExists(Path.of(args[1]));throw new IllegalStateException("counts "+width+" "+text+" "+draw+" "+added);}
    System.out.println("Compass added to Clan Ping HUD");
  }
}