package com.yny.utils.modules.clanping;

import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Players;

/** Formats an enemy ping using the clan supplied by the client's player list. */
final class PingClanName {
    private PingClanName() {}

    static String display(String nick) {
        if (nick == null || nick.isEmpty()) return "Alvo";
        String shown = null;
        try {
            Object entity = Entities.player(nick);
            if (entity != null) shown = Entities.displayName(entity);
            if (shown == null || shown.trim().isEmpty()) shown = Players.tabName(nick);
        } catch (RuntimeException ignored) {
            // The target may have left the world while the ping is still visible.
        }
        String group = null;
        try {
            group = Players.group(nick);
        } catch (RuntimeException ignored) {
            // Keep the nickname when the server's player list is unavailable.
        }
        return format(nick, shown, group);
    }

    static String format(String nick, String shown, String group) {
        String name = shown == null || shown.trim().isEmpty() ? nick : shown;
        if (group == null || group.trim().isEmpty()) return name;
        String tag = group.trim();
        if (tag.equalsIgnoreCase(Players.bracketTag(name))) return name;
        return "§7[" + tag + "] §f" + name;
    }
}
