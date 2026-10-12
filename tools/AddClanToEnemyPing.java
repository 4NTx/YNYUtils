import java.io.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Adds the local server-formatted player display name (including team/clan prefix) to the enemy ping label. */
public final class AddClanToEnemyPing {
  private static final String OWNER="com/yny/utils/modules/clanping/ClanPing";
  private static final String ACTIVE="com/yny/utils/modules/clanping/ClanPing$Active";
  private static final String ELEMENT="com/yny/utils/modules/clanping/ClanPing.class";
  private AddClanToEnemyPing() { }
  public static void main(String[] a) throws Exception {
    if(a.length!=2) throw new IllegalArgumentException("usage: AddClanToEnemyPing input.steinmod output.steinmod");
    try(ZipInputStream in=new ZipInputStream(new FileInputStream(a[0])); ZipOutputStream out=new ZipOutputStream(new FileOutputStream(a[1]))) {
      ZipEntry e; boolean found=false;
      while((e=in.getNextEntry())!=null) {
        ByteArrayOutputStream b=new ByteArrayOutputStream(); in.transferTo(b); byte[] bytes=b.toByteArray();
        if(e.getName().equals(ELEMENT)){bytes=patch(bytes);found=true;}
        ZipEntry copy=new ZipEntry(e.getName()); copy.setTime(e.getTime()); out.putNextEntry(copy); out.write(bytes); out.closeEntry();
      }
      if(!found) throw new IllegalStateException("ClanPing.class not found");
    }
  }
  private static byte[] patch(byte[] bytes) {
    int originalVersion=((bytes[6]&255)<<8)|(bytes[7]&255);
    byte[] readable=bytes.clone();
    if(originalVersion>60){readable[6]=0;readable[7]=60;}
    ClassNode c=new ClassNode(); new ClassReader(readable).accept(c,0);
    c.version=originalVersion;
    MethodNode render=null;
    for(MethodNode m:c.methods) if(m.name.equals("renderWorld")&&m.desc.equals("(F)V")) render=m;
    if(render==null) throw new IllegalStateException("renderWorld(F)V missing");
    int count=0;
    for(AbstractInsnNode n:render.instructions.toArray()) if(n instanceof FieldInsnNode) {
      FieldInsnNode f=(FieldInsnNode)n;
      if(f.getOpcode()==Opcodes.GETFIELD&&f.owner.equals(ACTIVE)&&f.name.equals("targetName")&&f.desc.equals("Ljava/lang/String;")) {
        InsnList replacement=new InsnList(); replacement.add(new VarInsnNode(Opcodes.ALOAD,3)); replacement.add(new InsnNode(Opcodes.SWAP));
        replacement.add(new MethodInsnNode(Opcodes.INVOKESTATIC,OWNER,"targetDisplayName","(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/String;",false));
        render.instructions.insert(f,replacement); count++;
      }
    }
    if(count!=1) throw new IllegalStateException("expected one live enemy label name, found "+count);
    MethodNode helper=new MethodNode(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC,"targetDisplayName","(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/String;",null,null);
    LabelNode fallback=new LabelNode(); InsnList i=helper.instructions;
    i.add(new VarInsnNode(Opcodes.ALOAD,0));
    i.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/xavier/stein/loader/api/Entities","displayName","(Ljava/lang/Object;)Ljava/lang/String;",false));
    i.add(new VarInsnNode(Opcodes.ASTORE,2)); i.add(new VarInsnNode(Opcodes.ALOAD,2)); i.add(new JumpInsnNode(Opcodes.IFNULL,fallback));
    i.add(new VarInsnNode(Opcodes.ALOAD,2)); i.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"java/lang/String","trim","()Ljava/lang/String;",false));
    i.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"java/lang/String","isEmpty","()Z",false)); i.add(new JumpInsnNode(Opcodes.IFNE,fallback));
    i.add(new VarInsnNode(Opcodes.ALOAD,2)); i.add(new InsnNode(Opcodes.ARETURN));
    i.add(fallback); i.add(new VarInsnNode(Opcodes.ALOAD,1)); i.add(new InsnNode(Opcodes.ARETURN));
    helper.maxLocals=3; helper.maxStack=1; c.methods.add(helper);
    ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS); c.accept(w); return w.toByteArray();
  }
}



