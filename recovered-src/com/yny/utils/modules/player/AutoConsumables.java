package com.yny.utils.modules.player;

import com.yny.utils.core.DebugLog;
import dev.xavier.stein.loader.api.Inventory;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

public final class AutoConsumables {
   private static final long TICKS_PER_SECOND = 20L;
   private static final long MIN_USE_TICKS = 32L;
   private static final long MAX_USE_TICKS = 48L;
   private static final long FAILED_USE_RETRY_TICKS = 40L;
   private static final int MAX_USE_RESTARTS = 2;
   private static final long POTION_CONFIRM_TICKS = 60L;
   private static final long SERVER_EFFECT_CONFIRM_TICKS = 40L;
   private static final long ENCHANTED_APPLE_GUARD_TICKS = 600L;
   private final BooleanSupplier enabled;
   private final IntSupplier appleMode;
   private final IntSupplier applePreference;
   private final IntSupplier potionMode;
   private final BooleanSupplier strengthEnabled;
   private final BooleanSupplier speedEnabled;
   private final IntSupplier potionRefreshSeconds;
   private final IntSupplier combatSeconds;
   private final IntSupplier appleCooldownSeconds;
   private final BooleanSupplier diagnostics;
   private final Consumer<String> status;
   private final String[] lastDecision = new String[3];
   private final long[] lastDecisionAt = new long[3];
   private long tick;
   private long combatUntil;
   private long nextAppleTick;
   private final long[] nextFailedUseRetryTick = new long[3];
   private long strengthConfirmUntil;
   private long speedConfirmUntil;
   private int strengthDurationBeforeUse;
   private int speedDurationBeforeUse;
   private long damageEpoch;
   private long lastDamageTick = -1L;
   private final long[] consumedDamageEpoch = new long[3];
   private AutoConsumables.PendingUse pendingUse;
   private AutoConsumables.PendingConfirmation pendingConfirmation;

   public AutoConsumables(
      BooleanSupplier var1,
      IntSupplier var2,
      IntSupplier var3,
      IntSupplier var4,
      BooleanSupplier var5,
      BooleanSupplier var6,
      IntSupplier var7,
      IntSupplier var8,
      IntSupplier var9,
      BooleanSupplier var10,
      Consumer<String> var11
   ) {
      this.enabled = var1;
      this.appleMode = var2;
      this.applePreference = var3;
      this.potionMode = var4;
      this.strengthEnabled = var5;
      this.speedEnabled = var6;
      this.potionRefreshSeconds = var7;
      this.combatSeconds = var8;
      this.appleCooldownSeconds = var9;
      this.diagnostics = var10;
      this.status = var11;
   }

   public void onAttack(Object var1) {
      if (var1 instanceof wn && var1 != ave.A().h) {
         this.markCombat();
         this.trace(0, "combat-attack", "alvo=" + ((wn)var1).e_());
      }
   }

   public void onHurt() {
      this.markCombat();
      this.registerDamage("hurt-signal");
   }

   public void onHealthChanged(float var1, float var2, float var3, float var4) {
      if (ConsumablePolicy.isDamageWithoutHurt(var1, var2, var3, var4)) {
         this.markCombat();
         this.registerDamage("health-drop " + var1 + "->" + var2);
      } else if (var4 < var3) {
         this.trace(0, "absorption-drop-ignored", "absorcao=" + var3 + "->" + var4 + "; hurtTick=" + this.lastDamageTick);
      }
   }

   private void registerDamage(String var1) {
      if (this.lastDamageTick != this.tick) {
         this.lastDamageTick = this.tick;
         this.damageEpoch++;
         this.trace(0, "damage", "source=" + var1 + "; evento=" + this.damageEpoch);
      }
   }

   public void resetSession() {
      this.cancelPendingUse(true);
      this.pendingConfirmation = null;
      this.combatUntil = 0L;
      this.nextAppleTick = 0L;

      for (int var1 = 0; var1 < this.nextFailedUseRetryTick.length; var1++) {
         this.nextFailedUseRetryTick[var1] = 0L;
      }

      this.strengthConfirmUntil = 0L;
      this.speedConfirmUntil = 0L;
      this.damageEpoch = 0L;
      this.lastDamageTick = -1L;

      for (int var2 = 0; var2 < this.consumedDamageEpoch.length; var2++) {
         this.consumedDamageEpoch[var2] = 0L;
      }

      for (int var3 = 0; var3 < this.lastDecision.length; var3++) {
         this.lastDecision[var3] = null;
         this.lastDecisionAt[var3] = 0L;
      }
   }

   public void onTickEnd() {
      this.tick++;
      ave var1 = ave.A();
      bew var2 = var1.h;
      if (this.pendingConfirmation != null) {
         this.checkServerConfirmation(var2);
      } else if (this.pendingUse != null) {
         String var11 = this.pendingAbortReason(var1, var2);
         if (var11 != null) {
            log("uso cancelado: " + var11);
            this.cancelPendingUse(true);
         } else {
            var1.t.ag.h = true;
            this.finishOrContinueUse(var2);
         }
      } else if (!this.beginEnchantedAppleInOpenInventory(var1, var2)) {
         String var3 = this.guardReason(var1, var2);
         if (var3 != null) {
            this.trace(
               0,
               "blocked-" + var3,
               "ativo=" + this.enabled.getAsBoolean() + "; damageEpoch=" + this.damageEpoch + "; held=" + (var2 == null ? "sem-jogador" : describe(var2.bA()))
            );
            this.trace(1, "blocked-" + var3, "auto pot pausado");
            this.trace(2, "blocked-" + var3, "auto pot pausado");
         } else {
            boolean var4 = this.tick < this.combatUntil;
            int var5 = clamp(this.appleMode.getAsInt(), 0, 2);
            boolean var6 = var5 == 2 && var4 || var5 == 1 && var4 && this.damageEpoch > this.consumedDamageEpoch[0];
            if (var6 && this.tick < this.nextFailedUseRetryTick[0]) {
               this.trace(0, "retry-wait", "restam=" + (this.nextFailedUseRetryTick[0] - this.tick) + " ticks");
            } else if (var6 && this.tick >= this.nextAppleTick) {
               int var7 = this.findGoldenApple();
               if (var7 < 0) {
                  this.trace(0, "no-hotbar-apple", "preferencia=" + this.applePreference.getAsInt() + "; hotbar=" + this.hotbarSummary());
               }

               int var8 = var7 < 0 ? 0 : appleEffectRemaining(var2, this.isEnchantedApple(var7));
               boolean var9 = ConsumablePolicy.shouldUse(
                  var5, var4, this.damageEpoch, this.consumedDamageEpoch[0], var8, (int)seconds(this.potionRefreshSeconds.getAsInt())
               );
               if (var7 >= 0 && !var9) {
                  this.trace(
                     0,
                     "effect-active",
                     "remaining=" + var8 + "; mode=" + var5 + "; damageEpoch=" + this.damageEpoch + "; consumedEpoch=" + this.consumedDamageEpoch[0]
                  );
               }

               if (var7 >= 0 && var9 && this.beginUse(var7, var2)) {
                  this.trace(0, "use-started", "slot=" + var7);
                  this.status.accept("Auto Consumíveis: maçã dourada");
                  return;
               }
            } else if (var6) {
               this.trace(0, "apple-cooldown", "restam=" + (this.nextAppleTick - this.tick) + " ticks");
            } else {
               this.trace(
                  0,
                  "not-triggered",
                  "mode=" + var5 + "; combat=" + var4 + "; damageEpoch=" + this.damageEpoch + "; consumedEpoch=" + this.consumedDamageEpoch[0]
               );
            }

            int var12 = clamp(this.potionMode.getAsInt(), 0, 2);
            if (var12 == 0) {
               this.trace(1, "mode-off", "potions OFF");
               this.trace(2, "mode-off", "potions OFF");
            } else {
               int var13 = (int)seconds(this.potionRefreshSeconds.getAsInt());
               int var14 = pe.g.d();
               int var10 = -1;
               if (this.tick >= this.nextFailedUseRetryTick[1]) {
                  var10 = this.findPotion(var2, pe.g, this.strengthEnabled.getAsBoolean(), var12, var4, var13);
               } else {
                  this.trace(1, "retry-wait", "restam=" + (this.nextFailedUseRetryTick[1] - this.tick) + " ticks");
               }

               if (var10 < 0) {
                  var14 = pe.c.d();
                  if (this.tick >= this.nextFailedUseRetryTick[2]) {
                     var10 = this.findPotion(var2, pe.c, this.speedEnabled.getAsBoolean(), var12, var4, var13);
                  } else {
                     this.trace(2, "retry-wait", "restam=" + (this.nextFailedUseRetryTick[2] - this.tick) + " ticks");
                  }
               } else {
                  this.trace(2, "deferred-strength-priority", "forca slot=" + var10);
               }

               if (var10 >= 0 && this.beginUse(var10, var2, var14)) {
                  this.trace(var14 == pe.g.d() ? 1 : 2, "use-started", "slot=" + var10);
                  this.status.accept("Auto Consumíveis: poção");
               }
            }
         }
      }
   }

   public boolean isControllingUseKey() {
      return this.pendingUse != null;
   }

   private void markCombat() {
      int var1 = clamp(this.combatSeconds.getAsInt(), 1, 15);
      this.combatUntil = this.tick + var1 * 20L;
   }

   private boolean beginEnchantedAppleInOpenInventory(ave var1, wn var2) {
      if (this.enabled.getAsBoolean()
         && var2 != null
         && var1.f != null
         && isSafeInventoryScreen(var1)
         && Inventory.cursor() == null
         && var1.c != null
         && !var1.c.a()
         && !var1.c.h()) {
         int var3 = clamp(this.appleMode.getAsInt(), 0, 2);
         boolean var4 = this.tick < this.combatUntil;
         boolean var5 = var3 == 2 && var4 || var3 == 1 && var4 && this.damageEpoch > this.consumedDamageEpoch[0];
         if (var5 && this.tick >= this.nextAppleTick && this.tick >= this.nextFailedUseRetryTick[0] && clamp(this.applePreference.getAsInt(), 0, 2) != 2) {
            int var6 = findEnchantedGoldenApple();
            if (var6 < 0
               || !ConsumablePolicy.shouldUse(
                  var3,
                  var4,
                  this.damageEpoch,
                  this.consumedDamageEpoch[0],
                  appleEffectRemaining(var2, true),
                  (int)seconds(this.potionRefreshSeconds.getAsInt())
               )) {
               return false;
            } else if (this.beginUse(var6, var2)) {
               this.status.accept("Auto Consumíveis: capiroto com inventário aberto");
               return true;
            } else {
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private String guardReason(ave var1, wn var2) {
      if (!this.enabled.getAsBoolean()) {
         return "module-off";
      } else if (var2 == null || var1.f == null) {
         return "no-world";
      } else if (!var2.ai()) {
         return "dead";
      } else if (var1.m != null) {
         return "screen-" + var1.m.getClass().getSimpleName();
      } else if (var1.c == null) {
         return "no-controller";
      } else if (var1.c.a()) {
         return "spectator";
      } else if (var1.c.h()) {
         return "creative";
      } else if (Inventory.isContainerOpen()) {
         return "container-open";
      } else if (Inventory.windowId() != 0) {
         return "window-" + Inventory.windowId();
      } else if (Inventory.cursor() != null) {
         return "cursor-occupied";
      } else {
         return var2.bS() ? "manual-item-use" : null;
      }
   }

   private String pendingAbortReason(ave var1, wn var2) {
      if (!this.enabled.getAsBoolean()) {
         return "module-off";
      }

      if (var2 == null || var1.f == null) {
         return "no-world";
      }

      if (!var2.ai()) {
         return "dead";
      }

      if (var1.m == null || this.pendingUse.item == zy.ao && this.pendingUse.metadata == 1 && isSafeInventoryScreen(var1)) {
         if (var1.c == null) {
            return "no-controller";
         } else if (!Inventory.isContainerOpen() && Inventory.windowId() == 0
            || this.pendingUse.item == zy.ao && this.pendingUse.metadata == 1 && isSafeInventoryScreen(var1)) {
            return Inventory.cursor() != null ? "cursor-occupied" : null;
         } else {
            return "container-open";
         }
      } else {
         return "screen-open";
      }
   }

   private String hotbarSummary() {
      StringBuilder var1 = new StringBuilder();

      for (int var2 = 0; var2 < 9; var2++) {
         if (var2 > 0) {
            var1.append(',');
         }

         var1.append(var2).append(':').append(describe(hotbarStack(var2)));
      }

      return var1.toString();
   }

   private void trace(int var1, String var2, String var3) {
      if (this.diagnostics.getAsBoolean()) {
         if (!var2.equals(this.lastDecision[var1]) || this.tick - this.lastDecisionAt[var1] >= 20L) {
            this.lastDecision[var1] = var2;
            this.lastDecisionAt[var1] = this.tick;
            DebugLog.write(var1 == 0 ? "AutoCapira" : (var1 == 1 ? "AutoPotForca" : "AutoPotVelo"), "tick=" + this.tick + "; reason=" + var2 + "; " + var3);
         }
      }
   }

   private boolean beginUse(int var1, wn var2) {
      return this.beginUse(var1, var2, -1);
   }

   private boolean beginUse(int var1, wn var2, int var3) {
      int var4 = Inventory.selectedHotbar();
      if (var4 < 0 || var4 > 8) {
         var4 = var1;
      }

      Inventory.selectHotbar(var1);
      mc().c.e();
      zx var5 = var2.bA();
      if (var5 != null && isConsumable(var5)) {
         int var6 = var5.b;
         boolean var7 = mc().c.a(var2, mc().f, var5);
         boolean var8 = var2.bS();
         byte var9 = 0;
         byte var10 = -1;
         if (var3 >= 0) {
            pe var11 = var3 == pe.g.d() ? pe.g : (var3 == pe.c.d() ? pe.c : null);
            pf var12 = var11 == null ? null : var2.b(var11);
            var9 = (byte)(var12 == null ? 0 : var12.b());
            var10 = (byte)(var12 == null ? -1 : var12.c());
         }

         this.pendingUse = new AutoConsumables.PendingUse(
            var4,
            var1,
            this.tick,
            var8,
            var5.b(),
            var5.i(),
            var6,
            var3,
            var9,
            var10,
            effectDuration(var2, pe.l),
            effectAmplifier(var2, pe.l),
            effectDuration(var2, pe.x),
            effectAmplifier(var2, pe.x)
         );
         mc().t.ag.h = true;
         log(
            "uso iniciado: slot="
               + var1
               + ", item="
               + var5.b().a()
               + ", count="
               + var6
               + ", localUsing="
               + var8
               + ", effectId="
               + var3
               + ", previousSlot="
               + var4
               + ", controllerChanged="
               + var7
               + ", useKeyHeld=true"
         );
         return true;
      } else {
         log("uso não enviado: slot=" + var1 + ", held=" + (var5 == null ? "vazio" : var5.b().a()));
         if (Inventory.selectedHotbar() == var1) {
            Inventory.selectHotbar(var4);
         }

         return false;
      }
   }

   private void finishOrContinueUse(wn var1) {
      if (var1 == null) {
         this.pendingUse = null;
      } else if (this.tick > this.pendingUse.startedAt) {
         boolean var2 = var1.bS();
         int var3 = Inventory.selectedHotbar();
         zx var4 = hotbarStack(this.pendingUse.usedSlot);
         boolean var5 = itemConsumed(this.pendingUse, var4);
         boolean var6 = effectApplied(var1, this.pendingUse);
         boolean var7 = var5 || var6;
         long var8 = this.tick - this.pendingUse.startedAt;
         if (this.diagnostics.getAsBoolean() && var8 % 5L == 0L) {
            log(
               "uso andamento: tick="
                  + this.tick
                  + ", age="
                  + var8
                  + ", selected="
                  + var3
                  + ", expectedSlot="
                  + this.pendingUse.usedSlot
                  + ", using="
                  + var2
                  + ", sawUsing="
                  + this.pendingUse.sawUsing
                  + ", useKeyPressed="
                  + mc().t.ag.h
                  + ", physicalUse="
                  + avh.a(mc().t.ag)
                  + ", consumedOrEffect="
                  + var7
                  + ", item="
                  + describe(var4)
            );
         }

         if (var7) {
            if (var2) {
               mc().c.c(var1);
            }

            this.finishUse(true, var1, var4, var6 ? "efeito confirmado" : "pilha diminuiu; aguardando efeito do servidor");
         } else if (var3 != this.pendingUse.usedSlot) {
            log("uso interrompido: slot mudou de " + this.pendingUse.usedSlot + " para " + var3);
            this.cancelPendingUse(false);
         } else if (var1.bS()) {
            this.pendingUse.sawUsing = true;
            if (var8 >= 48L) {
               mc().c.c(var1);
               this.finishUse(false, var1, var4, "animação excedeu o limite de 48 ticks");
            }
         } else {
            if (var8 < 32L) {
               this.restartUse(var1, "animação terminou cedo sem consumir");
            } else {
               this.finishUse(false, var1, var4, "32 ticks passaram sem item/efeito confirmado");
            }
         }
      }
   }

   private void restartUse(wn var1, String var2) {
      if (this.pendingUse.restarts >= 2) {
         this.finishUse(false, var1, hotbarStack(this.pendingUse.usedSlot), var2 + "; limite de reinícios");
      } else {
         zx var3 = var1.bA();
         if (var3 != null && var3.b() == this.pendingUse.item && var3.i() == this.pendingUse.metadata) {
            boolean var4 = mc().c.a(var1, mc().f, var3);
            this.pendingUse.restarts++;
            this.pendingUse.startedAt = this.tick;
            this.pendingUse.sawUsing = var1.bS();
            log(
               "uso reiniciado: motivo="
                  + var2
                  + ", tentativa="
                  + this.pendingUse.restarts
                  + ", localUsing="
                  + this.pendingUse.sawUsing
                  + ", controllerChanged="
                  + var4
            );
         } else {
            this.finishUse(false, var1, var3, var2 + "; item mudou");
         }
      }
   }

   private void finishUse(boolean var1, wn var2, zx var3, String var4) {
      AutoConsumables.PendingUse var5 = this.pendingUse;
      boolean var6 = var1 && effectApplied(var2, var5);
      log(
         "uso "
            + (var6 ? "confirmado" : (var1 ? "observado" : "falhou"))
            + ": motivo="
            + var4
            + ", item="
            + var5.itemName
            + ", metadata="
            + var5.metadata
            + ", initialCount="
            + var5.startingCount
            + ", current="
            + describe(var3)
            + ", elapsed="
            + (this.tick - var5.firstStartedAt)
            + ", lastAttemptElapsed="
            + (this.tick - var5.startedAt)
            + ", sawUsing="
            + var5.sawUsing
      );
      if (var1) {
         if (var6) {
            this.confirmUse(var5);
         } else {
            this.acknowledgeObservedConsumption(var5);
            this.pendingConfirmation = new AutoConsumables.PendingConfirmation(var5, this.tick + 40L);
            log("aguardando confirmação do servidor: item=" + var5.itemName + ", slot=" + var5.usedSlot + ", prazo=40 ticks");
         }
      } else {
         this.nextFailedUseRetryTick[var5.kind] = this.tick + 40L;
      }

      restoreUseKey();
      if (Inventory.selectedHotbar() == var5.usedSlot) {
         Inventory.selectHotbar(var5.previousSlot);
      }

      this.pendingUse = null;
   }

   private static zx hotbarStack(int var0) {
      Object var1 = Inventory.stack(0 + var0);
      return var1 instanceof zx ? (zx)var1 : null;
   }

   private static boolean itemConsumed(AutoConsumables.PendingUse var0, zx var1) {
      return var1 == null || var1.b() != var0.item || var1.i() != var0.metadata || var1.b < var0.startingCount;
   }

   private static boolean effectApplied(wn var0, AutoConsumables.PendingUse var1) {
      if (var1.item == zy.ao) {
         return effectIncreased(var0, pe.l, var1.regenerationDurationBeforeUse, var1.regenerationAmplifierBeforeUse)
            || effectIncreased(var0, pe.x, var1.absorptionDurationBeforeUse, var1.absorptionAmplifierBeforeUse);
      }

      pe var2 = var1.potionEffectId == pe.g.d() ? pe.g : (var1.potionEffectId == pe.c.d() ? pe.c : null);
      return var2 != null && effectIncreased(var0, var2, var1.effectDurationBeforeUse, var1.effectAmplifierBeforeUse);
   }

   private static boolean effectIncreased(wn var0, pe var1, int var2, int var3) {
      pf var4 = var0.b(var1);
      return var4 != null && (var4.b() > var2 + 5 || var4.c() > var3);
   }

   private static int effectDuration(wn var0, pe var1) {
      pf var2 = var0.b(var1);
      return var2 == null ? 0 : var2.b();
   }

   private static int effectAmplifier(wn var0, pe var1) {
      pf var2 = var0.b(var1);
      return var2 == null ? -1 : var2.c();
   }

   private void checkServerConfirmation(wn var1) {
      AutoConsumables.PendingConfirmation var2 = this.pendingConfirmation;
      if (var1 != null && var1.ai() && mc().f != null) {
         if (effectApplied(var1, var2.use)) {
            log("efeito confirmado pelo servidor: item=" + var2.use.itemName + ", espera=" + (this.tick - var2.use.firstStartedAt) + " ticks");
            this.confirmUse(var2.use);
            this.pendingConfirmation = null;
         } else if (this.tick >= var2.deadline) {
            zx var3 = hotbarStack(var2.use.usedSlot);
            log("sem confirmação de efeito do servidor: item=" + var2.use.itemName + ", pilha=" + describe(var3) + "; nova tentativa após cooldown");
            this.nextFailedUseRetryTick[var2.use.kind] = this.tick + 40L;
            this.pendingConfirmation = null;
         }
      } else {
         this.pendingConfirmation = null;
      }
   }

   private void confirmUse(AutoConsumables.PendingUse var1) {
      this.registerPotionConfirmation(var1);
      this.consumedDamageEpoch[var1.kind] = this.damageEpoch;
      if (var1.item == zy.ao) {
         this.nextAppleTick = this.tick + seconds(this.appleCooldownSeconds.getAsInt());
      }
   }

   private void acknowledgeObservedConsumption(AutoConsumables.PendingUse var1) {
      this.consumedDamageEpoch[var1.kind] = this.damageEpoch;
      if (var1.item == zy.ao) {
         long var2 = var1.metadata == 1 ? 600L : seconds(this.appleCooldownSeconds.getAsInt());
         this.nextAppleTick = this.tick + Math.max(seconds(this.appleCooldownSeconds.getAsInt()), var2);
      }
   }

   private static String describe(zx var0) {
      return var0 == null ? "vazio" : var0.b().a() + "[meta=" + var0.i() + ",count=" + var0.b + "]";
   }

   private void cancelPendingUse(boolean var1) {
      if (this.pendingUse != null) {
         log("uso cancelado: slot=" + this.pendingUse.usedSlot + ", restoreSlot=" + var1);
         ave var2 = ave.A();
         bew var3 = var2.h;
         if (var3 != null && var3.bS() && Inventory.selectedHotbar() == this.pendingUse.usedSlot && var2.f != null && var2.c != null) {
            var2.c.c(var3);
         }

         restoreUseKey();
         if (var1 && Inventory.selectedHotbar() == this.pendingUse.usedSlot) {
            Inventory.selectHotbar(this.pendingUse.previousSlot);
         }

         this.nextFailedUseRetryTick[this.pendingUse.kind] = this.tick + 40L;
         this.pendingUse = null;
      }
   }

   private static void restoreUseKey() {
      ave var0 = ave.A();
      var0.t.ag.h = avh.a(var0.t.ag);
   }

   private int findGoldenApple() {
      int var1 = clamp(this.applePreference.getAsInt(), 0, 2);
      int var2 = -1;
      int var3 = -1;

      for (int var4 = 0; var4 < 9; var4++) {
         Object var5 = Inventory.stack(0 + var4);
         zx var6 = var5 instanceof zx ? (zx)var5 : null;
         if (var6 != null && var6.b() == zy.ao) {
            if (var6.i() == 1) {
               if (var2 < 0) {
                  var2 = var4;
               }
            } else if (var3 < 0) {
               var3 = var4;
            }
         }
      }

      if (var1 == 1) {
         return var2;
      } else if (var1 == 2) {
         return var3;
      } else {
         return var2 >= 0 ? var2 : var3;
      }
   }

   private static int findEnchantedGoldenApple() {
      for (int var0 = 0; var0 < 9; var0++) {
         Object var1 = Inventory.stack(0 + var0);
         zx var2 = var1 instanceof zx ? (zx)var1 : null;
         if (var2 != null && var2.b() == zy.ao && var2.i() == 1) {
            return var0;
         }
      }

      return -1;
   }

   private static boolean isSafeInventoryScreen(ave var0) {
      return var0.m instanceof ayl && !(var0.m instanceof aym);
   }

   private static boolean isConsumable(zx var0) {
      return var0.b() == zy.ao || var0.b() instanceof aai;
   }

   private boolean isEnchantedApple(int var1) {
      Object var2 = Inventory.stack(0 + var1);
      return var2 instanceof zx && ((zx)var2).b() == zy.ao && ((zx)var2).i() == 1;
   }

   private static int appleEffectRemaining(wn var0, boolean var1) {
      pf var2 = var0.b(pe.l);
      return var2 != null && var2.c() >= (var1 ? 4 : 1) ? var2.b() : 0;
   }

   private int findPotion(wn var1, pe var2, boolean var3, int var4, boolean var5, int var6) {
      int var7 = var2 == pe.g ? 1 : 2;
      if (!var3) {
         this.trace(var7, "effect-disabled", "efeito=" + var2.a());
         return -1;
      }

      pf var8 = var1.b(var2);
      int var9 = var8 == null ? 0 : var8.b();
      if (this.awaitingPotionEffect(var2, var9)) {
         this.trace(var7, "awaiting-confirmation", "remaining=" + var9);
         return -1;
      }

      if (!ConsumablePolicy.shouldUse(var4, var5, this.damageEpoch, this.consumedDamageEpoch[var7], var9, var6)) {
         this.trace(
            var7,
            "not-due",
            "mode="
               + var4
               + "; combat="
               + var5
               + "; remaining="
               + var9
               + "; refreshAt="
               + var6
               + "; damageEpoch="
               + this.damageEpoch
               + "; consumedEpoch="
               + this.consumedDamageEpoch[var7]
         );
         return -1;
      }

      int var10 = 0;
      int var11 = 0;
      int var12 = 0;

      for (int var13 = 0; var13 < 9; var13++) {
         Object var14 = Inventory.stack(0 + var13);
         zx var15 = var14 instanceof zx ? (zx)var14 : null;
         if (var15 != null && var15.b() instanceof aai) {
            if (aai.f(var15.i())) {
               var11++;
            } else {
               var10++;
               List var16 = ((aai)var15.b()).h(var15);
               if (var16 == null) {
                  var12++;
               } else {
                  for (Object var18 : var16) {
                     if (var18 instanceof pf && ((pf)var18).a() == var2.d()) {
                        this.trace(var7, "ready-slot", "slot=" + var13 + "; remaining=" + var9 + "; hotbar=" + this.hotbarSummary());
                        return var13;
                     }
                  }
               }
            }
         }
      }

      this.trace(
         var7,
         "no-matching-hotbar-potion",
         "remaining=" + var9 + "; drinkable=" + var10 + "; splashIgnored=" + var11 + "; noEffects=" + var12 + "; hotbar=" + this.hotbarSummary()
      );
      return -1;
   }

   private boolean awaitingPotionEffect(pe var1, int var2) {
      int var3 = var1.d();
      long var4 = var3 == pe.g.d() ? this.strengthConfirmUntil : this.speedConfirmUntil;
      int var6 = var3 == pe.g.d() ? this.strengthDurationBeforeUse : this.speedDurationBeforeUse;
      if (var4 <= this.tick) {
         if (var3 == pe.g.d()) {
            this.strengthConfirmUntil = 0L;
         } else {
            this.speedConfirmUntil = 0L;
         }

         return false;
      } else if (var2 > var6 + 5) {
         if (var3 == pe.g.d()) {
            this.strengthConfirmUntil = 0L;
         } else {
            this.speedConfirmUntil = 0L;
         }

         return false;
      } else {
         return true;
      }
   }

   private void registerPotionConfirmation(AutoConsumables.PendingUse var1) {
      if (var1.potionEffectId == pe.g.d()) {
         this.strengthConfirmUntil = this.tick + 60L;
         this.strengthDurationBeforeUse = var1.effectDurationBeforeUse;
      } else if (var1.potionEffectId == pe.c.d()) {
         this.speedConfirmUntil = this.tick + 60L;
         this.speedDurationBeforeUse = var1.effectDurationBeforeUse;
      }
   }

   private static long seconds(int var0) {
      return clamp(var0, 1, 30) * 20L;
   }

   private static int clamp(int var0, int var1, int var2) {
      return Math.max(var1, Math.min(var2, var0));
   }

   private static ave mc() {
      return ave.A();
   }

   private static void log(String var0) {
      DebugLog.write("AutoConsumables", var0);
   }

   private static final class PendingConfirmation {
      final AutoConsumables.PendingUse use;
      final long deadline;

      PendingConfirmation(AutoConsumables.PendingUse var1, long var2) {
         this.use = var1;
         this.deadline = var2;
      }
   }

   private static final class PendingUse {
      final int previousSlot;
      final int usedSlot;
      final long firstStartedAt;
      long startedAt;
      final String itemName;
      final zw item;
      final int metadata;
      final int startingCount;
      final int potionEffectId;
      final int kind;
      final int effectDurationBeforeUse;
      final int effectAmplifierBeforeUse;
      final int regenerationDurationBeforeUse;
      final int regenerationAmplifierBeforeUse;
      final int absorptionDurationBeforeUse;
      final int absorptionAmplifierBeforeUse;
      boolean sawUsing;
      int restarts;

      PendingUse(
         int var1, int var2, long var3, boolean var5, zw var6, int var7, int var8, int var9, int var10, int var11, int var12, int var13, int var14, int var15
      ) {
         this.previousSlot = var1;
         this.usedSlot = var2;
         this.firstStartedAt = var3;
         this.startedAt = var3;
         this.sawUsing = var5;
         this.itemName = var6.a();
         this.item = var6;
         this.metadata = var7;
         this.startingCount = var8;
         this.potionEffectId = var9;
         this.kind = var9 == pe.g.d() ? 1 : (var9 == pe.c.d() ? 2 : 0);
         this.effectDurationBeforeUse = var10;
         this.effectAmplifierBeforeUse = var11;
         this.regenerationDurationBeforeUse = var12;
         this.regenerationAmplifierBeforeUse = var13;
         this.absorptionDurationBeforeUse = var14;
         this.absorptionAmplifierBeforeUse = var15;
      }
   }
}
