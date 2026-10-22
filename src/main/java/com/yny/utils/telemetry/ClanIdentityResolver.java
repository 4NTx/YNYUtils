package com.yny.utils.telemetry;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.yny.utils.modules.clanping.PingClanName;
import dev.xavier.stein.loader.api.Players;
import net.minecraft.client.Minecraft;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;

/** Reads the local player's clan after server scoreboard and tab data arrive. */
public final class ClanIdentityResolver {
    private static final Pattern LABEL = Pattern.compile("(?i)(?:clã|cla|clan)\\s*[:»|=-]\\s*\\[?([\\p{L}0-9_]{2,16})\\]?");
    private static final Pattern BRACKET = Pattern.compile("\\[([^]\\r\\n]{2,16})\\]");

    private ClanIdentityResolver() { }

    public static String resolve(String nick) {
        if (nick == null || nick.isEmpty()) return null;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.theWorld != null) {
                Scoreboard board = mc.theWorld.getScoreboard();
                if (board != null) {
                    ScoreObjective sidebar = board.getObjectiveInDisplaySlot(1);
                    if (sidebar != null) {
                        for (Score score : board.getSortedScores(sidebar)) {
                            String name = score.getPlayerName();
                            ScorePlayerTeam lineTeam = board.getPlayersTeam(name);
                            String line = lineTeam == null ? name : lineTeam.formatString(name);
                            String clan = clanFromLabel(line);
                            if (valid(clan)) return clan;
                        }
                    }
                    try {
                        String group = clean(PingClanName.resolveGroup(null, nick));
                        if (valid(group)) return group;
                    } catch (Throwable ignored) { }
                    ScorePlayerTeam ownTeam = board.getPlayersTeam(nick);
                    if (ownTeam != null) {
                        String clan = bracket(ownTeam.getColorPrefix());
                        if (valid(clan)) return clan;
                        clan = bracket(ownTeam.getTeamName());
                        if (valid(clan)) return clan;
                        clan = clean(ownTeam.getRegisteredName());
                        if (valid(clan)) return clan;
                    }
                }
            }
        } catch (Throwable ignored) { }
        try {
            String group = clean(PingClanName.resolveGroup(null, nick));
            if (valid(group)) return group;
        } catch (Throwable ignored) { }
        try {
            String team = clean(Players.team(nick));
            if (valid(team)) return team;
        } catch (Throwable ignored) { }
        return null;
    }

    public static String clanFromLabel(String line) {
        if (line == null) return null;
        Matcher match = LABEL.matcher(stripColors(line));
        return match.find() ? clean(match.group(1)) : null;
    }

    private static String bracket(String value) {
        if (value == null) return null;
        Matcher match = BRACKET.matcher(stripColors(value));
        return match.find() ? clean(match.group(1)) : null;
    }

    private static String clean(String value) {
        if (value == null) return null;
        String clean = stripColors(value).trim();
        if (clean.startsWith("[") && clean.endsWith("]")) clean = clean.substring(1, clean.length() - 1).trim();
        return clean.length() > 16 ? null : clean;
    }

    private static String stripColors(String value) { return value.replaceAll("§.", ""); }

    private static boolean valid(String value) {
        return value != null && value.length() >= 2 && value.length() <= 16
                && !value.equalsIgnoreCase("none") && !value.equalsIgnoreCase("null")
                && !value.equalsIgnoreCase("sem clan") && !value.equalsIgnoreCase("sem clã")
                && !value.matches("(?i)(VIP|MVP|ADMIN|MOD|HELPER|MEMBRO|STAFF|PRO|DONO|YT|YOUTUBER)[+0-9]*")
                && !PingClanName.isIgnoredClanTag(value);
    }
}


