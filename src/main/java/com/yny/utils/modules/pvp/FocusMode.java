package com.yny.utils.modules.pvp;

import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.yny.utils.YNYUtils;
import com.yny.utils.config.YNYConfig;
import com.yny.utils.core.DebugLog;
import com.yny.utils.core.SafeEntities;
import com.yny.utils.core.ToggleKey;
import com.yny.utils.modules.player.AutoArmorHelper;
import com.yny.utils.modules.player.PriorityBus;

import dev.xavier.stein.loader.api.Chat;
import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Keys;
import dev.xavier.stein.loader.api.Screens;
import dev.xavier.stein.loader.api.Targeting;
import dev.xavier.stein.loader.api.Terrain;
import dev.xavier.stein.loader.api.WorldRender;

/**
 * Modo Foco (PvP Focus Mode):
 * Foca e prioriza ataques no adversário que estiver sem capacete por mais de 1 segundo
 * (ou sem outra peça de armadura), respeitando paredes sólidas, clã/aliados e telas.
 *
 * Inclui:
 * 1. Rastreamento temporal exato de tempo sem capacete/armadura (>= 1.0s).
 * 2. Redirecionamento inteligente de ataque (onAttackEntity e clique contínuo).
 * 3. Marcador visual 3D distinto e luminoso sobre o alvo em foco.
 * 4. Zero-Allocation de GC em ticks e frames.
 */
public final class FocusMode {

    private static final Map<String, Long> withoutHelmetSince = new ConcurrentHashMap<>();
    private static final Map<String, Long> withoutAnyArmorSince = new ConcurrentHashMap<>();

    private static volatile Object currentFocusTarget = null;
    private static volatile String currentFocusReason = null;
    private static volatile int currentFocusPriority = 0; // 2 = sem capacete (>1s), 1 = sem outra peça (>1s)

    private static long lastAttackMs = 0L;
    private static final ToggleKey toggleKey = new ToggleKey();
    private static long cleanCounter = 0L;
    private static volatile boolean isRedirecting = false;

    private FocusMode() {}

    public static Object getCurrentFocusTarget() {
        return currentFocusTarget;
    }

    public static String getCurrentFocusReason() {
        return currentFocusReason;
    }

    public static void reset() {
        withoutHelmetSince.clear();
        withoutAnyArmorSince.clear();
        currentFocusTarget = null;
        currentFocusReason = null;
        currentFocusPriority = 0;
        lastAttackMs = 0L;
        cleanCounter = 0L;
        isRedirecting = false;
    }

    public static boolean toggleState() {
        YNYConfig config = YNYUtils.getConfig();
        if (config == null) return false;
        return setState(!config.focusModeEnabled);
    }

    public static boolean setState(boolean enabled) {
        YNYConfig config = YNYUtils.getConfig();
        if (config == null) return false;
        config.focusModeEnabled = enabled;
        if (!enabled) {
            currentFocusTarget = null;
            currentFocusReason = null;
            currentFocusPriority = 0;
            withoutHelmetSince.clear();
            withoutAnyArmorSince.clear();
        }
        AutoArmorHelper.playToggleSound(enabled);
        try {
            Chat.actionBar(enabled ? "§a§lModo Foco: ATIVADO" : "§c§lModo Foco: DESATIVADO");
            Chat.print("§8[§c§lYNY§8] §7Modo Foco: " + (enabled ? "§a§lATIVADO §7(focando adversários sem capacete/armadura)" : "§c§lDESATIVADO"));
        } catch (Throwable ignored) {}
        if (YNYUtils.getConfigStore() != null) {
            YNYUtils.getConfigStore().saveSoon();
        }
        return enabled;
    }

    public static void updateToggleKey() {
        YNYConfig config = YNYUtils.getConfig();
        if (config == null || config.focusToggleKey == 0) return;
        if (toggleKey.wasPressed(config.focusToggleKey)) {
            toggleState();
        }
    }

    /**
     * Avaliação executada a 20 TPS no onTickEnd.
     */
    public static void onTickEnd() {
        try {
            CpsRateLimiter.sanitizeMouseEvents();
            CpsRateLimiter.syncHudCps();

            YNYConfig config = YNYUtils.getConfig();
            if (config == null || !config.focusModeEnabled) {
                currentFocusTarget = null;
                currentFocusReason = null;
                currentFocusPriority = 0;
                withoutHelmetSince.clear();
                withoutAnyArmorSince.clear();
                return;
            }

            Object self = Entities.self();
            if (self == null || !Entities.isAlive(self)) {
                currentFocusTarget = null;
                currentFocusReason = null;
                currentFocusPriority = 0;
                return;
            }

            if (Screens.isOpen()) {
                return;
            }

            long now = System.currentTimeMillis();
            long thresholdMs = Math.max(500L, (long) config.focusHelmetTimeSeconds * 1000L); // Padrão 1000ms = 1 segundo
            double reach = config.focusReach > 0 ? config.focusReach : 3.0D;
            double trackingDist = Math.max(reach, (double) config.brokenArmorMaxDistance);

            Object bestTarget = null;
            String bestReason = null;
            int bestPriority = 0;
            double bestDist = Double.MAX_VALUE;
            double bestScore = -Double.MAX_VALUE;

            List<Object> playerList = SafeEntities.safePlayers();
            for (Object player : playerList) {
                if (player == null || player == self) continue;
                if (!Entities.isAlive(player)) {
                    String deadName = Entities.name(player);
                    if (deadName != null) {
                        withoutHelmetSince.remove(deadName);
                        withoutAnyArmorSince.remove(deadName);
                    }
                    continue;
                }
                if (MobKnockbackFilter.isArmorStand(player)) continue;

                String name = Entities.name(player);
                if (name == null || name.isEmpty()) continue;

                // NUNCA focar membros do mesmo clã ou aliados
                if (BrokenArmorHighlight.isSameClanOrAlly(player, name)) {
                    withoutHelmetSince.remove(name);
                    withoutAnyArmorSince.remove(name);
                    continue;
                }

                double dist = Entities.distance(self, player);
                if (dist > trackingDist) {
                    continue;
                }

                // Checagem de armadura: 0 = Bota, 1 = Calça, 2 = Peitoral, 3 = Elmo
                Object boots = BrokenArmorHighlight.getArmorItem(player, 0);
                Object legs = BrokenArmorHighlight.getArmorItem(player, 1);
                Object chest = BrokenArmorHighlight.getArmorItem(player, 2);
                Object helm = BrokenArmorHighlight.getArmorItem(player, 3);

                // Ignorar staff invisível no vanish sem armadura
                if (boots == null && legs == null && chest == null && helm == null) {
                    try {
                        if (Entities.isInvisible(player)) continue;
                    } catch (Throwable ignored) {}
                }

                // Atualiza tempo sem capacete para todos no raio tático
                if (helm == null) {
                    if (!withoutHelmetSince.containsKey(name)) {
                        withoutHelmetSince.put(name, now);
                    }
                } else {
                    withoutHelmetSince.remove(name);
                }

                // Atualiza tempo sem qualquer peça de armadura
                boolean missingAny = (helm == null || chest == null || legs == null || boots == null);
                if (missingAny) {
                    if (!withoutAnyArmorSince.containsKey(name)) {
                        withoutAnyArmorSince.put(name, now);
                    }
                } else {
                    withoutAnyArmorSince.remove(name);
                }

                // Candidato a ataque precisa estar dentro do alcance de golpe
                if (dist > reach) {
                    continue;
                }

                // Avalia se atende os critérios do Modo Foco
                Long helmSince = withoutHelmetSince.get(name);
                boolean noHelmEligible = (helmSince != null && (now - helmSince >= thresholdMs));

                Long anySince = withoutAnyArmorSince.get(name);
                boolean anyEligible = !config.focusOnlyHelmet && (anySince != null && (now - anySince >= thresholdMs));

                if (!noHelmEligible && !anyEligible) {
                    continue;
                }

                // Verifica se o alvo está no campo de visão frontal do jogador (evita bater de costas)
                if (!isInViewCone(self, player)) {
                    continue;
                }

                // Verifica linha de visão desobstruída (sem parede sólida entre o jogador e o alvo)
                if (!isLineOfSightClear(self, player)) {
                    continue;
                }

                int priority = 0;
                String reason = null;
                if (noHelmEligible) {
                    priority = 2; // Prioridade máxima: sem capacete por mais de 1s
                    reason = "§c§l🎯 FOCO: SEM CAPACETE";
                } else if (anyEligible) {
                    priority = 1; // Prioridade secundária: sem peitoral/calça/bota
                    if (chest == null) reason = "§c§l🎯 FOCO: SEM PEITORAL";
                    else if (legs == null) reason = "§c§l🎯 FOCO: SEM CALÇA";
                    else reason = "§c§l🎯 FOCO: SEM BOTA";
                }

                // Ponderação inteligente: prioridade (elmo > outras peças), centralização na mira (dot) e distância
                double dot = getLookDotProduct(self, player);
                double score = (priority * 100.0D) + (dot * 5.0D) - dist;

                if (score > bestScore) {
                    bestScore = score;
                    bestTarget = player;
                    bestReason = reason;
                    bestPriority = priority;
                    bestDist = dist;
                }
            }

            currentFocusTarget = bestTarget;
            currentFocusReason = bestReason;
            currentFocusPriority = bestPriority;

            // Limpeza periódica inteligente: remove apenas quem se desconectou ou saiu da render distance
            cleanCounter++;
            if (cleanCounter >= 100L) {
                cleanCounter = 0L;
                if (!withoutHelmetSince.isEmpty() || !withoutAnyArmorSince.isEmpty()) {
                    java.util.Set<String> activeNames = new java.util.HashSet<>();
                    for (Object p : SafeEntities.safePlayers()) {
                        if (p != null && Entities.isAlive(p)) {
                            String n = Entities.name(p);
                            if (n != null) activeNames.add(n);
                        }
                    }
                    withoutHelmetSince.keySet().retainAll(activeNames);
                    withoutAnyArmorSince.keySet().retainAll(activeNames);
                }
            }

            // Se o botão esquerdo estiver pressionado continuamente no tick e a mira não estiver em entidade:
            if (currentFocusTarget != null && Keys.isMouseDown(0) && Targeting.pointedEntity() == null) {
                tryAttackFocusTarget(true);
            }
        } catch (ConcurrentModificationException ignored) {
        } catch (Throwable ignored) {}
    }

    /**
     * Intercepta cliques de mouse para atacar o alvo focado sem duplicar pacotes vanilla.
     */
    public static void onMouseInput() {
        try {
            if (Screens.isOpen()) return;
            if (Keys.mousePressed() && Keys.mouseButton() == 0) {
                // Apenas ataca no mouseInput se a mira vanilla não estiver apontando para uma entidade
                // (se estiver apontando, onAttackEntity cuidará do hit sem duplicar pacotes!)
                if (Targeting.pointedEntity() == null) {
                    tryAttackFocusTarget(false);
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Redireciona o ataque vanilla quando o jogador tenta golpear outra entidade (ex: um oponente de armadura cheia),
     * garantindo que o hit acerte o alvo vulnerável sem capacete/armadura.
     * Retorna true se redirecionou o ataque (cancelando o ataque vanilla original).
     */
    public static boolean onAttackEntity(Object originalTarget) {
        if (isRedirecting || HitThroughWeb.isWebAttacking() || MobAura.isAuraAttacking()) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - lastAttackMs < 50L) {
            return true; // Já desferiu ataque neste clique/tick, cancela pacote duplicado
        }

        YNYConfig config = YNYUtils.getConfig();
        if (config == null || !config.focusModeEnabled) return false;
        if (isRedirecting) return false;

        Object focus = currentFocusTarget;
        if (focus == null || !Entities.isAlive(focus)) return false;

        // Se o jogador já clicou no próprio alvo focado, preserva o ataque vanilla normalmente
        if (originalTarget == focus) return false;

        Object self = Entities.self();
        if (self == null) return false;

        double reach = config.focusReach > 0 ? config.focusReach : 3.0D;
        if (Entities.distance(self, focus) > reach) return false;

        if (!isInViewCone(self, focus)) return false;
        if (!isLineOfSightClear(self, focus)) return false;

        // Redireciona o golpe com sucesso para o alvo vulnerável
        lastAttackMs = System.currentTimeMillis();
        isRedirecting = true;
        try {
            Targeting.attack(focus);
            // O Minecraft vanilla (clickMouse) já invocou thePlayer.swingItem() imediatamente
            // antes de onAttackEntity ser chamado. Chamar Targeting.swing() aqui enviava um pacote
            // C0APacketAnimation DUPLICADO ao servidor para o mesmo clique, inflando o CPS do anti-cheat!
            DebugLog.write("FocusMode", "Ataque redirecionado para " + Entities.name(focus) + " (" + currentFocusReason + ")");
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            isRedirecting = false;
        }
    }

    public static boolean tryAttackFocusTarget() {
        return tryAttackFocusTarget(false);
    }

    /**
     * Desfere o golpe no alvo focado com cadência controlada pelo CpsRateLimiter (máximo 25 CPS).
     * @param needSwing se true, despacha Targeting.swing(); se false, não despacha (porque vanilla já deu swing).
     */
    public static boolean tryAttackFocusTarget(boolean needSwing) {
        YNYConfig config = YNYUtils.getConfig();
        if (config == null || !config.focusModeEnabled) return false;
        if (isRedirecting) return false;

        Object focus = currentFocusTarget;
        if (focus == null || !Entities.isAlive(focus)) return false;

        if (Screens.isOpen()) return false;
        if (PriorityBus.isCursorBusy() || PriorityBus.isConsuming()) return false;

        Object self = Entities.self();
        if (self == null) return false;

        double reach = config.focusReach > 0 ? config.focusReach : 3.0D;
        if (Entities.distance(self, focus) > reach) return false;

        if (!isInViewCone(self, focus)) return false;
        if (!isLineOfSightClear(self, focus)) return false;

        long now = System.currentTimeMillis();
        if (now - lastAttackMs < 50L) {
            return false; // Previne envio duplicado no mesmo tick
        }
        lastAttackMs = now;
        isRedirecting = true;
        try {
            Targeting.attack(focus);
            if (needSwing) {
                Targeting.swing();
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            isRedirecting = false;
        }
    }

    /**
     * Calcula o produto escalar entre o vetor de visão do jogador e a direção do alvo.
     * Retorna ~1.0 quando o alvo está à frente, ~0.0 a 90 graus nas laterais, e < -0.15 quando às costas.
     */
    public static double getLookDotProduct(Object self, Object target) {
        if (self == null || target == null) return -1.0D;
        try {
            float yaw = Entities.yaw(self, 1.0F);
            double radYaw = Math.toRadians(yaw);
            double lx = -Math.sin(radYaw);
            double lz = Math.cos(radYaw);

            double sx = Entities.x(self, 1.0F);
            double sz = Entities.z(self, 1.0F);
            double tx = Entities.x(target, 1.0F);
            double tz = Entities.z(target, 1.0F);

            double dx = tx - sx;
            double dz = tz - sz;
            double dLen = Math.sqrt(dx * dx + dz * dz);
            if (dLen <= 0.001D) return 1.0D;

            return (lx * (dx / dLen)) + (lz * (dz / dLen));
        } catch (Throwable ignored) {
            return 1.0D;
        }
    }

    /**
     * Retorna true se o alvo estiver no cone de visão frontal/lateral útil do jogador (arco de ~190°).
     * Evita ataques anti-naturais e bloqueios de mira para alvos que estejam 100% às costas do jogador.
     */
    public static boolean isInViewCone(Object self, Object target) {
        return getLookDotProduct(self, target) >= -0.15D;
    }

    private static double renderTx, renderTy, renderTz;
    private static int renderRingColor;
    private static String renderLabel;

    private static final Runnable RENDER_ACTION = () -> {
        try {
            // 1. Anel luminoso duplo no chão sob os pés do alvo focado (vermelho/dourado de mira)
            WorldRender.ring(renderTx, renderTy + 0.04D, renderTz, 0.60D, 0.08D, renderRingColor);
            WorldRender.circle(renderTx, renderTy + 0.02D, renderTz, 0.52D, 0x48FF1133);

            // 2. Coluna luminosa translúcida destacando o corpo do alvo
            WorldRender.beam(renderTx, renderTz, renderTy, renderTy + 1.90D, 0.46D, 0x32FF1133);

            // 3. Etiqueta 3D flutuante de FOCO alto (py + 3.10D) para não colidir com o nick ou tags de armadura
            WorldRender.label(renderLabel, renderTx, renderTy + 3.10D, renderTz, 1.25F, 0xFFFFFFFF, true);
        } catch (Throwable ignored) {}
    };

    /**
     * Renderização no mundo 3D com destaque visual exclusivo sobre o alvo em foco.
     */
    public static void onRenderWorld(float partialTicks) {
        CpsRateLimiter.sanitizeMouseEvents();
        CpsRateLimiter.syncHudCps();
        YNYConfig config = YNYUtils.getConfig();
        if (config == null || !config.focusModeEnabled || !config.focusVisuals) return;

        Object target = currentFocusTarget;
        if (target == null || !Entities.isAlive(target)) return;

        try {
            renderTx = Entities.x(target, partialTicks);
            renderTy = Entities.y(target, partialTicks);
            renderTz = Entities.z(target, partialTicks);
            renderRingColor = currentFocusPriority == 2 ? 0xFFFF1133 : 0xFFFF7700;
            renderLabel = currentFocusReason != null ? currentFocusReason : "§c§l🎯 FOCO";

            WorldRender.throughWalls(RENDER_ACTION);
        } catch (Throwable ignored) {}
    }

    /**
     * Validação geométrica precisa e rápida de linha de visão sem colisões com blocos sólidos.
     * Teias e vegetação são permitidas; blocos maciços (pedra, obsidiana, etc.) bloqueiam a mira.
     */
    public static boolean isLineOfSightClear(Object self, Object target) {
        if (self == null || target == null) return false;
        try {
            double sx = Entities.x(self, 1.0F);
            double sy = Entities.y(self, 1.0F) + 1.62D; // Altura dos olhos
            double sz = Entities.z(self, 1.0F);

            double tx = Entities.x(target, 1.0F);
            double ty = Entities.y(target, 1.0F) + 1.20D; // Centro de massa do alvo
            double tz = Entities.z(target, 1.0F);

            double dx = tx - sx;
            double dy = ty - sy;
            double dz = tz - sz;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist <= 0.05D) return true;

            int steps = (int) Math.ceil(dist / 0.38D);
            double stepX = dx / steps;
            double stepY = dy / steps;
            double stepZ = dz / steps;

            double cx = sx;
            double cy = sy;
            double cz = sz;

            int maxCheck = Math.max(1, steps - 1);
            for (int i = 0; i < maxCheck; i++) {
                cx += stepX;
                cy += stepY;
                cz += stepZ;
                int bx = (int) Math.floor(cx);
                int by = (int) Math.floor(cy);
                int bz = (int) Math.floor(cz);

                if (Terrain.isSolid(bx, by, bz)) {
                    String blockId = Terrain.blockId(bx, by, bz);
                    if (blockId == null) return false;
                    String id = blockId.toLowerCase();
                    // Teia, vidro, cerca, portas, barras, plantas e escadas não bloqueiam a visão do combate
                    if (id.contains("web") || id.contains("glass") || id.contains("fence")
                            || id.contains("door") || id.contains("torch") || id.contains("ladder")
                            || id.contains("sign") || id.contains("water") || id.contains("lava")
                            || id.contains("bars") || id.contains("pane") || id.contains("carpet")
                            || id.contains("flower") || id.contains("grass") || id.contains("plant")
                            || id.contains("vine") || id.contains("leaves") || id.contains("snow")
                            || id.contains("slab") || id.contains("stair")) {
                        continue;
                    }
                    return false;
                }
            }
            return true;
        } catch (Throwable ignored) {
            return true;
        }
    }
}
