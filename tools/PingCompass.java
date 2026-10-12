package com.yny.utils.modules.clanping;
import dev.xavier.stein.loader.api.Render;
final class PingCompass {
  static void draw(String title, String detail, float x, float height) {
    float cx = x + 17.0F;
    float cy = height / 2.0F;
    int color = title != null && title.startsWith("§b") ? 0xFF26C9EE : 0xFFFF554D;
    Render.circle(cx, cy, 11.0F, 0xAA1B2635);
    if (detail == null || !detail.startsWith("§e") || detail.length() < 3) {
      Render.circle(cx, cy, 2.5F, color);
      return;
    }
    float angle;
    switch (detail.charAt(2)) {
      case '↑': angle = 0.0F; break;
      case '↗': angle = 45.0F; break;
      case '→': angle = 90.0F; break;
      case '↘': angle = 135.0F; break;
      case '↓': angle = 180.0F; break;
      case '↙': angle = 225.0F; break;
      case '←': angle = 270.0F; break;
      case '↖': angle = 315.0F; break;
      default: Render.circle(cx, cy, 2.5F, color); return;
    }
    Render.push();
    Render.translate(cx, cy);
    Render.rotate(angle);
    Render.triangle(0.0F, -8.0F, -5.0F, 5.0F, 5.0F, 5.0F, color);
    Render.pop();
  }
}