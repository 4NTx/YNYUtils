package com.yny.utils.modules.pvp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.lang.reflect.Method;

import com.yny.utils.YNYUtils;
import com.yny.utils.config.YNYConfig;
import com.yny.utils.core.DebugLog;
import com.yny.utils.core.SafeEntities;
import com.yny.utils.core.ToggleKey;
import com.yny.utils.modules.clanping.PingClanName;
import com.yny.utils.modules.player.AutoProtect;
import com.yny.utils.modules.player.PriorityBus;

import dev.xavier.stein.loader.api.Chat;
import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Inventory;
import dev.xavier.stein.loader.api.Keys;
import dev.xavier.stein.loader.api.ModConfig;
import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Page;
import dev.xavier.stein.loader.api.Relation;
import dev.xavier.stein.loader.api.Relations;
import dev.xavier.stein.loader.api.Screens;
import dev.xavier.stein.loader.api.Targeting;
import dev.xavier.stein.loader.api.WorldRender;

/**
 * Mob Aura avançada com suporte a múltiplos alvos (até 2 mobs simultâneos),
 * priorização estrita de Wither e Guardian, filtro refinado de segurança PvP
 * e otimização máxima de GC (Zero-Allocation em loops de tick e render).
 */
public final class MobAura {

    private static long lastAttackNanos = 0L;
    private static final List<Object> currentTargets = new ArrayList<>(2);
    private static final ToggleKey toggleKey = new ToggleKey();

    // -------------------------------------------------------------------------
    // Pool de candidatos e coleções estáticas reutilizáveis (Zero-Allocation de GC)
    // -------------------------------------------------------------------------
    private static final int POOL_SIZE = 64;
    private static final Candidate[] POOL = new Candidate[POOL_SIZE];
    static {
        for (int i = 0; i < POOL_SIZE; i++) {
            POOL[i] = new Candidate();
        }
    }
    private static int poolIndex = 0;

    private static final List<Candidate> withers = new ArrayList<>(16);
    private static final List<Candidate> guardians = new ArrayList<>(16);
    private static final List<Candidate> otherMobs = new ArrayList<>(32);
    private static final List<Candidate> remaining = new ArrayList<>(32);
    private static final List<Object> activeTargets = new ArrayList<>(2);

    private static final Comparator<Candidate> DISTANCE_COMPARATOR = (c1, c2) -> Double.compare(c1.distance, c2.distance);

    private static volatile boolean auraAttacking = false;

    public static boolean isAuraAttacking() {
        return auraAttacking || System.nanoTime() < CpsRateLimiter.getAuraAllowUntilNanos();
    }

    private MobAura() {}

    public static List<Object> getCurrentTargets() {
        return currentTargets;
    }

    public static void onTickEnd() {
        // Aciona salvaguardas de knockback e sprint contra guardian
        MobKnockbackFilter.onTickEnd();

        YNYConfig config = YNYUtils.getConfig();
        if (config == null || !config.mobAuraEnabled) {
            currentTargets.clear();
            return;
        }

        Object self = Entities.self();
        if (self == null || !Entities.isAlive(self)) {
            currentTargets.clear();
            return;
        }

        // Não bater com telas/inventário abertos
        if (Screens.isOpen() || Screens.current() != null || Inventory.isScreenOpen() || Inventory.isContainerOpen()) {
            return;
        }

        // Não agir se o cursor estiver preso ou ocupado (Cursor Failsafe)
        if (PriorityBus.isCursorBusy()) {
            return;
        }

        // Não atrapalhar uso de consumíveis (maçã dourada ou poções)
        if (isConsumableHolding() || PriorityBus.isConsuming()) {
            return;
        }

        // ---------------------------------------------------------------------
        // Filtro refinado de segurança PvP: se houver jogadores inimigos no raio
        // de ameaça PvP, cede o foco e a cadência para o combate entre jogadores!
        // ---------------------------------------------------------------------
        if (config.mobAuraPvpSafety && isEnemyPlayerNear(self, config.mobAuraPvpThreatRadius)) {
            if (!currentTargets.isEmpty()) {
                currentTargets.clear();
            }
            return;
        }

        // Controle de cadência (CPS)
        int cps = Math.max(1, Math.min(20, config.mobAuraCps));
        long intervalNanos = 1_000_000_000L / cps;
        long now = System.nanoTime();
        if (now - lastAttackNanos < intervalNanos) {
            return;
        }

        double reach = config.mobAuraReach;
        int maxTargets = Math.max(1, Math.min(2, config.mobAuraTargets));
        boolean prioritizeWither = config.mobAuraWitherPriority;
        boolean prioritizeGuardian = config.mobAuraGuardianPriority;

        // Limpeza das coleções reutilizáveis (sem realocações de GC)
        poolIndex = 0;
        withers.clear();
        guardians.clear();
        otherMobs.clear();
        remaining.clear();
        activeTargets.clear();

        for (Object entity : SafeEntities.safeAll()) {
            if (entity == self) continue;
            if (Entities.isPlayer(entity)) continue; // NUNCA atacar players com MobAura
            if (!Entities.isAlive(entity) || Entities.health(entity) <= 0.0F) continue;
            if (MobKnockbackFilter.isArmorStand(entity)) continue;

            boolean isWith = MobKnockbackFilter.isWither(entity);
            boolean isGuard = MobKnockbackFilter.isGuardian(entity);
            boolean isMob = isWith || isGuard || MobKnockbackFilter.isMonster(entity)
                    || (config.mobAuraAttackPassives && isPassiveMob(entity));
            if (!isMob) continue;

            double dist = Entities.distance(self, entity);
            if (dist > reach) continue;

            // Linha de visão limpa: não atacar mobs através de paredes sólidas (evita flag de Wall em anticheat)
            if (!FocusMode.isLineOfSightClear(self, entity)) continue;

            Candidate candidate = poolIndex < POOL_SIZE ? POOL[poolIndex++] : new Candidate();
            candidate.set(entity, dist, isWith, isGuard);

            if (isWith) {
                withers.add(candidate);
            } else if (isGuard) {
                guardians.add(candidate);
            } else {
                otherMobs.add(candidate);
            }
        }

        // Ordena cada grupo por proximidade reutilizando o mesmo comparator estático
        Collections.sort(withers, DISTANCE_COMPARATOR);
        Collections.sort(guardians, DISTANCE_COMPARATOR);
        Collections.sort(otherMobs, DISTANCE_COMPARATOR);

        // 1. Se focar Wither primeiro:
        if (prioritizeWither) {
            for (int i = 0; i < withers.size() && activeTargets.size() < maxTargets; i++) {
                activeTargets.add(withers.get(i).entity);
            }
        }

        // 2. Se focar Guardian:
        if (prioritizeGuardian) {
            for (int i = 0; i < guardians.size() && activeTargets.size() < maxTargets; i++) {
                activeTargets.add(guardians.get(i).entity);
            }
        }

        // 3. Preenche as vagas restantes com outros mobs (e withers/guardians caso não estivessem priorizados)
        if (activeTargets.size() < maxTargets) {
            if (!prioritizeWither) {
                remaining.addAll(withers);
            }
            if (!prioritizeGuardian) {
                remaining.addAll(guardians);
            }
            remaining.addAll(otherMobs);
            Collections.sort(remaining, DISTANCE_COMPARATOR);

            for (int i = 0; i < remaining.size() && activeTargets.size() < maxTargets; i++) {
                Object ent = remaining.get(i).entity;
                if (!activeTargets.contains(ent)) {
                    activeTargets.add(ent);
                }
            }
        }

        currentTargets.clear();
        currentTargets.addAll(activeTargets);

        if (!activeTargets.isEmpty()) {
            lastAttackNanos = now;
            auraAttacking = true;
            try {
                // Dispara exatamente 1 swing para a acao de ataque (sem pacote de animacao duplicado)
                Targeting.swing();

                // Executa os ataques na ordem estrita de prioridade (Guardian/Wither primeiro)
                for (int i = 0; i < activeTargets.size(); i++) {
                    Object target = activeTargets.get(i);
                    try {
                        Targeting.attack(target);
                    } catch (Throwable t) {
                        DebugLog.write("MobAura", "Erro ao atacar entidade: " + t.getMessage());
                    }
                }
            } finally {
                auraAttacking = false;
            }
        }
    }

    /**
     * Seleção pura de alvos para verificação e testes unitários independentes.
     */
    public static List<Object> selectTargets(List<Candidate> candidates, int maxTargets, double reach,
            boolean prioritizeWither, boolean prioritizeGuardian) {
        List<Candidate> wList = new ArrayList<>();
        List<Candidate> gList = new ArrayList<>();
        List<Candidate> oList = new ArrayList<>();

        for (Candidate c : candidates) {
            if (c.distance > reach) continue;
            if (c.isWither) wList.add(c);
            else if (c.isGuardian) gList.add(c);
            else oList.add(c);
        }

        Collections.sort(wList, DISTANCE_COMPARATOR);
        Collections.sort(gList, DISTANCE_COMPARATOR);
        Collections.sort(oList, DISTANCE_COMPARATOR);

        List<Object> targets = new ArrayList<>(maxTargets);
        if (prioritizeWither) {
            for (Candidate w : wList) {
                if (targets.size() < maxTargets) targets.add(w.entity);
            }
        }
        if (prioritizeGuardian) {
            for (Candidate g : gList) {
                if (targets.size() < maxTargets) targets.add(g.entity);
            }
        }
        if (targets.size() < maxTargets) {
            List<Candidate> rem = new ArrayList<>();
            if (!prioritizeWither) rem.addAll(wList);
            if (!prioritizeGuardian) rem.addAll(gList);
            rem.addAll(oList);
            Collections.sort(rem, DISTANCE_COMPARATOR);
            for (Candidate c : rem) {
                if (targets.size() < maxTargets && !targets.contains(c.entity)) {
                    targets.add(c.entity);
                }
            }
        }
        return targets;
    }

    /**
     * Verifica se há jogadores inimigos no raio de ameaça PvP.
     * Exclui o próprio jogador, aliados, membros do mesmo clã e armor stands.
     */
    public static boolean isEnemyPlayerNear(Object self, double radius) {
        if (self == null) return false;
        try {
            List<Object> players = SafeEntities.safePlayers();
            if (players == null || players.isEmpty()) return false;
            for (Object player : players) {
                if (player == null || player == self || !Entities.isAlive(player)) continue;
                if (MobKnockbackFilter.isArmorStand(player)) continue;
                if (Entities.distance(self, player) > radius) continue;
                try {
                    Relation rel = Relations.of(player);
                    if (rel == Relation.GROUP || rel == Relation.SELF || rel == Relation.ALLY || (rel != null && rel.friendly())) {
                        continue;
                    }
                } catch (Throwable ignored) {}
                String name = Entities.name(player);
                if (PingClanName.isSameClan(player, name)) {
                    continue;
                }
                return true; // Inimigo detectado no raio de combate PvP!
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static float renderPartialTicks;
    private static final Runnable RENDER_ACTION = () -> renderTargets();

    public static void onRenderWorld(float partialTicks) {
        YNYConfig config = YNYUtils.getConfig();
        if (config == null || !config.mobAuraEnabled || !config.mobAuraVisuals) {
            return;
        }

        List<Object> targets = currentTargets;
        if (targets == null || targets.isEmpty()) {
            return;
        }

        renderPartialTicks = partialTicks;
        try {
            WorldRender.throughWalls(RENDER_ACTION);
        } catch (Throwable ignored) {}
    }

    private static void renderTargets() {
        List<Object> targets = currentTargets;
        if (targets == null || targets.isEmpty()) return;

        for (int i = 0; i < targets.size(); i++) {
            Object t = targets.get(i);
            if (t == null || !Entities.isAlive(t)) continue;

            boolean isWith = MobKnockbackFilter.isWither(t);
            boolean isGuard = MobKnockbackFilter.isGuardian(t);
            int outlineColor = isWith ? 0xFF8A2BE2 : (isGuard ? 0xFFFF2020 : (i == 0 ? 0xFFFFA000 : 0xFF00D0FF));

            double mx = Entities.x(t, renderPartialTicks);
            double my = Entities.y(t, renderPartialTicks);
            double mz = Entities.z(t, renderPartialTicks);

            double radius = isWith ? 0.85D : (isGuard ? 0.65D : 0.52D);
            double height = isWith ? 2.8D : (isGuard ? 1.5D : 1.8D);
            int auraColor = (0x2A << 24) | (outlineColor & 0x00FFFFFF);
            int discColor = (0x35 << 24) | (outlineColor & 0x00FFFFFF);

            int targetIndex = i;
            String targetTag = isWith ? "§5§l[WITHER]" : (isGuard ? "§c§l[GUARDIAN]" : (targetIndex == 0 ? "§6§l[ALVO #1]" : "§b§l[ALVO #2]"));

            WorldRender.beam(mx, mz, my, my + height, radius * 0.90D, auraColor);
            WorldRender.ring(mx, my + 0.03D, mz, radius, 0.05D, outlineColor);
            WorldRender.circle(mx, my + 0.015D, mz, radius * 0.95D, discColor);
            WorldRender.label(targetTag, mx, my + height + 0.30D, mz, 1.15F, 0xFFFFFFFF, true);
        }
    }

    public static void updateToggleKey() {
        YNYConfig config = YNYUtils.getConfig();
        if (config == null || config.mobAuraToggleKey == 0) return;

        if (toggleKey.wasPressed(config.mobAuraToggleKey)) {
            config.mobAuraEnabled = !config.mobAuraEnabled;
            if (config.mobAuraStatusHudEnabled) {
                Chat.actionBar(config.mobAuraEnabled
                        ? "§aMob Aura: ATIVADA (Wither: " + (config.mobAuraWitherPriority ? "SIM" : "NÃO") + " | Guardian: " + (config.mobAuraGuardianPriority ? "SIM" : "NÃO") + ")"
                        : "§cMob Aura: DESATIVADA");
            }
            DebugLog.write("MobAura", "event=toggle_key; enabled=" + config.mobAuraEnabled
                    + "; key=" + config.mobAuraToggleKey);
            ModConfig<YNYConfig> store = YNYUtils.getConfigStore();
            if (store != null) {
                store.saveSoon();
            }
        }
    }

    public static void reset() {
        currentTargets.clear();
        activeTargets.clear();
        withers.clear();
        guardians.clear();
        otherMobs.clear();
        remaining.clear();
        lastAttackNanos = 0L;
    }

    public static void addOptions(Page page) {
        YNYConfig config = YNYUtils.getConfig();
        if (page == null || config == null) return;

        page.section("Mob Combat & Proteções")
                .option(Option.toggle("Não levar KB do Wither (Explosões/Ataques)", () -> config.witherKnockbackProtect, value -> {
                    config.witherKnockbackProtect = value;
                    markConfigChanged();
                }))
                .option(Option.toggle("Não levar KB do Guardian (Raio/Ataques)", () -> config.guardianBeamProtect, value -> {
                    config.guardianBeamProtect = value;
                    markConfigChanged();
                }))
                .option(Option.toggle("Não levar KB de outros Mobs", () -> config.mobKnockbackEnabled, value -> {
                    config.mobKnockbackEnabled = value;
                    markConfigChanged();
                }))
                .option(Option.toggle("Ativar Mob Aura", () -> config.mobAuraEnabled, value -> {
                    config.mobAuraEnabled = value;
                    markConfigChanged();
                }))
                .option(Option.key("Atalho para alternar Mob Aura", () -> config.mobAuraToggleKey, value -> {
                    config.mobAuraToggleKey = value;
                    markConfigChanged();
                }))
                .option(Option.toggle("Priorizar Wither (atacar antes de outros mobs)", () -> config.mobAuraWitherPriority, value -> {
                    config.mobAuraWitherPriority = value;
                    markConfigChanged();
                }))
                .option(Option.toggle("Priorizar Guardian (atacar antes de outros mobs)", () -> config.mobAuraGuardianPriority, value -> {
                    config.mobAuraGuardianPriority = value;
                    markConfigChanged();
                }))
                .option(Option.slider("Quantidade de alvos simultâneos", 1, 2, 1,
                        () -> config.mobAuraTargets, value -> {
                    config.mobAuraTargets = (int) value;
                    markConfigChanged();
                }, value -> (int) value == 1 ? "1 alvo" : "2 alvos simultâneos"))
                .option(Option.slider("Alcance da Mob Aura", 3, 6, 1,
                        () -> (int) Math.round(config.mobAuraReach), value -> {
                    config.mobAuraReach = value;
                    markConfigChanged();
                }, value -> String.format(Locale.ROOT, "%.1f blocos", (double) value)))
                .option(Option.slider("Velocidade de ataque (CPS)", 5, 20, 1,
                        () -> config.mobAuraCps, value -> {
                    config.mobAuraCps = (int) value;
                    markConfigChanged();
                }, value -> (int) value + " CPS"))
                .option(Option.toggle("Atacar mobs passivos", () -> config.mobAuraAttackPassives, value -> {
                    config.mobAuraAttackPassives = value;
                    markConfigChanged();
                }))
                .option(Option.toggle("Prioridade PvP na Mob Aura (pausar perto de inimigos)", () -> config.mobAuraPvpSafety, value -> {
                    config.mobAuraPvpSafety = value;
                    markConfigChanged();
                }))
                .option(Option.slider("Raio de ameaça PvP para pausar Mob Aura", 3, 10, 1,
                        () -> (int) Math.round(config.mobAuraPvpThreatRadius), value -> {
                    config.mobAuraPvpThreatRadius = value;
                    markConfigChanged();
                }, value -> (int) value + " blocos"))
                .option(Option.toggle("Marcador visual nos alvos da Mob Aura", () -> config.mobAuraVisuals, value -> {
                    config.mobAuraVisuals = value;
                    markConfigChanged();
                }))
                .option(Option.toggle("Notificação de Mob Aura na Action Bar", () -> config.mobAuraStatusHudEnabled, value -> {
                    config.mobAuraStatusHudEnabled = value;
                    markConfigChanged();
                }));
    }

    private static boolean isPassiveMob(Object e) {
        if (e == null) return false;
        if (Entities.kind(e) == Entities.Kind.PASSIVE) return true;
        String type = Entities.typeName(e);
        if (type == null) return false;
        String t = type.toLowerCase();
        return t.contains("cow") || t.contains("sheep") || t.contains("pig")
                || t.contains("chicken") || t.contains("horse") || t.contains("bat")
                || t.contains("squid") || t.contains("rabbit") || t.contains("wolf")
                || t.contains("ocelot") || t.contains("villager");
    }

    private static boolean isConsumableHolding() {
        // Se estiver segurando espada, o clique direito é defesa/bloqueio (AutoProtect), NUNCA consumível!
        try {
            Object held = Inventory.heldItem();
            if (AutoProtect.sword(held)) {
                return false;
            }
        } catch (Throwable ignored) {}

        if (PriorityBus.isConsuming()) {
            return true;
        }

        if (Inventory.isUsingItem()) {
            return true;
        }

        try {
            return Keys.isMouseDown(1);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void markConfigChanged() {
        ModConfig<YNYConfig> store = YNYUtils.getConfigStore();
        if (store != null) {
            store.saveSoon();
        }
    }

    public static final class Candidate {
        public Object entity;
        public double distance;
        public boolean isWither;
        public boolean isGuardian;

        public Candidate() {}

        public Candidate(Object entity, double distance, boolean isWither, boolean isGuardian) {
            this.entity = entity;
            this.distance = distance;
            this.isWither = isWither;
            this.isGuardian = isGuardian;
        }

        public void set(Object entity, double distance, boolean isWither, boolean isGuardian) {
            this.entity = entity;
            this.distance = distance;
            this.isWither = isWither;
            this.isGuardian = isGuardian;
        }
    }
}
