package com.yny.utils.modules.player;

final class ArmorSwapSafety {
   private ArmorSwapSafety() {
   }

   static boolean mayReturnCursor(boolean var0, boolean var1, boolean var2, int var3) {
      return var0 && !var1 && !var2 && var3 == 0;
   }

   static boolean closerDamage(int var0, int var1, int var2) {
      return Math.abs((long)var0 - var1) < Math.abs((long)var0 - var2);
   }

   static boolean mayDropStuckOld(boolean var0, boolean var1, boolean var2, boolean var3, boolean var4, boolean var5, int var6, int var7) {
      return var0 && !var1 && var2 && var3 && var4 && (var5 || var6 >= var7);
   }

   static ArmorSwapSafety.Outcome returned(boolean var0, boolean var1, boolean var2, boolean var3, boolean var4, int var5, int var6) {
      if (!var0) {
         return ArmorSwapSafety.Outcome.WAIT;
      } else if (!var1) {
         return ArmorSwapSafety.Outcome.ARMOR_CHANGED;
      } else if (var2 && var3 && var4) {
         return ArmorSwapSafety.Outcome.CONFIRMED;
      } else if (var5 < var6) {
         return ArmorSwapSafety.Outcome.WAIT;
      } else {
         return var2 ? ArmorSwapSafety.Outcome.LOCAL_STABLE : ArmorSwapSafety.Outcome.DESTINATION_UNKNOWN;
      }
   }

   enum Outcome {
      WAIT,
      CONFIRMED,
      LOCAL_STABLE,
      DESTINATION_UNKNOWN,
      ARMOR_CHANGED;
   }
}
