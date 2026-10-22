import com.yny.utils.config.YNYConfig;
import com.yny.utils.modules.player.ArmorThreshold;
import java.lang.reflect.*;

public final class ArmorThresholdTest {
    public static void main(String[] args) throws Exception {
        int[] values = {0, 20, 25, 30, 35, 40, 45, 50};
        for (int i = 0; i < values.length; i++) {
            if (ArmorThreshold.fromSlider(i) != values[i]) {
                throw new AssertionError("from " + i + " got " + ArmorThreshold.fromSlider(i) + " expected " + values[i]);
            }
            if ((int) ArmorThreshold.toSlider(values[i]) != i) {
                throw new AssertionError("to " + values[i] + " got " + ArmorThreshold.toSlider(values[i]) + " expected " + i);
            }
        }
        for (int value : new int[]{-5, 0, 5, 10, 15, 18, 19}) {
            YNYConfig c = new YNYConfig();
            c.autoArmorPreventiveThreshold = value;
            c.sanitize();
            if (c.autoArmorPreventiveThreshold != 0) {
                throw new AssertionError("sanitize below 20 (" + value + ") got " + c.autoArmorPreventiveThreshold);
            }
        }
        for (int value : new int[]{20, 22, 25, 30, 35, 40, 45, 50, 55}) {
            YNYConfig c = new YNYConfig();
            c.autoArmorPreventiveThreshold = value;
            c.sanitize();
            int expected = Math.max(20, Math.min(50, Math.round(value / 5f) * 5));
            if (c.autoArmorPreventiveThreshold != expected) {
                throw new AssertionError("sanitize " + value + " got " + c.autoArmorPreventiveThreshold + " expected " + expected);
            }
        }

        Field field = Class.forName("com.yny.utils.YNYUtils").getDeclaredField("config");
        field.setAccessible(true);
        YNYConfig config = new YNYConfig();
        field.set(null, config);

        Method runtime = null;
        for (Method m : Class.forName("com.yny.utils.YNYUtils").getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0 && m.getReturnType() == int.class) {
                m.setAccessible(true);
                config.autoArmorPreventiveThreshold = 30;
                int v1 = (int) m.invoke(null);
                config.autoArmorPreventiveThreshold = 40;
                int v2 = (int) m.invoke(null);
                if (v1 == 30 && v2 == 40) {
                    runtime = m;
                    break;
                }
            }
        }
        if (runtime == null) {
            throw new AssertionError("Could not find autoArmorPreventiveThreshold lambda in YNYUtils");
        }

        for (int v : new int[]{-5, 0, 5, 10, 15, 18, 19}) {
            config.autoArmorPreventiveThreshold = v;
            int res = (int) runtime.invoke(null);
            if (res != 0) {
                throw new AssertionError("runtime floor for " + v + " got " + res + " expected 0");
            }
        }
        for (int v : new int[]{20, 22, 25, 30, 35, 40, 45, 50, 55}) {
            config.autoArmorPreventiveThreshold = v;
            int res = (int) runtime.invoke(null);
            int expected = Math.max(20, Math.min(50, Math.round(v / 5f) * 5));
            if (res != expected) {
                throw new AssertionError("runtime floor for " + v + " got " + res + " expected " + expected);
            }
        }

        System.out.println("PASS: break only (0) or 20-50%, config and runtime floor");
    }
}