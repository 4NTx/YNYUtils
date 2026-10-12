package com.yny.utils.modules.player;

import com.yny.utils.core.DebugLog;
import dev.xavier.stein.loader.api.Inventory;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import yj.a;

public final class AutoArmor {
   private static final int[] PRIORITY = new int[]{2, 1, 3, 0};
   private static final a[] PREFERRED_MATERIALS = new a[]{null, a.e, a.c, a.b, a.d, a.a};
   private static final long FIRST_RETRY_TICKS = 1L;
   private static final long SECOND_RETRY_TICKS = 3L;
   private static final long LATER_RETRY_TICKS = 10L;
   private static final long NO_ITEM_RECHECK_TICKS = 4L;
   private static final long CLICK_DELAY_TICKS = 1L;
   private static final long SERVER_WAIT_TIMEOUT_TICKS = 8L;
   private static final int CURSOR_RETURN_TIMEOUT_TICKS = 8;
   private static final int CURSOR_RETURN_MAX_ATTEMPTS = 3;
   private static final int CURSOR_NO_SLOT_GRACE_TICKS = 8;
   private static final int MIN_DISTINGUISHABLE_DAMAGE_GAP = 32;
   private final BooleanSupplier enabled;
   private final IntSupplier preventiveThreshold;
   private final IntSupplier preferredMaterial;
   private final BooleanSupplier ignoreUnenchanted;
   private final BooleanSupplier useDamagedReserves;
   private final BooleanSupplier dropUnenchantedOld;
   private final BooleanSupplier dropStuckOld;
   private final Consumer<String> status;
   private final long[] retryAt = new long[4];
   private final int[] failedAttempts = new int[4];
   private final long[] lastNoChoiceLog = new long[4];
   private final boolean[] preventiveFailed = new boolean[4];
   private final zx[] failedWornArmor = new zx[4];
   private long tick;
   private long nextClickAt;
   private AutoArmor.Pending pending;
   private String lastPauseReason;

   public AutoArmor(
      BooleanSupplier var1,
      IntSupplier var2,
      IntSupplier var3,
      BooleanSupplier var4,
      BooleanSupplier var5,
      BooleanSupplier var6,
      BooleanSupplier var7,
      Consumer<String> var8
   ) {
      this.enabled = var1;
      this.preventiveThreshold = var2;
      this.preferredMaterial = var3;
      this.ignoreUnenchanted = var4;
      this.useDamagedReserves = var5;
      this.dropUnenchantedOld = var6;
      this.dropStuckOld = var7;
      this.status = var8;
   }

   private static boolean isOwnInventoryScreen(ave var0) {
      return var0.m instanceof azc && !Inventory.isContainerOpen() && Inventory.windowId() == 0;
   }

   private static boolean hasUnsafeInventoryUi(ave var0) {
      return !isOwnInventoryScreen(var0) && (var0.m != null || Inventory.isScreenOpen() || Inventory.isContainerOpen());
   }

   public void onTickEnd() {
      this.tick++;
      if (this.pending != null) {
         ave var9 = ave.A();
         if (hasUnsafeInventoryUi(var9)) {
            this.pending.manualIntervened = true;
            this.pending.serverOldCursor = null;
         }

         this.observeCurrentInventory();
         this.advancePending();
      } else if (this.enabled.getAsBoolean()) {
         ave var1 = ave.A();
         if (var1.h != null
            && var1.f != null
            && var1.h.ai()
            && var1.c != null
            && !var1.c.h()
            && !var1.c.a()
            && !hasUnsafeInventoryUi(var1)
            && !Inventory.isContainerOpen()
            && Inventory.windowId() == 0
            && Inventory.cursor() == null) {
            this.lastPauseReason = null;
            if (this.tick >= this.nextClickAt) {
               int var10 = Math.max(0, Math.min(50, this.preventiveThreshold.getAsInt()));

               for (int var6 : PRIORITY) {
                  zx var7 = asStack(Inventory.armor(var6));
                  if (this.preventiveFailed[var6]) {
                     if (var7 != null && !isBroken(var7)) {
                        if (matchesArmor(var7, this.failedWornArmor[var6])) {
                           continue;
                        }

                        this.preventiveFailed[var6] = false;
                        this.failedWornArmor[var6] = null;
                     } else {
                        this.preventiveFailed[var6] = false;
                        this.failedWornArmor[var6] = null;
                        log(pieceName(var6) + " quebrou após falha preventiva; iniciando reposição urgente");
                     }
                  }

                  if ((var7 == null || isBroken(var7) || var10 != 0 && durabilityPercent(var7) <= var10)
                     && (var7 == null || isBroken(var7) || this.tick >= this.retryAt[var6])) {
                     AutoArmor.Choice var8 = this.bestReplacement(var6, var7, var10);
                     if (var8 != null) {
                        this.beginEquip(var6, var8, var7);
                        return;
                     }

                     if (this.lastNoChoiceLog[var6] == 0L || this.tick - this.lastNoChoiceLog[var6] >= 100L) {
                        log(
                           "sem reserva elegível para "
                              + pieceName(var6)
                              + "; worn="
                              + describe(var7)
                              + "; threshold="
                              + var10
                              + "%; ignoreUnenchanted="
                              + this.ignoreUnenchanted.getAsBoolean()
                              + "; damagedReserves="
                              + this.useDamagedReserves.getAsBoolean()
                        );
                        this.lastNoChoiceLog[var6] = this.tick;
                     }

                     this.retryAt[var6] = this.tick + 4L;
                  }
               }
            }
         } else {
            String var2 = "pausado: player="
               + (var1.h != null)
               + "; world="
               + (var1.f != null)
               + "; alive="
               + (var1.h != null && var1.h.ai())
               + "; controller="
               + (var1.c != null)
               + "; creative="
               + (var1.c != null && var1.c.h())
               + "; spectator="
               + (var1.c != null && var1.c.a())
               + "; screen="
               + (var1.m == null ? "none" : var1.m.getClass().getName())
               + "; container="
               + Inventory.isContainerOpen()
               + "; window="
               + Inventory.windowId()
               + "; cursor="
               + describe(asStack(Inventory.cursor()));
            if (!var2.equals(this.lastPauseReason)) {
               log("tick=" + this.tick + " " + var2);
               this.lastPauseReason = var2;
            }
         }
      }
   }

   private void beginEquip(int var1, AutoArmor.Choice var2, zx var3) {
      if (this.tick < this.retryAt[var1]) return;
      AutoArmor.Pending var4 = new AutoArmor.Pending(
         var1, var2.inventoryIndex, var2.stack, var3, this.dropUnenchantedOld.getAsBoolean() && var3 != null && !var3.w()
      );
      this.pending = var4;
      this.nextClickAt = this.tick + 1L;
      if (!Inventory.equip(var2.inventoryIndex)) {
         log("equip() recusou " + pieceName(var1) + " source=" + var2.inventoryIndex + " worn=" + describe(var4.oldArmor) + " reserve=" + describe(var2.stack));
         this.pending = null;
         this.deferPreventiveUntilBroken(var4);
         this.scheduleRetry(var1);
         this.status.accept("Auto Armor: troca não iniciada");
      } else {
         log(
            "iniciada "
               + pieceName(var1)
               + " source="
               + var2.inventoryIndex
               + " mode="
               + (var3 != null && !isBroken(var3) ? "preventiva" : "reposicao urgente")
               + " worn="
               + describe(var4.oldArmor)
               + " reserve="
               + describe(var2.stack)
         );
         this.observeCurrentInventory();
      }
   }

   public void onSlotChanged(int var1, int var2, Object var3) {
      if (this.pending != null) {
         zx var4 = asStack(var3);
         if (var1 == -1 && var2 == -1) {
            if (var4 != null
               && this.pending.serverOldCursor == null
               && !this.pending.manualIntervened
               && this.pending.oldArmor != null
               && this.pending.localApplied
               && matchesOldIdentityAndDamage(var4, this.pending)) {
               this.pending.serverOldCursor = copy(var4);
               log("cursor do servidor identificado como peça antiga: " + describe(var4));
            }

            if ((this.pending.cursorReturnIndex >= 0 || this.pending.cursorDropIssued) && var4 == null) {
               this.pending.cursorClearSeen = true;
            }

            if (this.pending.rejected && var4 == null) {
               this.finishRejected();
            }
         } else if (var1 == 0) {
            int var5 = Inventory.inventoryIndexOf(var2);
            if (var5 == this.pending.sourceIndex || var5 == 36 + this.pending.piece || var5 == this.pending.cursorReturnIndex) {
               if (var5 == this.pending.cursorReturnIndex && matchesOldOrObservedCursor(var4, this.pending)) {
                  this.pending.cursorReturnSlotSeen = true;
               }

               if (this.pending.dropping) {
                  if (var5 == this.pending.sourceIndex && var4 == null) {
                     this.pending.serverConfirmed = true;
                  }
               } else {
                  if (var5 == this.pending.sourceIndex) {
                     this.pending.sourceSeen = true;
                     this.pending.source = copy(var4);
                  }

                  if (var5 == 36 + this.pending.piece) {
                     this.pending.armorSeen = true;
                     this.pending.armor = copy(var4);
                  }

                  this.confirmIfAuthoritative();
               }
            }
         }
      }
   }

   public void onWindowItems(int var1) {
      if (this.pending != null && var1 == 0) {
         if (this.pending.cursorReturnIndex >= 0) {
            this.pending.cursorClearSeen = Inventory.cursor() == null;
            this.pending.cursorReturnSlotSeen = matchesOldOrObservedCursor(asStack(Inventory.stack(this.pending.cursorReturnIndex)), this.pending);
         }

         if (this.pending.dropping) {
            if (Inventory.stack(this.pending.sourceIndex) == null) {
               this.pending.serverConfirmed = true;
            } else {
               this.pending.rejected = true;
            }
         } else {
            this.pending.sourceSeen = true;
            this.pending.source = copy(asStack(Inventory.stack(this.pending.sourceIndex)));
            this.pending.armorSeen = true;
            this.pending.armor = copy(asStack(Inventory.armor(this.pending.piece)));
            if (matchesEquipState(this.pending.source, this.pending.armor, this.pending) && Inventory.cursor() == null) {
               boolean var2 = !this.pending.serverConfirmed;
               this.pending.serverConfirmed = true;
               this.pending.localApplied = true;
               if (var2) {
                  logConfirmation("snapshot", this.pending);
               }
            }
         }
      }
   }

   public void resetSession() {
      this.pending = null;
      this.nextClickAt = 0L;
      this.lastPauseReason = null;

      for (int var1 = 0; var1 < this.retryAt.length; var1++) {
         this.retryAt[var1] = 0L;
         this.failedAttempts[var1] = 0;
         this.preventiveFailed[var1] = false;
         this.failedWornArmor[var1] = null;
      }
   }

   private void observeCurrentInventory() {
      if (!this.pending.dropping) {
         zx var1 = asStack(Inventory.stack(this.pending.sourceIndex));
         zx var2 = asStack(Inventory.armor(this.pending.piece));
         boolean var3 = matchesEquipState(var1, var2, this.pending);
         if (Inventory.cursor() == null && var3) {
            this.pending.localApplied = true;
         }
      }
   }

   private void confirmIfAuthoritative() {
      if (!this.pending.serverConfirmed) {
         if (this.pending.sourceSeen && this.pending.armorSeen && matchesEquipState(this.pending.source, this.pending.armor, this.pending)) {
            this.pending.serverConfirmed = true;
            this.pending.localApplied = true;
            logConfirmation("slots", this.pending);
         }
      }
   }

   private void advancePending() {
      this.pending.age++;
      if (this.pending.rejected) {
         if (Inventory.cursor() == null) {
            this.finishRejected();
         }
      } else if (this.pending.dropping && this.pending.serverConfirmed) {
         this.status.accept("Auto Armor: peça antiga descartada");
         this.pending = null;
      } else if (!this.pending.localApplied || !this.pending.serverConfirmed) {
         if (this.pending.age >= 8L && Inventory.cursor() != null) {
            if (!this.pending.cursorTimeoutLogged) {
               log(
                  "FALLBACK BLOQUEADO: cursor não vazio após timeout em "
                     + pieceName(this.pending.piece)
                     + "; cursor="
                     + describe(asStack(Inventory.cursor()))
                     + "; source="
                     + describe(asStack(Inventory.stack(this.pending.sourceIndex)))
                     + "; armor="
                     + describe(asStack(Inventory.armor(this.pending.piece)))
                     + "; pending="
                     + pendingSummary(this.pending)
               );
               this.status.accept("Auto Armor: cursor ocupado; troca pausada");
               this.pending.cursorTimeoutLogged = true;
            }
         } else {
            if (this.pending.age >= Long.MAX_VALUE) {
               if (this.pending.localApplied
                  && matchesEquipState(asStack(Inventory.stack(this.pending.sourceIndex)), asStack(Inventory.armor(this.pending.piece)), this.pending)) {
                  this.failedAttempts[this.pending.piece] = 0;
                  this.status.accept("Auto Armor: peça equipada; confirmação atrasada");
                  log(
                     "fallback local após timeout "
                        + pieceName(this.pending.piece)
                        + "; source="
                        + describe(asStack(Inventory.stack(this.pending.sourceIndex)))
                        + "; armor="
                        + describe(asStack(Inventory.armor(this.pending.piece)))
                  );
                  this.pending = null;
               } else {
                  log(
                     "timeout sem troca coerente "
                        + pieceName(this.pending.piece)
                        + "; source="
                        + describe(asStack(Inventory.stack(this.pending.sourceIndex)))
                        + "; armor="
                        + describe(asStack(Inventory.armor(this.pending.piece)))
                        + "; cursor="
                        + describe(asStack(Inventory.cursor()))
                  );
                  this.deferPreventiveUntilBroken(this.pending);
                  this.scheduleRetry(this.pending.piece);
                  this.pending = null;
               }
            }
         }
      } else if (this.pending.age >= 1) {
         ArmorSwapSafety.Outcome var1 = this.settleOldArmorCursor();
         if (var1 != ArmorSwapSafety.Outcome.WAIT) {
            if (var1 != ArmorSwapSafety.Outcome.ARMOR_CHANGED && var1 != ArmorSwapSafety.Outcome.DESTINATION_UNKNOWN) {
               this.failedAttempts[this.pending.piece] = 0;
               this.preventiveFailed[this.pending.piece] = false;
               this.failedWornArmor[this.pending.piece] = null;
               if (this.pending.dropOld
                  && this.pending.cursorReturnIndex < 0
                  && this.enabled.getAsBoolean()
                  && matchesExpected(asStack(Inventory.stack(this.pending.sourceIndex)), this.pending.oldArmor)
                  && Inventory.cursor() == null) {
                  this.pending.dropping = true;
                  this.pending.serverConfirmed = false;
                  this.pending.sourceSeen = false;
                  if (Inventory.drop(this.pending.sourceIndex, true)) {
                     return;
                  }

                  this.pending.dropping = false;
               }

               this.status.accept("Auto Armor: " + pieceName(this.pending.piece) + " equipada");
               log("concluída " + pieceName(this.pending.piece) + "; armor=" + describe(asStack(Inventory.armor(this.pending.piece))));
               this.pending = null;
            } else {
               log(
                  "troca com resultado incerto em "
                     + pieceName(this.pending.piece)
                     + "; outcome="
                     + var1
                     + "; cursor="
                     + describe(asStack(Inventory.cursor()))
                     + "; destino="
                     + (this.pending.cursorReturnIndex < 0 ? "n/a" : describe(asStack(Inventory.stack(this.pending.cursorReturnIndex))))
                     + "; armor="
                     + describe(asStack(Inventory.armor(this.pending.piece)))
               );
               this.status.accept("Auto Armor: verificando " + pieceName(this.pending.piece) + " novamente");
               this.nextClickAt = this.tick + 1L;
               this.pending = null;
            }
         }
      }
   }

   private ArmorSwapSafety.Outcome settleOldArmorCursor() {
      ave var1 = ave.A();
      boolean var2 = ArmorSwapSafety.mayReturnCursor(
         var1.h != null && var1.h.ai(), hasUnsafeInventoryUi(var1), Inventory.isContainerOpen(), Inventory.windowId()
      );
      if (!var2) {
         if (hasUnsafeInventoryUi(var1)) {
            this.pending.manualIntervened = true;
            this.pending.serverOldCursor = null;
         }

         if (!this.pending.manualPauseLogged) {
            log(
               "recuperação pausada para intervenção do jogador: "
                  + pieceName(this.pending.piece)
                  + "; screen="
                  + (var1.m == null ? "none" : var1.m.getClass().getName())
                  + "; window="
                  + Inventory.windowId()
            );
            this.pending.manualPauseLogged = true;
         }

         return ArmorSwapSafety.Outcome.WAIT;
      } else {
         this.pending.manualPauseLogged = false;
         zx var3 = asStack(Inventory.cursor());
         if (!this.pending.cursorDropIssued) {
            if (var3 != null) {
               if (!matchesOldOrObservedCursor(var3, this.pending)) {
                  if (!this.pending.cursorRecoveryWarned) {
                     log(
                        "cursor ocupado por outro item durante "
                           + pieceName(this.pending.piece)
                           + "; item="
                           + describe(var3)
                           + "; antigo="
                           + describe(this.pending.oldArmor)
                     );
                     this.status.accept("Auto Armor: cursor ocupado; troca pausada");
                     this.pending.cursorRecoveryWarned = true;
                  }

                  return ArmorSwapSafety.Outcome.WAIT;
               } else {
                  if (this.pending.cursorReturnIndex >= 0 && ++this.pending.cursorRecoveryAge < 8) {
                     return ArmorSwapSafety.Outcome.WAIT;
                  }

                  if (this.pending.cursorReturnAttempts >= 3) {
                     if (this.dropStuckCursor(var3, true)) {
                        return ArmorSwapSafety.Outcome.WAIT;
                     }

                     if (!this.pending.cursorRecoveryWarned) {
                        log("devolução da peça antiga não confirmada após 3 tentativas; cursor=" + describe(var3));
                        this.status.accept("Auto Armor: peça no cursor; resolva no inventário");
                        this.pending.cursorRecoveryWarned = true;
                     }

                     return ArmorSwapSafety.Outcome.WAIT;
                  } else {
                     int var6 = Inventory.stack(this.pending.sourceIndex) == null ? this.pending.sourceIndex : Inventory.firstEmpty();
                     int var5 = var6 < 0 ? -1 : Inventory.slotOf(var6);
                     if (var5 >= 0 && Inventory.stack(var6) == null && Inventory.accepts(var5, var3)) {
                        this.pending.cursorNoSlotAge = 0;
                        this.pending.cursorReturnIndex = var6;
                        this.pending.cursorRecoveryAge = 0;
                        this.pending.cursorReturnAttempts++;
                        this.pending.cursorClearSeen = false;
                        this.pending.cursorReturnSlotSeen = false;
                        log("devolvendo peça antiga do cursor: " + pieceName(this.pending.piece) + " -> inventário " + var6 + "; item=" + describe(var3));
                        if (!Inventory.pickup(var5)) {
                           this.pending.cursorReturnIndex = -1;
                           log("clique de devolução recusado para " + pieceName(this.pending.piece));
                        }

                        return ArmorSwapSafety.Outcome.WAIT;
                     } else {
                        if (++this.pending.cursorNoSlotAge >= 8 && this.dropStuckCursor(var3, false)) {
                           return ArmorSwapSafety.Outcome.WAIT;
                        }

                        if (!this.pending.cursorRecoveryWarned) {
                           log(
                              "sem slot seguro para devolver peça antiga de "
                                 + pieceName(this.pending.piece)
                                 + "; cursor="
                                 + describe(var3)
                                 + "; source="
                                 + describe(asStack(Inventory.stack(this.pending.sourceIndex)))
                           );
                           this.status.accept("Auto Armor: peça antiga no cursor; inventário cheio");
                           this.pending.cursorRecoveryWarned = true;
                        }

                        return ArmorSwapSafety.Outcome.WAIT;
                     }
                  }
               }
            } else if (this.pending.cursorReturnIndex >= 0) {
               ArmorSwapSafety.Outcome var4 = ArmorSwapSafety.returned(
                  true,
                  matchesReplacementArmor(asStack(Inventory.armor(this.pending.piece)), this.pending),
                  matchesOldOrObservedCursor(asStack(Inventory.stack(this.pending.cursorReturnIndex)), this.pending),
                  this.pending.cursorClearSeen,
                  this.pending.cursorReturnSlotSeen,
                  ++this.pending.cursorRecoveryAge,
                  8
               );
               if (var4 != ArmorSwapSafety.Outcome.WAIT) {
                  log(
                     "devolução "
                        + var4
                        + ": "
                        + pieceName(this.pending.piece)
                        + "; destino="
                        + describe(asStack(Inventory.stack(this.pending.cursorReturnIndex)))
                        + "; armor="
                        + describe(asStack(Inventory.armor(this.pending.piece)))
                  );
               }

               return var4;
            } else {
               return matchesReplacementArmor(asStack(Inventory.armor(this.pending.piece)), this.pending)
                  ? ArmorSwapSafety.Outcome.CONFIRMED
                  : ArmorSwapSafety.Outcome.ARMOR_CHANGED;
            }
         } else if (var3 != null || !this.pending.cursorClearSeen && ++this.pending.cursorDropAge < 8) {
            if (var3 != null && ++this.pending.cursorDropAge == 8) {
               log("descarte do cursor não confirmado; peça ainda no cursor: " + describe(var3));
               this.status.accept("Auto Armor: cursor ainda ocupado; intervenção manual necessária");
            }

            return ArmorSwapSafety.Outcome.WAIT;
         } else {
            log("descarte do cursor concluído: " + pieceName(this.pending.piece) + "; confirmação servidor=" + this.pending.cursorClearSeen);
            return matchesReplacementArmor(asStack(Inventory.armor(this.pending.piece)), this.pending)
               ? ArmorSwapSafety.Outcome.CONFIRMED
               : ArmorSwapSafety.Outcome.ARMOR_CHANGED;
         }
      }
   }

   private boolean dropStuckCursor(zx var1, boolean var2) {
      boolean var3 = this.pending.serverOldCursor != null && matchesObservedCursor(var1, this.pending.serverOldCursor);
      boolean var4 = ArmorSwapSafety.mayDropStuckOld(
         this.dropStuckOld.getAsBoolean(),
         this.pending.manualIntervened,
         var3,
         this.pending.serverConfirmed,
         matchesReplacementArmor(asStack(Inventory.armor(this.pending.piece)), this.pending),
         var2,
         this.pending.cursorNoSlotAge,
         8
      );
      if (!var4) {
         return false;
      }

      this.pending.cursorDropIssued = true;
      this.pending.cursorDropAge = 0;
      this.pending.cursorClearSeen = false;
      log(
         "ÚLTIMO RECURSO: descartando peça antiga presa no cursor: "
            + describe(var1)
            + "; tentativas de devolução="
            + this.pending.cursorReturnAttempts
            + "; sem slot ticks="
            + this.pending.cursorNoSlotAge
      );
      this.status.accept("Auto Armor: peça antiga presa descartada");
      if (Inventory.dropCursor()) {
         return true;
      }

      log("descarte do cursor recusado pelo SDK; peça preservada no cursor");
      this.status.accept("Auto Armor: não foi possível liberar o cursor");
      return false;
   }

   private void finishRejected() {
      if (this.pending != null) {
         log("troca rejeitada/desync " + pieceName(this.pending.piece) + "; tentativa=" + (this.failedAttempts[this.pending.piece] + 1));
         this.deferPreventiveUntilBroken(this.pending);
         this.scheduleRetry(this.pending.piece);
         this.pending = null;
      }
   }

   private void deferPreventiveUntilBroken(AutoArmor.Pending var1) {
      // A falha preventiva pode ser temporária. scheduleRetry limita a próxima tentativa.
   }

   private void scheduleRetry(int var1) {
      int var2 = ++this.failedAttempts[var1];
      long var3 = var2 == 1 ? 1L : (var2 == 2 ? 2L : 4L);
      this.retryAt[var1] = this.tick + var3;
      if (var2 == 2) {
         this.status.accept("Auto Armor: sem confirmação; tentando novamente");
      } else if (var2 >= 4) {
         this.status.accept("Auto Armor: troca não confirmada; verificando novamente");
      }
   }

   private AutoArmor.Choice bestReplacement(int var1, zx var2, int var3) {
      AutoArmor.Choice var4 = null;
      AutoArmor.Choice var5 = null;
      StringBuilder var6 = new StringBuilder();
      int var7 = Math.max(0, Math.min(PREFERRED_MATERIALS.length - 1, this.preferredMaterial.getAsInt()));
      boolean var8 = var2 != null && !isBroken(var2) && var3 > 0 && durabilityPercent(var2) <= var3;

      for (int var9 = 0; var9 < 36; var9++) {
         zx var10 = asStack(Inventory.stack(var9));
         if (var10 != null) {
            int var11 = Inventory.armorPiece(var10);
            String var12 = "eligible";
            if (var11 != var1) {
               var12 = "wrong-piece:" + var11;
            } else if (isBroken(var10)) {
               var12 = "broken";
            } else if (this.ignoreUnenchanted.getAsBoolean() && !var10.w()) {
               var12 = "unenchanted";
            } else if ((var2 == null || isBroken(var2)) && !this.useDamagedReserves.getAsBoolean() && durabilityPercent(var10) < 100) {
               var12 = "damaged-reserve-disabled";
            } else if (var8 && isPreventiveUpgrade(var10, var2)) {
               var12 = "preventive-upgrade";
            } else if (var8 && durabilityPercent(var10) > durabilityPercent(var2) && Inventory.armorValue(var10) < Inventory.armorValue(var2)) {
               var12 = "preventive-fallback-lower-armor";
            } else if (var8) {
               var12 = "preventive-not-durable-enough";
            }

            if (var6.length() > 0) {
               var6.append(" | ");
            }

            var6.append("slot=")
               .append(var9)
               .append(':')
               .append(describe(var10))
               .append(" piece=")
               .append(var11)
               .append(" ench=")
               .append(var10.w())
               .append(" armor=")
               .append(Inventory.armorValue(var10))
               .append(" remaining=")
               .append(durabilityPercent(var10))
               .append("% reason=")
               .append(var12);
            if (var11 == var1 && !isBroken(var10) && (!this.ignoreUnenchanted.getAsBoolean() || var10.w())) {
               boolean var13 = var2 == null || isBroken(var2);
               if (!var13 || this.useDamagedReserves.getAsBoolean() || durabilityPercent(var10) >= 100) {
                  AutoArmor.Choice var14 = new AutoArmor.Choice(var9, var10.k(), score(var10), isPreferred(var10, var7));
                  if (var8 && !isPreventiveUpgrade(var10, var2)) {
                     if (durabilityPercent(var10) > durabilityPercent(var2) && (var5 == null || compare(var14, var5) > 0)) {
                        var5 = var14;
                     }
                  } else if (var4 == null || compare(var14, var4) > 0) {
                     var4 = var14;
                  }
               }
            }
         }
      }

      if (var4 != null || var5 != null || this.lastNoChoiceLog[var1] == 0L || this.tick - this.lastNoChoiceLog[var1] >= 100L) {
         log(
            "scan reservas "
               + pieceName(var1)
               + "; worn="
               + describe(var2)
               + "; preventive="
               + var8
               + "; slots="
               + (var6.length() == 0 ? "nenhuma armadura nos slots 0-35" : var6.toString())
         );
      }

      return var4 != null ? var4 : var5;
   }

   private static boolean isPreventiveUpgrade(zx var0, zx var1) {
      return Inventory.armorValue(var0) >= Inventory.armorValue(var1) && durabilityPercent(var0) > durabilityPercent(var1);
   }

   private static int compare(AutoArmor.Choice var0, AutoArmor.Choice var1) {
      if (var0.preferred != var1.preferred) {
         return var0.preferred ? 1 : -1;
      }

      int var2 = Long.compare(var0.score, var1.score);
      return var2 != 0 ? var2 : Integer.compare(var1.inventoryIndex, var0.inventoryIndex);
   }

   private static long score(zx var0) {
      return protectionScore(var0) + durabilityPercent(var0) * 1000L;
   }

   private static long protectionScore(zx var0) {
      long var1 = Inventory.armorValue(var0) * 1000000L;
      long var3 = level(aci.c, var0) * 250000L;
      long var5 = (level(aci.d, var0) + level(aci.f, var0) + level(aci.g, var0) + level(aci.e, var0)) * 55000L;
      long var7 = level(aci.j, var0) * 25000L + level(aci.t, var0) * 70000L;
      return var1 + var3 + var5 + var7;
   }

   private static int level(aci var0, zx var1) {
      return var0 == null ? 0 : Math.max(0, Math.min(20, ack.a(var0.B, var1)));
   }

   private static boolean isPreferred(zx var0, int var1) {
      return var1 > 0 && var1 < PREFERRED_MATERIALS.length && var0.b() instanceof yj ? ((yj)var0.b()).x_() == PREFERRED_MATERIALS[var1] : false;
   }

   private static boolean isBroken(zx var0) {
      int var1 = var0.j();
      return var1 > 0 && var0.h() >= var1;
   }

   private static int durabilityPercent(zx var0) {
      int var1 = var0.j();
      return var1 > 0 && var0.e() ? Math.max(0, Math.min(100, (int)((var1 - var0.h()) * 100L / var1))) : 100;
   }

   private static boolean matchesExpected(zx var0, zx var1) {
      return var0 != null && var1 != null ? zx.b(var0, var1) : var0 == var1;
   }

   private static boolean matchesEquipState(zx var0, zx var1, AutoArmor.Pending var2) {
      return !matchesReplacementArmor(var1, var2) ? false : matchesFormerArmor(var0, var2) || var2.oldArmor != null && var0 == null;
   }

   private static boolean matchesReplacementArmor(zx var0, AutoArmor.Pending var1) {
      return !matchesArmor(var0, var1.expectedArmor)
         ? false
         : !sameKindDifferentDamage(var1.oldArmor, var1.expectedArmor) || ArmorSwapSafety.closerDamage(var0.h(), var1.expectedArmor.h(), var1.oldArmor.h());
   }

   private static boolean matchesFormerArmor(zx var0, AutoArmor.Pending var1) {
      return !matchesArmor(var0, var1.oldArmor)
         ? false
         : var0 == null
            || !sameKindDifferentDamage(var1.oldArmor, var1.expectedArmor)
            || ArmorSwapSafety.closerDamage(var0.h(), var1.oldArmor.h(), var1.expectedArmor.h());
   }

   private static boolean matchesOldOrObservedCursor(zx var0, AutoArmor.Pending var1) {
      return matchesFormerArmor(var0, var1) || var1.serverOldCursor != null && !var1.manualIntervened && matchesObservedCursor(var0, var1.serverOldCursor);
   }

   private static boolean matchesOldIdentityAndDamage(zx var0, AutoArmor.Pending var1) {
      return var0 != null && var1.oldArmor != null && var0.b() == var1.oldArmor.b() && var0.b == var1.oldArmor.b && Inventory.armorPiece(var0) == var1.piece
         ? !sameItemDifferentDamage(var1.oldArmor, var1.expectedArmor) || ArmorSwapSafety.closerDamage(var0.h(), var1.oldArmor.h(), var1.expectedArmor.h())
         : false;
   }

   private static boolean matchesObservedCursor(zx var0, zx var1) {
      return var0 != null && var1 != null && var0.b() == var1.b() && var0.b == var1.b && var0.h() == var1.h();
   }

   private static boolean sameItemDifferentDamage(zx var0, zx var1) {
      return var0 != null && var1 != null && var0.b() == var1.b() && Math.abs((long)var0.h() - var1.h()) >= 32L;
   }

   private static boolean sameKindDifferentDamage(zx var0, zx var1) {
      return var0 != null && var1 != null && matchesArmor(var0, var1) && Math.abs((long)var0.h() - var1.h()) >= 32L;
   }

   private static boolean matchesArmor(zx var0, zx var1) {
      if (var0 != null && var1 != null) {
         if (var0.b() == var1.b() && var0.b == var1.b) {
            dn var2 = var0.o();
            dn var3 = var1.o();
            return var2 == null ? var3 == null : var2.equals(var3);
         } else {
            return false;
         }
      } else {
         return var0 == var1;
      }
   }

   private static zx asStack(Object var0) {
      return var0 instanceof zx ? (zx)var0 : null;
   }

   private static zx copy(zx var0) {
      return var0 == null ? null : var0.k();
   }

   private static String pieceName(int var0) {
      switch (var0) {
         case 0:
            return "botas";
         case 1:
            return "calças";
         case 2:
            return "peitoral";
         case 3:
            return "elmo";
         default:
            return "peça";
      }
   }

   private static String describe(zx var0) {
      if (var0 == null) {
         return "vazio";
      }

      int var1 = var0.j();
      String var2 = var1 > 0 ? var1 - var0.h() + "/" + var1 : "indestrutível";
      return var0.b().a() + "[" + var2 + ";meta=" + var0.i() + ";count=" + var0.b + ";ench=" + var0.w() + "]";
   }

   private static String pendingSummary(AutoArmor.Pending var0) {
      return "piece="
         + pieceName(var0.piece)
         + "; sourceIndex="
         + var0.sourceIndex
         + "; old="
         + describe(var0.oldArmor)
         + "; expectedSource="
         + describe(var0.expectedSource)
         + "; expectedArmor="
         + describe(var0.expectedArmor)
         + "; age="
         + var0.age
         + "; local="
         + var0.localApplied
         + "; server="
         + var0.serverConfirmed
         + "; sourceSeen="
         + var0.sourceSeen
         + "; armorSeen="
         + var0.armorSeen
         + "; preventive="
         + var0.preventive
         + "; dropping="
         + var0.dropping
         + "; rejected="
         + var0.rejected;
   }

   private static void logConfirmation(String var0, AutoArmor.Pending var1) {
      String var2 = var1.source == null && var1.oldArmor != null ? "; origem vazia, verificando cursor" : "";
      log(var0 + " confirmou " + pieceName(var1.piece) + var2 + "; reserve=" + describe(var1.armor));
   }

   private static void log(String var0) {
      DebugLog.write("AutoArmor", var0);
   }

   private static final class Choice {
      final int inventoryIndex;
      final zx stack;
      final long score;
      final boolean preferred;

      Choice(int var1, zx var2, long var3, boolean var5) {
         this.inventoryIndex = var1;
         this.stack = var2;
         this.score = var3;
         this.preferred = var5;
      }
   }

   private static final class Pending {
      final int piece;
      final int sourceIndex;
      final zx replacement;
      final zx oldArmor;
      final zx expectedSource;
      final zx expectedArmor;
      final boolean dropOld;
      final boolean preventive;
      zx source;
      zx armor;
      boolean sourceSeen;
      boolean armorSeen;
      boolean localApplied;
      boolean serverConfirmed;
      boolean rejected;
      boolean dropping;
      boolean cursorTimeoutLogged;
      boolean cursorRecoveryWarned;
      boolean manualPauseLogged;
      boolean cursorClearSeen;
      boolean cursorReturnSlotSeen;
      boolean manualIntervened;
      boolean cursorDropIssued;
      zx serverOldCursor;
      int cursorReturnIndex = -1;
      int cursorRecoveryAge;
      int cursorReturnAttempts;
      int cursorNoSlotAge;
      int cursorDropAge;
      int age;

      Pending(int var1, int var2, zx var3, zx var4, boolean var5) {
         this.piece = var1;
         this.sourceIndex = var2;
         this.replacement = var3.k();
         this.oldArmor = AutoArmor.copy(var4);
         this.expectedSource = AutoArmor.copy(var4);
         this.expectedArmor = var3.k();
         this.dropOld = var5;
         this.preventive = var4 != null && !AutoArmor.isBroken(var4);
      }
   }
}
