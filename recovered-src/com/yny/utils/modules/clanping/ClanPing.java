package com.yny.utils.modules.clanping;

import auh.a;
import com.yny.utils.core.ToggleKey;
import dev.xavier.stein.loader.api.Chat;
import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.ModContext;
import dev.xavier.stein.loader.api.Relation;
import dev.xavier.stein.loader.api.Relations;
import dev.xavier.stein.loader.api.Render;
import dev.xavier.stein.loader.api.Server;
import dev.xavier.stein.loader.api.Targeting;
import dev.xavier.stein.loader.api.Tasks;
import dev.xavier.stein.loader.api.Targeting.Hit;
import dev.xavier.stein.loader.api.Targeting.Query;
import dev.xavier.stein.loader.api.Targeting.Ray;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import org.lwjgl.opengl.GL11;

public final class ClanPing {
   static final String RELAY = "wss://ping.antiutm.com/ws";
   private static final double WAYPOINT_HEIGHT = 24.0;
   private final ModContext context;
   private final BooleanSupplier enabled;
   private final BooleanSupplier hudEnabled;
   private final IntSupplier locationSeconds;
   private final IntSupplier enemySeconds;
   private final IntSupplier targetRange;
   private final BooleanSupplier soundEnabled;
   private final BooleanSupplier distantIndicatorEnabled;
   private final IntSupplier cooldownSeconds;
   private final DoubleSupplier markerScale;
   private final ToggleKey locationKey = new ToggleKey();
   private final ToggleKey enemyKey = new ToggleKey();
   private final ToggleKey cancelKey = new ToggleKey();
   private final ClanPingHud hud = new ClanPingHud(this);
   private final ClanPingTransport transport;
   private ClanPing.Active active;
   private String ownPingId;
   private boolean cancelPending;
   private long cancelRequestedAt;
   private int cancelAttempts;
   private ClanPing.QueuedPing queuedPing = ClanPing.QueuedPing.NONE;
   private int receivedEvents;
   private int shownEvents;
   private String lastReceivedRelation = "nenhum";
   private final Map<String, ClanPing.Active> pendingRelations = new LinkedHashMap<>();
   private long lastTargetUpdateAt;
   private long nextPingAt;
   private int syncTicks;
   private int joinSyncBurstTicks;

   public ClanPing(
      ModContext var1,
      BooleanSupplier var2,
      BooleanSupplier var3,
      IntSupplier var4,
      IntSupplier var5,
      IntSupplier var6,
      BooleanSupplier var7,
      BooleanSupplier var8,
      IntSupplier var9,
      DoubleSupplier var10
   ) {
      this.context = var1;
      this.enabled = var2;
      this.hudEnabled = var3;
      this.locationSeconds = var4;
      this.enemySeconds = var5;
      this.targetRange = var6;
      this.soundEnabled = var7;
      this.distantIndicatorEnabled = var8;
      this.cooldownSeconds = var9;
      this.markerScale = var10;
      this.transport = new ClanPingTransport(var1.secrets(), this::receive, this::updateRemote, this::remove, this::relayStatus);
   }

   public ClanPingHud hud() {
      return this.hud;
   }

   public boolean hudEnabled() {
      return this.enabled.getAsBoolean() && this.hudEnabled.getAsBoolean();
   }

   public boolean hasActive() {
      return this.active != null && !this.active.expired();
   }

   public boolean wasLocationPressed(int var1) {
      return this.enabled.getAsBoolean() && this.locationKey.wasPressed(var1);
   }

   public boolean wasEnemyPressed(int var1) {
      return this.enabled.getAsBoolean() && this.enemyKey.wasPressed(var1);
   }

   public boolean wasCancelPressed(int var1) {
      return this.enabled.getAsBoolean() && this.cancelKey.wasPressed(var1);
   }

   public void start() {
   }

   public void stop() {
      this.transport.close();
   }

   public void onJoinGame() {
      this.active = null;
      this.pendingRelations.clear();
      this.syncTicks = 0;
      this.joinSyncBurstTicks = 200;
      Tasks.later(20, this.transport::refresh);
   }

   public void onDisconnected() {
      this.active = null;
      this.ownPingId = null;
      this.cancelPending = false;
      this.cancelAttempts = 0;
      this.transport.disconnectServer();
   }

   public void onTickEnd() {
      if (this.enabled.getAsBoolean()) {
         this.syncTicks++;
         if (this.joinSyncBurstTicks > 0) {
            this.joinSyncBurstTicks--;
            if (this.syncTicks >= 20) {
               this.syncTicks = 0;
               this.transport.refresh();
            }
         } else if (this.syncTicks >= 100) {
            this.syncTicks = 0;
            this.transport.refresh();
         }
      }

      this.resolvePendingRelations();
      if (this.active != null && this.active.expired()) {
         this.active = null;
      }

      if (this.active != null && this.active.enemy) {
         wn var1 = this.findLoadedTarget(ave.A());
         if (var1 != null) {
            this.active.updatePosition(var1.s, var1.t, var1.u);
            if (this.active.id.equals(this.ownPingId) && this.transport.canSend() && System.currentTimeMillis() - this.lastTargetUpdateAt >= 500L) {
               this.lastTargetUpdateAt = System.currentTimeMillis();
               this.transport.update(this.active.id, var1.s, var1.t, var1.u);
            }
         }
      }

      if (this.cancelPending && System.currentTimeMillis() - this.cancelRequestedAt >= 750L && this.cancelAttempts < 3) {
         this.cancelAttempts++;
         this.cancelRequestedAt = System.currentTimeMillis();
         this.transport.cancel(this.ownPingId);
      }
   }

   public void createEnemyFromCrosshair() {
      ave var1 = ave.A();
      if (var1.h != null && var1.f != null) {
         if (this.canCreatePing()) {
            if (this.ownPingId != null) {
               this.queuedPing = ClanPing.QueuedPing.ENEMY;
               this.beginCancel();
               Chat.actionBar("§7Clan Ping: trocando ping anterior...");
            } else {
               wn var2 = this.crosshairPlayer(var1);
               if (var2 == null) {
                  Chat.actionBar("§eClan Ping: mire em um inimigo para atacar.");
               } else {
                  Relation var3 = Relations.of(var2);
                  if (var3.friendly()) {
                     Chat.actionBar("§eClan Ping: não é possível pingar aliado.");
                  } else {
                     this.active = ClanPing.Active.enemy(
                        UUID.randomUUID().toString(), var1.h.e_(), var2.aK().toString(), var2.e_(), var2.s, var2.t, var2.u, this.enemySeconds.getAsInt()
                     );
                     this.ownPingId = this.active.id;
                     this.transport.create(this.active);
                     this.startCooldown();
                     Chat.actionBar("§cClan Ping: inimigo marcado.");
                  }
               }
            }
         }
      }
   }

   public void createQuickPing() {
      ave var1 = ave.A();
      if (var1.m == null) {
         if (var1.h != null && this.crosshairPlayer(var1) != null) {
            this.createEnemyFromCrosshair();
         } else {
            this.createLocationFromCrosshair();
         }
      }
   }

   public void createLocationFromCrosshair() {
      ave var1 = ave.A();
      if (var1.h != null && var1.f != null) {
         if (this.canCreatePing()) {
            if (this.ownPingId != null) {
               this.queuedPing = ClanPing.QueuedPing.LOCATION;
               this.beginCancel();
               Chat.actionBar("§7Clan Ping: trocando ping anterior...");
            } else {
               auh var2 = var1.s;
               aui var3 = var2 != null && var2.a == a.b ? var2.c : null;
               if (var3 == null) {
                  Ray var4 = Targeting.raycast(Targeting.query().reach(this.targetRange.getAsInt()).cutAtBlock());
                  if (var4.blockId != null) {
                     var3 = new aui(var4.blockX + 0.5, var4.blockY + 1.03, var4.blockZ + 0.5);
                  }
               }

               if (var3 == null) {
                  Chat.actionBar("§eClan Ping: nenhum bloco encontrado no alcance visual.");
               } else {
                  this.active = ClanPing.Active.location(
                     UUID.randomUUID().toString(), var1.h.e_(), var3.a, var3.b + 0.03, var3.c, this.locationSeconds.getAsInt()
                  );
                  this.ownPingId = this.active.id;
                  this.transport.create(this.active);
                  this.startCooldown();
                  Chat.actionBar("§bClan Ping: local marcado.");
               }
            }
         }
      }
   }

   public void cancelOwnPing() {
      if (this.ownPingId == null) {
         Chat.actionBar("§7Clan Ping: você não possui ping ativo.");
      } else {
         this.queuedPing = ClanPing.QueuedPing.NONE;
         this.beginCancel();
         Chat.actionBar("§7Clan Ping: removendo...");
      }
   }

   public void openTokenScreen() {
      ave.A().a(new ClanPingTokenScreen(this));
   }

   void saveToken(String var1) {
      if (var1 != null && !var1.trim().isEmpty()) {
         this.transport.setToken(var1.trim());
         Chat.actionBar("§aClan Ping: token de líder salvo.");
      } else {
         Chat.actionBar("§eClan Ping: cole um token válido.");
      }
   }

   void removeToken() {
      this.transport.clearToken();
      Chat.actionBar("§7Clan Ping: autorização removida deste PC.");
   }

   public String onSendChat(String var1) {
      if (!var1.startsWith("/ynyping")) {
         return var1;
      }

      String[] var2 = var1.trim().split("\\s+", 3);
      if (var2.length == 3 && "token".equalsIgnoreCase(var2[1])) {
         this.transport.setToken(var2[2]);
         Chat.print("§aClan Ping: token de líder salvo com segurança.");
      } else if (var2.length == 2 && "logout".equalsIgnoreCase(var2[1])) {
         this.transport.clearToken();
         Chat.print("§eClan Ping: autorização de líder removida deste PC.");
      } else {
         Chat.print("§7Uso: /ynyping token <token>  ou  /ynyping logout");
      }

      return null;
   }

   public void onEntityDeath(Object var1) {
      if (this.active != null && this.active.enemy && var1 instanceof wn && this.active.targetUuid.equals(((wn)var1).aK().toString())) {
         this.active = null;
      }
   }

   private void receive(ClanPing.Active var1) {
      this.receivedEvents++;
      if (this.enabled.getAsBoolean() && !var1.expired() && Server.address().equals(var1.server)) {
         ave var2 = ave.A();
         if (var2.h != null && var1.author.equalsIgnoreCase(var2.h.e_())) {
            this.active = var1;
            this.ownPingId = var1.id;
         } else if (var1.id.equals(this.ownPingId) && this.active != null) {
            this.active.recipients = var1.recipients;
            Chat.actionBar("§aClan Ping enviado para §f" + var1.recipients + " §amembro(s).");
         } else if (this.active != null && this.active.id.equals(var1.id)) {
            this.active.updatePosition(var1.x, var1.y, var1.z);
            this.active.recipients = var1.recipients;
         } else {
            Relation var3 = Relations.of(var1.author);
            this.lastReceivedRelation = var3.name();
            if (var3 != Relation.GROUP) {
               if (this.pendingRelations.size() >= 12) {
                  this.pendingRelations.remove(this.pendingRelations.keySet().iterator().next());
               }

               this.pendingRelations.put(var1.id, var1);
            } else {
               this.showIncoming(var1);
            }
         }
      }
   }

   private void resolvePendingRelations() {
      Iterator var1 = this.pendingRelations.values().iterator();

      while (var1.hasNext()) {
         ClanPing.Active var2 = (ClanPing.Active)var1.next();
         if (var2.expired()) {
            var1.remove();
         } else {
            Relation var3 = Relations.of(var2.author);
            this.lastReceivedRelation = var3.name();
            if (var3 == Relation.GROUP) {
               var1.remove();
               this.showIncoming(var2);
            }
         }
      }
   }

   private void showIncoming(ClanPing.Active var1) {
      this.active = var1;
      this.shownEvents++;
      ave var2 = ave.A();
      if (this.soundEnabled.getAsBoolean() && var2.h != null) {
         var2.h.a("random.orb", 0.45F, var1.enemy ? 0.75F : 1.25F);
      }
   }

   private void updateRemote(ClanPingTransport.PositionUpdate var1) {
      if (this.active != null && this.active.id.equals(var1.id)) {
         this.active.updatePosition(var1.x, var1.y, var1.z);
      }
   }

   private void remove(String var1) {
      if (this.active != null && this.active.id.equals(var1)) {
         this.active = null;
      }

      if (var1 != null && var1.equals(this.ownPingId)) {
         this.ownPingId = null;
         this.cancelPending = false;
         this.cancelAttempts = 0;
         if (this.queuedPing == ClanPing.QueuedPing.NONE) {
            Chat.actionBar("§7Clan Ping removido.");
         }

         this.runQueuedPing();
      }
   }

   private void beginCancel() {
      if (this.ownPingId != null && !this.cancelPending) {
         this.transport.cancel(this.ownPingId);
         if (this.active != null && this.ownPingId.equals(this.active.id)) {
            this.active = null;
         }

         this.cancelPending = true;
         this.cancelAttempts = 1;
         this.cancelRequestedAt = System.currentTimeMillis();
      }
   }

   private boolean canCreatePing() {
      if (!this.transport.canSend()) {
         Chat.actionBar("§eClan Ping: somente líder autorizado pode pingar.");
         return false;
      } else {
         long var1 = this.nextPingAt - System.currentTimeMillis();
         if (var1 > 0L) {
            Chat.actionBar("§7Clan Ping: aguarde " + Math.max(1L, (var1 + 999L) / 1000L) + "s.");
            return false;
         } else {
            return true;
         }
      }
   }

   private void startCooldown() {
      this.nextPingAt = System.currentTimeMillis() + this.cooldownSeconds.getAsInt() * 1000L;
   }

   private void runQueuedPing() {
      ClanPing.QueuedPing var1 = this.queuedPing;
      this.queuedPing = ClanPing.QueuedPing.NONE;
      if (var1 == ClanPing.QueuedPing.ENEMY) {
         this.createEnemyFromCrosshair();
      }

      if (var1 == ClanPing.QueuedPing.LOCATION) {
         this.createLocationFromCrosshair();
      }
   }

   private void relayStatus(String var1) {
      this.context.log().debug("Clan Ping relay: " + var1);
   }

   public void showStatus() {
      Chat.print(
         "§7Clan Ping: "
            + this.transport.status()
            + " §8| §7eventos: §f"
            + this.receivedEvents
            + " §7mostrados: §f"
            + this.shownEvents
            + " §7última relação: §f"
            + this.lastReceivedRelation
      );
   }

   public String hudTitle() {
      if (!this.hasActive()) {
         return "";
      } else {
         return this.active.enemy ? "§cFOCAR INIMIGO §7• " + this.active.author : "§bPINGAR AQUI §7• " + this.active.author;
      }
   }

   public String hudDetail() {
      if (!this.hasActive()) {
         return "";
      }

      ave var1 = ave.A();
      if (var1.h == null) {
         return "";
      }

      double var2 = this.active.x - var1.h.s;
      double var4 = this.active.z - var1.h.u;
      int var6 = (int)Math.round(Math.sqrt(var2 * var2 + var4 * var4));
      int var7 = (int)Math.max(0L, (this.active.expiresAt - System.currentTimeMillis() + 999L) / 1000L);
      return direction(var2, var4, var1.h.y).repeat(this.distantIndicatorEnabled.getAsBoolean() ? 1 : 0) + " " + this.active.label() + "  §7• §f" + var6 + "m  §7• §f0:" + String.format("%02d", var7);
   }

   public void drawDistantIndicator() {
   }

   public void renderWorld(float var1) {
      if (this.hasActive()) {
         if (!this.active.enemy) {
            this.renderLocationWaypoint(var1);
         } else {
            ave var2 = ave.A();
            wn var3 = this.findLoadedTarget(var2);
            if (var2.h != null) {
               if (var3 == null) {
                  this.renderEnemyLastKnownWaypoint(var1);
               } else {
                  pk var4 = var2.ac();
                  if (var4 != null) {
                     double var5 = var4.P + (var4.s - var4.P) * var1;
                     double var7 = var4.Q + (var4.t - var4.Q) * var1;
                     double var9 = var4.R + (var4.u - var4.R) * var1;
                     double var11 = this.visualScale();
                     aug var13 = var3.aR().b(0.08 * var11, 0.12 * var11, 0.08 * var11).c(-var5, -var7, -var9);
                     float var14 = (0.55F + 0.45F * (float)Math.sin(System.currentTimeMillis() / 120.0)) * this.active.fade();
                     bfl.x();
                     bfl.i();
                     bfl.p();
                     bfl.a(false);
                     bfl.c(1.0F, 0.05F, 0.05F, var14);
                     GL11.glLineWidth((float)(1.5 + var11));
                     bfr.a(var13);
                     GL11.glLineWidth(1.0F);
                     bfl.a(true);
                     bfl.j();
                     bfl.o();
                     bfl.w();
                     bfl.c(1.0F, 1.0F, 1.0F, 1.0F);
                     renderWorldLabel(
                        "§c◆ ATACAR ESTE INIMIGO §7• " + targetDisplayName(var3, this.active.targetName) + this.worldMeta(),
                        var3.s,
                        var3.t + var3.K + 1.3D,
                        var3.u,
                        var5,
                        var7,
                        var9,
                        -43691
                     );
                  }
               }
            }
         }
      }
   }

   private void renderLocationWaypoint(float var1) {
      ave var2 = ave.A();
      pk var3 = var2.ac();
      if (var3 != null) {
         double var4 = var3.P + (var3.s - var3.P) * var1;
         double var6 = var3.Q + (var3.t - var3.Q) * var1;
         double var8 = var3.R + (var3.u - var3.R) * var1;
         float var10 = (0.45F + 0.45F * (float)Math.sin(System.currentTimeMillis() / 130.0)) * this.active.fade();
         double var11 = this.visualScale();
         aug var13 = new aug(
               this.active.x - 0.95 * var11,
               this.active.y,
               this.active.z - 0.95 * var11,
               this.active.x + 0.95 * var11,
               this.active.y + 0.16 * var11,
               this.active.z + 0.95 * var11
            )
            .c(-var4, -var6, -var8);
         aug var14 = new aug(
               this.active.x - 0.18 * var11,
               this.active.y,
               this.active.z - 0.18 * var11,
               this.active.x + 0.18 * var11,
               this.active.y + 24.0 * var11,
               this.active.z + 0.18 * var11
            )
            .c(-var4, -var6, -var8);
         bfl.x();
         bfl.i();
         bfl.p();
         bfl.a(false);
         bfl.l();
         bfl.a(770, 1, 1, 0);
         bfl.c(0.12F, 0.75F, 1.0F, var10);
         bfr.a(var13);
         bfl.c(0.15F, 0.85F, 1.0F, Math.max(0.2F, var10 * 0.7F));
         bfr.a(var14);
         double var15 = System.currentTimeMillis() / 700.0;
         double var17 = this.active.x - var4;
         double var19 = this.active.y - var6;
         double var21 = this.active.z - var8;
         drawRing(var17, var19 + 0.16 * var11, var21, (1.22 + var10 * 0.18) * var11, var15, 0.95F);
         drawRing(var17, var19 + 0.92 * var11, var21, 0.82 * var11, -var15 * 1.35, 0.7F);
         drawRing(var17, var19 + 1.68 * var11, var21, 0.52 * var11, var15 * 1.8, 0.48F);
         drawDiamond(var17, var19 + (2.45 + var10 * 0.12) * var11, var21, 0.45 * var11, 0.9F);
         bfl.a(true);
         bfl.j();
         bfl.o();
         bfl.k();
         bfl.w();
         bfl.c(1.0F, 1.0F, 1.0F, 1.0F);
         renderWorldLabel(
            "§b◆ IR AQUI §7• " + this.active.author + this.worldMeta(),
            this.active.x,
            this.active.y + 24.0 * var11 + 0.7,
            this.active.z,
            var4,
            var6,
            var8,
            -11149825
         );
      }
   }

   private static String targetDisplayName(Object entity, String fallback) {
      return PingClanName.display(fallback);
   }

   private void renderEnemyLastKnownWaypoint(float var1) {
      ave var2 = ave.A();
      pk var3 = var2.ac();
      if (var3 != null) {
         double var4 = var3.P + (var3.s - var3.P) * var1;
         double var6 = var3.Q + (var3.t - var3.Q) * var1;
         double var8 = var3.R + (var3.u - var3.R) * var1;
         float var10 = 0.6F + 0.4F * (float)Math.sin(System.currentTimeMillis() / 110.0);
         double var11 = this.visualScale();
         aug var13 = new aug(
               this.active.x - 0.85 * var11,
               this.active.y,
               this.active.z - 0.85 * var11,
               this.active.x + 0.85 * var11,
               this.active.y + 0.16 * var11,
               this.active.z + 0.85 * var11
            )
            .c(-var4, -var6, -var8);
         aug var14 = new aug(
               this.active.x - 0.16 * var11,
               this.active.y,
               this.active.z - 0.16 * var11,
               this.active.x + 0.16 * var11,
               this.active.y + 24.0 * var11,
               this.active.z + 0.16 * var11
            )
            .c(-var4, -var6, -var8);
         bfl.x();
         bfl.i();
         bfl.p();
         bfl.a(false);
         bfl.l();
         bfl.a(770, 1, 1, 0);
         bfl.c(1.0F, 0.08F, 0.08F, var10);
         bfr.a(var13);
         bfl.c(1.0F, 0.14F, 0.14F, var10 * 0.7F);
         bfr.a(var14);
         bfl.a(true);
         bfl.j();
         bfl.o();
         bfl.k();
         bfl.w();
         bfl.c(1.0F, 1.0F, 1.0F, 1.0F);
         renderWorldLabel(
            "§c◆ INIMIGO FORA DO RENDER §7• " + this.active.targetName + this.worldMeta(),
            this.active.x,
            this.active.y + 24.0 * var11 + 0.7,
            this.active.z,
            var4,
            var6,
            var8,
            -43691
         );
      }
   }

   private static void drawRing(double var0, double var2, double var4, double var6, double var8, float var10) {
      bfx var11 = bfx.a();
      bfd var12 = var11.c();
      var12.a(2, bms.f);

      for (int var13 = 0; var13 < 24; var13++) {
         double var14 = var8 + var13 * Math.PI * 2.0 / 24.0;
         var12.b(var0 + Math.cos(var14) * var6, var2, var4 + Math.sin(var14) * var6).b(45, 210, 255, (int)(255.0F * var10)).d();
      }

      var11.b();
   }

   private static void drawDiamond(double var0, double var2, double var4, double var6, float var8) {
      bfx var9 = bfx.a();
      bfd var10 = var9.c();
      var10.a(2, bms.f);
      vertex(var10, var0, var2 + var6, var4, var8);
      vertex(var10, var0 + var6, var2, var4, var8);
      vertex(var10, var0, var2 - var6, var4, var8);
      vertex(var10, var0 - var6, var2, var4, var8);
      var9.b();
      var10.a(1, bms.f);
      vertex(var10, var0, var2 + var6, var4, var8);
      vertex(var10, var0, var2 - var6, var4, var8);
      vertex(var10, var0 - var6, var2, var4, var8);
      vertex(var10, var0 + var6, var2, var4, var8);
      var9.b();
   }

   private static void vertex(bfd var0, double var1, double var3, double var5, float var7) {
      var0.b(var1, var3, var5).b(90, 225, 255, (int)(255.0F * var7)).d();
   }

   private static void renderWorldLabel(String var0, double var1, double var3, double var5, double var7, double var9, double var11, int var13) {
      ave var14 = ave.A();
      avn var15 = var14.k;
      bfl.E();
      bfl.b(var1 - var7, var3 - var9, var5 - var11);
      bfl.b(-var14.af().e, 0.0F, 1.0F, 0.0F);
      bfl.b(var14.af().f, 1.0F, 0.0F, 0.0F);
      bfl.a(-0.025F, -0.025F, 0.025F);
      bfl.i();
      bfl.p();
      var15.a(var0, -var15.a(var0) / 2.0F, 0.0F, var13);
      bfl.o();
      bfl.j();
      bfl.F();
   }

   private wn crosshairPlayer(ave var1) {
      auh var2 = var1.s;
      if (var2 != null && var2.d instanceof wn && var2.d != var1.h) {
         return (wn)var2.d;
      }

      Query var3 = Targeting.query().reach(this.targetRange.getAsInt()).living().margin(0.3).cutAtBlock().exclude(var1.h);
      Ray var4 = Targeting.raycast(var3);

      for (Hit var6 : var4.hits) {
         if (var6.distance >= var4.blockDistance) {
            break;
         }

         if (var6.entity instanceof wn) {
            return (wn)var6.entity;
         }
      }

      return null;
   }

   private double visualScale() {
      return Math.max(0.75, Math.min(2.0, this.markerScale.getAsDouble()));
   }

   private String worldMeta() {
      ave var1 = ave.A();
      if (var1.h != null && this.active != null) {
         double var2 = this.active.x - var1.h.s;
         double var4 = this.active.z - var1.h.u;
         int var6 = (int)Math.round(Math.sqrt(var2 * var2 + var4 * var4));
         int var7 = (int)Math.max(0L, (this.active.expiresAt - System.currentTimeMillis() + 999L) / 1000L);
         return " §f" + var6 + "m §7• §f0:" + String.format("%02d", var7);
      } else {
         return "";
      }
   }

   private wn findLoadedTarget(ave var1) {
      if (var1.f != null && this.active != null) {
         for (Object var3 : var1.f.j) {
            if (var3 instanceof wn && this.active.targetUuid.equals(((wn)var3).aK().toString())) {
               return (wn)var3;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static String direction(double var0, double var2, float var4) {
      double var5 = Math.toDegrees(Math.atan2(var2, var0)) - var4 - 90.0;
      String[] var7 = new String[]{"§e↑ Frente§f", "§e↗ Direita à frente§f", "§e→ Direita§f", "§e↘ Direita atrás§f", "§e↓ Atrás§f", "§e↙ Esquerda atrás§f", "§e← Esquerda§f", "§e↖ Esquerda à frente§f"};
      return var7[(int)Math.floor((var5 + 22.5) / 45.0) & 7];
   }

   static final class Active {
      final String id;
      final String author;
      final String targetUuid;
      final String targetName;
      final String server;
      double x;
      double y;
      double z;
      final boolean enemy;
      final long expiresAt;
      int recipients;

      private Active(String var1, String var2, String var3, String var4, double var5, double var7, double var9, boolean var11, int var12, String var13) {
         this.id = var1;
         this.author = var2;
         this.targetUuid = var3;
         this.targetName = var4;
         this.x = var5;
         this.y = var7;
         this.z = var9;
         this.enemy = var11;
         this.expiresAt = System.currentTimeMillis() + var12 * 1000L;
         this.server = var13 == null ? Server.address() : var13;
      }

      static ClanPing.Active enemy(String var0, String var1, String var2, String var3, double var4, double var6, double var8, int var10) {
         return new ClanPing.Active(var0, var1, var2, var3, var4, var6, var8, true, var10, null);
      }

      static ClanPing.Active location(String var0, String var1, double var2, double var4, double var6, int var8) {
         return new ClanPing.Active(var0, var1, "", "", var2, var4, var6, false, var8, null);
      }

      boolean expired() {
         return System.currentTimeMillis() >= this.expiresAt;
      }

      float fade() {
         return Math.max(0.0F, Math.min(1.0F, (float)(this.expiresAt - System.currentTimeMillis()) / 2000.0F));
      }

      String label() {
         return this.enemy ? this.targetName : "Local marcado";
      }

      void updatePosition(double var1, double var3, double var5) {
         this.x = var1;
         this.y = var3;
         this.z = var5;
      }
   }

   private enum QueuedPing {
      NONE,
      ENEMY,
      LOCATION;
   }
}
