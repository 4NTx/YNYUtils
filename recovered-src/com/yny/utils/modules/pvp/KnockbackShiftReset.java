package com.yny.utils.modules.pvp;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

public final class KnockbackShiftReset {
   private static final long WINDOW_NS = 250000000L;
   private static final long COOLDOWN_NS = 250000000L;
   private final BooleanSupplier enabled;
   private final AtomicLong pending = new AtomicLong();
   private long lastPulse;
   private int releaseAfterTicks;

   public KnockbackShiftReset(BooleanSupplier var1) {
      this.enabled = var1;
   }

   public void onVelocity(int var1, int var2) {
      if (this.enabled.getAsBoolean() && (var1 != 0 || var2 != 0)) {
         this.pending.set(System.nanoTime());
      }
   }

   public void onVelocityApplied() {
      long var1 = this.pending.get();
      long var3 = System.nanoTime();
      ave var5 = ave.A();
      bew var6 = var5.h;
      if (var1 != 0L
         && this.enabled.getAsBoolean()
         && var6 != null
         && var5.f != null
         && var5.m == null
         && var3 - var1 <= 250000000L
         && var3 - this.lastPulse >= 250000000L
         && var6.C
         && var6.ai()
         && !var6.au()
         && !var6.V()
         && !var6.ab()
         && !var6.H
         && !var6.k_()) {
         if (this.pending.compareAndSet(var1, 0L)) {
            this.lastPulse = var3;
            var5.t.ad.h = true;
            var6.b.d = true;
            this.releaseAfterTicks = 2;
         }
      } else {
         this.pending.compareAndSet(var1, 0L);
      }
   }

   public void onTickEnd() {
      if (this.releaseAfterTicks > 0 && --this.releaseAfterTicks == 0) {
         restore();
      }
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
         boolean var1 = avh.a(var0.t.ad);
         var0.t.ad.h = var1;
         if (var0.h != null && var0.h.b != null) {
            var0.h.b.d = var1;
         }
      }
   }
}
