package com.yny.utils.modules.pvp;

import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Keys;
import dev.xavier.stein.loader.api.Relation;
import dev.xavier.stein.loader.api.Relations;
import dev.xavier.stein.loader.api.Screens;
import dev.xavier.stein.loader.api.Targeting;
import dev.xavier.stein.loader.api.Targeting.BlockFilter;
import dev.xavier.stein.loader.api.Targeting.Hit;
import dev.xavier.stein.loader.api.Targeting.Query;
import dev.xavier.stein.loader.api.Targeting.Ray;
import com.yny.utils.core.DebugLog;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class HitThroughWeb {
    private static final BlockFilter WEB_FILTER = HitThroughWeb::isWebBlock;
    private static volatile boolean webAttacking = false;
    private static long lastAttackMs = 0L;

    private HitThroughWeb() {}

    public static boolean isWebAttacking() {
        return webAttacking;
    }

    public static boolean isWebBlock(String blockId) {
        if (blockId == null) return false;
        String s = blockId.toLowerCase(Locale.ROOT);
        return s.contains("web");
    }

    /**
     * Checks if cobweb is blocking the crosshair or if the player is stuck in web.
     */
    public static boolean isBlockedByWeb() {
        try {
            double reach = Targeting.attackReach();
            Ray vanillaRay = Targeting.raycast(Targeting.query().reach(reach).living().cutAtBlock());
            if (vanillaRay != null && isWebBlock(vanillaRay.blockId)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /**
     * Finds an enemy target that can be hit through cobweb.
     * Guaranteed:
     * - Only passes through cobweb ("SÓ TEIA"), stops at solid blocks.
     * - Same clan members are NEVER hit (100% pass-through).
     * - Allies have 50% chance to be hit or pass through.
     */
    public static Object findTargetThroughWeb() {
        try {
            double reach = Math.max(3.0D, Targeting.attackReach());
            // 1. Raycast through cobweb only with entity expansion margin
            Query webQuery = Targeting.query()
                    .reach(reach)
                    .living()
                    .margin(0.2D)
                    .passThrough(WEB_FILTER)
                    .cutAtBlock();
            Ray webRay = Targeting.raycast(webQuery);
            if (webRay == null || webRay.hits == null || webRay.hits.isEmpty()) {
                return null;
            }

            // 2. Check if vanilla raycast was blocked by cobweb (or inside cobweb)
            Ray vanillaRay = Targeting.raycast(Targeting.query().reach(reach).living().cutAtBlock());
            boolean webObstacle = (vanillaRay != null && isWebBlock(vanillaRay.blockId));

            // If vanilla raycast already found an entity with NO web blockage and it is NOT a teammate, let vanilla handle it
            if (!webObstacle && vanillaRay != null && vanillaRay.hits != null && !vanillaRay.hits.isEmpty()) {
                Hit firstVanillaHit = vanillaRay.first();
                if (firstVanillaHit != null && firstVanillaHit.entity != null) {
                    if (!HitThroughAllies.isSameClanMember(firstVanillaHit.entity)) {
                        Relation rel = Relations.of(firstVanillaHit.entity);
                        if (rel != Relation.GROUP && rel != Relation.SELF) {
                            return null;
                        }
                    }
                }
            }

            java.util.List<Hit> hits = new java.util.ArrayList<>(webRay.hits);
            hits.sort(new java.util.Comparator<Hit>() {
                @Override
                public int compare(Hit a, Hit b) {
                    return Double.compare(a.distance, b.distance);
                }
            });
            for (Hit hit : hits) {
                if (hit == null) continue;
                if (hit.distance >= webRay.blockDistance) break;
                if (hit.entity == null) continue;

                // Same clan is NEVER hit (100% pass-through)
                if (HitThroughAllies.isSameClanMember(hit.entity)) {
                    continue;
                }

                Relation rel = Relations.of(hit.entity);
                if (rel == Relation.GROUP || rel == Relation.SELF) {
                    continue;
                }

                if (rel == Relation.ALLY) {
                    boolean hitAlly = ThreadLocalRandom.current().nextBoolean();
                    if (!hitAlly) {
                        continue;
                    }
                }

                return hit.entity;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Attacks the entity behind the cobweb.
     */
    public static boolean tryAttackThroughWeb() {
        return tryAttackThroughWeb(false);
    }

    public static boolean tryAttackThroughWeb(boolean needSwing) {
        if (webAttacking) return false;
        try {
            if (Screens.isOpen()) return false;

            long now = System.currentTimeMillis();
            if (now - lastAttackMs < 50L) {
                return false; // Previne envio duplicado de pacotes no mesmo tick
            }

            Object target = findTargetThroughWeb();
            if (target == null) return false;

            lastAttackMs = now;
            webAttacking = true;
            try {
                Targeting.attack(target);
                if (needSwing) {
                    Targeting.swing();
                }
                DebugLog.write("HitThroughWeb", "event=attack_through_web; target=" + Entities.name(target));
            } finally {
                webAttacking = false;
            }
            return true;
        } catch (Throwable t) {
            webAttacking = false;
            return false;
        }
    }

    public static void onMouseInput() {
        try {
            if (Screens.isOpen()) return;
            if (Keys.mousePressed() && Keys.mouseButton() == 0) {
                tryAttackThroughWeb(false);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void onTickEnd() {
        try {
            if (Screens.isOpen()) return;
            if (Keys.isMouseDown(0)) {
                tryAttackThroughWeb(true);
            }
        } catch (Throwable ignored) {
        }
    }
}
