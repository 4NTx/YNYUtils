/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package com.yny.utils.team;

import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Hud;
import dev.xavier.stein.loader.api.Inventory;
import dev.xavier.stein.loader.api.Log;
import dev.xavier.stein.loader.api.ModContext;
import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Page;
import dev.xavier.stein.loader.api.Players;
import dev.xavier.stein.loader.api.Relation;
import dev.xavier.stein.loader.api.Relations;
import dev.xavier.stein.loader.api.SteinMod;

import java.io.File;

/** Team features packaged with YNYUtils. The remote source is fixed by the pack. */
public final class SteinCombat implements SteinMod {
    public static volatile SteinCombat instance;
    static final String NAME = "YNYUtils";
    static final String MOD_ID = "ynyutils-team";
    static final String REMOTE_URL = "https://ping.antiutm.com/allies.json";
    static final boolean NO_SLOWDOWN_ENABLED = false;

    private final ModContext ctx = ModContext.of(this);
    CombatConfig config = new CombatConfig();
    final Teams teams = new Teams(this);
    final ToggleSprint sprint = new ToggleSprint();
    final ClanGuard guard = new ClanGuard(this);
    final RemoteList remote = new RemoteList(this);
    final Marks marks = new Marks(this);
    CombatKeys keys;
    private File configFile;

    public SteinCombat() {
        instance = this;
    }

    @Override
    public void afterStartGame() {
        configFile = new File(ctx.configFile().getParentFile(), "ynyutils-team.json");
        config = ConfigFile.load(configFile);
        config.remote = true;
        config.remoteUrl = REMOTE_URL;
        keys = new CombatKeys(this);
        sprint.toggled = config.toggleSprint;
        CombatApi.mod = this;
        Players.groupResolver(
                MOD_ID,
                nick -> {
                    String tag = Clans.of(nick);
                    return tag == null ? "" : tag;
                });
        Relations.register(MOD_ID, teams::relationOf);
        apply();
        Hud.register(new NearestPlayer(this, false));
        Hud.register(new NearestPlayer(this, true));
        log().info("team source fixed at " + REMOTE_URL);
    }

    Log log() {
        return ctx.log();
    }

    File configFile() {
        return configFile;
    }

    boolean armorSettling() {
        return false;
    }

    boolean refillBusy() {
        return false;
    }

    boolean handBusy() {
        if (Inventory.isUsingItem()) {
            return true;
        }
        try {
            Class<?> ap = Class.forName("com.yny.utils.modules.player.AutoProtect");
            java.lang.reflect.Method m = ap.getMethod("isControllingUseKey");
            if (Boolean.TRUE.equals(m.invoke(null))) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    @Override
    public void onJoinGame() {
        remote.joined();
        apply();
        remote.fetch(false);
    }

    void save() {
        config.remote = true;
        config.remoteUrl = REMOTE_URL;
        if (configFile != null) ConfigFile.save(config, configFile);
        apply();
    }

    void apply() {
        config.autoclick = false;
        config.remote = true;
        config.remoteUrl = REMOTE_URL;
        teams.apply(config, remote.feed());
        publishColors();
    }

    private void publishColors() {
        boolean on = config.colorNames;
        Relations.setColor(
                Relation.GROUP, on ? Option.rgb(String.valueOf(config.clanColor)) : 0xFFFFFF);
        Relations.setColor(
                Relation.ALLY, on ? Option.rgb(String.valueOf(config.allyColor)) : 0xFFFFFF);
        Relations.setColor(
                Relation.ENEMY, on ? Option.rgb(String.valueOf(config.enemyColor)) : 0xFFFFFF);
        Relations.setColor(
                Relation.NEUTRAL, on ? Option.rgb(String.valueOf(config.neutralColor)) : 0xFFFFFF);
        Relations.changed();
    }

    @Override
    public void onRenderWorld(float partialTicks) {}

    @Override
    public void onTickEnd() {
        if (keys == null) return;
        if (remote.serverChanged()) {
            apply();
            remote.fetch(false);
        }
        remote.poll();
        keys.handle();
        sprint.tick(config.allDirections);
    }

    @Override
    public boolean onAttackEntity(Object target) {
        return false;
    }

    @Override
    public boolean onHideEntity(Object entity) {
        return guard.onHideEntity(entity);
    }

    @Override
    public boolean onGhostEntity(Object entity) {
        return guard.onGhostEntity(entity);
    }

    @Override
    public String onPlayerListName(Object playerInfo, String name) {
        String nick = config.colorNames ? Players.nickOf(playerInfo) : null;
        return nick == null ? name : teams.paintListName(nick, name);
    }

    @Override
    public Object onNametag(Object entity, Object name) {
        if (!config.colorNames || !Entities.isPlayer(entity)) return name;
        return teams.paintNametagComponent(Entities.name(entity), name);
    }

    @Override
    public void onPage(Page page) {
        // As opções de Equipe são exibidas exclusivamente na aba principal unificada do YNYUtils.
    }

    public static void addOptions(Page page) {
        if (instance != null) {
            CombatPage.build(instance, page);
        }
    }
}
