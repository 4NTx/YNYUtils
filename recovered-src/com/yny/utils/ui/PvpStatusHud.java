package com.yny.utils.ui;

import dev.xavier.stein.loader.api.HudElement;
import dev.xavier.stein.loader.api.HudPlacement;
import java.util.function.BooleanSupplier;

public final class PvpStatusHud implements HudElement {
   private static final long DISPLAY_NANOS = 2500000000L;
   private static final String KB_ENABLED = "Knockback: ATIVADO";
   private static final String KB_DISABLED = "Knockback: DESATIVADO";
   private static final String JUMP_RESET_ENABLED = "Jump Reset: ATIVADO";
   private static final String JUMP_RESET_DISABLED = "Jump Reset: DESATIVADO";
   private static final String SHIFT_RESET_ENABLED = "Shift Reset: ATIVADO";
   private static final String SHIFT_RESET_DISABLED = "Shift Reset: DESATIVADO";
   private static final String AUTO_ARMOR_ENABLED = "Auto Armor: ATIVADO";
   private static final String AUTO_ARMOR_DISABLED = "Auto Armor: DESATIVADO";
   private static final String AUTO_ARMOR_WIDEST = "Auto Armor: aguardando confirmação do servidor";
   private static final String CONSUMABLES_ENABLED = "Auto Consumíveis: ATIVADO";
   private static final String CONSUMABLES_DISABLED = "Auto Consumíveis: DESATIVADO";
   private final BooleanSupplier knockbackNotificationsEnabled;
   private final BooleanSupplier autoArmorNotificationsEnabled;
   private final BooleanSupplier consumablesNotificationsEnabled;
   private String message = "Notificações de PvP";
   private int color = 5635925;
   private long visibleUntil;
   private boolean autoArmorMessage;
   private boolean consumablesMessage;
   private avn measuredFont;
   private boolean measuredUnicode;
   private int measuredWidth;

   public void invalidateFontMetrics() {
      this.measuredFont = null;
   }

   public PvpStatusHud(BooleanSupplier var1, BooleanSupplier var2, BooleanSupplier var3) {
      this.knockbackNotificationsEnabled = var1;
      this.autoArmorNotificationsEnabled = var2;
      this.consumablesNotificationsEnabled = var3;
   }

   public void showKnockback(boolean ignored) {
   }

   public void showReset(boolean var1, boolean var2) {
      this.message = var1 ? (var2 ? "Jump Reset: ATIVADO" : "Jump Reset: DESATIVADO") : (var2 ? "Shift Reset: ATIVADO" : "Shift Reset: DESATIVADO");
      this.color = var2 ? 5635925 : 16733525;
      this.autoArmorMessage = false;
      this.consumablesMessage = false;
      this.visibleUntil = System.nanoTime() + 2500000000L;
   }

   public void showAutoArmor(boolean var1) {
      this.message = var1 ? "Auto Armor: ATIVADO" : "Auto Armor: DESATIVADO";
      this.color = var1 ? 5635925 : 16733525;
      this.autoArmorMessage = true;
      this.consumablesMessage = false;
      this.visibleUntil = System.nanoTime() + 2500000000L;
   }

   public void showAutoArmorStatus(String var1) {
      this.message = var1;
      this.color = !var1.contains("rejeitou") && !var1.contains("sem confirmação") && !var1.contains("não iniciada") ? 5614335 : 16755285;
      this.autoArmorMessage = true;
      this.consumablesMessage = false;
      this.visibleUntil = System.nanoTime() + 2500000000L;
   }

   public void showAutoConsumables(boolean var1) {
      this.message = var1 ? "Auto Consumíveis: ATIVADO" : "Auto Consumíveis: DESATIVADO";
      this.color = var1 ? 5635925 : 16733525;
      this.autoArmorMessage = false;
      this.consumablesMessage = true;
      this.visibleUntil = System.nanoTime() + 2500000000L;
   }

   public void showAutoConsumablesStatus(String var1) {
      this.message = var1;
      this.color = 5614335;
      this.autoArmorMessage = false;
      this.consumablesMessage = true;
      this.visibleUntil = System.nanoTime() + 2500000000L;
   }

   public String id() {
      return "ynyutils.knockback-status";
   }

   public String name() {
      return "Notificações de PvP";
   }

   public boolean layout(boolean var1, float var2) {
      boolean var3 = this.consumablesMessage
         ? this.consumablesNotificationsEnabled.getAsBoolean()
         : (this.autoArmorMessage ? this.autoArmorNotificationsEnabled.getAsBoolean() : this.knockbackNotificationsEnabled.getAsBoolean());
      return var1 || var3 && System.nanoTime() < this.visibleUntil;
   }

   public int width() {
      avn var1 = ave.A().k;
      if (var1 != this.measuredFont || var1.a() != this.measuredUnicode) {
         this.measuredWidth = Math.max(
            Math.max(var1.a("Knockback: ATIVADO"), var1.a("Knockback: DESATIVADO")),
            Math.max(
               Math.max(var1.a("Auto Armor: ATIVADO"), var1.a("Auto Armor: DESATIVADO")),
               Math.max(
                  Math.max(var1.a("Auto Armor: aguardando confirmação do servidor"), var1.a(this.message)),
                  Math.max(var1.a("Auto Consumíveis: ATIVADO"), var1.a("Auto Consumíveis: DESATIVADO"))
               )
            )
         );
         this.measuredFont = var1;
         this.measuredUnicode = var1.a();
      }

      this.measuredWidth = Math.max(this.measuredWidth, var1.a(this.message));
      return this.measuredWidth;
   }

   public int height() {
      return 10;
   }

   public void draw(boolean var1, float var2) {
      int var3 = var1 ? -this.width() : 0;
      ave.A().k.a(this.message, var3, 0.0F, this.color);
   }

   public HudPlacement defaultPlacement() {
      return new HudPlacement(2, 0, -6, 6, 1.0F, true, false);
   }
}
