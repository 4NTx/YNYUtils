package com.yny.targethealth;

import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Players;

/** Adds the server supplied group when it is separate from the display name. */
public final class TargetClanName {
    private TargetClanName() { }

    public static String display(Object entity) {
        String nick = Entities.name(entity);
        String shown = Entities.displayName(entity);
        String group = null;
        try { if (nick != null) group = Players.group(nick); }
        catch (RuntimeException ignored) { /* Tab list can disappear mid-frame. */ }
        return format(nick, shown, group);
    }

    static String format(String nick, String shown, String group) {
        String name = shown == null || shown.trim().isEmpty() ? nick : shown;
        if (name == null || name.trim().isEmpty()) name = "Alvo";
        if (group == null || group.trim().isEmpty()) return name;
        String tag = group.trim();
        if (tag.equalsIgnoreCase(Players.bracketTag(name))) return name;
        return "§7[" + tag + "] §f" + name;
    }
}
