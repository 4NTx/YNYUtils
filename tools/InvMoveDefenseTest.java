import com.yny.utils.config.YNYConfig;
import java.util.zip.*;import java.io.*;import org.objectweb.asm.*;
public final class InvMoveDefenseTest implements Opcodes {
 static int label,removed,guard,constant;
 public static void main(String[] a)throws Exception {
  Class<?> move=Class.forName("com.yny.utils.modules.player.InventoryMove");
  java.lang.reflect.Method suspend=move.getDeclaredMethod("shouldSuspendUse",boolean.class);suspend.setAccessible(true);
  if(!(boolean)suspend.invoke(null,true)||(boolean)suspend.invoke(null,false))throw new AssertionError("movement predicate");
  YNYConfig config=new YNYConfig();
  config.inventoryMoveSuspendUse=false;config.sanitize();
  if(!config.inventoryMoveSuspendUse)throw new AssertionError("old config disabled defense release");
  try(ZipFile z=new ZipFile(a[0])){
   check(z,"com/yny/utils/YNYUtils.class",false);
   check(z,"com/yny/utils/modules/player/InventoryMove.class",true);
  }
  if(label!=1||removed!=1||guard!=1||constant!=1)throw new AssertionError("counts "+label+" "+removed+" "+guard+" "+constant);
  System.out.println("PASS: InvMove defense forced, old config normalized, UI control absent, reserved-key guard");
 }
 static void check(ZipFile z,String n,boolean inventory)throws Exception {
  try(InputStream s=z.getInputStream(z.getEntry(n))){
   new ClassReader(s.readAllBytes()).accept(new ClassVisitor(ASM9){
    public MethodVisitor visitMethod(int ac,String m,String d,String sg,String[] ex){
     if(!inventory&&m.equals("lambda$new$31"))return new MethodVisitor(ASM9){boolean one;public void visitInsn(int op){if(op==ICONST_1)one=true;if(op==IRETURN&&one)constant++;}};
     if(!inventory&&m.equals("onPage"))return new MethodVisitor(ASM9){
      boolean pending;public void visitLdcInsn(Object v){if("Soltar defesa ao mover (InvMove)".equals(v)){label++;pending=true;}}
      public void visitMethodInsn(int op,String owner,String method,String desc,boolean itf){
       if(pending&&owner.equals("dev/xavier/stein/loader/api/Page")&&method.equals("option"))throw new AssertionError("toggle still exposed");
      }
      public void visitInsn(int op){if(pending&&op==POP){removed++;pending=false;}}
     };
     if(inventory&&m.equals("onTickEnd"))return new MethodVisitor(ASM9){public void visitMethodInsn(int op,String owner,String method,String desc,boolean itf){if(owner.equals("com/yny/utils/modules/player/InventoryMove")&&method.equals("stopSwordBlocking"))guard++;}};
     return null;
    }
   },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
  }
 }
}