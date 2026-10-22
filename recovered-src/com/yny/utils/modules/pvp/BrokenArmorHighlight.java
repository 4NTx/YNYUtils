package com.yny.utils.modules.pvp;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.yny.utils.YNYUtils;
import com.yny.utils.config.YNYConfig;
import com.yny.utils.core.DebugLog;
import com.yny.utils.modules.clanping.PingClanName;

import dev.xavier.stein.loader.api.Chat;
import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Relation;
import dev.xavier.stein.loader.api.Relations;
import dev.xavier.stein.loader.api.WorldRender;

/**
 * Módulo de alta visibilidade e ZERO LAG para detectar e destacar jogadores inimigos
 * que estão com partes da armadura quebradas ou não equipadas (ex: sem elmo, sem bota).
 *
 * Filtro estrito: NUNCA destaca membros do mesmo clã ou aliados ("SÓ quem n for do mesmo clan").
 * Arquitetura desacoplada: Avaliações pesadas e checagens ocorrem no onTickEnd (20 TPS),
 * gerando um snapshot pré-calculado para que o onRenderWorld (144+ FPS) execute com ZERO LAG.
 */
public final class BrokenArmorHighlight {

    private static Method armorItemMethod;
    private static boolean methodResolved;

    // Snapshot pré-calculado no onTickEnd (20 TPS) para renderização com ZERO LAG a 144+ FPS
    private static volatile List<TargetSnapshot> cachedTargets = Collections.emptyList();

    // Rastreamento para alertas na Action Bar (apenas ao quebrar)
    private static final Map<String, Integer> lastArmorMask = new HashMap<>();
    private static final Map<String, Long> lastAlertTime = new HashMap<>();
    private static long tickCounter = 0L;

    private BrokenArmorHighlight() {}

    /**
     * Renderização no mundo 3D com ZERO LAG (0 reflexões por frame, 0 alocações de string).
     */
    public static void onRenderWorld(float partialTicks) {
        YNYConfig config = YNYUtils.getConfig();
        if (config == null || !config.brokenArmorHighlight) {
            return;
        }

        List<TargetSnapshot> targets = cachedTargets;
        if (targets == null || targets.isEmpty()) {
            return;
        }

        Object self = Entities.self();
        if (self == null) return;

        double selfX = Entities.x(self, partialTicks);
        double selfY = Entities.y(self, partialTicks);
        double selfZ = Entities.z(self, partialTicks);

        // Pulsação suave calculada 1 única vez por frame ("brilhando mais que os outros")
        long now = System.currentTimeMillis();
        float pulse = (float) (0.82f + 0.18f * Math.sin(now / 130.0D));

        boolean throughWalls = config.brokenArmorThroughWalls;
        boolean drawTags = config.brokenArmorTags;

        Runnable drawAction = () -> {
            for (int i = 0; i < targets.size(); i++) {
                TargetSnapshot t = targets.get(i);
                Object p = t.entity;
                if (p == null || !Entities.isAlive(p)) continue;

                int outlineColor = applyPulse(t.outlineColor, pulse);

                // Aura translúcida e contorno destacado espesso (2.8D)
                WorldRender.entityBox(p, t.boxColor);
                WorldRender.entityOutline(p, 2.8D, outlineColor);

                if (drawTags && t.tag != null) {
                    double px = Entities.x(p, partialTicks);
                    double py = Entities.y(p, partialTicks);
                    double pz = Entities.z(p, partialTicks);

                    double dx = px - selfX;
                    double dy = py - selfY;
                    double dz = pz - selfZ;
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

                    // Escala adaptativa para manter o texto 3D nítido e altamente visível de perto e de longe
                    float tagScale = (float) Math.max(0.85F, Math.min(2.4F, 0.75F + dist * 0.040F));
                    WorldRender.label(t.tag, px, py + 2.25D, pz, tagScale, 0xFFFFFFFF, true);
                }
            }
        };

        if (throughWalls) {
            WorldRender.throughWalls(drawAction);
        } else {
            drawAction.run();
        }
    }

    /**
     * Avaliação de estado executada no ritmo de tick (20 TPS).
     * Faz todas as checagens de clã, armadura e reflexão de forma assíncrona ao renderizador.
     */
    public static void onTickEnd() {
        YNYConfig config = YNYUtils.getConfig();
        if (config == null || !config.brokenArmorHighlight) {
            if (!cachedTargets.isEmpty()) {
                cachedTargets = Collections.emptyList();
            }
            lastArmorMask.clear();
            return;
        }

        Object self = Entities.self();
        if (self == null) {
            if (!cachedTargets.isEmpty()) {
                cachedTargets = Collections.emptyList();
            }
            return;
        }

        double maxDistSq = (double) config.brokenArmorMaxDistance * (double) config.brokenArmorMaxDistance;
        List<TargetSnapshot> newTargets = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (Object player : Entities.players()) {
            if (player == null || player == self) continue;
            if (!Entities.isAlive(player)) continue;
            if (MobKnockbackFilter.isArmorStand(player)) continue;

            String name = Entities.name(player);

            // REGRA ESTRITA: SÓ QUEM NÃO FOR DO MESMO CLÃ (E NÃO FOR ALIADO)
            if (config.brokenArmorOnlyEnemies && isSameClanOrAlly(player, name)) {
                continue; // Membro do clã ou aliado -> NUNCA destacar!
            }

            // Distância máxima (cálculo de distSq para zero custo de Math.sqrt no tick)
            double dist = Entities.distance(self, player);
            if ((dist * dist) > maxDistSq) continue;

            // Leitura de armadura (0 = Bota, 1 = Calça, 2 = Peitoral, 3 = Elmo)
            Object boots = getArmorItem(player, 0);
            Object legs = getArmorItem(player, 1);
            Object chest = getArmorItem(player, 2);
            Object helm = getArmorItem(player, 3);

            // Ignorar staff invisível no vanish sem armadura
            if (boots == null && legs == null && chest == null && helm == null) {
                try {
                    if (Entities.isInvisible(player)) continue;
                } catch (Throwable ignored) {}
            }

            ArmorStatus status = evaluateArmor(boots, legs, chest, helm);
            if (!status.hasMissingPiece) {
                // Armadura completa
                int fullMask = 15;
                if (name != null) lastArmorMask.put(name, fullMask);
                continue;
            }

            // Adiciona ao snapshot que será renderizado a 144+ FPS com zero overhead
            newTargets.add(new TargetSnapshot(player, status.outlineColor, status.boxColor, status.tag));

            // Alerta instantâneo na Action Bar no segundo exato em que a peça quebra
            if (config.brokenArmorAlertChat && name != null && !name.isEmpty()) {
                int currentMask = (boots != null ? 1 : 0)
                        | (legs != null ? 2 : 0)
                        | (chest != null ? 4 : 0)
                        | (helm != null ? 8 : 0);

                Integer prevMask = lastArmorMask.get(name);
                if (prevMask != null && prevMask != currentMask) {
                    Long lastAlert = lastAlertTime.get(name);
                    boolean canAlert = (lastAlert == null || now - lastAlert > 3500L);

                    if (canAlert) {
                        boolean lostHelm = (prevMask & 8) != 0 && (currentMask & 8) == 0;
                        boolean lostBoots = (prevMask & 1) != 0 && (currentMask & 1) == 0;
                        boolean lostChest = (prevMask & 4) != 0 && (currentMask & 4) == 0;
                        boolean lostLegs = (prevMask & 2) != 0 && (currentMask & 2) == 0;

                        if (lostHelm && lostBoots) {
                            Chat.actionBar("§c§l⚔ " + name + " §eperdeu §c§lELMO & BOTA§e! Foque nele!");
                            DebugLog.write("BrokenArmor", "Inimigo " + name + " quebrou ELMO e BOTA!");
                            lastAlertTime.put(name, now);
                        } else if (lostHelm) {
                            Chat.actionBar("§c§l⚔ " + name + " §eestá §c§lSEM ELMO§e! Foque nele!");
                            DebugLog.write("BrokenArmor", "Inimigo " + name + " quebrou o ELMO!");
                            lastAlertTime.put(name, now);
                        } else if (lostBoots) {
                            Chat.actionBar("§c§l⚔ " + name + " §eestá §6§lSEM BOTA§e! Foque nele!");
                            DebugLog.write("BrokenArmor", "Inimigo " + name + " quebrou a BOTA!");
                            lastAlertTime.put(name, now);
                        } else if (lostChest) {
                            Chat.actionBar("§4§l⚔ " + name + " §cperdeu o PEITORAL! Alvo vulnerável!");
                            DebugLog.write("BrokenArmor", "Inimigo " + name + " quebrou o PEITORAL!");
                            lastAlertTime.put(name, now);
                        } else if (lostLegs) {
                            Chat.actionBar("§c§l⚔ " + name + " §cperdeu a CALÇA!");
                            DebugLog.write("BrokenArmor", "Inimigo " + name + " quebrou a CALÇA!");
                            lastAlertTime.put(name, now);
                        }
                    }
                }
                lastArmorMask.put(name, currentMask);
            }
        }

        cachedTargets = newTargets;

        // Limpeza periódica de memória a cada ~200 ticks
        tickCounter++;
        if (tickCounter >= 200L) {
            tickCounter = 0L;
            if (lastArmorMask.size() > 64) lastArmorMask.clear();
            if (lastAlertTime.size() > 64) lastAlertTime.clear();
        }
    }

    /**
     * Validação estrita de clã e aliados. Retorna true se o jogador for do mesmo clã ou aliado.
     */
    public static boolean isSameClanOrAlly(Object player, String name) {
        if (player == null) return false;
        if (player == Entities.self()) return true;

        // 1. Verificação direta de relações SteinLoader (GROUP = mesmo clã, ALLY = aliado)
        try {
            Relation rel = Relations.of(player);
            if (rel != null && rel.friendly()) {
                return true;
            }
        } catch (Throwable ignored) {}

        // 2. Verificação de relação pelo nick do jogador
        if (name != null && !name.isEmpty()) {
            try {
                Relation rel = Relations.of(name);
                if (rel != null && rel.friendly()) {
                    return true;
                }
            } catch (Throwable ignored) {}

            // 3. Verificação com resolução de tags de clã do Tab / Prefixo / DisplayName
            try {
                if (PingClanName.isSameClan(player, name)) {
                    return true;
                }
            } catch (Throwable ignored) {}
        }

        return false;
    }

    public static void reset() {
        cachedTargets = Collections.emptyList();
        lastArmorMask.clear();
        lastAlertTime.clear();
    }

    /**
     * Avalia as peças de armadura e retorna a tag e as cores visuais brilhantes.
     */
    public static ArmorStatus evaluateArmor(Object boots, Object legs, Object chest, Object helm) {
        boolean noBoots = (boots == null);
        boolean noLegs = (legs == null);
        boolean noChest = (chest == null);
        boolean noHelm = (helm == null);

        int missingCount = (noBoots ? 1 : 0) + (noLegs ? 1 : 0) + (noChest ? 1 : 0) + (noHelm ? 1 : 0);
        if (missingCount == 0) {
            return new ArmorStatus(false, 0, null, 0, 0);
        }

        String tag;
        int outlineColor;
        int boxColor;

        if (missingCount == 4) {
            // Sem armadura nenhuma
            tag = "§c§l☠ SEM ARMADURA";
            outlineColor = 0xFFFF1111;
            boxColor = 0x30FF1111;
        } else if (noHelm && missingCount == 1) {
            // Apenas sem elmo (cenário mais comum de rush/foco)
            tag = "§e§l⚠ SEM ELMO";
            outlineColor = 0xFFFFD700; // Ouro / Amarelo brilhante
            boxColor = 0x38FFD700;
        } else if (noBoots && missingCount == 1) {
            // Apenas sem bota
            tag = "§6§l⚠ SEM BOTA";
            outlineColor = 0xFFFF7700; // Laranja vivo
            boxColor = 0x38FF7700;
        } else if (noHelm && noBoots && missingCount == 2) {
            // Sem elmo e sem bota
            tag = "§c§l☠ SEM ELMO & BOTA";
            outlineColor = 0xFFFF2244; // Coral neon / Vermelho alerta
            boxColor = 0x40FF2244;
        } else if (noChest && missingCount == 1) {
            // Sem peitoral
            tag = "§4§l☠ SEM PEITORAL";
            outlineColor = 0xFFFF0055; // Carmesim neon
            boxColor = 0x45FF0055;
        } else if (noLegs && missingCount == 1) {
            // Sem calça
            tag = "§d§l⚠ SEM CALÇA";
            outlineColor = 0xFFFF1493; // Magenta brilhante
            boxColor = 0x38FF1493;
        } else if (noHelm) {
            // Múltiplas peças faltando incluindo o elmo
            tag = "§c§l☠ SEM ELMO (-" + missingCount + ")";
            outlineColor = 0xFFFF3333;
            boxColor = 0x40FF3333;
        } else {
            // Múltiplas peças faltando
            tag = "§c§l☠ QUEBRADO (-" + missingCount + ")";
            outlineColor = 0xFFFF3333;
            boxColor = 0x40FF3333;
        }

        return new ArmorStatus(true, missingCount, tag, outlineColor, boxColor);
    }

    /**
     * Aplica fator de pulsação nos canais RGB para efeito de brilho pulsante.
     */
    private static int applyPulse(int argb, float pulse) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;

        r = Math.min(255, (int) (r * pulse));
        g = Math.min(255, (int) (g * pulse));
        b = Math.min(255, (int) (b * pulse));

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Retorna o ItemStack do slot de armadura informado via reflexão compatível
     * com Notch ('q') e MCP ('getCurrentArmor').
     * Slot: 0 = Bota, 1 = Calça, 2 = Peitoral, 3 = Elmo.
     */
    public static Object getArmorItem(Object living, int slot) {
        if (living == null) return null;
        resolveArmorMethod(living.getClass());
        if (armorItemMethod != null) {
            try {
                return armorItemMethod.invoke(living, slot);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static synchronized void resolveArmorMethod(Class<?> clazz) {
        if (methodResolved) return;
        methodResolved = true;
        for (String mName : new String[]{"q", "getCurrentArmor", "func_82169_q"}) {
            try {
                Method m = clazz.getMethod(mName, Integer.TYPE);
                m.setAccessible(true);
                armorItemMethod = m;
                return;
            } catch (Throwable ignored) {}
        }
        for (Method m : clazz.getMethods()) {
            if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == int.class && m.getReturnType() != void.class) {
                if ("q".equals(m.getName()) || "getCurrentArmor".equals(m.getName())) {
                    m.setAccessible(true);
                    armorItemMethod = m;
                    return;
                }
            }
        }
    }

    public static final class TargetSnapshot {
        public final Object entity;
        public final int outlineColor;
        public final int boxColor;
        public final String tag;

        public TargetSnapshot(Object entity, int outlineColor, int boxColor, String tag) {
            this.entity = entity;
            this.outlineColor = outlineColor;
            this.boxColor = boxColor;
            this.tag = tag;
        }
    }

    public static final class ArmorStatus {
        public final boolean hasMissingPiece;
        public final int missingCount;
        public final String tag;
        public final int outlineColor;
        public final int boxColor;

        public ArmorStatus(boolean hasMissingPiece, int missingCount, String tag, int outlineColor, int boxColor) {
            this.hasMissingPiece = hasMissingPiece;
            this.missingCount = missingCount;
            this.tag = tag;
            this.outlineColor = outlineColor;
            this.boxColor = boxColor;
        }
    }
}
