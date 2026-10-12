package com.yny.utils.modules.clanping;

import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Players;

/** Formats an enemy ping using the clan supplied by the client's player list. */
public final class PingClanName {
    private PingClanName() {}

    public static String display(String nick) {
        return display(null, nick);
    }

    public static String display(Object entity, String nick) {
        if ((nick == null || nick.isEmpty()) && entity != null) {
            nick = Entities.name(entity);
        }
        if (nick == null || nick.isEmpty()) return "Alvo";
        String shown = null;
        try {
            if (entity != null) shown = Entities.displayName(entity);
            if (shown == null || shown.trim().isEmpty()) {
                Object found = Entities.player(nick);
                if (found != null) shown = Entities.displayName(found);
            }
            if (shown == null || shown.trim().isEmpty()) {
                shown = Players.tabName(nick);
            }
        } catch (RuntimeException ignored) {
            // The target may have left the world while the ping is still visible.
        }
        String group = null;
        try {
            group = Players.group(nick);
            if (group == null || group.trim().isEmpty()) {
                String prefix = Players.prefix(nick);
                if (prefix != null) group = Players.bracketTag(prefix);
            }
            if ((group == null || group.trim().isEmpty()) && shown != null) {
                group = Players.bracketTag(shown);
            }
            if (group == null || group.trim().isEmpty()) {
                String tab = Players.tabName(nick);
                if (tab != null) group = Players.bracketTag(tab);
            }
        } catch (RuntimeException ignored) {
            // Keep the nickname when the server's player list is unavailable.
        }
        return format(nick, shown, group);
    }

    public static String format(String nick, String shown, String group) {
        String name = shown == null || shown.trim().isEmpty() ? nick : shown;
        if (name == null || name.trim().isEmpty()) name = "Alvo";
        if (group == null || group.trim().isEmpty()) return name;
        String tag = group.replace("[", "").replace("]", "").trim();
        if (tag.isEmpty()) return name;
        if (tag.equalsIgnoreCase(Players.bracketTag(name))) return name;
        return "§7[" + tag + "] §f" + name;
    }
}
