import java.lang.reflect.*;
import java.util.function.BooleanSupplier;
import com.yny.utils.config.YNYConfig;

public final class FixedKnockbackTest {
  public static void main(String[] args) throws Exception {
    Class<?> mod = Class.forName("com.yny.utils.YNYUtils");
    Field cfgField = mod.getDeclaredField("config");
    cfgField.setAccessible(true);
    YNYConfig cfg = (YNYConfig) cfgField.get(null);

    // 1. Dynamic lambda$new$2 (knockbackEnabled)
    cfg.knockbackEnabled = true;
    check(mod, "lambda$new$2", true);
    cfg.knockbackEnabled = false;
    check(mod, "lambda$new$2", false);

    // 2. Dynamic lambda$new$3 (knockbackPercent)
    cfg.knockbackPercent = 80;
    check(mod, "lambda$new$3", 80);
    cfg.knockbackPercent = 0;
    check(mod, "lambda$new$3", 0);
    cfg.knockbackPercent = 100;
    check(mod, "lambda$new$3", 100);

    // 3. Dynamic lambda$new$4 (knockbackPreserveVertical)
    cfg.knockbackPreserveVertical = true;
    check(mod, "lambda$new$4", true);
    cfg.knockbackPreserveVertical = false;
    check(mod, "lambda$new$4", false);

    // 4. PvpStatusHud.showKnockback(boolean)
    Class<?> hud = Class.forName("com.yny.utils.ui.PvpStatusHud");
    Constructor<?> ctor = hud.getConstructor(BooleanSupplier.class, BooleanSupplier.class, BooleanSupplier.class);
    Object instance = ctor.newInstance((BooleanSupplier) () -> true, (BooleanSupplier) () -> true, (BooleanSupplier) () -> true);
    Field message = hud.getDeclaredField("message");
    message.setAccessible(true);

    hud.getMethod("showKnockback", boolean.class).invoke(instance, true);
    if (!"Knockback: ATIVADO".equals(message.get(instance))) {
        throw new AssertionError("showKnockback(true) message expected 'Knockback: ATIVADO', got: " + message.get(instance));
    }

    hud.getMethod("showKnockback", boolean.class).invoke(instance, false);
    if (!"Knockback: DESATIVADO".equals(message.get(instance))) {
        throw new AssertionError("showKnockback(false) message expected 'Knockback: DESATIVADO', got: " + message.get(instance));
    }

    System.out.println("dynamic knockback (0-100%, toggle, vertical, HUD): PASS");
  }

  static void check(Class<?> c, String name, Object expected) throws Exception {
    Method m = c.getDeclaredMethod(name);
    m.setAccessible(true);
    Object actual = m.invoke(null);
    if (!expected.equals(actual)) throw new AssertionError(name + "=" + actual + ", expected=" + expected);
  }
}