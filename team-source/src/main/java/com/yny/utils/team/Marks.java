/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package com.yny.utils.team;

import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Keys;
import dev.xavier.stein.loader.api.Lang;
import dev.xavier.stein.loader.api.Players;
import dev.xavier.stein.loader.api.Screens;
import dev.xavier.stein.loader.api.Server;
import dev.xavier.stein.loader.api.Targeting;

import java.util.List;

/**
 * Marking allies and enemies by hand: the keys aimed at a player (with Shift, their clan) and the
 * Panel fields. Ally and enemy never at the same time: marking one removes it from the other list.
 */
final class Marks {

    private final SteinCombat mod;

    /** The allies field's text while the Panel is open (not saved). */
    String pendingAlly = "";

    /** The enemies field's text while the Panel is open (not saved). */
    String pendingEnemy = "";

    Marks(SteinCombat mod) {
        this.mod = mod;
    }

    /**
     * The ally key: aimed at a player, marks or unmarks them (with Shift, their clan). <b>Aimed at
     * nobody, it does nothing</b>: the Panel opens with Insert, and only with it — a mod key that
     * opens a screen in the middle of a fight was the easiest way to stand still looking at a menu.
     */
    void allyAtCrosshair() {
        String nick = aimedNick();
        if (nick == null) {
            return;
        }
        CombatConfig c = mod.config;
        if (Keys.isShiftDown()) {
            String tag = Clans.of(nick);
            if (tag == null) {
                Say.chat(Lang.tr("steincombat.targetNoClan", nick));
                return;
            }
            if (c.allyClans.remove(tag)) {
                Say.chat(Lang.tr("steincombat.clanRemoved", tag));
            } else {
                if (!canAddAlly(true, tag)) {
                    return;
                }
                c.addAllyClan(tag);
                Say.chat(Lang.tr("steincombat.clanAdded", tag));
            }
        } else if (c.hasAlly(nick)) {
            c.removeAlly(nick);
            Say.chat(Lang.tr("steincombat.allyRemoved", nick));
        } else {
            if (!canAddAlly(false, nick)) {
                return;
            }
            c.addAlly(nick);
            Say.chat(Lang.tr("steincombat.allyAdded", nick));
        }
        mod.save();
    }

    /**
     * The enemy key: aimed at a player, marks or unmarks them (with Shift, their clan). Aimed at
     * nobody, nothing.
     */
    void enemyAtCrosshair() {
        String nick = aimedNick();
        if (nick == null) {
            return;
        }
        CombatConfig c = mod.config;
        if (Keys.isShiftDown()) {
            String tag = Clans.of(nick);
            if (tag == null) {
                Say.chat(Lang.tr("steincombat.targetNoClan", nick));
                return;
            }
            if (c.enemyClans.remove(tag)) {
                Say.chat(Lang.tr("steincombat.enemyClanRemoved", tag));
            } else {
                c.addEnemyClan(tag);
                Say.chat(Lang.tr("steincombat.enemyClanAdded", tag));
            }
        } else if (c.hasEnemy(nick)) {
            c.removeEnemy(nick);
            Say.chat(Lang.tr("steincombat.enemyRemoved", nick));
        } else {
            c.addEnemy(nick);
            Say.chat(Lang.tr("steincombat.enemyAdded", nick));
        }
        mod.save();
    }

    /**
     * The nick of the player under the crosshair, or null (a screen open, nobody, or the lists
     * locked).
     */
    private String aimedNick() {
        if (Screens.isOpen()) {
            return null;
        }
        Object target = Targeting.pointedEntity();
        if (!Entities.isPlayer(target) || locked()) {
            return null;
        }
        return Entities.name(target);
    }

    /** Adds what is in the allies field, as a clan (with brackets) or as a player. */
    void addAlly(boolean clan) {
        String text = pendingAlly.trim();
        if (text.isEmpty()) {
            return;
        }
        CombatConfig c = mod.config;
        if (clan) {
            String t = Clans.tag(text);
            if (t == null) {
                return; // not a tag: the text stays in the field, for the player to see what they
                // typed
            }
            if (!canAddAlly(true, t)) {
                return;
            }
            c.addAllyClan(t);
            Say.chat(Lang.tr("steincombat.clanAdded", t));
        } else if (Clans.validNick(text) && !Teams.isMe(text)) {
            if (!canAddAlly(false, text)) {
                return;
            }
            c.addAlly(text);
            Say.chat(Lang.tr("steincombat.allyAdded", text));
        } else {
            return;
        }
        pendingAlly = "";
        mod.save();
    }

    private static String getLeaderServer() {
        try {
            Class<?> pcn = Class.forName("com.yny.utils.modules.clanping.PingClanName");
            Object res = pcn.getMethod("getLeaderServer").invoke(null);
            if (res instanceof String) {
                return (String) res;
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> cp = Class.forName("com.yny.utils.modules.clanping.ClanPing");
            Object res = cp.getMethod("getLeaderServer").invoke(null);
            if (res instanceof String) {
                return (String) res;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean isPlayerPresent(String nick) {
        if (nick == null || nick.trim().isEmpty()) {
            return false;
        }
        String cleanNick = nick.trim();
        try {
            List<String> online = Players.online();
            if (online != null) {
                for (String name : online) {
                    if (name != null && cleanNick.equalsIgnoreCase(name)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            if (Entities.player(cleanNick) != null) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            List<Object> list = Entities.players();
            if (list != null) {
                for (Object p : list) {
                    if (p != null && cleanNick.equalsIgnoreCase(Entities.name(p))) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean isClanPresent(String tag) {
        if (tag == null || tag.trim().isEmpty()) {
            return false;
        }
        String cleanTag = tag.trim();
        try {
            List<String> online = Players.online();
            if (online != null) {
                for (String name : online) {
                    if (name == null || name.isEmpty()) continue;
                    String c = Clans.of(name);
                    if (c != null && cleanTag.equalsIgnoreCase(c)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            List<Object> list = Entities.players();
            if (list != null) {
                for (Object p : list) {
                    if (p == null) continue;
                    String c = Clans.of(Entities.name(p));
                    if (c != null && cleanTag.equalsIgnoreCase(c)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean canAddAlly(boolean isClan, String target) {
        if (!Server.isMultiplayer()) {
            return true;
        }
        String currentServer = Server.address();
        String leaderServer = getLeaderServer();
        if (leaderServer != null
                && !leaderServer.isEmpty()
                && currentServer != null
                && !currentServer.isEmpty()) {
            if (!currentServer.equalsIgnoreCase(leaderServer)) {
                Say.chat(
                        "§c[Aliados] Só é permitido adicionar aliados no servidor onde o líder está! (Líder em: "
                                + leaderServer
                                + ")");
                return false;
            }
        }

        if (isClan) {
            if (!isClanPresent(target)) {
                Say.chat(
                        "§c[Aliados] Nenhum membro do clã §f["
                                + target
                                + "] §cestá presente no servidor do líder!");
                return false;
            }
        } else {
            if (!isPlayerPresent(target)) {
                Say.chat(
                        "§c[Aliados] O jogador §f"
                                + target
                                + " §cnão está presente no servidor do líder!");
                return false;
            }
        }
        return true;
    }

    /**
     * Adds what is in the enemies field, as a clan or as a player (and removes it from the allies,
     * if it was there).
     */
    void addEnemy(boolean clan) {
        String text = pendingEnemy.trim();
        if (text.isEmpty()) {
            return;
        }
        CombatConfig c = mod.config;
        if (clan) {
            String t = Clans.tag(text);
            if (t == null) {
                return; // not a tag: the text stays in the field, for the player to see what they
                // typed
            }
            c.addEnemyClan(t);
        } else if (Clans.validNick(text) && !Teams.isMe(text)) {
            c.addEnemy(text);
        } else {
            return;
        }
        pendingEnemy = "";
        mod.save();
    }

    /**
     * In URL mode the hand-written lists are frozen, and the marking keys warn and change nothing:
     * marking someone on a list that does not apply would only make the player think they marked
     * them.
     */
    boolean locked() {
        if (!mod.config.remote) {
            return false;
        }
        Say.chat(Lang.tr("steincombat.remoteLocked"));
        return true;
    }
}
