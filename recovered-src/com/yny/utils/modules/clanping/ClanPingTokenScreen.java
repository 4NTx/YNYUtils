package com.yny.utils.modules.clanping;

final class ClanPingTokenScreen extends axu {
   private final ClanPing ping;
   private avw token;

   ClanPingTokenScreen(ClanPing var1) {
      this.ping = var1;
   }

   public void b() {
      this.n.clear();
      int var1 = this.l / 2;
      this.token = new avw(0, this.q, var1 - 140, this.m / 2 - 8, 280, 20);
      this.token.f(512);
      this.token.b(true);
      this.n.add(new avs(1, var1 - 140, this.m / 2 + 24, 136, 20, "Salvar token"));
      this.n.add(new avs(2, var1 + 4, this.m / 2 + 24, 136, 20, "Remover token"));
      this.n.add(new avs(3, var1 - 70, this.m / 2 + 50, 140, 20, "Cancelar"));
   }

   protected void a(char var1, int var2) {
      if (var2 == 1) {
         this.j.a(null);
      } else if (var2 != 28 && var2 != 156) {
         this.token.a(var1, var2);
      } else {
         this.ping.saveToken(this.token.b());
         this.j.a(null);
      }
   }

   protected void a(int var1, int var2, int var3) {
      super.a(var1, var2, var3);
      this.token.a(var1, var2, var3);
   }

   protected void a(avs var1) {
      if (var1.k == 1) {
         this.ping.saveToken(this.token.b());
      }

      if (var1.k == 2) {
         this.ping.removeToken();
      }

      if (var1.k != 0) {
         this.j.a(null);
      }
   }

   public void a(int var1, int var2, float var3) {
      this.c();
      this.a(this.q, "Clan Ping — autorização de líder", this.l / 2, this.m / 2 - 48, 16777215);
      this.a(this.q, "Cole aqui o token criado no painel. Ele não é enviado ao chat.", this.l / 2, this.m / 2 - 32, 12566463);
      this.token.g();
      super.a(var1, var2, var3);
   }
}
