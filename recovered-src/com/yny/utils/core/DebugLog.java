package com.yny.utils.core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class DebugLog {
   private static final String FILE_NAME = "ynyutils-debug.log";
   private static final String PREVIOUS_FILE_NAME = "ynyutils-debug.1.log";
   private static final long MAX_FILE_BYTES = 4194304L;

   private DebugLog() {
   }

   public static synchronized void write(String var0, String var1) {
      try {
         ave var2 = ave.A();
         File var3 = new File(new File(var2.v, "logs"), "ynyutils-debug.log");
         File var4 = var3.getParentFile();
         if (var4 != null && !var4.isDirectory() && !var4.mkdirs()) {
            return;
         }

         String var5 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
         String var6 = "[" + var5 + "] [" + var0 + "] " + var1 + System.lineSeparator();
         byte[] var7 = var6.getBytes(StandardCharsets.UTF_8);
         Path var8 = var3.toPath();
         Path var9 = new File(var4, "ynyutils-debug.1.log").toPath();
         if (Files.exists(var9) && Files.size(var9) > 4194304L) {
            Files.delete(var9);
         }

         if (Files.exists(var8) && Files.size(var8) + var7.length > 4194304L) {
            if (Files.size(var8) <= 4194304L) {
               Files.move(var8, var9, StandardCopyOption.REPLACE_EXISTING);
            } else {
               Files.delete(var8);
            }
         }

         Files.write(var8, var7, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      } catch (Exception var10) {
         System.out.println("[YNYUtils][" + var0 + "] log write failed: " + var10.getMessage());
      }
   }
}
