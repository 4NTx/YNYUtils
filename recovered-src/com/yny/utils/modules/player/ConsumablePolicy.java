package com.yny.utils.modules.player;

final class ConsumablePolicy {
   static final int OFF = 0;
   static final int ECONOMIC = 1;
   static final int COMBAT = 2;

   private ConsumablePolicy() {
   }

   static boolean isDamageWithoutHurt(float var0, float var1, float var2, float var3) {
      return var1 < var0;
   }

   static boolean shouldUse(int var0, boolean var1, long var2, long var4, int var6, int var7) {
      if (var0 == 1) {
         return var1 && var2 > var4 && var6 <= 0;
      } else {
         return var0 != 2 ? false : var1 && var6 <= var7;
      }
   }
}
