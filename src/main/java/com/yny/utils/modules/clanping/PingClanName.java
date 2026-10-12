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
                if (prefix != null) group = extractBracketTag(prefix);
            }
            if ((group == null || group.trim().isEmpty()) && shown != null) {
                group = extractBracketTag(shown);
            }
            if (group == null || group.trim().isEmpty()) {
                String tab = Players.tabName(nick);
                if (tab != null) group = extractBracketTag(tab);
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
        if (hasBracketTag(name, tag)) return name;
        return "§7[" + tag + "] §f" + name;
    }

    static boolean hasBracketTag(String text, String tag) {
        if (text == null || tag == null || tag.trim().isEmpty()) return false;
        if (tag.equalsIgnoreCase(Players.bracketTag(text))) return true;
        String lowerTag = tag.trim().toLowerCase();
        String clean = text.replaceAll("§.", "").toLowerCase();
        return containsTagInEnclosures(clean, lowerTag, '[', ']')
                || containsTagInEnclosures(clean, lowerTag, '(', ')');
    }

    private static boolean containsTagInEnclosures(String clean, String lowerTag, char open, char close) {
        int start = 0;
        while ((start = clean.indexOf(open, start)) != -1) {
            int end = clean.indexOf(close, start + 1);
            if (end == -1) break;
            String inside = clean.substring(start + 1, end).trim();
            if (inside.equalsIgnoreCase(lowerTag)) return true;
            start = end + 1;
        }
        return false;
    }

    static String extractBracketTag(String text) {
        if (text == null) return null;
        String clean = text.replaceAll("§.", "").trim();
        int start = 0;
        String fallbackTag = null;
        while ((start = clean.indexOf('[', start)) != -1) {
            int end = clean.indexOf(']', start + 1);
            if (end == -1) break;
            String inside = clean.substring(start + 1, end).trim();
            if (!inside.isEmpty()) {
                if (!isKnownRank(inside)) {
                    return inside;
                }
                fallbackTag = inside;
            }
            start = end + 1;
        }
        start = 0;
        while ((start = clean.indexOf('(', start)) != -1) {
            int end = clean.indexOf(')', start + 1);
            if (end == -1) break;
            String inside = clean.substring(start + 1, end).trim();
            if (!inside.isEmpty()) {
                if (!isKnownRank(inside)) {
                    return inside;
                }
                if (fallbackTag == null) fallbackTag = inside;
            }
            start = end + 1;
        }
        return fallbackTag != null ? fallbackTag : Players.bracketTag(text);
    }

    static boolean isKnownRank(String tag) {
        if (tag == null || tag.trim().isEmpty()) return false;
        String upper = tag.trim().toUpperCase();
        String base = upper.replaceAll("[+\\-*0-9]+$", "").trim();
        return isRankWord(upper) || isRankWord(base);
    }

    private static boolean isRankWord(String word) {
        return word.equals("VIP") || word.equals("MVP") || word.equals("PRO")
                || word.equals("MOD") || word.equals("ADMIN") || word.equals("HELPER")
                || word.equals("DONO") || word.equals("MASTER") || word.equals("ULTRA")
                || word.equals("SUPREMO") || word.equals("HEROI") || word.equals("HERÓI")
                || word.equals("CAMPEAO") || word.equals("CAMPEÃO") || word.equals("LENDARIO")
                || word.equals("LENDÁRIO") || word.equals("MEMBRO") || word.equals("BUILDER")
                || word.equals("SUB") || word.equals("YT") || word.equals("YOUTUBER")
                || word.equals("STAFF") || word.equals("TRIAL") || word.equals("COORD")
                || word.equals("GERENTE") || word.equals("DIRETOR") || word.equals("DEV")
                || word.equals("DEVELOPER") || word.equals("ELITE") || word.equals("CHAMPION")
                || word.equals("BETA") || word.equals("ALPHA") || word.equals("SOCIO")
                || word.equals("SÓCIO") || word.equals("SUBDONO") || word.equals("GERENCIA");
    }
}
