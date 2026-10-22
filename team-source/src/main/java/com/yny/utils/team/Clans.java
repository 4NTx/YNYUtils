/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package com.yny.utils.team;

import dev.xavier.stein.loader.api.Players;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A player's clan tag, read from the scoreboard: the {@code sc_ABC} team of this project's
 * SteinCombat server plugin or, on servers with another clan plugin, the team prefix ({@code "[ABC]
 * Nick"} in the Tab list; an option, on by default). On a server that uses the prefix for ranks
 * ({@code "[VIP]"}), the player turns the option off.
 */
final class Clans {

    /** The SteinCombat plugin's teams on the scoreboard: "sc_" + tag. */
    private static final String CLAN_TEAM = "sc_";

    /** How long a looked-up clan stays cached. */
    private static final long CACHE_NANOS = 1_000_000_000L;

    /**
     * A player's clan and <b>where it came from</b>. The difference matters: the plugin's {@code
     * sc_TAG} team is proof (a clan plugin wrote it), while brackets in the name are only a hint —
     * on an ordinary server {@code [VIP]}, {@code [ADM]} and {@code [PRO]} have the same three
     * letters as a clan tag.
     */
    static final class Clan {
        static final Clan NONE = new Clan(null, false);

        final String tag;

        /**
         * It came from this project's clan plugin (an sc_TAG team), not from brackets in the name.
         */
        final boolean proven;

        Clan(String tag, boolean proven) {
            this.tag = tag;
            this.proven = proven;
        }
    }

    /** "No clan" is a valid answer and is cached too (hence containsKey, not computeIfAbsent). */
    private static final Map<String, Clan> CACHE = new HashMap<>();

    private static long cacheAt;

    private Clans() {}

    /**
     * The player's clan tag, cached for a second. The real lookup reads the scoreboard and the Tab
     * list, and finding a nick in the Tab list walks every player: the HUD's Tab list asks for each
     * player's relation and clan every frame, which became one walk per player per frame (with 200
     * on the list, tens of thousands of iterations per frame — that is what froze a held Tab).
     */
    public static String of(String nick) {
        return info(nick).tag;
    }

    /** The same, with where it came from (see {@link Clan}). */
    static Clan info(String nick) {
        epoch();
        if (CACHE.containsKey(nick)) {
            return CACHE.get(nick);
        }
        Clan clan = lookup(nick);
        CACHE.put(nick, clan);
        return clan;
    }

    /**
     * Starts a new round of the clan cache if the current one is older than a second, and returns
     * when the current round started: the number changes with every new round, and that is how the
     * relation cache ({@link Teams}) knows the clans may have changed.
     */
    static long epoch() {
        long now = System.nanoTime();
        if (now - cacheAt > CACHE_NANOS) {
            CACHE.clear();
            cacheAt = now;
        }
        return cacheAt;
    }

    private static volatile String detectedClan;

    public static void setDetectedClan(String tag) {
        String clean = tag == null ? null : tag(tag);
        detectedClan = clean;
        clearCache();
    }

    public static String getDetectedClan() {
        return detectedClan;
    }

    /** My clan's tag on this server, or null. */
    public static String mine() {
        if (detectedClan != null && !detectedClan.isEmpty()) {
            return detectedClan;
        }
        return Players.self() == null ? null : of(Players.self());
    }

    public static void clearCache() {
        CACHE.clear();
        cacheAt = 0L;
    }

    private static Clan lookup(String nick) {
        if (nick == null || Players.self() == null) {
            return Clan.NONE;
        }
        if (detectedClan != null && nick.equalsIgnoreCase(Players.self())) {
            return new Clan(detectedClan, true);
        }
        String team = Players.team(nick);
        SteinCombat mod = CombatApi.mod;
        boolean fromName = mod == null || mod.config.clanFromPrefix;
        if (team != null) {
            if (team.startsWith(CLAN_TEAM)) {
                // This project's clan plugin.
                return new Clan(tag(team.substring(CLAN_TEAM.length())), true);
            }
            if (fromName) {
                String prefix = bracketTag(Players.prefix(nick), nick);
                if (prefix != null) {
                    // Another plugin, through the team prefix.
                    return new Clan(prefix, false);
                }
                String suffix = bracketTag(Players.suffix(nick), nick);
                if (suffix != null) {
                    return new Clan(suffix, false);
                }
            }
        }
        // Check nametag display name, tab name, suffix, and prefix
        String named = fromName ? bracketTag(Players.displayName(nick), nick) : null;
        if (named == null && fromName) {
            named = bracketTag(Players.tabName(nick), nick);
        }
        if (named == null && fromName) {
            named = bracketTag(Players.suffix(nick), nick);
        }
        if (named == null && fromName) {
            named = bracketTag(Players.prefix(nick), nick);
        }
        if (named == null && team != null && !isDuelTag(team)) {
            String teamTag = tag(team);
            if (teamTag != null) {
                return new Clan(teamTag, true);
            }
        }
        return named == null ? Clan.NONE : new Clan(named, false);
    }

    /**
     * A typed clan tag ("abc" or "[ABC]") in upper case, or null if it is not a tag. Supports clan
     * tags of 2 to 6 characters (e.g. YNY, YNYY, FX, TITAN).
     */
    static String tag(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        while ((t.startsWith("[") && t.endsWith("]"))
                || (t.startsWith("(") && t.endsWith(")"))
                || (t.startsWith("{") && t.endsWith("}"))
                || (t.startsWith("<") && t.endsWith(">"))
                || (t.startsWith("«") && t.endsWith("»"))) {
            if (t.length() < 2) return null;
            t = t.substring(1, t.length() - 1).trim();
        }
        if (isIgnoredTag(t)) {
            return null;
        }
        return t.matches("[A-Za-z0-9_.-]{2,6}") ? t.toUpperCase(Locale.ROOT) : null;
    }

    static boolean isDuelTag(String t) {
        if (t == null) return false;
        String u = t.toUpperCase(Locale.ROOT);
        String compact = u.replaceAll("[^A-Z0-9]", "");
        return compact.equals("X1")
                || compact.equals("1V1")
                || compact.equals("1X1")
                || compact.equals("1VS1")
                || compact.equals("2V2")
                || compact.equals("2X2")
                || compact.equals("3V3")
                || compact.equals("FILA")
                || compact.equals("X1FILA")
                || compact.equals("FILAX1")
                || compact.equals("QUEUE")
                || compact.equals("DUEL")
                || compact.equals("DUELO")
                || compact.equals("ARENA");
    }

    static boolean isIgnoredTag(String t) {
        if (t == null) return true;
        String u = t.toUpperCase(Locale.ROOT);
        String compact = u.replaceAll("[^A-Z0-9]", "");
        return isDuelTag(t)
                || compact.equals("PVP")
                || compact.equals("AFK")
                || compact.equals("Z")
                || compact.equals("OZ")
                || compact.equals("SONO")
                || compact.equals("DORMINDO")
                || compact.equals("OFF")
                || compact.equals("TAG")
                || compact.equals("MEDALHA")
                || compact.equals("VIP")
                || compact.equals("MOD")
                || compact.equals("PRO")
                || compact.equals("DEV")
                || compact.equals("SUB")
                || compact.equals("GLA")
                || compact.equals("GLD")
                || compact.contains("LUTADOR")
                || compact.contains("GUERREIRO")
                || compact.contains("MATADOR")
                || compact.contains("ASSASSINO")
                || compact.contains("PREDADOR")
                || compact.contains("DESTRUIDOR")
                || compact.contains("EXTERMINADOR")
                || compact.contains("GLADIADOR")
                || compact.contains("COMBATENTE")
                || compact.contains("DUELISTA")
                || compact.contains("CACADOR")
                || compact.contains("MITO")
                || compact.contains("MAGNATA")
                || compact.contains("IMPERADOR")
                || compact.contains("SOBERANO")
                || compact.contains("MESTRE")
                || compact.contains("MONSTRO")
                || compact.contains("BRUTO")
                || compact.contains("HEROI")
                || compact.contains("CAMPEAO")
                || compact.contains("LENDARIO")
                || compact.contains("STAFF")
                || compact.contains("ADMIN")
                || compact.contains("HELPER")
                || compact.contains("BUILDER");
    }

    /** The bracketed tag of a team prefix ("§b[ABC] " -> "ABC"), or null. */
    static String bracketTag(String prefix) {
        return bracketTag(prefix, null);
    }

    static String bracketTag(String text, String nick) {
        if (text == null) {
            return null;
        }
        String clean = text.replaceAll("§.", "").trim();
        if (clean.isEmpty()) return null;

        char[][] enclosures = {{'[', ']'}, {'(', ')'}, {'{', '}'}, {'<', '>'}, {'«', '»'}};
        for (char[] enc : enclosures) {
            int start = 0;
            while ((start = clean.indexOf(enc[0], start)) != -1) {
                int close = clean.indexOf(enc[1], start + 1);
                if (close == -1) break;
                String candidate = clean.substring(start + 1, close).trim();
                String t = tag(candidate);
                if (t != null) {
                    return t;
                }
                start = close + 1;
            }
        }

        // Also check separators like '|', '•', '▪', '-'
        String[] separators = {"|", "•", "▪", " - ", "—"};
        for (String sep : separators) {
            int idx = clean.indexOf(sep);
            if (idx != -1) {
                String part1 = clean.substring(0, idx).trim();
                String part2 = clean.substring(idx + sep.length()).trim();
                String t1 = tag(part1);
                if (t1 != null) return t1;
                String t2 = tag(part2);
                if (t2 != null) return t2;
            }
        }
        return null;
    }

    /** A Minecraft nick: 1 to 16 letters, digits or underscores. */
    static boolean validNick(String nick) {
        return nick != null && nick.matches("[A-Za-z0-9_]{1,16}");
    }
}
