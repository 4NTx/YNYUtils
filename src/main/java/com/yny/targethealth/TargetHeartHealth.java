package com.yny.targethealth;

import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Players;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Leitor de Vida em Tempo Real via Coração (Scoreboard Below-Name & Nametag).
 * Em servidores como CraftLandia, a vida em tempo real fica sincronizada no objetivo
 * Below-Name (coração em cima da cabeça) ou na nametag do jogador.
 */
public final class TargetHeartHealth {

    // Regex para corações na tag/display/sufixo/prefixo:
    // Ex: "❤ 14", "♥ 14", "14 ❤", "14.5 ❤", "❤14", "14/20 ❤", "HP: 14"
    private static final Pattern HEART_AFTER = Pattern.compile("(?:[❤♥♡]|HP|hp)\\s*[:=]?\\s*([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern HEART_BEFORE = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*(?:/\\s*[0-9]+(?:\\.[0-9]+)?)?\\s*[❤♥♡]");

    // Cache de reflexão do Scoreboard
    private static boolean reflectionInit = false;
    private static Method mcGetMethod;
    private static Field worldField;
    private static Method getScoreboardMethod;
    private static Method getObjectiveInDisplaySlotMethod;
    private static Method entityHasObjectiveMethod;
    private static Method getValueFromObjectiveMethod;
    private static Method getScorePointsMethod;

    private TargetHeartHealth() {}

    private static void initReflection() {
        if (reflectionInit) return;
        reflectionInit = true;
        try {
            Class<?> aveClass = null;
            try {
                aveClass = Class.forName("ave");
            } catch (Throwable t) {
                try {
                    aveClass = Class.forName("net.minecraft.client.Minecraft");
                } catch (Throwable ignored) {}
            }
            if (aveClass == null) return;

            try {
                mcGetMethod = aveClass.getMethod("A");
            } catch (Throwable t) {
                try {
                    mcGetMethod = aveClass.getMethod("getMinecraft");
                } catch (Throwable ignored) {}
            }

            try {
                worldField = aveClass.getField("f");
            } catch (Throwable t) {
                try {
                    worldField = aveClass.getField("theWorld");
                } catch (Throwable ignored) {}
            }

            Class<?> worldClass = null;
            try {
                worldClass = Class.forName("bdb");
            } catch (Throwable t) {
                try {
                    worldClass = Class.forName("net.minecraft.client.multiplayer.WorldClient");
                } catch (Throwable ignored) {}
            }
            if (worldClass == null) return;

            try {
                getScoreboardMethod = worldClass.getMethod("Z");
            } catch (Throwable t) {
                try {
                    getScoreboardMethod = worldClass.getMethod("getScoreboard");
                } catch (Throwable ignored) {}
            }

            Class<?> scoreboardClass = null;
            try {
                scoreboardClass = Class.forName("auo");
            } catch (Throwable t) {
                try {
                    scoreboardClass = Class.forName("net.minecraft.scoreboard.Scoreboard");
                } catch (Throwable ignored) {}
            }
            if (scoreboardClass == null) return;

            Class<?> objectiveClass = null;
            try {
                objectiveClass = Class.forName("auk");
            } catch (Throwable t) {
                try {
                    objectiveClass = Class.forName("net.minecraft.scoreboard.ScoreObjective");
                } catch (Throwable ignored) {}
            }

            try {
                getObjectiveInDisplaySlotMethod = scoreboardClass.getMethod("a", int.class);
            } catch (Throwable t) {
                try {
                    getObjectiveInDisplaySlotMethod = scoreboardClass.getMethod("getObjectiveInDisplaySlot", int.class);
                } catch (Throwable ignored) {}
            }

            if (objectiveClass != null) {
                try {
                    entityHasObjectiveMethod = scoreboardClass.getMethod("b", String.class, objectiveClass);
                } catch (Throwable t) {
                    try {
                        entityHasObjectiveMethod = scoreboardClass.getMethod("entityHasObjective", String.class, objectiveClass);
                    } catch (Throwable ignored) {}
                }

                try {
                    getValueFromObjectiveMethod = scoreboardClass.getMethod("c", String.class, objectiveClass);
                } catch (Throwable t) {
                    try {
                        getValueFromObjectiveMethod = scoreboardClass.getMethod("getValueFromObjective", String.class, objectiveClass);
                    } catch (Throwable ignored) {}
                }
            }

            Class<?> scoreClass = null;
            try {
                scoreClass = Class.forName("aum");
            } catch (Throwable t) {
                try {
                    scoreClass = Class.forName("net.minecraft.scoreboard.Score");
                } catch (Throwable ignored) {}
            }
            if (scoreClass != null) {
                try {
                    getScorePointsMethod = scoreClass.getMethod("c");
                } catch (Throwable t) {
                    try {
                        getScorePointsMethod = scoreClass.getMethod("getScorePoints");
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Tenta obter a vida em tempo real do jogador a partir do Scoreboard Below-Name (coração acima da cabeça)
     * ou do texto do nametag/display/sufixo. Retorna null se não houver dados de coração disponíveis.
     */
    public static Float getRealTimeHealth(Object entity, String nick) {
        if (nick == null || nick.isEmpty()) {
            if (entity != null) nick = Entities.name(entity);
        }
        if (nick == null || nick.isEmpty()) return null;

        // 1. Tenta ler o Scoreboard no display slot 2 (BELOW_NAME - onde fica o coração oficial em cima da cabeça)
        Float sbHealth = readBelowNameScoreboard(entity, nick);
        if (sbHealth != null && sbHealth >= 0.0F) {
            return sbHealth;
        }

        // 2. Tenta extrair coração dos textos da cabeça/nametag/sufixo/displayName
        Float tagHealth = extractHeartFromText(entity, nick);
        if (tagHealth != null && tagHealth >= 0.0F) {
            return tagHealth;
        }

        // 3. Tenta ler o Scoreboard no display slot 0 (PLAYER_LIST / tab) via Players.listScore
        Float listHealth = readListScoreHealth(nick);
        if (listHealth != null && listHealth >= 0.0F) {
            return listHealth;
        }

        return null;
    }

    private static Float readListScoreHealth(String nick) {
        if (nick == null || nick.isEmpty()) return null;
        try {
            String ls = Players.listScore(nick);
            if (ls == null || ls.trim().isEmpty()) {
                ls = Players.listScore(stripColor(nick));
            }
            if (ls != null && !ls.trim().isEmpty()) {
                float val = Float.parseFloat(ls.trim());
                if (val > 0.0F && val <= 40.0F) {
                    return val;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static Float readBelowNameScoreboard(Object entity, String nick) {
        initReflection();
        if (mcGetMethod == null || worldField == null || getScoreboardMethod == null || getObjectiveInDisplaySlotMethod == null) {
            return null;
        }
        try {
            Object mc = mcGetMethod.invoke(null);
            if (mc == null) return null;
            Object world = worldField.get(mc);
            if (world == null) return null;
            Object sb = getScoreboardMethod.invoke(world);
            if (sb == null) return null;

            // Slot 2 = BELOW_NAME no Minecraft (em cima da cabeça do jogador)
            Object belowNameObj = getObjectiveInDisplaySlotMethod.invoke(sb, 2);
            if (belowNameObj == null) return null;

            if (entityHasObjectiveMethod != null && getValueFromObjectiveMethod != null && getScorePointsMethod != null) {
                // Testar possíveis chaves de registro do jogador no scoreboard
                String cleanNick = stripColor(nick);
                String disp = entity != null ? Entities.displayName(entity) : null;
                String cleanDisp = disp != null ? stripColor(disp) : null;

                String[] candidates = new String[] { nick, cleanNick, disp, cleanDisp };
                for (String cand : candidates) {
                    if (cand == null || cand.isEmpty()) continue;
                    Boolean has = (Boolean) entityHasObjectiveMethod.invoke(sb, cand, belowNameObj);
                    if (has != null && has.booleanValue()) {
                        Object score = getValueFromObjectiveMethod.invoke(sb, cand, belowNameObj);
                        if (score != null) {
                            int points = ((Number) getScorePointsMethod.invoke(score)).intValue();
                            return (float) points;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static Float extractHeartFromText(Object entity, String nick) {
        // Checa displayName da entidade
        if (entity != null) {
            try {
                String disp = Entities.displayName(entity);
                Float h = parseHeartValue(disp);
                if (h != null) return h;
            } catch (Throwable ignored) {}
        }

        // Checa prefix/suffix/tabName/displayName
        if (nick != null && !nick.isEmpty()) {
            try {
                String suffix = Players.suffix(nick);
                Float h = parseHeartValue(suffix);
                if (h != null) return h;

                String disp = Players.displayName(nick);
                h = parseHeartValue(disp);
                if (h != null) return h;

                String tab = Players.tabName(nick);
                h = parseHeartValue(tab);
                if (h != null) return h;

                String prefix = Players.prefix(nick);
                h = parseHeartValue(prefix);
                if (h != null) return h;
            } catch (Throwable ignored) {}
        }

        return null;
    }

    public static Float parseHeartValue(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        String clean = stripColor(text);
        if (!clean.contains("❤") && !clean.contains("♥") && !clean.contains("♡") && !clean.toLowerCase().contains("hp")) {
            return null;
        }

        Matcher m1 = HEART_AFTER.matcher(clean);
        if (m1.find()) {
            try {
                return Float.parseFloat(m1.group(1));
            } catch (Throwable ignored) {}
        }

        Matcher m2 = HEART_BEFORE.matcher(clean);
        if (m2.find()) {
            try {
                return Float.parseFloat(m2.group(1));
            } catch (Throwable ignored) {}
        }

        return null;
    }

    private static String stripColor(String input) {
        if (input == null) return "";
        return input.replaceAll("(?i)[§&][0-9a-fk-or]", "").trim();
    }
}
