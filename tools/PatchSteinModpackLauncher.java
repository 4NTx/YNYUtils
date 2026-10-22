package tools;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.regex.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public class PatchSteinModpackLauncher {

    public static void main(String[] args) throws Exception {
        File modpackRoot = new File("C:/Users/artur/Desktop/Modpack").getAbsoluteFile();
        System.out.println("Modpack root: " + modpackRoot.getAbsolutePath());

        File dataLauncherJar = new File(modpackRoot, "data/launcher.jar");
        File abrirPorAquiJar = new File(modpackRoot, "ABRIR POR AQUI.jar");
        File launcherProfilesJson = new File(modpackRoot, "data/.minecraft/launcher_profiles.json");

        // 1. Compile ProfileHelper.class into memory
        byte[] helperClassBytes = generateProfileHelperClass();

        // 2. Patch data/launcher.jar
        if (dataLauncherJar.exists()) {
            patchLauncherJar(dataLauncherJar, helperClassBytes);
            System.out.println("PASS: Patched " + dataLauncherJar.getAbsolutePath());
        }

        // 3. Patch ABRIR POR AQUI.jar
        if (abrirPorAquiJar.exists()) {
            patchAbrirPorAquiJar(abrirPorAquiJar, helperClassBytes);
            System.out.println("PASS: Patched " + abrirPorAquiJar.getAbsolutePath());
        }

        // 4. Update data/.minecraft/launcher_profiles.json
        if (launcherProfilesJson.exists()) {
            updateLauncherProfiles(launcherProfilesJson);
            System.out.println("PASS: Updated " + launcherProfilesJson.getAbsolutePath());
        }

        System.out.println("ALL LAUNCHER PATCHES APPLIED SUCCESSFULLY!");
    }

    private static void updateLauncherProfiles(File jsonFile) throws Exception {
        String content = new String(Files.readAllBytes(jsonFile.toPath()), StandardCharsets.UTF_8);

        String relativeJavaDir = "data/.minecraft/versions/1.8.9-SteinLoader/runtime/bin/javaw.exe";
        String steinVersion = "1.8.9-SteinLoader";
        String steinArgs = "-Xmx8G -Xms256M -XX:SoftMaxHeapSize=768M -XX:+UseZGC -XX:-ZProactive --enable-native-access=ALL-UNNAMED -Dsteinbridge.gfx=false";

        // Regex / JSON structure ensure
        // We ensure both 3NTI and Stein Loader 1.8.9 exist and have the proper version, javaDir, and javaArgs
        // If content is valid JSON, let's inject into profiles object
        int profilesIdx = content.indexOf("\"profiles\"");
        if (profilesIdx >= 0) {
            int openBrace = content.indexOf('{', profilesIdx);
            if (openBrace >= 0) {
                // Check if "Stein Loader 1.8.9" exists
                boolean hasSteinProfile = content.contains("\"Stein Loader 1.8.9\"");
                boolean has3NTI = content.contains("\"3NTI\"");

                StringBuilder sb = new StringBuilder();

                String steinProfileBlock = 
                    "    \"Stein Loader 1.8.9\": {\n" +
                    "      \"name\": \"Stein Loader 1.8.9\",\n" +
                    "      \"lastVersionId\": \"" + steinVersion + "\",\n" +
                    "      \"javaDir\": \"" + relativeJavaDir + "\",\n" +
                    "      \"javaArgs\": \"" + steinArgs + "\",\n" +
                    "      \"useHopperCrashService\": false\n" +
                    "    }";

                if (!hasSteinProfile) {
                    // Inject Stein Loader 1.8.9 right after "profiles": {
                    content = content.substring(0, openBrace + 1) + "\n" + steinProfileBlock + ",\n" + content.substring(openBrace + 1);
                } else {
                    // Update existing Stein Loader profile
                    // ensure lastVersionId, javaDir, javaArgs
                    content = content.replaceAll("\"Stein Loader 1.8.9\"\\s*:\\s*\\{[^}]*\\}", Matcher.quoteReplacement(steinProfileBlock.trim()));
                }

                // If 3NTI profile exists, make sure it ALSO has lastVersionId, javaDir, and javaArgs so even if selected, it loads SteinLoader!
                if (content.contains("\"3NTI\"")) {
                    Pattern p = Pattern.compile("(\"3NTI\"\\s*:\\s*\\{)([^}]+)(\\})");
                    Matcher m = p.matcher(content);
                    if (m.find()) {
                        String body = m.group(2);
                        if (!body.contains("\"lastVersionId\"")) {
                            body = body + ",\n      \"lastVersionId\": \"" + steinVersion + "\"";
                        } else {
                            body = body.replaceAll("\"lastVersionId\"\\s*:\\s*\".*?\"", "\"lastVersionId\": \"" + steinVersion + "\"");
                        }
                        if (!body.contains("\"javaDir\"")) {
                            body = body + ",\n      \"javaDir\": \"" + relativeJavaDir + "\"";
                        } else {
                            body = body.replaceAll("\"javaDir\"\\s*:\\s*\".*?\"", "\"javaDir\": \"" + relativeJavaDir + "\"");
                        }
                        if (!body.contains("\"javaArgs\"")) {
                            body = body + ",\n      \"javaArgs\": \"" + steinArgs + "\"";
                        } else {
                            body = body.replaceAll("\"javaArgs\"\\s*:\\s*\".*?\"", "\"javaArgs\": \"" + steinArgs + "\"");
                        }
                        content = content.substring(0, m.start(2)) + body + content.substring(m.end(2));
                    }
                }

                // Ensure selectedProfile is "Stein Loader 1.8.9"
                content = content.replaceAll("\"selectedProfile\"\\s*:\\s*\".*?\"", "\"selectedProfile\": \"Stein Loader 1.8.9\"");

                Files.write(jsonFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private static void patchLauncherJar(File jarFile, byte[] helperBytes) throws Exception {
        File temp = new File(jarFile.getParentFile(), jarFile.getName() + ".tmp");
        try (JarFile jf = new JarFile(jarFile);
             JarOutputStream jos = new JarOutputStream(new FileOutputStream(temp))) {

            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry entry = en.nextElement();
                String name = entry.getName();
                if (name.equals("net/minecraft/launcher/profile/ProfileHelper.class")) {
                    continue;
                }

                byte[] data;
                try (InputStream is = jf.getInputStream(entry)) {
                    data = readAll(is);
                }

                if (name.equals("net/minecraft/launcher/profile/Profile.class")) {
                    data = patchProfileClass(data);
                }

                JarEntry outEntry = new JarEntry(name);
                outEntry.setTime(entry.getTime());
                jos.putNextEntry(outEntry);
                jos.write(data);
                jos.closeEntry();
            }

            // Write ProfileHelper.class
            JarEntry helperEntry = new JarEntry("net/minecraft/launcher/profile/ProfileHelper.class");
            helperEntry.setTime(System.currentTimeMillis());
            jos.putNextEntry(helperEntry);
            jos.write(helperBytes);
            jos.closeEntry();
        }

        Files.move(temp.toPath(), jarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void patchAbrirPorAquiJar(File jarFile, byte[] helperBytes) throws Exception {
        File temp = new File(jarFile.getParentFile(), jarFile.getName() + ".tmp");
        try (JarFile jf = new JarFile(jarFile);
             JarOutputStream jos = new JarOutputStream(new FileOutputStream(temp))) {

            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry entry = en.nextElement();
                String name = entry.getName();

                byte[] data;
                try (InputStream is = jf.getInputStream(entry)) {
                    data = readAll(is);
                }

                if (name.equals("craftlandia/loader/launcher.jar")) {
                    // Patch the nested launcher.jar
                    File nestedTemp = File.createTempFile("nested_launcher", ".jar");
                    Files.write(nestedTemp.toPath(), data);
                    patchLauncherJar(nestedTemp, helperBytes);
                    data = Files.readAllBytes(nestedTemp.toPath());
                    nestedTemp.delete();
                } else if (name.equals("craftlandia/loader/Loader.class")) {
                    // Update Loader.class with dynamic self-healing code
                    data = generateLoaderClass();
                }

                JarEntry outEntry = new JarEntry(name);
                outEntry.setTime(entry.getTime());
                jos.putNextEntry(outEntry);
                jos.write(data);
                jos.closeEntry();
            }
        }

        Files.move(temp.toPath(), jarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static byte[] patchProfileClass(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        for (MethodNode mn : cn.methods) {
            if (mn.name.equals("getJavaPath") && mn.desc.equals("()Ljava/lang/String;")) {
                mn.instructions.clear();
                InsnList il = new InsnList();
                il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                il.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/launcher/profile/Profile", "javaDir", "Ljava/lang/String;"));
                il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                il.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/launcher/profile/Profile", "gameDir", "Ljava/io/File;"));
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/minecraft/launcher/profile/ProfileHelper", "resolveJavaPath", "(Ljava/lang/String;Ljava/io/File;)Ljava/lang/String;", false));
                il.add(new InsnNode(Opcodes.ARETURN));
                mn.instructions.add(il);
                mn.maxStack = 2;
                mn.maxLocals = 1;
                break;
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private static byte[] generateProfileHelperClass() throws Exception {
        String source = 
            "package net.minecraft.launcher.profile;\n" +
            "import java.io.File;\n" +
            "public final class ProfileHelper {\n" +
            "    public static String resolveJavaPath(String javaDir, File gameDir) {\n" +
            "        if (javaDir != null) {\n" +
            "            String trimmed = javaDir.trim().replace('\\\\', '/');\n" +
            "            if (!trimmed.isEmpty()) {\n" +
            "                File f = new File(javaDir);\n" +
            "                if (f.exists()) return f.getAbsolutePath();\n" +
            "                if (gameDir != null) {\n" +
            "                    File inGame = new File(gameDir, javaDir);\n" +
            "                    if (inGame.exists()) return inGame.getAbsolutePath();\n" +
            "                    File parent = gameDir.getParentFile();\n" +
            "                    if (parent != null) {\n" +
            "                        File inParent = new File(parent, javaDir);\n" +
            "                        if (inParent.exists()) return inParent.getAbsolutePath();\n" +
            "                        File root = parent.getParentFile();\n" +
            "                        if (root != null) {\n" +
            "                            File inRoot = new File(root, javaDir);\n" +
            "                            if (inRoot.exists()) return inRoot.getAbsolutePath();\n" +
            "                        }\n" +
            "                    }\n" +
            "                    File bundled = new File(gameDir, \"versions/1.8.9-SteinLoader/runtime/bin/javaw.exe\");\n" +
            "                    if (bundled.exists()) return bundled.getAbsolutePath();\n" +
            "                }\n" +
            "                File relCwd = new File(\"data/.minecraft/versions/1.8.9-SteinLoader/runtime/bin/javaw.exe\");\n" +
            "                if (relCwd.exists()) return relCwd.getAbsolutePath();\n" +
            "            }\n" +
            "        }\n" +
            "        if (gameDir != null) {\n" +
            "            File bundled = new File(gameDir, \"versions/1.8.9-SteinLoader/runtime/bin/javaw.exe\");\n" +
            "            if (bundled.exists()) return bundled.getAbsolutePath();\n" +
            "        }\n" +
            "        File relCwd = new File(\"data/.minecraft/versions/1.8.9-SteinLoader/runtime/bin/javaw.exe\");\n" +
            "        if (relCwd.exists()) return relCwd.getAbsolutePath();\n" +
            "        return javaDir;\n" +
            "    }\n" +
            "}\n";

        File tempDir = Files.createTempDirectory("profilehelper_src").toFile();
        File tempBin = Files.createTempDirectory("profilehelper_bin").toFile();
        File tempJava = new File(tempDir, "ProfileHelper.java");
        Files.write(tempJava.toPath(), source.getBytes(StandardCharsets.UTF_8));

        String javac = "C:/Program Files/Microsoft/jdk-25.0.4.101-hotspot/bin/javac.exe";
        Process p = new ProcessBuilder(javac, "--release", "8", "-d", tempBin.getAbsolutePath(), tempJava.getAbsolutePath()).inheritIO().start();
        p.waitFor();

        File classFile = new File(tempBin, "net/minecraft/launcher/profile/ProfileHelper.class");
        byte[] bytes = Files.readAllBytes(classFile.toPath());

        deleteRecursive(tempDir);
        deleteRecursive(tempBin);
        return bytes;
    }

    private static byte[] generateLoaderClass() throws Exception {
        String source = 
            "package craftlandia.loader;\n" +
            "import java.io.*;\n" +
            "import java.net.URI;\n" +
            "import java.nio.charset.StandardCharsets;\n" +
            "import java.nio.file.Files;\n" +
            "import java.util.*;\n" +
            "import java.util.regex.*;\n" +
            "public class Loader {\n" +
            "    public static void main(String[] args) throws Exception {\n" +
            "        File baseDir;\n" +
            "        try {\n" +
            "            URI uri = Loader.class.getProtectionDomain().getCodeSource().getLocation().toURI();\n" +
            "            baseDir = new File(uri).getParentFile();\n" +
            "        } catch (Exception e) {\n" +
            "            baseDir = new File(\".\").getAbsoluteFile();\n" +
            "        }\n" +
            "        if (baseDir == null || !baseDir.exists()) {\n" +
            "            baseDir = new File(\".\").getAbsoluteFile();\n" +
            "        }\n" +
            "        File workingFolder = new File(baseDir, \"data\");\n" +
            "        if (!workingFolder.exists()) workingFolder.mkdirs();\n" +
            "        try {\n" +
            "            fixProfiles(workingFolder);\n" +
            "        } catch (Throwable t) { t.printStackTrace(); }\n" +
            "        InputStream stream = Loader.class.getResourceAsStream(\"launcher.jar\");\n" +
            "        byte[] buffer = new byte[4096];\n" +
            "        File launcherFile = new File(workingFolder, \"launcher.jar\");\n" +
            "        try (FileOutputStream resStreamOut = new FileOutputStream(launcherFile)) {\n" +
            "            int readBytes;\n" +
            "            while ((readBytes = stream.read(buffer)) > 0) {\n" +
            "                resStreamOut.write(buffer, 0, readBytes);\n" +
            "            }\n" +
            "        } catch (IOException e1) { e1.printStackTrace(); }\n" +
            "        File bundledJava = new File(workingFolder, \".minecraft/versions/1.8.9-SteinLoader/runtime/bin/java.exe\");\n" +
            "        if (!bundledJava.exists()) {\n" +
            "            bundledJava = new File(workingFolder, \".minecraft/versions/1.8.9-SteinLoader/runtime/bin/javaw.exe\");\n" +
            "        }\n" +
            "        String javaExec = bundledJava.exists() ? bundledJava.getAbsolutePath() : \"java\";\n" +
            "        String[] arguments = new String[args.length + 4];\n" +
            "        arguments[0] = javaExec;\n" +
            "        arguments[1] = \"-Duser.home=\" + workingFolder.getAbsolutePath();\n" +
            "        arguments[2] = \"-jar\";\n" +
            "        arguments[3] = launcherFile.getAbsolutePath();\n" +
            "        for (int i = 0; i < args.length; i++) arguments[i + 4] = args[i];\n" +
            "        ProcessBuilder builder = new ProcessBuilder(arguments);\n" +
            "        builder.directory(baseDir);\n" +
            "        builder.environment().put(\"APPDATA\", workingFolder.getAbsolutePath());\n" +
            "        Process p = builder.start();\n" +
            "        BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));\n" +
            "        String lineRead;\n" +
            "        while ((lineRead = br.readLine()) != null) System.out.println(lineRead);\n" +
            "        p.waitFor();\n" +
            "    }\n" +
            "    private static void fixProfiles(File workingFolder) {\n" +
            "        File dotMinecraft = new File(workingFolder, \".minecraft\");\n" +
            "        File profilesFile = new File(dotMinecraft, \"launcher_profiles.json\");\n" +
            "        if (!profilesFile.exists()) return;\n" +
            "        try {\n" +
            "            String content = new String(Files.readAllBytes(profilesFile.toPath()), StandardCharsets.UTF_8);\n" +
            "            String rel = \"data/.minecraft/versions/1.8.9-SteinLoader/runtime/bin/javaw.exe\";\n" +
            "            String steinVersion = \"1.8.9-SteinLoader\";\n" +
            "            String steinArgs = \"-Xmx8G -Xms256M -XX:SoftMaxHeapSize=768M -XX:+UseZGC -XX:-ZProactive --enable-native-access=ALL-UNNAMED -Dsteinbridge.gfx=false\";\n" +
            "            if (content.contains(\"\\\"selectedProfile\\\"\")) {\n" +
            "                content = content.replaceAll(\"\\\"selectedProfile\\\"\\\\s*:\\\\s*\\\".*?\\\"\", \"\\\"selectedProfile\\\": \\\"Stein Loader 1.8.9\\\"\");\n" +
            "            }\n" +
            "            String steinBlock = \"    \\\"Stein Loader 1.8.9\\\": {\\n      \\\"name\\\": \\\"Stein Loader 1.8.9\\\",\\n      \\\"lastVersionId\\\": \\\"\" + steinVersion + \"\\\",\\n      \\\"javaDir\\\": \\\"\" + rel + \"\\\",\\n      \\\"javaArgs\\\": \\\"\" + steinArgs + \"\\\",\\n      \\\"useHopperCrashService\\\": false\\n    }\";\n" +
            "            if (!content.contains(\"\\\"Stein Loader 1.8.9\\\"\")) {\n" +
            "                int pIdx = content.indexOf(\"\\\"profiles\\\"\");\n" +
            "                if (pIdx >= 0) {\n" +
            "                    int brace = content.indexOf('{', pIdx);\n" +
            "                    if (brace >= 0) {\n" +
            "                        content = content.substring(0, brace + 1) + \"\\n\" + steinBlock + \",\\n\" + content.substring(brace + 1);\n" +
            "                    }\n" +
            "                }\n" +
            "            } else {\n" +
            "                content = content.replaceAll(\"\\\"Stein Loader 1.8.9\\\"\\\\s*:\\\\s*\\\\{[^}]*\\\\}\", Matcher.quoteReplacement(steinBlock.trim()));\n" +
            "            }\n" +
            "            Pattern p = Pattern.compile(\"(\\\"([^\\\"]+)\\\"\\\\s*:\\\\s*\\\\{)([^}]+)(\\\\})\");\n" +
            "            Matcher m = p.matcher(content);\n" +
            "            StringBuffer sb = new StringBuffer();\n" +
            "            int authIdx = content.indexOf(\"\\\"authenticationDatabase\\\"\");\n" +
            "            while (m.find()) {\n" +
            "                if (authIdx > 0 && m.start() >= authIdx) {\n" +
            "                    m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));\n" +
            "                    continue;\n" +
            "                }\n" +
            "                String profName = m.group(2);\n" +
            "                if (profName.equals(\"Stein Loader 1.8.9\") || profName.equals(\"profiles\")) {\n" +
            "                    m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));\n" +
            "                    continue;\n" +
            "                }\n" +
            "                String body = m.group(3);\n" +
            "                if (!body.contains(\"\\\"lastVersionId\\\"\")) {\n" +
            "                    body = body + \",\\n      \\\"lastVersionId\\\": \\\"\" + steinVersion + \"\\\"\";\n" +
            "                } else {\n" +
            "                    body = body.replaceAll(\"\\\"lastVersionId\\\"\\\\s*:\\\\s*\\\".*?\\\"\", \"\\\"lastVersionId\\\": \\\"\" + steinVersion + \"\\\"\");\n" +
            "                }\n" +
            "                if (!body.contains(\"\\\"javaDir\\\"\")) {\n" +
            "                    body = body + \",\\n      \\\"javaDir\\\": \\\"\" + rel + \"\\\"\";\n" +
            "                } else {\n" +
            "                    body = body.replaceAll(\"\\\"javaDir\\\"\\\\s*:\\\\s*\\\".*?\\\"\", \"\\\"javaDir\\\": \\\"\" + rel + \"\\\"\");\n" +
            "                }\n" +
            "                if (!body.contains(\"\\\"javaArgs\\\"\")) {\n" +
            "                    body = body + \",\\n      \\\"javaArgs\\\": \\\"\" + steinArgs + \"\\\"\";\n" +
            "                } else {\n" +
            "                    body = body.replaceAll(\"\\\"javaArgs\\\"\\\\s*:\\\\s*\\\".*?\\\"\", \"\\\"javaArgs\\\": \\\"\" + steinArgs + \"\\\"\");\n" +
            "                }\n" +
            "                String replacement = m.group(1) + body + m.group(4);\n" +
            "                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));\n" +
            "            }\n" +
            "            m.appendTail(sb);\n" +
            "            content = sb.toString();\n" +
            "            Files.write(profilesFile.toPath(), content.getBytes(StandardCharsets.UTF_8));\n" +
            "        } catch (Throwable t) { t.printStackTrace(); }\n" +
            "    }\n" +
            "}\n";

        File tempDir = Files.createTempDirectory("loader_src").toFile();
        File tempBin = Files.createTempDirectory("loader_bin").toFile();
        File tempJava = new File(tempDir, "Loader.java");
        Files.write(tempJava.toPath(), source.getBytes(StandardCharsets.UTF_8));

        String javac = "C:/Program Files/Microsoft/jdk-25.0.4.101-hotspot/bin/javac.exe";
        Process p = new ProcessBuilder(javac, "--release", "8", "-d", tempBin.getAbsolutePath(), tempJava.getAbsolutePath()).inheritIO().start();
        p.waitFor();

        File classFile = new File(tempBin, "craftlandia/loader/Loader.class");
        byte[] bytes = Files.readAllBytes(classFile.toPath());

        deleteRecursive(tempDir);
        deleteRecursive(tempBin);
        return bytes;
    }

    private static byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = is.read(buf)) != -1) baos.write(buf, 0, r);
        return baos.toByteArray();
    }

    private static void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] subs = f.listFiles();
            if (subs != null) for (File s : subs) deleteRecursive(s);
        }
        f.delete();
    }
}
