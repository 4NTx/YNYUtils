package com.yny.utils.modules.clanping;

import dev.xavier.stein.loader.api.Secrets;
import dev.xavier.stein.loader.api.Server;
import dev.xavier.stein.loader.api.Tasks;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ClanPingTransport implements Listener {
   private static final String TOKEN_KEY = "clan-ping.leader-token";
   private final Secrets secrets;
   private final Consumer<ClanPing.Active> receiver;
   private final Consumer<ClanPingTransport.PositionUpdate> updater;
   private final Consumer<String> remover;
   private final Consumer<String> logger;
   private volatile WebSocket socket;
   private volatile String state = "offline";
   private volatile boolean authorized;
   private volatile boolean connecting;
   private volatile boolean wantConnection;
   private final StringBuilder received = new StringBuilder();

   ClanPingTransport(
      Secrets var1, Consumer<ClanPing.Active> var2, Consumer<ClanPingTransport.PositionUpdate> var3, Consumer<String> var4, Consumer<String> var5
   ) {
      this.secrets = var1;
      this.receiver = var2;
      this.updater = var3;
      this.remover = var4;
      this.logger = var5;
   }

   void connect() {
      String var1 = Server.address();
      this.wantConnection = true;
      if (this.socket == null && !this.connecting && var1 != null && !var1.trim().isEmpty()) {
         this.connecting = true;
         this.state = "conectando";
         Tasks.background(() -> {
            try {
               HttpClient var1x = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8L)).build();
               var1x.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(8L)).buildAsync(URI.create("wss://ping.antiutm.com/ws"), this).join();
            } catch (Exception var2) {
               this.state = "offline";
               this.connecting = false;
               this.logger.accept("falha ao conectar: " + var2.getClass().getSimpleName());
               this.reconnectLater();
            }
         });
      }
   }

   void refresh() {
      this.wantConnection = true;
      if (this.socket == null) {
         this.connect();
      } else {
         this.send("{\"type\":\"ping.sync\",\"server\":\"" + escape(Server.address()) + "\"}");
      }
   }

   void close() {
      this.disconnectServer();
   }

   void disconnectServer() {
      this.wantConnection = false;
      WebSocket var1 = this.socket;
      this.socket = null;
      this.connecting = false;
      this.authorized = false;
      this.state = "offline";
      if (var1 != null) {
         var1.sendClose(1000, "disconnect");
      }
   }

   void setToken(String var1) {
      this.secrets.put("clan-ping.leader-token", var1);
      this.authorized = false;
      this.connect();
      this.authenticate();
   }

   void clearToken() {
      this.secrets.remove("clan-ping.leader-token");
      this.authorized = false;
   }

   boolean canSend() {
      return this.socket != null && this.authorized;
   }

   String status() {
      return this.state;
   }

   void create(ClanPing.Active var1) {
      this.send(
         "{\"type\":\"ping.create\",\"id\":\""
            + escape(var1.id)
            + "\",\"kind\":\""
            + (var1.enemy ? "enemy" : "location")
            + "\",\"server\":\""
            + escape(var1.server)
            + "\",\"target_uuid\":\""
            + escape(var1.targetUuid)
            + "\",\"target_name\":\""
            + escape(var1.targetName)
            + "\",\"x\":"
            + var1.x
            + ",\"y\":"
            + var1.y
            + ",\"z\":"
            + var1.z
            + ",\"expires_at\":"
            + var1.expiresAt
            + "}"
      );
   }

   void cancel(String var1) {
      this.send("{\"type\":\"ping.cancel\",\"id\":\"" + escape(var1) + "\"}");
   }

   void update(String var1, double var2, double var4, double var6) {
      this.send("{\"type\":\"ping.update\",\"id\":\"" + escape(var1) + "\",\"x\":" + var2 + ",\"y\":" + var4 + ",\"z\":" + var6 + "}");
   }

   private void authenticate() {
      String var1 = this.secrets.get("clan-ping.leader-token");
      if (var1 != null && !var1.trim().isEmpty()) {
         bew var2 = ave.A().h;
         if (var2 != null) {
            this.authorized = false;
            this.state = "conectado (verificando líder)";
            this.send(
               "{\"type\":\"auth\",\"token\":\""
                  + escape(var1)
                  + "\",\"server\":\""
                  + escape(Server.address())
                  + "\",\"player_name\":\""
                  + escape(var2.e_())
                  + "\",\"player_uuid\":\""
                  + var2.aK().toString()
                  + "\"}"
            );
         }
      }
   }

   private void send(String var1) {
      WebSocket var2 = this.socket;
      if (var2 != null) {
         var2.sendText(var1, true);
      }
   }

   @Override
   public void onOpen(WebSocket var1) {
      this.socket = var1;
      this.connecting = false;
      this.authorized = false;
      this.state = "conectado (anunciando servidor)";
      this.send("{\"type\":\"hello\",\"server\":\"" + escape(Server.address()) + "\"}");
      var1.request(1L);
   }

   @Override
   public CompletionStage<?> onText(WebSocket var1, CharSequence var2, boolean var3) {
      this.received.append(var2);
      if (var3) {
         String var4 = this.received.toString();
         this.received.setLength(0);
         Tasks.onGameThread(() -> this.accept(var4));
      }

      var1.request(1L);
      return null;
   }

   @Override
   public CompletionStage<?> onBinary(WebSocket var1, ByteBuffer var2, boolean var3) {
      var1.request(1L);
      return null;
   }

   @Override
   public CompletionStage<?> onClose(WebSocket var1, int var2, String var3) {
      if (this.socket == var1) {
         this.socket = null;
         this.authorized = false;
         this.connecting = false;
         this.state = "offline";
         this.reconnectLater();
      }

      return null;
   }

   @Override
   public void onError(WebSocket var1, Throwable var2) {
      if (this.socket == var1) {
         this.socket = null;
         this.authorized = false;
         this.connecting = false;
         this.state = "offline";
         this.reconnectLater();
      }

      this.logger.accept("socket: " + var2.getClass().getSimpleName());
   }

   private void accept(String var1) {
      if ("hello.ok".equals(string(var1, "type"))) {
         this.state = this.secrets.get("clan-ping.leader-token") == null ? "conectado (membro)" : "conectado (verificando líder)";
         this.send("{\"type\":\"ping.sync\",\"server\":\"" + escape(Server.address()) + "\"}");
         this.authenticate();
      } else if ("auth.ok".equals(string(var1, "type"))) {
         this.authorized = true;
         this.state = "conectado (líder autorizado)";
      } else if ("error".equals(string(var1, "type"))) {
         String var20 = string(var1, "message");
         this.state = this.authorized ? "conectado (líder autorizado; erro: " + safe(var20) + ")" : "conectado (não autorizado: " + safe(var20) + ")";
      } else if ("ping.removed".equals(string(var1, "type"))) {
         String var19 = string(var1, "id");
         if (var19 != null) {
            this.remover.accept(var19);
         }
      } else if ("ping.updated".equals(string(var1, "type"))) {
         String var18 = string(var1, "id");
         if (var18 != null) {
            this.updater.accept(new ClanPingTransport.PositionUpdate(var18, decimal(var1, "x"), decimal(var1, "y"), decimal(var1, "z")));
         }
      } else if ("ping.created".equals(string(var1, "type"))) {
         String var2 = string(var1, "id");
         String var3 = string(var1, "author");
         String var4 = string(var1, "kind");
         String var5 = string(var1, "server");
         String var6 = string(var1, "target_uuid");
         String var7 = string(var1, "target_name");
         long var8 = number(var1, "expires_at");
         if (var2 != null && var3 != null && var5 != null && var8 > System.currentTimeMillis()) {
            double var10 = decimal(var1, "x");
            double var12 = decimal(var1, "y");
            double var14 = decimal(var1, "z");
            int var16 = (int)Math.min(180L, Math.max(1L, (var8 - System.currentTimeMillis() + 999L) / 1000L));
            ClanPing.Active var17 = "enemy".equals(var4)
               ? ClanPing.Active.enemy(var2, var3, var6 == null ? "" : var6, var7 == null ? "Inimigo" : var7, var10, var12, var14, var16)
               : ClanPing.Active.location(var2, var3, var10, var12, var14, var16);
            var17.recipients = (int)number(var1, "recipients");
            this.receiver.accept(var17);
         }
      }
   }

   private static String string(String var0, String var1) {
      Matcher var2 = Pattern.compile("\\\"" + Pattern.quote(var1) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(var0);
      return var2.find() ? var2.group(1) : null;
   }

   private static long number(String var0, String var1) {
      Matcher var2 = Pattern.compile("\\\"" + Pattern.quote(var1) + "\\\"\\s*:\\s*(\\d+)").matcher(var0);
      return var2.find() ? Long.parseLong(var2.group(1)) : 0L;
   }

   private static double decimal(String var0, String var1) {
      Matcher var2 = Pattern.compile("\\\"" + Pattern.quote(var1) + "\\\"\\s*:\\s*(-?[0-9.]+)").matcher(var0);
      return var2.find() ? Double.parseDouble(var2.group(1)) : 0.0;
   }

   private static String escape(String var0) {
      return var0 == null ? "" : var0.replace("\\", "\\\\").replace("\"", "\\\"");
   }

   private static String safe(String var0) {
      return var0 != null && !var0.trim().isEmpty() ? var0 : "relay recusou a operação";
   }

   private void reconnectLater() {
      if (this.wantConnection) {
         Tasks.later(100, this::connect);
      }
   }

   static final class PositionUpdate {
      final String id;
      final double x;
      final double y;
      final double z;

      PositionUpdate(String var1, double var2, double var4, double var6) {
         this.id = var1;
         this.x = var2;
         this.y = var4;
         this.z = var6;
      }
   }
}
