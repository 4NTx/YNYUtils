package com.yny.utils.modules.pvp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import java.lang.reflect.Method;

import com.yny.utils.YNYUtils;
import com.yny.utils.config.YNYConfig;
import com.yny.utils.core.DebugLog;
import com.yny.utils.core.ToggleKey;

import dev.xavier.stein.loader.api.Chat;
import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Inventory;
import dev.xavier.stein.loader.api.Keys;
import dev.xavier.stein.loader.api.ModConfig;
import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Page;
import dev.xavier.stein.loader.api.Screens;
import dev.xavier.stein.loader.api.Targeting;
import dev.xavier.stein.loader.api.WorldRender;

/**
 * Mob Aura avançada com suporte a múltiplos alvos (até 2 mobs simultâneos)
 * e priorização estrita de Guardian.
 *
 * Se houver um Guardian dentro do alcance, ele é atacado PRIMEIRO antes
 * de qualquer outro mob. Se houver 2 alvos configurados e 1 Guardian com outros mobs,
 * o primeiro golpe é desferido no Guardian e o segundo golpe no outro mob.
 */
public final class MobAura {

    private static long lastAttackNanos = 0L;
    private static final List<Object> currentTargets = new CopyOnWriteArrayList<>();
    private static final ToggleKey toggleKey = new ToggleKey();

    private MobAura() {}

    public static List<Object> getCurrentTargets() {
        return currentTargets;
    }

    public static void onTickEnd() {
        // Também aciona salvaguardas de knockback e sprint contra guardian
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
        if (Screens.isOpen()) {
            return;
        }

        // Não atrapalhar uso de consumíveis (maçã dourada ou poções)
        if (isConsumableHolding()) {
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

        // Seleção de candidatos válidos dentro do alcance
        List<Candidate> withers = new ArrayList<>();
        List<Candidate> guardians = new ArrayList<>();
        List<Candidate> otherMobs = new ArrayList<>();

        for (Object entity : Entities.all()) {
            if (entity == self) continue;
            if (Entities.isPlayer(entity)) continue; // NUNCA atacar players
            if (!Entities.isAlive(entity) || Entities.health(entity) <= 0.0F) continue;
            if (MobKnockbackFilter.isArmorStand(entity)) continue;

            boolean isWith = MobKnockbackFilter.isWither(entity);
            boolean isGuard = MobKnockbackFilter.isGuardian(entity);
            boolean isMob = isWith || isGuard || MobKnockbackFilter.isMonster(entity)
                    || (config.mobAuraAttackPassives && isPassiveMob(entity));
            if (!isMob) continue;

            double dist = Entities.distance(self, entity);
            if (dist > reach) continue;
            if (!FocusMode.isLineOfSightClear(self, entity)) continue;

            Candidate candidate = new Candidate(entity, dist, isWith, isGuard);
            if (isWith) {
                withers.add(candidate);
            } else if (isGuard) {
                guardians.add(candidate);
            } else {
                otherMobs.add(candidate);
            }
        }

        // Ordena cada grupo por proximidade (mais próximo primeiro)
        Collections.sort(withers, Comparator.comparingDouble(c -> c.distance));
        Collections.sort(guardians, Comparator.comparingDouble(c -> c.distance));
        Collections.sort(otherMobs, Comparator.comparingDouble(c -> c.distance));

        List<Object> targets = new ArrayList<>(maxTargets);

        // 1. Se focar Wither primeiro:
        if (prioritizeWither) {
            for (Candidate w : withers) {
                if (targets.size() < maxTargets) {
                    targets.add(w.entity);
                }
            }
        }

        // 2. Se focar Guardian:
        if (prioritizeGuardian) {
            for (Candidate g : guardians) {
                if (targets.size() < maxTargets) {
                    targets.add(g.entity);
                }
            }
        }

        // 3. Preenche as vagas restantes com outros mobs (e withers/guardians caso não estivessem priorizados)
        if (targets.size() < maxTargets) {
            List<Candidate> remaining = new ArrayList<>();
            if (!prioritizeWither) {
                remaining.addAll(withers);
            }
            if (!prioritizeGuardian) {
                remaining.addAll(guardians);
            }
            remaining.addAll(otherMobs);
            Collections.sort(remaining, Comparator.comparingDouble(c -> c.distance));

            for (Candidate c : remaining) {
                if (targets.size() < maxTargets && !targets.contains(c.entity)) {
                    targets.add(c.entity);
                }
            }
        }

        currentTargets.clear();
        currentTargets.addAll(targets);

        if (!targets.isEmpty()) {
            lastAttackNanos = now;
            // Dispara exatamente 1 swing para a acao de ataque (sem animacao duplicada)
            Targeting.swing();
            for (Object target : targets) {
                try {
                    Targeting.attack(target);
                } catch (Throwable t) {
                    DebugLog.write("MobAura", "Erro ao atacar entidade: " + t.getMessage());
                }
            }
        }
    }

    public static void onRenderWorld(float partialTicks) {
        YNYConfig config = YNYUtils.getConfig();
        if (config == null || !config.mobAuraEnabled || !config.mobAuraVisuals) {
            return;
        }

        List<Object> targets = currentTargets;
        if (targets == null || targets.isEmpty()) {
            return;
        }

        try {
            for (int i = 0; i < targets.size(); i++) {
                Object t = targets.get(i);
                if (t == null || !Entities.isAlive(t)) continue;

                boolean isWith = MobKnockbackFilter.isWither(t);
                boolean isGuard = MobKnockbackFilter.isGuardian(t);
                // Wither em Roxo / Violeta
                // Guardian em destaque Vermelho / Dourado
                // Outro mob em Ciano / Azul
                int boxColor = isWith ? 0x408A2BE2 : (isGuard ? 0x40FF2020 : (i == 0 ? 0x40FFA000 : 0x4000D0FF));
                int outlineColor = isWith ? 0xFF8A2BE2 : (isGuard ? 0xFFFF2020 : (i == 0 ? 0xFFFFA000 : 0xFF00D0FF));

                final Object entityToRender = t;
                WorldRender.throughWalls(() -> {
                    WorldRender.entityBox(entityToRender, boxColor);
                    WorldRender.entityOutline(entityToRender, 2.0D, outlineColor);
                });
            }
        } catch (Throwable ignored) {}
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
                .option(Option.toggle("Não levar KB apenas do Guardian", () -> config.guardianOnlyKnockback, value -> {
                    config.guardianOnlyKnockback = value;
                    markConfigChanged();
                }))
                .option(Option.toggle("Não levar KB de outros Mobs", () -> config.mobKnockbackEnabled, value -> {
                    config.mobKnockbackEnabled = value;
                    markConfigChanged();
                }))
                .section("Mob Aura (Multi-Alvo & Foco Wither/Guardian)")
                .option(Option.toggle("Ligar/desligar Mob Aura", () -> config.mobAuraEnabled, value -> {
                    config.mobAuraEnabled = value;
                    if (config.mobAuraStatusHudEnabled) {
                        Chat.actionBar(value ? "§aMob Aura: ATIVADA" : "§cMob Aura: DESATIVADA");
                    }
                    markConfigChanged();
                }))
                .option(Option.key("Tecla para alternar Mob Aura", () -> config.mobAuraToggleKey, value -> {
                    config.mobAuraToggleKey = value;
                    markConfigChanged();
                }))
                .option(Option.cycle("Alvos simultâneos", new String[] { "1 mob", "2 mobs" },
                        () -> Math.max(0, config.mobAuraTargets - 1), value -> {
                    config.mobAuraTargets = value + 1;
                    markConfigChanged();
                }))
                .option(Option.toggle("Focar Wither primeiro", () -> config.mobAuraWitherPriority, value -> {
                    config.mobAuraWitherPriority = value;
                    markConfigChanged();
                }))
                .option(Option.toggle("Focar Guardian primeiro", () -> config.mobAuraGuardianPriority, value -> {
                    config.mobAuraGuardianPriority = value;
                    markConfigChanged();
                }))
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

    private static Method isBlockingMethod;
    private static boolean blockingMethodResolved;

    private static boolean isConsumableHolding() {
        try {
            Object self = Entities.self();
            if (self != null) {
                if (!blockingMethodResolved) {
                    blockingMethodResolved = true;
                    try {
                        for (Method m : self.getClass().getMethods()) {
                            if ("bW".equals(m.getName()) || "isBlocking".equals(m.getName())) {
                                isBlockingMethod = m;
                                m.setAccessible(true);
                                break;
                            }
                        }
                    } catch (Throwable ignored) {}
                }
                if (isBlockingMethod != null && Boolean.TRUE.equals(isBlockingMethod.invoke(self))) {
                    return false; // Bloqueando com espada -> NÃO pausar Mob Aura (permite bater defendendo!)
                }
            }
        } catch (Throwable ignored) {}

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

    private static final class Candidate {
        final Object entity;
        final double distance;
        final boolean isWither;
        final boolean isGuardian;

        Candidate(Object entity, double distance, boolean isWither, boolean isGuardian) {
            this.entity = entity;
            this.distance = distance;
            this.isWither = isWither;
            this.isGuardian = isGuardian;
        }
    }
}
