package com.yny.utils.modules.clanping;
import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.HudElement;
import dev.xavier.stein.loader.api.HudPlacement;
import dev.xavier.stein.loader.api.Render;
final class ClanPingHud implements HudElement {
 private static final int WIDTH=248,HEIGHT=58;
 private final ClanPing ping;
 private boolean enemy,stale;
 private int sector,distance,seconds;
 private String target="",author="";
 ClanPingHud(ClanPing ping){this.ping=ping;}
 public String id(){return "ynyutils.clanping";}
 public String name(){return "Clan Ping";}
 public boolean layout(boolean editor,float scale){
  if(!ping.hudEnabled())return false;
  ClanPing.Active active=ping.active;
  if(active==null||active.expired()){
   if(!editor)return false;
   enemy=true;stale=false;sector=1;distance=83;seconds=42;target="Jogador marcado";author="Líder";return true;
  }
  enemy=active.enemy;
  stale=enemy&&Entities.player(active.targetName)==null;
  target=enemy?PingClanName.display(active.targetName):"Local marcado";
  author=active.author;
  seconds=PingHudModel.seconds(active.expiresAt,System.currentTimeMillis());
  Object self=Entities.self();
  if(self==null){sector=-1;distance=0;}
  else {
   double dx=active.x-Entities.x(self,1.0F),dz=active.z-Entities.z(self,1.0F);
   sector=PingHudModel.sector(dx,dz,Entities.yaw(self,1.0F));
   distance=PingHudModel.meters(dx,dz);
  }
  return true;
 }
 public int width(){return WIDTH;}
 public int height(){return HEIGHT;}
 public void draw(boolean editor,float scale){
  float x=editor?-WIDTH:0;
  int accent=enemy?0xFFFF625D:0xFF36CBE5;
  Render.roundRect(x,0,WIDTH,HEIGHT,5,0xDC17212D);
  Render.rect(x,0,3,HEIGHT,accent);
  Render.roundRect(x+39,40,WIDTH-47,15,3,0x9B0B1520);
  PingCompass.draw(sector,accent,x+20,29);
  Render.text(enemy?"ALVO INIMIGO":"LOCAL MARCADO",x+40,5,accent,true);
  String remaining=seconds+"s";
  Render.text(remaining,x+WIDTH-Render.textWidth(remaining)-10,5,0xFFFFDA80,true);
  Render.text(Render.trim(target,WIDTH-54),x+40,18,0xFFFFFFFF,true);
  Render.text(Render.trim(stale?"ÚLTIMA POSIÇÃO · por "+author:"por "+author,WIDTH-54),x+40,29,stale?0xFFFFD280:0xFFB2C0CF,false);
  String direction=PingHudModel.direction(sector);
  Render.text(direction,x+45,43,0xFFFFDA80,true);
  String meters=distance+" m";
  Render.text(meters,x+WIDTH-Render.textWidth(meters)-12,43,0xFFFFFFFF,true);
 }
 public HudPlacement defaultPlacement(){return new HudPlacement(1,1,0,50,1.0F,true,false);}
}
