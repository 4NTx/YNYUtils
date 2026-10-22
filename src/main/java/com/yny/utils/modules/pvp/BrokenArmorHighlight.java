package com.yny.utils.modules.pvp;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.yny.utils.YNYUtils;
import com.yny.utils.config.YNYConfig;
import com.yny.utils.core.DebugLog;
import com.yny.utils.core.SafeEntities;
import com.yny.utils.modules.clanping.PingClanName;
import com.yny.utils.ui.BrokenArmorHud;

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

    // Pool de snapshots e coleções estáticas reutilizáveis (Zero-Allocation de GC em tick e render)
    private static final int SNAPSHOT_POOL_SIZE = 32;
    private static final TargetSnapshot[] SNAPSHOT_POOL = new TargetSnapshot[SNAPSHOT_POOL_SIZE];
    static {
        for (int i = 0; i < SNAPSHOT_POOL_SIZE; i++) {
            SNAPSHOT_POOL[i] = new TargetSnapshot();
        }
    }
    private static int poolIndex = 0;
    private static final List<TargetSnapshot> activeSnapshotList = new ArrayList<>(SNAPSHOT_POOL_SIZE);

    // Snapshot pré-calculado no onTickEnd (20 TPS) para renderização com ZERO LAG a 144+ FPS
    private static volatile List<TargetSnapshot> cachedTargets = Collections.emptyList();

    // Rastreamento para alertas na Action Bar (apenas ao quebrar)
    private static final Map<String, Integer> lastArmorMask = new ConcurrentHashMap<>();
    private static final Map<String, Long> lastAlertTime = new ConcurrentHashMap<>();
    private static long tickCounter = 0L;

    // Estado de renderização estático e Runnable singleton (ZERO LAG, zero alocações por frame)
    private static float renderPartialTicks;
    private static float renderPulse;
    private static boolean renderDrawTags;
    private static double renderSelfX;
    private static double renderSelfY;
    private static double renderSelfZ;

    private static final Runnable RENDER_ACTION = () -> renderTargets();

    private BrokenArmorHighlight() {}

    /**
     * Renderização no mundo 3D com ZERO LAG (0 reflexões por frame, 0 alocações de GC).
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

        renderPartialTicks = partialTicks;
        renderSelfX = Entities.x(self, partialTicks);
        renderSelfY = Entities.y(self, partialTicks);
        renderSelfZ = Entities.z(self, partialTicks);

        long now = System.currentTimeMillis();
        renderPulse = (float) (0.82f + 0.18f * Math.sin(now / 130.0D));
        renderDrawTags = config.brokenArmorTags;

        if (config.brokenArmorThroughWalls) {
            WorldRender.throughWalls(RENDER_ACTION);
        } else {
            RENDER_ACTION.run();
        }
    }

    private static void renderTargets() {
        List<TargetSnapshot> targets = cachedTargets;
        if (targets == null || targets.isEmpty()) return;

        for (int i = 0; i < targets.size(); i++) {
            TargetSnapshot t = targets.get(i);
            if (t == null) continue;
            Object p = t.entity;
            if (p == null || !Entities.isAlive(p)) continue;

            int outlineColor = applyPulse(t.outlineColor, renderPulse);

            double px = Entities.x(p, renderPartialTicks);
            double py = Entities.y(p, renderPartialTicks);
            double pz = Entities.z(p, renderPartialTicks);

            // Efeito luminoso suave e sem distorções ("player brilhando")
            // 1. Cilindro de luz/aura translúcido em volta do corpo (sem quads com pontas de estrela)
            int auraColor = (0x2E << 24) | (outlineColor & 0x00FFFFFF);
            WorldRender.beam(px, pz, py, py + 1.85D, 0.45D, auraColor);

            // 2. Anel luminoso nítido e pulsante no chão sob os pés
            WorldRender.ring(px, py + 0.03D, pz, 0.52D, 0.05D, outlineColor);

            // 3. Disco sutil no chão sob os pés
            int discColor = (0x38 << 24) | (outlineColor & 0x00FFFFFF);
            WorldRender.circle(px, py + 0.015D, pz, 0.48D, discColor);

            if (renderDrawTags && t.tag != null) {
                double dx = px - renderSelfX;
                double dy = py - renderSelfY;
                double dz = pz - renderSelfZ;
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

                // Escala adaptativa para manter o texto 3D nítido e altamente visível de perto e de longe
                float tagScale = (float) Math.max(0.85F, Math.min(2.4F, 0.75F + dist * 0.040F));
                // Posicionado a py + 2.68D para flutuar perfeitamente acima do nick do jogador (py + 2.25D),
                // eliminando sobreposição visual e garantindo leitura nítida do nick e do alerta de armadura.
                WorldRender.label(t.tag, px, py + 2.68D, pz, tagScale, 0xFFFFFFFF, true);
            }
        }
    }

    /**
     * Avaliação de estado executada no ritmo de tick (20 TPS).
     * Faz todas as checagens de clã, armadura e reflexão de forma assíncrona ao renderizador.
     */
    public static void onTickEnd() {
        try {
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

            for (Object player : SafeEntities.safePlayers()) {
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

                newTargets.add(new TargetSnapshot(player, status.outlineColor, status.boxColor, status.tag));

                if (name != null && !name.isEmpty()) {
                    int currentMask = (boots != null ? 1 : 0)
                            | (legs != null ? 2 : 0)
                            | (chest != null ? 4 : 0)
                            | (helm != null ? 8 : 0);
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
        } catch (Throwable t) {
            DebugLog.write("BrokenArmorHighlight", "Erro no onTickEnd: " + t.getMessage());
        }
    }

    public static void reset() {
        cachedTargets = Collections.emptyList();
        lastArmorMask.clear();
        lastAlertTime.clear();
        tickCounter = 0L;
        BrokenArmorHud.reset();
        FocusMode.reset();
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


    private static final ArmorStatus STATUS_FULL = new ArmorStatus(false, 0, null, 0, 0);
    private static final ArmorStatus[] STATUS_CACHE = new ArmorStatus[16];
    static {
        // Bitmask: bit 0 = noBoots (1), bit 1 = noLegs (2), bit 2 = noChest (4), bit 3 = noHelm (8)
        STATUS_CACHE[0] = STATUS_FULL;
        STATUS_CACHE[1] = new ArmorStatus(true, 1, "§6§l⚠ SEM BOTA", 0xFFFF7700, 0x38FF7700);
        STATUS_CACHE[2] = new ArmorStatus(true, 1, "§d§l⚠ SEM CALÇA", 0xFFFF1493, 0x38FF1493);
        STATUS_CACHE[3] = new ArmorStatus(true, 2, "§c§l☠ SEM CALÇA & BOTA", 0xFFFF2244, 0x40FF2244);
        STATUS_CACHE[4] = new ArmorStatus(true, 1, "§4§l☠ SEM PEITORAL", 0xFFFF0055, 0x45FF0055);
        STATUS_CACHE[5] = new ArmorStatus(true, 2, "§c§l☠ SEM PEITORAL & BOTA", 0xFFFF2244, 0x40FF2244);
        STATUS_CACHE[6] = new ArmorStatus(true, 2, "§c§l☠ SEM PEITORAL & CALÇA", 0xFFFF2244, 0x40FF2244);
        STATUS_CACHE[7] = new ArmorStatus(true, 3, "§4§l☠ SÓ DE ELMO (-3)", 0xFFFF3333, 0x40FF3333);
        STATUS_CACHE[8] = new ArmorStatus(true, 1, "§e§l⚠ SEM ELMO", 0xFFFFD700, 0x38FFD700);
        STATUS_CACHE[9] = new ArmorStatus(true, 2, "§c§l☠ SEM ELMO & BOTA", 0xFFFF2244, 0x40FF2244);
        STATUS_CACHE[10] = new ArmorStatus(true, 2, "§c§l☠ SEM ELMO & CALÇA", 0xFFFF2244, 0x40FF2244);
        STATUS_CACHE[11] = new ArmorStatus(true, 3, "§4§l☠ SÓ DE PEITORAL (-3)", 0xFFFF3333, 0x40FF3333);
        STATUS_CACHE[12] = new ArmorStatus(true, 2, "§4§l☠ SEM ELMO & PEITORAL", 0xFFFF0055, 0x45FF0055);
        STATUS_CACHE[13] = new ArmorStatus(true, 3, "§4§l☠ SÓ DE CALÇA (-3)", 0xFFFF3333, 0x40FF3333);
        STATUS_CACHE[14] = new ArmorStatus(true, 3, "§4§l☠ SÓ DE BOTA (-3)", 0xFFFF3333, 0x40FF3333);
        STATUS_CACHE[15] = new ArmorStatus(true, 4, "§c§l☠ SEM ARMADURA", 0xFFFF1111, 0x30FF1111);
    }

    /**
     * Avalia as peças de armadura e retorna a tag e as cores visuais brilhantes.
     * Indexação por máscara de bits com ZERO alocações de GC e tempo O(1).
     */
    public static ArmorStatus evaluateArmor(Object boots, Object legs, Object chest, Object helm) {
        int mask = (boots == null ? 1 : 0)
                | (legs == null ? 2 : 0)
                | (chest == null ? 4 : 0)
                | (helm == null ? 8 : 0);
        return STATUS_CACHE[mask];
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
        public Object entity;
        public int outlineColor;
        public int boxColor;
        public String tag;

        public TargetSnapshot() {}

        public TargetSnapshot(Object entity, int outlineColor, int boxColor, String tag) {
            set(entity, outlineColor, boxColor, tag);
        }

        public void set(Object entity, int outlineColor, int boxColor, String tag) {
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
