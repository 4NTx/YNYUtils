package com.yny.utils.core;

import dev.xavier.stein.loader.api.Keys;

public final class ToggleKey {
   private boolean wasDown;

   public boolean wasPressed(int var1) {
      boolean var2 = var1 != 0 && ave.A().m == null && Keys.isDown(var1);
      boolean var3 = var2 && !this.wasDown;
      this.wasDown = var2;
      return var3;
   }
}
