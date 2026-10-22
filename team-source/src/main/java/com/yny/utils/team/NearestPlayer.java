/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package com.yny.utils.team;

import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.HudElement;
import dev.xavier.stein.loader.api.HudPlacement;
import dev.xavier.stein.loader.api.Lang;
import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Render;

import java.util.List;
import java.util.Locale;

/**
 * The distance to the nearest known player of one side, on the HUD grid: the nearest enemy (neither
 * ally nor in my clan, {@link CombatApi}) or the nearest ally (ally or in my clan) — two elements,
 * one of each. Only the players the client knows — whoever is too far away does not reach it —, and
 * invisible ones do not count. With nobody around, the element does not show.
 */
final class NearestPlayer implements HudElement {

    private final SteinCombat mod;

    /** The nearest ally's element; false, the nearest enemy's. */
    private final boolean ally;

    private String text = "";
    private long lastScanTime = 0L;
    private Object cachedNear = null;
    private float cachedBest = Float.MAX_VALUE;

    NearestPlayer(SteinCombat mod, boolean ally) {
        this.mod = mod;
        this.ally = ally;
    }

    @Override
    public String id() {
        return ally ? "steincombat.nearestAlly" : "steincombat.nearest";
    }

    @Override
    public String name() {
        return Lang.tr(ally ? "steincombat.hud.nearestAlly" : "steincombat.hud.nearest");
    }

    private boolean wanted(Object p) {
        int r = CombatApi.relation(Entities.name(p));
        return ally ? (r == CombatApi.ALLY || r == CombatApi.CLAN) : (r == CombatApi.ENEMY || r == CombatApi.NEUTRAL);
    }

    @Override
    public boolean layout(boolean preview, float partialTicks) {
        if (preview) {
            String nick = Lang.tr("steinloader.hud.sample.player");
            String distance = String.format(Locale.ROOT, "%.1f m", 12.3F).replace('.', ',');
            String color = ally ? "§" + mod.config.allyColor : "§c";
            boolean name = ally ? mod.config.nearestAllyName : mod.config.nearestName;
            text = name ? color + nick + " §f" + distance : color + distance;
            return true;
        }

        Object me = Entities.self();
        if (me == null) {
            cachedNear = null;
            return false;
        }

        long now = System.currentTimeMillis();
        Object near = cachedNear;
        float best = Float.MAX_VALUE;

        // If we recently scanned and the cached target is still valid, update distance directly
        boolean needFullScan = (now - lastScanTime > 100L) || near == null;
        if (!needFullScan) {
            try {
                if (!Entities.isAlive(near) || Entities.isInvisible(near) || !wanted(near)) {
                    needFullScan = true;
                } else {
                    best = Entities.distance(me, near);
                }
            } catch (Throwable t) {
                needFullScan = true;
            }
        }

        if (needFullScan) {
            lastScanTime = now;
            near = null;
            best = Float.MAX_VALUE;
            Object[] playerList = null;
            try {
                java.util.List<Object> list = Entities.players();
                if (list != null && !list.isEmpty()) {
                    playerList = list.toArray();
                }
            } catch (Throwable ignored) {}
            if (playerList != null) {
                for (Object p : playerList) {
                    if (p == null || p == me || Entities.isInvisible(p) || !wanted(p)) {
                        continue;
                    }
                    float d = Entities.distance(me, p);
                    if (d < best) {
                        best = d;
                        near = p;
                    }
                }
            }
            cachedNear = near;
            cachedBest = best;
        }

        if (near == null) {
            return false;
        }

        String nick = Entities.name(near);
        String distance = String.format(Locale.ROOT, "%.1f m", best).replace('.', ',');
        String color = ally ? "§" + mod.config.allyColor : "§c";
        boolean name = ally ? mod.config.nearestAllyName : mod.config.nearestName;
        text = name ? color + nick + " §f" + distance : color + distance;
        return true;
    }

    @Override
    public int width() {
        return Render.textWidth(text) + 2;
    }

    @Override
    public int height() {
        return 12;
    }

    @Override
    public void draw(boolean alignRight, float partialTicks) {
        Render.text(text, 1, 2, 0xFFFFFFFF, true);
    }

    @Override
    public HudPlacement defaultPlacement() {
        // The ally's right below the enemy's.
        return new HudPlacement(1, 0, 0, ally ? 54 : 40, 1.0F, true, true);
    }

    @Override
    public float defaultBackground() {
        return 0.5F;
    }

    @Override
    public void options(List<Option> out) {
        if (ally) {
            out.add(
                    Option.toggle(
                            Lang.tr("steincombat.hud.name"),
                            () -> mod.config.nearestAllyName,
                            v -> mod.config.nearestAllyName = v));
        } else {
            out.add(
                    Option.toggle(
                            Lang.tr("steincombat.hud.name"),
                            () -> mod.config.nearestName,
                            v -> mod.config.nearestName = v));
        }
    }

    @Override
    public void restoreOptions() {
        CombatConfig d = new CombatConfig();
        if (ally) {
            mod.config.nearestAllyName = d.nearestAllyName;
        } else {
            mod.config.nearestName = d.nearestName;
        }
    }

    @Override
    public void optionsChanged() {
        mod.save();
    }
}
