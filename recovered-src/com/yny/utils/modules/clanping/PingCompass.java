package com.yny.utils.modules.clanping;
import dev.xavier.stein.loader.api.Render;
final class PingCompass {
 private PingCompass(){}
 static void draw(int sector,int color,float cx,float cy){
  Render.circle(cx,cy,13.0F,0x9A0B1520);
  Render.ring(cx,cy,12.0F,1.0F,0x8095A7BA);
  if(sector<0){Render.circle(cx,cy,3.0F,color);return;}
  Render.push();Render.translate(cx,cy);Render.rotate(sector*45.0F);
  Render.triangle(0.0F,-9.0F,-5.0F,5.0F,5.0F,5.0F,color);
  Render.pop();
  Render.circle(cx,cy,2.0F,0xFFFFFFFF);
 }
}