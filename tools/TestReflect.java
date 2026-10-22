import com.yny.utils.modules.clanping.PingWorld;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class TestReflect {
    public static void main(String[] args) throws Exception {
        Method init = PingWorld.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(null);
        
        for (String fieldName : new String[]{"mcClass", "getMinecraftMethod", "theWorldField", "fontRendererField", "fontWidthMethod", "fontDrawMethod", "renderManagerMethod", "playerViewYField", "playerViewXField"}) {
            Field f = PingWorld.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            System.out.println(fieldName + " = " + f.get(null));
        }
    }
}
