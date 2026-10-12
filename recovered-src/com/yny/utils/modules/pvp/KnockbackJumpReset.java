package com.yny.utils.modules.pvp;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

public final class KnockbackJumpReset {
   private static final long WINDOW_NS = 250000000L;
   private static final long COOLDOWN_NS = 450000000L;
   private static final float PREDICTION_RANGE = 3.35F;
   private static final double MIN_FACING_DOT = 0.55;
   private final BooleanSupplier enabled;
   private final AtomicLong pending = new AtomicLong();
   private long lastPulse;
   private int releaseAfterTicks;

   public KnockbackJumpReset(BooleanSupplier var1) {
      this.enabled = var1;
   }

   public void onVelocity(int var1, int var2) {
      if (this.enabled.getAsBoolean() && (var1 != 0 || var2 != 0)) {
         this.pending.set(System.nanoTime());
      }
   }

   public void onVelocityApplied() {
      long var1 = this.pending.get();
      if (var1 != 0L && System.nanoTime() - var1 <= 250000000L) {
         if (this.pending.compareAndSet(var1, 0L)) {
            this.pulseJump();
         }
      } else {
         this.pending.compareAndSet(var1, 0L);
      }
   }

   public void onTickEnd() {
      if (this.releaseAfterTicks > 0 && --this.releaseAfterTicks == 0) {
         restore();
      }

      this.predictIncomingHit();
   }

   private void predictIncomingHit() {
      if (this.enabled.getAsBoolean()) {
         ave var1 = ave.A();
         bew var2 = var1.h;
         if (canJump(var1, var2)) {
            for (Object var4 : var1.f.j) {
               if (var4 instanceof wn var5 && var5 != var2 && !var5.I && var5.ar && !(var2.g(var5) > 3.35F) && isFacing(var5, var2)) {
                  this.pulseJump();
                  return;
               }
            }
         }
      }
   }

   private void pulseJump() {
      ave var1 = ave.A();
      bew var2 = var1.h;
      long var3 = System.nanoTime();
      if (canJump(var1, var2) && var3 - this.lastPulse >= 450000000L) {
         this.lastPulse = var3;
         var1.t.ac.h = true;
         var2.b.c = true;
         var2.bF();
         this.releaseAfterTicks = 2;
      }
   }

   private static boolean canJump(ave var0, bew var1) {
      return var1 != null
         && var0.f != null
         && var0.m == null
         && var1.C
         && var1.ai()
         && !var1.au()
         && !var1.V()
         && !var1.ab()
         && !var1.H
         && !var1.k_()
         && !avh.a(var0.t.ac);
   }

   private static boolean isFacing(wn var0, bew var1) {
      aui var2 = var0.ap();
      double var3 = var1.s - var0.s;
      double var5 = var1.u - var0.u;
      double var7 = Math.sqrt(var3 * var3 + var5 * var5);
      return var7 > 0.001 && (var2.a * var3 + var2.c * var5) / var7 >= 0.55;
   }

   public void reset() {
      this.pending.set(0L);
      if (this.releaseAfterTicks > 0) {
         restore();
      }

      this.releaseAfterTicks = 0;
      this.lastPulse = 0L;
   }

   private static void restore() {
      ave var0 = ave.A();
      if (var0.t != null) {
         boolean var1 = avh.a(var0.t.ac);
         var0.t.ac.h = var1;
         if (var0.h != null && var0.h.b != null) {
            var0.h.b.c = var1;
         }
      }
   }
}
