import java.lang.reflect.*;

public class TestInstantiate {
    public static void main(String[] args) throws Exception {
        Class<?> hostClass = Class.forName("dev.xavier.stein.loader.api.spi.Host");
        Object mockHost = Proxy.newProxyInstance(hostClass.getClassLoader(), new Class<?>[] { hostClass }, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] margs) {
                if (method.getReturnType() == boolean.class) return false;
                if (method.getReturnType() == int.class) return 0;
                if (method.getReturnType() == float.class) return 0.0f;
                if (method.getReturnType() == double.class) return 0.0;
                return null;
            }
        });

        try {
            Field f = hostClass.getDeclaredField("INSTANCE");
            f.setAccessible(true);
            Field modifiers = Field.class.getDeclaredField("modifiers");
            modifiers.setAccessible(true);
            modifiers.setInt(f, f.getModifiers() & ~Modifier.FINAL);
            f.set(null, mockHost);
            System.out.println("Set Host.INSTANCE to mock successfully");
        } catch (Throwable t) {
            System.out.println("Could not set Host.INSTANCE via reflection: " + t);
        }

        test("com.yny.utils.YNYUtils");
        test("com.yny.utils.team.SteinCombat");
    }

    private static void test(String name) {
        System.out.println("Testing instantiation of: " + name);
        try {
            Class<?> c = Class.forName(name);
            Constructor<?> ctor = c.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object inst = ctor.newInstance();
            System.out.println("  SUCCESS: " + inst);
        } catch (InvocationTargetException ite) {
            System.out.println("  InvocationTargetException cause:");
            ite.getCause().printStackTrace(System.out);
        } catch (Throwable t) {
            System.out.println("  Caught throwable:");
            t.printStackTrace(System.out);
        }
    }
}
