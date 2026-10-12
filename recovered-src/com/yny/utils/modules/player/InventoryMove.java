package com.yny.utils.modules.player;

import java.util.function.BooleanSupplier;

public final class InventoryMove {
   private final BooleanSupplier enabled;
   private final BooleanSupplier suspendUseWhileMoving;
   private final BooleanSupplier useKeyReserved;
   private boolean controllingKeys;

   public InventoryMove(BooleanSupplier var1, BooleanSupplier var2, BooleanSupplier var3) {
      this.enabled = var1;
      this.suspendUseWhileMoving = var2;
      this.useKeyReserved = var3;
   }

   public void onTickEnd() {
      ave var1 = ave.A();
      if (this.enabled.getAsBoolean() && var1.h != null && isSupportedScreen(var1)) {
         boolean var2 = sync(var1.t.Y) | sync(var1.t.aa) | sync(var1.t.Z) | sync(var1.t.ab) | sync(var1.t.ac) | sync(var1.t.ae);
         sync(var1.t.ad);
         if (var2) stopSwordBlocking(var1);
         if (!this.useKeyReserved.getAsBoolean() && !AutoProtect.isControllingUseKey()) {
            boolean var3 = this.suspendUseWhileMoving.getAsBoolean() && shouldSuspendUse(var2);
            var1.t.ag.h = !var3 && avh.a(var1.t.ag);
         }

         this.controllingKeys = true;
      } else {
         this.releaseToPhysicalState(var1);
      }
   }

   static boolean shouldSuspendUse(boolean var0) {
      return var0;
   }

   static boolean shouldStopSwordDefense(boolean var0, boolean var1) {
      return var0 && var1;
   }

   private static void stopSwordBlocking(ave var0) {
      zx var1 = var0.h.bA();
      boolean var2 = var0.h.bS() && var1 != null && var1.b() instanceof aay;
      if (shouldStopSwordDefense(true, var2) && var0.c != null) {
         var0.c.c(var0.h);
      }
   }

   private static boolean isSupportedScreen(ave var0) {
      return var0.m instanceof ayl && !(var0.m instanceof aym);
   }

   private static boolean sync(avb var0) {
      var0.h = avh.a(var0);
      return var0.h;
   }

   private void releaseToPhysicalState(ave var1) {
      if (this.controllingKeys && var1.t != null) {
         sync(var1.t.Y);
         sync(var1.t.aa);
         sync(var1.t.Z);
         sync(var1.t.ab);
         sync(var1.t.ac);
         sync(var1.t.ae);
         sync(var1.t.ad);
         sync(var1.t.ag);
         this.controllingKeys = false;
      }
   }
}
