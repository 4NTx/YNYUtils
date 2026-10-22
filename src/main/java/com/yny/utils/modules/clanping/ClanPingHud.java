package com.yny.utils.modules.clanping;

import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.HudElement;
import dev.xavier.stein.loader.api.HudPlacement;
import dev.xavier.stein.loader.api.Render;

final class ClanPingHud implements HudElement {
    private static final int WIDTH = 248, HEIGHT = 58;
    private final ClanPing ping;
    private boolean enemy, stale, sameWorld = true;
    private int sector, distance;
    private String target = "", author = "", pingWorldName = "", currentWorldName = "";

    private int pingX, pingY, pingZ;

    ClanPingHud(ClanPing ping) {
        this.ping = ping;
    }

    public String id() {
        return "ynyutils.clanping";
    }

    public String name() {
        return "Clan Ping";
    }

    public boolean layout(boolean editor, float scale) {
        if (!ping.hudEnabled()) return false;
        ClanPing.Active active = ping.active;
        if (active == null || active.expired()) {
            if (!editor) return false;
            enemy = true;
            stale = false;
            sameWorld = true;
            sector = 1;
            distance = 83;
            target = "Jogador marcado";
            author = "Líder";
            pingWorldName = "Mundo Normal";
            currentWorldName = "Mundo Normal";
            pingX = 1420;
            pingY = 64;
            pingZ = -530;
            return true;
        }

        enemy = active.enemy;
        stale = enemy && Entities.player(active.targetName) == null;
        target = enemy ? PingClanName.display(active.targetName) : "Local marcado";
        author = active.author;
        sameWorld = PingWorld.isSameWorld(active);
        pingWorldName = PingWorld.getPingWorldName(active);
        currentWorldName = PingWorld.getCurrentWorldName();
        pingX = (int) Math.round(active.x);
        pingY = (int) Math.round(active.y);
        pingZ = (int) Math.round(active.z);

        Object self = Entities.self();
        if (self == null || !sameWorld) {
            sector = -1;
            distance = 0;
        } else {
            try {
                double sx = Entities.x(self, 1.0F);
                double sz = Entities.z(self, 1.0F);
                float yaw = Entities.yaw(self, 1.0F);
                double dx = active.x - sx;
                double dz = active.z - sz;
                sector = PingHudModel.sector(dx, dz, yaw);
                distance = PingHudModel.meters(dx, dz);
            } catch (Throwable t) {
                sector = 0;
                distance = 0;
            }
        }
        return true;
    }

    public int width() {
        return WIDTH;
    }

    public int height() {
        return HEIGHT;
    }

    public void draw(boolean editor, float scale) {
        float x = editor ? -WIDTH : 0;
        int accent = !sameWorld ? 0xFFFF9900 : (enemy ? 0xFFFF625D : 0xFF36CBE5);

        Render.roundRect(x, 0, WIDTH, HEIGHT, 5, 0xDC17212D);
        Render.rect(x, 0, 3, HEIGHT, accent);
        Render.roundRect(x + 39, 40, WIDTH - 47, 15, 3, 0x9B0B1520);

        if (!sameWorld) {
            PingCompass.draw(-1, accent, x + 20, 29);
            Render.text("OUTRO MUNDO", x + 40, 5, accent, true);
            String worldBadge = "MUNDO: " + pingWorldName;
            Render.text(worldBadge, x + WIDTH - Render.textWidth(worldBadge) - 10, 5, 0xFFFFDA80, true);
            Render.text(Render.trim(target, WIDTH - 54), x + 40, 18, 0xFFFFFFFF, true);
            Render.text(Render.trim("Ping em " + pingWorldName + " · " + pingX + ", " + pingY + ", " + pingZ, WIDTH - 54), x + 40, 29, 0xFFFFB280, false);
            Render.text("ALVO EM OUTRO MUNDO", x + 45, 43, 0xFFFF9900, true);
            Render.text(pingWorldName, x + WIDTH - Render.textWidth(pingWorldName) - 12, 43, 0xFFFFDA80, true);
        } else {
            PingCompass.draw(sector, accent, x + 20, 29);
            Render.text(enemy ? "ALVO INIMIGO" : "LOCAL MARCADO", x + 40, 5, accent, true);
            String fixedBadge = "FIXO";
            Render.text(fixedBadge, x + WIDTH - Render.textWidth(fixedBadge) - 10, 5, 0xFF85E3FF, true);
            Render.text(Render.trim(target, WIDTH - 54), x + 40, 18, 0xFFFFFFFF, true);
            String sub = (stale ? "ÚLTIMA POSIÇÃO · " : "por " + author + " · ") + pingX + ", " + pingY + ", " + pingZ;
            Render.text(Render.trim(sub, WIDTH - 54), x + 40, 29, stale ? 0xFFFFD280 : 0xFFB2C0CF, false);
            String direction = PingHudModel.direction(sector);
            Render.text(direction, x + 45, 43, 0xFFFFDA80, true);
            String meters = distance + " m";
            Render.text(meters, x + WIDTH - Render.textWidth(meters) - 12, 43, 0xFFFFFFFF, true);
        }
    }

    public HudPlacement defaultPlacement() {
        return new HudPlacement(1, 1, 0, 50, 1.0F, true, false);
    }
}
