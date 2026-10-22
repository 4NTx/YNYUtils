import java.io.*;
import java.net.*;
import java.util.*;
import java.util.zip.*;

public class TestAllCachedClasses {
    public static void main(String[] args) throws Exception {
        File cachedJar = new File("../../../data/.minecraft/steinloader/cache/YNYUtils.jar");
        System.out.println("Checking cached jar: " + cachedJar.getAbsolutePath());

        List<URL> urls = new ArrayList<>();
        urls.add(cachedJar.toURI().toURL());
        urls.add(new File("../../../data/.minecraft/versions/1.8.9-SteinLoader/1.8.9-SteinLoader.jar").toURI().toURL());
        urls.add(new File("../../../data/.minecraft/libraries/dev/xavier/stein/steinloader/0.1.27/steinloader-0.1.27.jar").toURI().toURL());
        urls.add(new File("../../../data/.minecraft/libraries/dev/xavier/stein/steinbridge/0.1.20/steinbridge-0.1.20.jar").toURI().toURL());
        urls.add(new File("../../../data/.minecraft/libraries/com/google/code/gson/gson/2.2.4/gson-2.2.4.jar").toURI().toURL());
        urls.add(new File("../../../data/.minecraft/libraries/io/netty/netty-all/4.0.23.Final/netty-all-4.0.23.Final.jar").toURI().toURL());
        urls.add(new File("../../../data/.minecraft/libraries/org/apache/commons/commons-lang3/3.3.2/commons-lang3-3.3.2.jar").toURI().toURL());
        urls.add(new File("C:/Users/artur/.stein-sdk/1.8.9/minecraft-named-open.jar").toURI().toURL());
        urls.add(new File("C:/Users/artur/.stein-sdk/1.8.9/stein-api.jar").toURI().toURL());

        // Use null parent classloader so that all classes are loaded strictly through the test URLClassLoader
        URLClassLoader cl = new URLClassLoader(urls.toArray(new URL[0]), null);

        int count = 0;
        int failed = 0;
        try (ZipFile zf = new ZipFile(cachedJar)) {
            for (ZipEntry ze : Collections.list(zf.entries())) {
                String name = ze.getName();
                if (name.endsWith(".class")) {
                    count++;
                    String className = name.substring(0, name.length() - 6).replace('/', '.');
                    try {
                        Class<?> c = Class.forName(className, true, cl);
                    } catch (Throwable t) {
                        failed++;
                        System.out.println("FAILED to load class: " + className);
                        t.printStackTrace(System.out);
                    }
                }
            }
        }

        System.out.println("Total classes tested: " + count + ", Failed: " + failed);
    }
}
