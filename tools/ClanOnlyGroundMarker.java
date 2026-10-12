import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import org.objectweb.asm.*;
public final class ClanOnlyGroundMarker implements Opcodes {
  static final String TARGET="com/yny/utils/modules/pvp/AllyGroundMarker.class";
  static int replacements;
  public static void main(String[] args)throws Exception {
    if(args.length!=2)throw new IllegalArgumentException("input output");
    try(ZipFile in=new ZipFile(args[0]);ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(Path.of(args[1])))){
      var entries=in.entries();
      while(entries.hasMoreElements()){
        ZipEntry e=entries.nextElement();byte[] bytes;
        try(InputStream s=in.getInputStream(e)){bytes=s.readAllBytes();}
        if(e.getName().equals(TARGET)){
          ClassWriter writer=new ClassWriter(0);
          new ClassReader(bytes).accept(new ClassVisitor(ASM9,writer){
            public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] ex){              MethodVisitor mv=super.visitMethod(access,name,desc,sig,ex);
              if(!name.equals("renderWorld"))return mv;
              return new MethodVisitor(ASM9,mv){
                public void visitMethodInsn(int opcode,String owner,String method,String descriptor,boolean itf){
                  if(opcode==INVOKEVIRTUAL&&owner.equals("dev/xavier/stein/loader/api/Relation")&&method.equals("friendly")&&descriptor.equals("()Z")){
                    replacements++;
                    super.visitFieldInsn(GETSTATIC,owner,"GROUP","Ldev/xavier/stein/loader/api/Relation;");
                    super.visitMethodInsn(INVOKEVIRTUAL,"java/lang/Object","equals","(Ljava/lang/Object;)Z",false);
                  }else super.visitMethodInsn(opcode,owner,method,descriptor,itf);
                }
              };
            }
          },0);
          bytes=writer.toByteArray();
        }
        ZipEntry copy=new ZipEntry(e.getName());copy.setTime(e.getTime());
        out.putNextEntry(copy);out.write(bytes);out.closeEntry();      }
    }
    if(replacements!=1){Files.deleteIfExists(Path.of(args[1]));throw new IllegalStateException("replacements "+replacements);}
    System.out.println("Ground marker restricted to GROUP");
  }
}
