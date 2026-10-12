package com.yny.utils.modules.pvp;

import dev.xavier.stein.loader.api.Relation;
import dev.xavier.stein.loader.api.Relations;
import org.lwjgl.opengl.GL11;

public final class AllyGroundMarker {
   public void renderWorld(float var1) {
      ave var2 = ave.A();
      pk var3 = var2.ac();
      if (var2.h != null && var2.f != null && var3 != null) {
         double var4 = var3.P + (var3.s - var3.P) * var1;
         double var6 = var3.Q + (var3.t - var3.Q) * var1;
         double var8 = var3.R + (var3.u - var3.R) * var1;
         bfl.E();
         bfl.x();
         bfl.p();
         bfl.l();
         bfl.a(770, 771, 1, 0);
         bfl.a(false);
         bfl.j();
         GL11.glLineWidth(2.0F);

         try {
            for (Object var11 : var2.f.j) {
               if (var11 instanceof wn var12 && var12 != var2.h && var12.ai()) {
                  Relation var13 = Relations.of(var12);
                  if (var13 == Relation.GROUP) {
                     double var14 = var12.P + (var12.s - var12.P) * var1 - var4;
                     double var16 = var12.Q + (var12.t - var12.Q) * var1 - var6 + 0.025;
                     double var18 = var12.R + (var12.u - var12.R) * var1 - var8;
                     drawRing(var14, var16, var18, Math.max(0.32, var12.J * 0.72));
                  }
               }
            }
         } finally {
            GL11.glLineWidth(1.0F);
            bfl.a(true);
            bfl.j();
            bfl.k();
            bfl.o();
            bfl.w();
            bfl.c(1.0F, 1.0F, 1.0F, 1.0F);
            bfl.F();
         }
      }
   }

   private static void drawRing(double var0, double var2, double var4, double var6) {
      bfx var8 = bfx.a();
      bfd var9 = var8.c();
      var9.a(2, bms.f);

      for (int var10 = 0; var10 < 32; var10++) {
         double var11 = var10 * Math.PI * 2.0 / 32.0;
         var9.b(var0 + Math.cos(var11) * var6, var2, var4 + Math.sin(var11) * var6).b(70, 220, 150, 220).d();
      }

      var8.b();
   }
}
