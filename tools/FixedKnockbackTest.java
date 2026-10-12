import java.lang.reflect.*;
import java.util.function.BooleanSupplier;
public final class FixedKnockbackTest {
  public static void main(String[] args)throws Exception {
    Class<?> mod=Class.forName("com.yny.utils.YNYUtils");
    check(mod,"lambda$new$2",true);
    check(mod,"lambda$new$3",92);
    check(mod,"lambda$new$4",false);
    Class<?> hud=Class.forName("com.yny.utils.ui.PvpStatusHud");
    Constructor<?> ctor=hud.getConstructor(BooleanSupplier.class,BooleanSupplier.class,BooleanSupplier.class);
    Object instance=ctor.newInstance((BooleanSupplier)()->true,(BooleanSupplier)()->true,(BooleanSupplier)()->true);
    Field message=hud.getDeclaredField("message");message.setAccessible(true);
    if(!"Notificações de PvP".equals(message.get(instance)))throw new AssertionError("default HUD text");
    hud.getMethod("showKnockback",boolean.class).invoke(instance,false);
    if(!"Notificações de PvP".equals(message.get(instance)))throw new AssertionError("knockback notice visible");
    System.out.println("fixed 92% X/Y/Z, hidden notice: PASS");
  }
  static void check(Class<?> c,String name,Object expected)throws Exception {
    Method m=c.getDeclaredMethod(name);m.setAccessible(true);
    Object actual=m.invoke(null);
    if(!expected.equals(actual))throw new AssertionError(name+"="+actual);
  }
}