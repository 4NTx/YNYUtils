package com.yny.utils.modules.pvp;

import com.yny.utils.core.DebugLog;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

public final class KnockbackControl {
   private static final String HANDLER_NAME = "ynyutils_knockback_control";
   private static final int DISPLACEMENT_TICKS = 6;
   private final BooleanSupplier enabled;
   private final IntSupplier percent;
   private final BooleanSupplier preserveVertical;
   private final BooleanSupplier diagnostics;
   private final BiConsumer<Integer, Integer> localVelocityListener;
   private final Runnable velocityAppliedListener;
   private final ArrayBlockingQueue<KnockbackControl.VelocitySample> samples = new ArrayBlockingQueue<>(128);
   private final AtomicInteger droppedSamples = new AtomicInteger();
   private final AtomicInteger serverCorrections = new AtomicInteger();
   private KnockbackControl.DisplacementTrace displacement;
   private volatile int localPlayerId = Integer.MIN_VALUE;
   private volatile Channel installedChannel;
   private volatile boolean installationPending;

   public KnockbackControl(BooleanSupplier var1, IntSupplier var2, BooleanSupplier var3, BooleanSupplier var4, BiConsumer<Integer, Integer> var5, Runnable var6) {
      this.enabled = var1;
      this.percent = var2;
      this.preserveVertical = var3;
      this.diagnostics = var4;
      this.localVelocityListener = var5;
      this.velocityAppliedListener = var6;
   }

   public void requestInstallation() {
      this.samples.clear();
      this.droppedSamples.set(0);
      this.serverCorrections.set(0);
      this.displacement = null;
      this.removeHandler();
      this.localPlayerId = Integer.MIN_VALUE;
      this.installationPending = true;
   }

   public void onDisconnected() {
      this.samples.clear();
      this.droppedSamples.set(0);
      this.serverCorrections.set(0);
      this.displacement = null;
      this.removeHandler();
      this.localPlayerId = Integer.MIN_VALUE;
      this.installationPending = false;
   }

   private void removeHandler() {
      Channel var1 = this.installedChannel;
      this.installedChannel = null;
      if (var1 != null && var1.pipeline().get("ynyutils_knockback_control") != null) {
         var1.pipeline().remove("ynyutils_knockback_control");
      }
   }

   public void installIfPending() {
      if (this.installationPending) {
         ave var1 = ave.A();
         if (var1.h != null) {
            this.localPlayerId = var1.h.F();
            bcy var2 = var1.u();
            if (var2 != null) {
               ek var3 = var2.a();
               Channel var4 = var3 == null ? null : var3.k;
               if (var4 != null) {
                  if (var4.pipeline().get("ynyutils_knockback_control") != null) {
                     this.installedChannel = var4;
                     this.installationPending = false;
                  } else if (var4.pipeline().get("packet_handler") != null) {
                     try {
                        var4.pipeline().addBefore("packet_handler", "ynyutils_knockback_control", new KnockbackControl.VelocityInterceptor(this));
                        this.installedChannel = var4;
                        this.installationPending = false;
                     } catch (Exception var6) {
                     }
                  }
               }
            }
         }
      }
   }

   public void onTickEnd() {
      if (!this.diagnostics.getAsBoolean()) {
         this.samples.clear();
         this.droppedSamples.set(0);
         this.displacement = null;
      } else {
         bew var1 = ave.A().h;
         int var3 = 0;
         boolean var4 = false;

         KnockbackControl.VelocitySample var2;
         while (var3++ < 64 && (var2 = this.samples.poll()) != null) {
            DebugLog.write("Knockback", var2.describe());
            if (var2.originalX != 0 || var2.originalZ != 0) {
               if (this.displacement != null) {
                  DebugLog.write("Knockback", this.displacement.describe("outro-S12", this.serverCorrections.get()));
               }

               this.displacement = var1 == null ? null : new KnockbackControl.DisplacementTrace(var2, var1, this.serverCorrections.get());
               var4 = true;
            }
         }

         if (!var4 && this.displacement != null) {
            if (var1 == null) {
               DebugLog.write("Knockback", this.displacement.describe("sem-jogador", this.serverCorrections.get()));
               this.displacement = null;
            } else {
               this.displacement.observe(var1);
               if (this.displacement.ticks >= 6) {
                  DebugLog.write("Knockback", this.displacement.describe("janela-completa", this.serverCorrections.get()));
                  this.displacement = null;
               }
            }
         }

         int var5 = this.droppedSamples.getAndSet(0);
         if (var5 > 0) {
            DebugLog.write("Knockback", "S12 amostras descartadas por fila cheia=" + var5);
         }
      }
   }

   public void reset() {
      this.displacement = null;
   }

   private KnockbackControl.VelocitySample adjust(hm var1) {
      if (var1.a() != this.localPlayerId) {
         return null;
      }

      int var2 = var1.b;
      int var3 = var1.c;
      int var4 = var1.d;
      DebugLog.write("Knockback", "S12 local recebido; bruto=(" + var2 + "," + var3 + "," + var4 + ")");
      this.localVelocityListener.accept(var2, var4);
      boolean var5 = this.enabled.getAsBoolean();
      int var6 = var5 ? Math.max(92, Math.min(100, this.percent.getAsInt())) : 100;
      boolean var7 = this.preserveVertical.getAsBoolean();
      if (var5 && var6 != 100) {
         var1.b = scale(var1.b, var6);
         if (!var7) {
            var1.c = scale(var1.c, var6);
         }

         var1.d = scale(var1.d, var6);
      }

      KnockbackControl.VelocitySample var8 = new KnockbackControl.VelocitySample(var1.a(), var2, var3, var4, var1.b, var1.c, var1.d, var5, var6, var7);
      if (this.diagnostics.getAsBoolean() && !this.samples.offer(var8)) {
         this.droppedSamples.incrementAndGet();
      }

      return var8;
   }

   private void afterVelocityHandled(KnockbackControl.VelocitySample var1, Channel var2) {
      ave var3 = ave.A();
      bew var4 = var3.h;
      if (var2 == this.installedChannel && var4 != null && var3.f != null && var4.F() == var1.entityId) {
         this.velocityAppliedListener.run();
         double var5 = var1.appliedX / 8000.0;
         double var7 = var1.appliedY / 8000.0;
         double var9 = var1.appliedZ / 8000.0;
         boolean var11 = near(var4.v, var5) && near(var4.x, var9) && near(var4.w, var7);
         String var12 = var11 ? "aplicado" : "divergente";
         if (!var11
            && var1.reduce
            && var1.percent != 100
            && near(var4.v, var1.originalX / 8000.0)
            && near(var4.x, var1.originalZ / 8000.0)
            && near(var4.w, var1.originalY / 8000.0)) {
            var4.v = var5;
            var4.w = var7;
            var4.x = var9;
            var12 = "corrigido-do-vanilla";
         }

         if (this.diagnostics.getAsBoolean()) {
            DebugLog.write(
               "Knockback",
               String.format(
                  Locale.ROOT,
                  "entidade pós-S12; resultado=%s; motion=(%.4f,%.4f,%.4f); esperado=(%.4f,%.4f,%.4f); ground=%s; sprint=%s; hurtTime=%d",
                  var12,
                  var4.v,
                  var4.w,
                  var4.x,
                  var5,
                  var7,
                  var9,
                  var4.C,
                  var4.aw(),
                  var4.au
               )
            );
         }
      }
   }

   private static boolean near(double var0, double var2) {
      return Math.abs(var0 - var2) < 1.0E-4;
   }

   static int scale(int var0, int var1) {
      return Math.round(var0 * (var1 / 100.0F));
   }

   private static final class DisplacementTrace {
      final KnockbackControl.VelocitySample impulse;
      final double startX;
      final double startY;
      final double startZ;
      final int correctionsAtStart;
      double lastX;
      double lastY;
      double lastZ;
      double pathXZ;
      int ticks;
      int inputTicks;

      DisplacementTrace(KnockbackControl.VelocitySample var1, bew var2, int var3) {
         this.impulse = var1;
         this.startX = this.lastX = var2.s;
         this.startY = this.lastY = var2.t;
         this.startZ = this.lastZ = var2.u;
         this.correctionsAtStart = var3;
      }

      void observe(bew var1) {
         this.pathXZ = this.pathXZ + Math.hypot(var1.s - this.lastX, var1.u - this.lastZ);
         this.lastX = var1.s;
         this.lastY = var1.t;
         this.lastZ = var1.u;
         if (var1.b != null && (var1.b.b != 0.0F || var1.b.a != 0.0F)) {
            this.inputTicks++;
         }

         this.ticks++;
      }

      String describe(String var1, int var2) {
         return String.format(
            Locale.ROOT,
            "deslocamento cliente pós-S12; fim=%s; ticks=%d/%d; deltaXZ=%.4f; caminhoXZ=%.4f; deltaY=%.4f; inputTicks=%d; S08=%d; percentualXZ=%d; impulsoXZ=%.4f; comparavel=%s",
            var1,
            this.ticks,
            6,
            Math.hypot(this.lastX - this.startX, this.lastZ - this.startZ),
            this.pathXZ,
            this.lastY - this.startY,
            this.inputTicks,
            var2 - this.correctionsAtStart,
            this.impulse.percent,
            Math.hypot(this.impulse.appliedX, this.impulse.appliedZ) / 8000.0,
            "janela-completa".equals(var1) && this.inputTicks == 0 && var2 == this.correctionsAtStart
         );
      }
   }

   private static final class VelocityInterceptor extends ChannelInboundHandlerAdapter {
      private final KnockbackControl control;

      VelocityInterceptor(KnockbackControl var1) {
         this.control = var1;
      }

      public void channelRead(ChannelHandlerContext var1, Object var2) throws Exception {
         KnockbackControl.VelocitySample var3 = null;
         if (var2 instanceof hm) {
            var3 = this.control.adjust((hm)var2);
         } else if (var2 instanceof fi && this.control.diagnostics.getAsBoolean()) {
            this.control.serverCorrections.incrementAndGet();
         }

         try {
            var1.fireChannelRead(var2);
         } finally {
            if (var3 != null && (var3.originalX != 0 || var3.originalY != 0 || var3.originalZ != 0)) {
               KnockbackControl.VelocitySample var7 = var3;
               Channel var8 = var1.channel();
               ave.A().a(() -> this.control.afterVelocityHandled(var7, var8));
            }
         }
      }
   }

   private static final class VelocitySample {
      final int entityId;
      final long receivedAtNanos;
      final int originalX;
      final int originalY;
      final int originalZ;
      final int appliedX;
      final int appliedY;
      final int appliedZ;
      final boolean reduce;
      final boolean verticalVanilla;
      final int percent;

      VelocitySample(int var1, int var2, int var3, int var4, int var5, int var6, int var7, boolean var8, int var9, boolean var10) {
         this.entityId = var1;
         this.receivedAtNanos = System.nanoTime();
         this.originalX = var2;
         this.originalY = var3;
         this.originalZ = var4;
         this.appliedX = var5;
         this.appliedY = var6;
         this.appliedZ = var7;
         this.reduce = var8;
         this.percent = var9;
         this.verticalVanilla = var10;
      }

      String describe() {
         return String.format(
            Locale.ROOT,
            "S12 local; reducao=%s; percentualXZ=%d; percentualY=%d; verticalVanilla=%s; vanilla=(%.4f,%.4f,%.4f); aplicado=(%.4f,%.4f,%.4f); inteirosVanilla=(%d,%d,%d); inteirosAplicado=(%d,%d,%d)",
            this.reduce,
            this.percent,
            this.reduce && !this.verticalVanilla ? this.percent : 100,
            this.verticalVanilla,
            this.originalX / 8000.0,
            this.originalY / 8000.0,
            this.originalZ / 8000.0,
            this.appliedX / 8000.0,
            this.appliedY / 8000.0,
            this.appliedZ / 8000.0,
            this.originalX,
            this.originalY,
            this.originalZ,
            this.appliedX,
            this.appliedY,
            this.appliedZ
         );
      }
   }
}
