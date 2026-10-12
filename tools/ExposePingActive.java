import java.io.*;import java.nio.file.*;import java.util.zip.*;import org.objectweb.asm.*;
public final class ExposePingActive implements Opcodes {
 static int changed;
 public static void main(String[] a)throws Exception{
  if(a.length!=2)throw new IllegalArgumentException("input output");
  Path out=Path.of(a[1]);
  try(ZipFile in=new ZipFile(a[0]);ZipOutputStream z=new ZipOutputStream(Files.newOutputStream(out))){
   var entries=in.entries();while(entries.hasMoreElements()){
    ZipEntry e=entries.nextElement();byte[] b;
    try(InputStream s=in.getInputStream(e)){b=s.readAllBytes();}
    if(e.getName().equals("com/yny/utils/modules/clanping/ClanPing.class")){
     ClassWriter w=new ClassWriter(0);
     new ClassReader(b).accept(new ClassVisitor(ASM9,w){
      public FieldVisitor visitField(int access,String name,String desc,String sig,Object value){
       if(name.equals("active")&&desc.equals("Lcom/yny/utils/modules/clanping/ClanPing$Active;")){access&=~ACC_PRIVATE;changed++;}
       return super.visitField(access,name,desc,sig,value);
      }
     },0);b=w.toByteArray();
    }
    ZipEntry copy=new ZipEntry(e.getName());copy.setTime(e.getTime());z.putNextEntry(copy);z.write(b);z.closeEntry();
   }
  }
  if(changed!=1){Files.deleteIfExists(out);throw new IllegalStateException("active field count "+changed);}
  System.out.println("ClanPing active readable by HUD");
 }
}