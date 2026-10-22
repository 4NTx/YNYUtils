package com.yny.utils.modules.clanping;

import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Players;
import dev.xavier.stein.loader.api.Relation;
import dev.xavier.stein.loader.api.Relations;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Formats an enemy ping using the clan supplied by the client's player list and validates clan relations with high-performance caching. */
public final class PingClanName {
    private static final Pattern SUFFIX_PATTERN = Pattern.compile("(?:\\s*[-_]?\\s*(?:[0-9]+[Xx]|[Xx][0-9]+|[+\\-*#]+|[0-9]+))+$");
    private static final Pattern PREFIX_PATTERN = Pattern.compile("^(?:(?:[0-9]+[Xx]|[Xx][0-9]+)\\s*[-_]?\\s*)");
    private static final Pattern DUEL_PATTERN_1 = Pattern.compile("^[0-9]+[XV][0-9]+$");
    private static final Pattern DUEL_PATTERN_2 = Pattern.compile("^[XV][0-9]+$");

    private static volatile String detectedSelfClan;
    private static volatile String leaderServer;
    private static volatile String cachedSelfNick;
    private static volatile String cachedSelfGroup;
    private static volatile long lastSelfGroupCheck;

    private static Class<?> aveClass;
    private static Method mcGetMethod;
    private static Field worldField;
    private static Method getScoreboardMethod;
    private static Method getObjectiveInDisplaySlotMethod;
    private static Method getSortedScoresMethod;
    private static Method getPlayerNameMethod;
    private static Method getTeamMethod;
    private static Method formatPlayerNameMethod;
    private static Class<?> aukClass;
    private static boolean sidebarReflectionInitialized;

    private static final class CacheEntry {
        final boolean value;
        final long expiresAt;
        CacheEntry(boolean value, long expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }
    }

    private static final class GroupCacheEntry {
        final String group;
        final long expiresAt;
        GroupCacheEntry(String group, long expiresAt) {
            this.group = group;
            this.expiresAt = expiresAt;
        }
    }

    private static final Map<String, CacheEntry> SAME_CLAN_CACHE = new ConcurrentHashMap<String, CacheEntry>();
    private static final Map<String, CacheEntry> DUEL_TARGET_CACHE = new ConcurrentHashMap<String, CacheEntry>();
    private static final Map<String, GroupCacheEntry> GROUP_CACHE = new ConcurrentHashMap<String, GroupCacheEntry>();

    private PingClanName() {}

    public static void setDetectedSelfClan(String tag) {
        String clean = cleanTag(tag);
        if (clean != null && isValidClanTag(clean) && !isIgnoredClanTag(clean)) {
            detectedSelfClan = clean;
        } else {
            detectedSelfClan = null;
        }
        clearCaches();
        try {
            Class<?> clansClass = Class.forName("com.yny.utils.team.Clans");
            clansClass.getMethod("setDetectedClan", String.class).invoke(null, detectedSelfClan);
        } catch (Throwable ignored) {}
    }

    public static String getDetectedSelfClan() {
        return detectedSelfClan;
    }

    public static void setLeaderServer(String server) {
        if (server != null && !server.trim().isEmpty()) {
            leaderServer = server.trim();
        }
    }

    public static String getLeaderServer() {
        return leaderServer;
    }

    public static void onDisconnect() {
        leaderServer = null;
        detectedSelfClan = null;
        clearCaches();
    }

    public static void processOutgoingChat(String message) {
        if (message == null) return;
        String trimmed = message.trim();
        if (trimmed.startsWith("/")) {
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (lower.startsWith("/clan entrar ") || lower.startsWith("/clan join ") || lower.startsWith("/clan aceitar ")) {
                String[] parts = trimmed.split("\\s+");
                if (parts.length >= 3) {
                    setDetectedSelfClan(parts[2]);
                }
            } else if (lower.startsWith("/clan criar ") || lower.startsWith("/clan create ")) {
                String[] parts = trimmed.split("\\s+");
                if (parts.length >= 3) {
                    setDetectedSelfClan(parts[2]);
                }
            } else if (lower.equals("/clan sair") || lower.equals("/clan leave") || lower.equals("/clan disband") || lower.equals("/clan fechar")) {
                setDetectedSelfClan(null);
            }
        }
    }

    public static void processIncomingChat(String formattedMsg) {
        if (formattedMsg == null) return;
        String clean = stripColor(formattedMsg).trim();
        if (clean.isEmpty()) return;
        String lower = clean.toLowerCase(Locale.ROOT);

        if (clean.startsWith("[Clans]") || clean.startsWith("[Clan]")) {
            String self = null;
            try {
                self = Players.self();
            } catch (Throwable ignored) {}
            boolean refersToMe = lower.contains("você") || lower.contains("voce")
                    || (self != null && !self.isEmpty() && lower.contains(self.toLowerCase(Locale.ROOT)));
            if (refersToMe) {
                if (lower.contains("entrou no clan") || lower.contains("membro do clan") || lower.contains("bem-vindo ao clan") || lower.contains("clan criado") || lower.contains("entrou para o clan") || lower.contains("joined the clan")) {
                    String[] words = clean.split("\\s+");
                    if (words.length > 0) {
                        String lastWord = words[words.length - 1].replaceAll("[^A-Za-z0-9_.-]", "");
                        if (isValidClanTag(lastWord) && !isIgnoredClanTag(lastWord)) {
                            setDetectedSelfClan(lastWord);
                        }
                    }
                } else if (lower.contains("saiu do clan") || lower.contains("foi expulso") || lower.contains("clan foi desfeito") || lower.contains("left the clan")) {
                    setDetectedSelfClan(null);
                }
            }
        }
    }

    private static void initSidebarReflection() {
        if (sidebarReflectionInitialized) return;
        sidebarReflectionInitialized = true;
        try {
            aveClass = Class.forName("ave");
            mcGetMethod = aveClass.getMethod("A");
            worldField = aveClass.getField("f");
            Class<?> bdbClass = Class.forName("bdb");
            getScoreboardMethod = bdbClass.getMethod("Z");
            Class<?> auoClass = Class.forName("auo");
            getObjectiveInDisplaySlotMethod = auoClass.getMethod("a", int.class);
            aukClass = Class.forName("auk");
            getSortedScoresMethod = auoClass.getMethod("i", aukClass);
            Class<?> aumClass = Class.forName("aum");
            getPlayerNameMethod = aumClass.getMethod("e");
            getTeamMethod = auoClass.getMethod("h", String.class);
            Class<?> aulClass = Class.forName("aul");
            Class<?> auqClass = Class.forName("auq");
            formatPlayerNameMethod = aulClass.getMethod("a", auqClass, String.class);
        } catch (Throwable ignored) {}
    }

    public static String extractClanFromSidebar() {
        initSidebarReflection();
        if (mcGetMethod == null || worldField == null || getScoreboardMethod == null) return null;
        try {
            Object mc = mcGetMethod.invoke(null);
            if (mc == null) return null;
            Object world = worldField.get(mc);
            if (world == null) return null;
            Object sb = getScoreboardMethod.invoke(world);
            if (sb == null) return null;
            Object obj = getObjectiveInDisplaySlotMethod.invoke(sb, 1);
            if (obj == null) return null;
            Collection<?> scores = (Collection<?>) getSortedScoresMethod.invoke(sb, obj);
            if (scores == null || scores.isEmpty()) return null;

            for (Object s : scores) {
                String scoreName = (String) getPlayerNameMethod.invoke(s);
                Object team = getTeamMethod.invoke(sb, scoreName);
                String fullLine = (String) formatPlayerNameMethod.invoke(null, team, scoreName);
                if (fullLine != null) {
                    String clean = fullLine.replaceAll("§.", "").trim();
                    String lower = clean.toLowerCase(Locale.ROOT);
                    if (lower.contains("clan") || lower.contains("clã") || lower.contains("tag")) {
                        int colonIdx = clean.indexOf(':');
                        if (colonIdx != -1) {
                            String after = clean.substring(colonIdx + 1).trim();
                            String tag = extractPrefixBracketTag(after, null);
                            if (tag != null && isValidClanTag(tag) && !isIgnoredClanTag(tag)) {
                                return cleanTag(tag);
                            }
                            String[] words = after.split("\\s+");
                            if (words.length > 0) {
                                String cand = cleanTag(words[0].replaceAll("[^A-Za-z0-9_.-]", ""));
                                if (cand != null && isValidClanTag(cand) && !isIgnoredClanTag(cand)) {
                                    return cand;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static void clearCaches() {
        cachedSelfNick = null;
        cachedSelfGroup = null;
        lastSelfGroupCheck = 0L;
        SAME_CLAN_CACHE.clear();
        DUEL_TARGET_CACHE.clear();
        GROUP_CACHE.clear();
        try {
            Class.forName("com.yny.utils.team.Clans").getMethod("clearCache").invoke(null);
        } catch (Throwable ignored) {}
    }

    public static String stripColor(String text) {
        if (text == null || text.indexOf('§') == -1) return text;
        int len = text.length();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < len) {
                i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String extractAlphanumericUpper(String str) {
        if (str == null) return "";
        StringBuilder sb = new StringBuilder(str.length());
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                sb.append(c);
            } else if (c >= 'a' && c <= 'z') {
                sb.append((char)(c - 32));
            }
        }
        return sb.toString();
    }

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
            group = resolveGroup(entity, nick);
        } catch (RuntimeException ignored) {
            // Keep the nickname when the server's player list is unavailable.
        }
        return format(nick, shown, group);
    }

    public static String resolveGroup(Object entity, String nick) {
        if ((nick == null || nick.isEmpty()) && entity != null) {
            try { nick = Entities.name(entity); } catch (Throwable ignored) {}
        }
        if (nick == null || nick.isEmpty()) return null;
        long now = System.currentTimeMillis();
        GroupCacheEntry cached = GROUP_CACHE.get(nick);
        if (cached != null && now < cached.expiresAt) {
            return cached.group;
        }
        String group = computeResolveGroup(entity, nick);
        if (GROUP_CACHE.size() > 500) GROUP_CACHE.clear();
        GROUP_CACHE.put(nick, new GroupCacheEntry(group, now + (group != null ? 2000L : 150L)));
        return group;
    }

    private static String computeResolveGroup(Object entity, String nick) {
        try {
            if (nick == null && entity != null) {
                try { nick = Entities.name(entity); } catch (Throwable ignored) {}
            }
            if (entity == null && nick != null) {
                try {
                    Object self = Entities.self();
                    if (self != null && nick.equalsIgnoreCase(Entities.name(self))) {
                        entity = self;
                    }
                } catch (Throwable ignored) {}
            }

            // 1. Prefix
            if (nick != null) {
                String prefix = Players.prefix(nick);
                if (prefix != null) {
                    String tag = extractPrefixBracketTag(prefix, nick);
                    if (tag != null && isValidClanTag(tag) && !isIgnoredClanTag(tag)) return cleanTag(tag);
                }
            }

            // 2. Suffix (e.g. "[CLAN]" placed in player suffix by clan plugin)
            if (nick != null) {
                String suffix = Players.suffix(nick);
                if (suffix != null) {
                    String tag = extractPrefixBracketTag(suffix, nick);
                    if (tag != null && isValidClanTag(tag) && !isIgnoredClanTag(tag)) return cleanTag(tag);
                }
            }

            // 3. Tab name (both prefix and suffix in Tab list)
            if (nick != null) {
                String tab = Players.tabName(nick);
                if (tab != null) {
                    String tag = extractPrefixBracketTag(tab, nick);
                    if (tag != null && isValidClanTag(tag) && !isIgnoredClanTag(tag)) return cleanTag(tag);
                }
            }

            // 4. Entity / player display name
            if (entity != null) {
                String shown = Entities.displayName(entity);
                if (shown != null) {
                    String tag = extractPrefixBracketTag(shown, nick);
                    if (tag != null && isValidClanTag(tag) && !isIgnoredClanTag(tag)) return cleanTag(tag);
                }
            } else if (nick != null) {
                String shown = Players.displayName(nick);
                if (shown != null) {
                    String tag = extractPrefixBracketTag(shown, nick);
                    if (tag != null && isValidClanTag(tag) && !isIgnoredClanTag(tag)) return cleanTag(tag);
                }
            }

            // 5. Scoreboard team
            if (nick != null) {
                String team = Players.team(nick);
                if (team != null && !isDuelTarget(entity, nick)) {
                    if (team.startsWith("sc_")) {
                        String clean = cleanTag(team.substring(3));
                        if (clean != null && isValidClanTag(clean) && !isIgnoredClanTag(clean)) return clean;
                    }
                    String tag = extractPrefixBracketTag(team, nick);
                    if (tag != null && isValidClanTag(tag) && !isIgnoredClanTag(tag)) return cleanTag(tag);
                    String clean = cleanTag(team);
                    if (clean != null && isValidClanTag(clean) && !isIgnoredClanTag(clean)) return clean;
                }
            }

            // 6. Loader group resolver
            if (nick != null) {
                String group = Players.group(nick);
                if (group != null && !group.trim().isEmpty()) {
                    String clean = cleanTag(group);
                    if (clean != null && isValidClanTag(clean) && !isIgnoredClanTag(clean)) return clean;
                }
            }

            // 7. Team Clans fallback
            if (nick != null) {
                try {
                    java.lang.reflect.Method m = Class.forName("com.yny.utils.team.Clans").getMethod("of", String.class);
                    Object c = m.invoke(null, nick);
                    if (c instanceof String) {
                        String clean = cleanTag((String) c);
                        if (clean != null && isValidClanTag(clean) && !isIgnoredClanTag(clean)) return clean;
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static boolean isSameClan(Object entity, String nick) {
        String lookupNick = (nick != null && !nick.isEmpty()) ? nick : (entity != null ? Entities.name(entity) : null);
        long now = System.currentTimeMillis();

        if (lookupNick != null) {
            CacheEntry cached = SAME_CLAN_CACHE.get(lookupNick);
            if (cached != null && now < cached.expiresAt) {
                return cached.value;
            }
        }

        boolean result = computeIsSameClan(entity, lookupNick, now);

        if (lookupNick != null) {
            if (SAME_CLAN_CACHE.size() > 500) SAME_CLAN_CACHE.clear();
            SAME_CLAN_CACHE.put(lookupNick, new CacheEntry(result, now + (result ? 2000L : 150L)));
        }
        return result;
    }

    private static boolean computeIsSameClan(Object entity, String nick, long now) {
        if (isDuelTarget(entity, nick)) return false;
        try {
            String selfNick = cachedSelfNick;
            if (selfNick == null || now - lastSelfGroupCheck > 1000L) {
                selfNick = Players.self();
                cachedSelfNick = selfNick;
                Object selfEnt = Entities.self();

                String newSelfGroup = (selfNick == null || selfNick.isEmpty()) ? null : resolveGroup(selfEnt, selfNick);
                if (newSelfGroup == null) {
                    newSelfGroup = extractClanFromSidebar();
                }
                if (newSelfGroup == null) {
                    newSelfGroup = detectedSelfClan;
                }
                if (newSelfGroup == null) {
                    try {
                        Object m = Class.forName("com.yny.utils.team.Clans").getMethod("mine").invoke(null);
                        if (m instanceof String) newSelfGroup = cleanTag((String) m);
                    } catch (Throwable ignored) {}
                }
                if (newSelfGroup != null && (detectedSelfClan == null || !detectedSelfClan.equalsIgnoreCase(newSelfGroup))) {
                    setDetectedSelfClan(newSelfGroup);
                }

                if (newSelfGroup != null) {
                    boolean groupChanged = (cachedSelfGroup == null) || (!cachedSelfGroup.equalsIgnoreCase(newSelfGroup));
                    if (groupChanged) {
                        SAME_CLAN_CACHE.clear();
                        GROUP_CACHE.clear();
                        try {
                            Class.forName("com.yny.utils.team.Clans").getMethod("clearCache").invoke(null);
                        } catch (Throwable ignored) {}
                    }
                    cachedSelfGroup = newSelfGroup;
                }
                lastSelfGroupCheck = now;
            }

            if (selfNick == null || selfNick.isEmpty()) return false;
            if (nick != null && selfNick.equalsIgnoreCase(nick)) return true;

            // 1. Direct SteinLoader relation check (always check GROUP, ALLY, and ENEMY first!)
            if (entity != null) {
                Relation rel = Relations.of(entity);
                if (rel == Relation.GROUP || rel == Relation.ALLY) return true;
                if (rel == Relation.ENEMY) return false;
            }
            if (nick != null) {
                Relation rel = Relations.of(nick);
                if (rel == Relation.GROUP || rel == Relation.ALLY) return true;
                if (rel == Relation.ENEMY) return false;
            }

            // 2. Scoreboard team match (same non-empty team and not duel)
            if (nick != null) {
                String selfTeam = Players.team(selfNick);
                String targetTeam = Players.team(nick);
                if (selfTeam != null && !selfTeam.isEmpty() && targetTeam != null && !targetTeam.isEmpty()) {
                    if (selfTeam.equalsIgnoreCase(targetTeam) && !isDuelOrQueueTagOnly(selfTeam)) {
                        return true;
                    }
                }
            }

            // 3. Clan tag matching from prefix, suffix, display name, tab, or group resolver
            String selfGroup = cachedSelfGroup != null ? cachedSelfGroup : detectedSelfClan;
            if (selfGroup == null) {
                try {
                    Object m = Class.forName("com.yny.utils.team.Clans").getMethod("mine").invoke(null);
                    if (m instanceof String) selfGroup = cleanTag((String) m);
                } catch (Throwable ignored) {}
            }
            String targetGroup = resolveGroup(entity, nick);
            if (targetGroup == null && nick != null) {
                try {
                    String tag = (String) Class.forName("com.yny.utils.team.Clans").getMethod("of", String.class).invoke(null, nick);
                    if (tag != null) targetGroup = cleanTag(tag);
                } catch (Throwable ignored) {}
            }

            boolean hasSelfGroup = selfGroup != null && !selfGroup.isEmpty() && isValidClanTag(selfGroup) && !isIgnoredClanTag(selfGroup);
            boolean hasTargetGroup = targetGroup != null && !targetGroup.isEmpty() && isValidClanTag(targetGroup) && !isIgnoredClanTag(targetGroup);

            if (hasSelfGroup && hasTargetGroup) {
                return selfGroup.equalsIgnoreCase(targetGroup);
            }

            // If one has a clan tag and the other has a different clan tag, they are not same clan
            if (hasSelfGroup != hasTargetGroup) {
                return false;
            }

            // Neutral players without matching clan tag or explicit group/ally relation are NOT same clan
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String cleanTag(String tag) {
        if (tag == null) return null;
        String cleaned = tag.replace("[", "").replace("]", "").replace("(", "").replace(")", "").replace("{", "").replace("}", "").replace("<", "").replace(">", "").replace("«", "").replace("»", "").trim();
        while (cleaned.endsWith(".") || cleaned.endsWith("!") || cleaned.endsWith(",") || cleaned.endsWith(":") || cleaned.endsWith(";")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        return cleaned.isEmpty() ? null : cleaned.toUpperCase(Locale.ROOT);
    }

    public static String format(String nick, String shown, String group) {
        String name = shown == null || shown.trim().isEmpty() ? nick : shown;
        if (name == null || name.trim().isEmpty()) name = "Alvo";
        if (group == null || group.trim().isEmpty() || isIgnoredClanTag(group)) return name;
        String tag = group.replace("[", "").replace("]", "").replace("(", "").replace(")", "").trim();
        if (tag.isEmpty() || isIgnoredClanTag(tag)) return name;
        if (hasBracketTag(name, tag)) return name;
        return "§7[" + tag + "] §f" + name;
    }

    public static boolean hasBracketTag(String text, String tag) {
        if (text == null || tag == null || tag.trim().isEmpty()) return false;
        if (tag.equalsIgnoreCase(Players.bracketTag(text))) return true;
        String lowerTag = tag.trim().toLowerCase();
        String clean = stripColor(text).toLowerCase();
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

    public static boolean isValidClanTag(String tag) {
        if (tag == null) return false;
        String clean = stripColor(tag.replace("[", "").replace("]", "").replace("(", "").replace(")", "")).trim();
        if (clean.length() < 2 || clean.length() > 6) return false;
        return isValidClanTagChars(clean);
    }

    private static boolean isValidClanTagChars(String clean) {
        int len = clean.length();
        for (int i = 0; i < len; i++) {
            char c = clean.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.')) {
                return false;
            }
        }
        return true;
    }

    public static String extractBracketTag(String text) {
        return extractPrefixBracketTag(text, null);
    }

    public static String extractPrefixBracketTag(String text, String nick) {
        if (text == null) return null;
        String fullClean = stripColor(text).trim();
        if (fullClean.isEmpty()) return null;

        // 1. If nick is present, first try extracting from the prefix before nick
        if (nick != null && !nick.isEmpty()) {
            int nickIdx = fullClean.toLowerCase(java.util.Locale.ROOT).indexOf(nick.toLowerCase(java.util.Locale.ROOT));
            if (nickIdx != -1) {
                String prefixPart = fullClean.substring(0, nickIdx).trim();
                String prefixTag = extractEnclosureTag(prefixPart);
                if (prefixTag != null) return prefixTag;

                // 2. If no valid clan tag before nick, check suffix after nick (e.g. "Nick [CLAN]")
                String suffixPart = fullClean.substring(nickIdx + nick.length()).trim();
                String suffixTag = extractEnclosureTag(suffixPart, true);
                if (suffixTag != null) return suffixTag;
            }
        }

        // 3. Otherwise extract from the full text
        return extractEnclosureTag(fullClean, false);
    }

    private static String extractEnclosureTag(String clean) {
        return extractEnclosureTag(clean, false);
    }

    private static String extractEnclosureTag(String clean, boolean isSuffix) {
        if (clean == null || clean.isEmpty()) return null;
        char[][] pairs = {{'[', ']'}, {'(', ')'}, {'{', '}'}, {'<', '>'}, {'«', '»'}};
        for (char[] pair : pairs) {
            int start = 0;
            while ((start = clean.indexOf(pair[0], start)) != -1) {
                int end = clean.indexOf(pair[1], start + 1);
                if (end == -1) break;
                String inside = clean.substring(start + 1, end).trim();
                if (!inside.isEmpty() && isValidClanTag(inside)) {
                    if (isSuffix) {
                        if (!isDuelOrQueueTagOnly(inside)) return inside;
                    } else {
                        if (!isIgnoredClanTag(inside)) return inside;
                    }
                }
                start = end + 1;
            }
        }

        // Check delimiters: '|', '•', '▪', '-', '—'
        String[] seps = {"|", "•", "▪", " - ", "—"};
        for (String sep : seps) {
            int idx = clean.indexOf(sep);
            if (idx != -1) {
                String p1 = clean.substring(0, idx).trim();
                String p2 = clean.substring(idx + sep.length()).trim();
                if (isValidClanTag(p1) && (isSuffix ? !isDuelOrQueueTagOnly(p1) : !isIgnoredClanTag(p1))) return p1;
                if (isValidClanTag(p2) && (isSuffix ? !isDuelOrQueueTagOnly(p2) : !isIgnoredClanTag(p2))) return p2;
            }
        }
        return null;
    }

    public static boolean isDuelTarget(Object entity) {
        return isDuelTarget(entity, null);
    }

    public static boolean isDuelTarget(Object entity, String nick) {
        String lookupNick = null;
        try {
            lookupNick = (nick != null && !nick.isEmpty()) ? nick : (entity != null ? Entities.name(entity) : null);
        } catch (Throwable ignored) {
            return false;
        }
        long now = System.currentTimeMillis();

        if (lookupNick != null) {
            CacheEntry cached = DUEL_TARGET_CACHE.get(lookupNick);
            if (cached != null && now < cached.expiresAt) {
                return cached.value;
            }
        }

        boolean result = computeIsDuelTarget(entity, lookupNick);

        if (lookupNick != null) {
            if (DUEL_TARGET_CACHE.size() > 500) DUEL_TARGET_CACHE.clear();
            DUEL_TARGET_CACHE.put(lookupNick, new CacheEntry(result, now + 1000L));
        }
        return result;
    }

    private static boolean computeIsDuelTarget(Object entity, String nick) {
        if (entity != null) {
            try {
                if (nick == null || nick.isEmpty()) {
                    nick = Entities.name(entity);
                }
                String shown = Entities.displayName(entity);
                if (hasDuelOrQueueTag(shown)) return true;
            } catch (Throwable ignored) {}
        }
        if (nick != null && !nick.isEmpty()) {
            if (hasDuelOrQueueTag(nick)) return true;
            try {
                String prefix = Players.prefix(nick);
                if (hasDuelOrQueueTag(prefix)) return true;
                String suffix = Players.suffix(nick);
                if (hasDuelOrQueueTag(suffix)) return true;
                String tab = Players.tabName(nick);
                if (hasDuelOrQueueTag(tab)) return true;
                String team = Players.team(nick);
                if (team != null && (isDuelOrQueueTagOnly(team) || (team.startsWith("sc_") && isDuelOrQueueTagOnly(team.substring(3))))) return true;
            } catch (Throwable ignored) {}
        }
        return false;
    }

    public static boolean hasDuelOrQueueTag(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        String clean = stripColor(text).trim();
        int start = 0;
        while ((start = clean.indexOf('[', start)) != -1) {
            int end = clean.indexOf(']', start + 1);
            if (end == -1) break;
            String inside = clean.substring(start + 1, end).trim();
            if (isDuelOrQueueTagOnly(inside)) return true;
            start = end + 1;
        }
        start = 0;
        while ((start = clean.indexOf('(', start)) != -1) {
            int end = clean.indexOf(')', start + 1);
            if (end == -1) break;
            String inside = clean.substring(start + 1, end).trim();
            if (isDuelOrQueueTagOnly(inside)) return true;
            start = end + 1;
        }
        return isDuelOrQueueTagOnly(clean);
    }

    public static boolean isDuelOrQueueTagOnly(String tag) {
        if (tag == null || tag.trim().isEmpty()) return false;
        String clean = stripColor(tag.replace("[", "").replace("]", "").replace("(", "").replace(")", "")).trim();
        if (clean.isEmpty()) return false;
        String upper = clean.toUpperCase(java.util.Locale.ROOT);
        String compact = extractAlphanumericUpper(upper);
        if (compact.equals("X1") || compact.equals("1X1") || compact.equals("1V1") || compact.equals("1VS1")
                || compact.equals("2V2") || compact.equals("2X2") || compact.equals("X2")
                || compact.equals("3V3") || compact.equals("3X3") || compact.equals("X3")
                || compact.equals("4V4") || compact.equals("4X4") || compact.equals("X4")
                || compact.equals("5V5") || compact.equals("5X5") || compact.equals("X5")
                || compact.equals("X1FILA") || compact.equals("FILAX1")
                || DUEL_PATTERN_1.matcher(compact).matches() || DUEL_PATTERN_2.matcher(compact).matches()) {
            return true;
        }
        if (upper.equals("DUEL") || upper.equals("DUELO")) {
            return true;
        }
        if (upper.startsWith("X1 ") || upper.startsWith("1V1 ")) {
            return true;
        }
        return false;
    }

    private static final String[] TITLE_ROOTS = {
        "LUTADOR", "GUERREIRO", "MATADOR", "ASSASSINO", "PREDADOR",
        "DESTRUIDOR", "EXTERMINADOR", "GLADIADOR", "COMBATENTE", "DUELISTA",
        "CACADOR", "CAÇADOR", "IMPERADOR", "SOBERANO", "CENTURIAO", "CENTURIÃO",
        "LENDARIO", "LENDÁRIO", "VENCEDOR", "FINALISTA", "DESAFIANTE",
        "YOUTUBER", "STREAMER", "MODERADOR", "ADMINISTRADOR", "DESENVOLVEDOR",
        "COORDENADOR", "MAGNATA"
    };

    public static boolean isIgnoredClanTag(String tag) {
        if (tag == null || tag.trim().isEmpty()) return true;
        String clean = stripColor(tag.replace("[", "").replace("]", "").replace("(", "").replace(")", "")).trim();
        if (clean.length() < 2 || clean.length() > 6) return true;
        String upper = clean.toUpperCase(java.util.Locale.ROOT);
        String compact = extractAlphanumericUpper(upper);
        if (compact.isEmpty()) return true;

        if (compact.equals("Z") || compact.equals("OZ") || compact.equals("SONO") || compact.equals("AFK")
                || compact.equals("DORMINDO") || compact.equals("SLEEP") || compact.equals("OFF")
                || compact.equals("MEDALHA") || compact.equals("COIN") || compact.equals("TAG")
                || compact.equals("FILA") || compact.equals("QUEUE") || compact.equals("PVP")
                || compact.equals("GLAD") || compact.equals("GLAC") || compact.equals("GLADIADOR")
                || compact.equals("ARENA") || compact.equals("EVENTO") || compact.equals("SPEC")
                || compact.equals("SPECTATOR") || compact.equals("MITO") || compact.equals("MAGNATA")
                || compact.equals("NONE") || compact.equals("NULL")
                || upper.contains("SEM CLAN") || upper.contains("SEM CLÃ") || compact.equals("SEMCLAN")) {
            return true;
        }

        return isDuelOrQueueTagOnly(tag) || isKnownRank(tag);
    }

    static boolean isKnownRank(String tag) {
        if (tag == null || tag.trim().isEmpty()) return false;
        String clean = stripColor(tag.replace("[", "").replace("]", "").replace("(", "").replace(")", "")).trim();
        if (clean.isEmpty()) return false;
        String upper = clean.toUpperCase(java.util.Locale.ROOT);
        String compact = extractAlphanumericUpper(upper);

        if (isRankWord(upper) || isRankWord(compact)) return true;

        for (String root : TITLE_ROOTS) {
            if (compact.contains(root) || upper.contains(root)) {
                return true;
            }
        }

        // Multiplier suffixes: "2X", " 2X", "-2X", "_2X", "X2", "+", "++", "*", "#", or trailing digits
        String baseSuffix = SUFFIX_PATTERN.matcher(upper).replaceAll("").trim();
        if (!baseSuffix.isEmpty() && (isRankWord(baseSuffix) || isRankWord(extractAlphanumericUpper(baseSuffix)))) {
            return true;
        }

        // Multiplier prefix: "2X-", "2X ", "2X_"
        String basePrefix = PREFIX_PATTERN.matcher(upper).replaceAll("").trim();
        if (!basePrefix.isEmpty() && (isRankWord(basePrefix) || isRankWord(extractAlphanumericUpper(basePrefix)))) {
            return true;
        }

        return false;
    }

    private static boolean isRankWord(String word) {
        if (word == null || word.isEmpty()) return false;
        String w = word.toUpperCase(java.util.Locale.ROOT).trim();
        return w.equals("VIP") || w.equals("MVP") || w.equals("PRO")
                || w.equals("MOD") || w.equals("ADMIN") || w.equals("HELPER")
                || w.equals("DONO") || w.equals("MASTER") || w.equals("ULTRA")
                || w.equals("SUPREMO") || w.equals("HEROI") || w.equals("HERÓI") || w.equals("HERO")
                || w.equals("CAMPEAO") || w.equals("CAMPEÃO") || w.equals("CHAMPION")
                || w.equals("LENDARIO") || w.equals("LENDÁRIO") || w.equals("LENDA")
                || w.equals("MEMBRO") || w.equals("BUILDER") || w.equals("CONSTRUTOR")
                || w.equals("SUB") || w.equals("YT") || w.equals("YOUTUBER") || w.equals("STREAMER")
                || w.equals("STAFF") || w.equals("TRIAL") || w.equals("COORD")
                || w.equals("GERENTE") || w.equals("DIRETOR") || w.equals("DEV")
                || w.equals("DEVELOPER") || w.equals("ELITE")
                || w.equals("SOCIO") || w.equals("SÓCIO") || w.equals("SUBDONO") || w.equals("GERENCIA")
                || w.equals("LUTADOR") || w.equals("GUERREIRO") || w.equals("MATADOR")
                || w.equals("ASSASSINO") || w.equals("PREDADOR") || w.equals("DESTRUIDOR")
                || w.equals("EXTERMINADOR") || w.equals("GLADIADOR") || w.equals("GLAD") || w.equals("GLAC")
                || w.equals("COMBATENTE") || w.equals("DUELISTA") || w.equals("CACADOR") || w.equals("CAÇADOR")
                || w.equals("MITO") || w.equals("MAGNATA") || w.equals("IMPERADOR") || w.equals("SOBERANO")
                || w.equals("MESTRE")
                || w.equals("VENCEDOR") || w.equals("FINALISTA") || w.equals("DESAFIANTE")
                || w.equals("PLAYER") || w.equals("JOGADOR") || w.equals("USER") || w.equals("DEFAULT");
    }
}
