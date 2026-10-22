package com.yny.utils.modules.clanping;

import com.yny.utils.core.DebugLog;
import com.yny.utils.core.SafeEntities;
import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Relation;
import dev.xavier.stein.loader.api.Relations;
import java.lang.reflect.Method;

/**
 * High-performance world marker renderer utilizing SteinLoader's native WorldRender pipeline.
 * Renders large, distance-adaptive nametags visible through walls even from far away ("mesmo de longe").
 */
public final class GfxWorldMarkers {
    private static boolean checked;
    private static boolean available;
    private static boolean failureLogged;
    private static Method ring, circle, outline, label, beam, throughWalls;

    private GfxWorldMarkers() { }

    public static boolean isAvailable() {
        if (!checked) initialize();
        return available;
    }

    /**
     * Renderização de alta visibilidade e ZERO LAG do marcador nos pés ("bolinha") dos membros do mesmo clã.
     * Visível através de paredes (throughWalls) com contorno circular preenchido e anel de brilho pulsante.
     */
    public static void renderAllyMarkers(float partialTicks) {
        if (!checked) initialize();
        if (!available) return;
        try {
            Object self = Entities.self();
            if (self == null) return;

            // Pré-filtração rápida com zero alocação de heap por frame
            Runnable drawAction = () -> {
                try {
                    float pulse = 0.88F + 0.12F * (float) Math.sin(System.currentTimeMillis() * 0.005D);
                    int alphaCircle = Math.min(255, Math.max(90, (int) (0x99 * pulse)));
                    int alphaRing = Math.min(255, Math.max(160, (int) (0xFF * pulse)));
                    int circleColor = (alphaCircle << 24) | 0x0000EE66; // High visibility emerald green filled disc ("bolinha")
                    int ringColor = (alphaRing << 24) | 0x0033FF77;   // Vibrant glowing green outer ring

                    for (Object player : SafeEntities.safePlayers()) {
                        if (player != self && Entities.isAlive(player) && !PingClanName.isDuelTarget(player)) {
                            String pName = Entities.name(player);
                            if (Relations.of(player) == Relation.GROUP || PingClanName.isSameClan(player, pName)) {
                                double px = Entities.x(player, partialTicks);
                                double py = Entities.y(player, partialTicks) + 0.035D;
                                double pz = Entities.z(player, partialTicks);
                                if (circle != null) {
                                    circle.invoke(null, px, py, pz, 0.52D, circleColor);
                                }
                                if (ring != null) {
                                    ring.invoke(null, px, py, pz, 0.52D, 0.04D, ringColor);
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            };

            if (throughWalls != null) {
                throughWalls.invoke(null, drawAction);
            } else {
                drawAction.run();
            }
        } catch (Throwable error) {
            if (!failureLogged) {
                failureLogged = true;
                DebugLog.write("GfxWorldMarkers", "event=ally_ground_render_failed; cause=" + error.getClass().getSimpleName());
            }
        }
    }

    public static boolean render(ClanPing ping, float partialTicks) {
        if (!checked) initialize();
        if (!available) return false;
        ClanPing.Active active = ping != null ? ping.active : null;
        if (active == null || active.expired() || !PingWorld.isSameWorld(active)) return false;
        try {
            Runnable drawAction = () -> {
                try {
                    Object self = Entities.self();
                    double selfX = self != null ? Entities.x(self, partialTicks) : active.x;
                    double selfY = self != null ? Entities.y(self, partialTicks) : active.y;
                    double selfZ = self != null ? Entities.z(self, partialTicks) : active.z;

                    if (active.enemy) {
                        Object target = Entities.player(active.targetName);
                        if (target == null) {
                            for (Object p : SafeEntities.safePlayers()) {
                                if (active.targetName.equalsIgnoreCase(Entities.name(p))) {
                                    target = p;
                                    break;
                                }
                            }
                        }

                        if (target != null && Entities.isAlive(target)) {
                            double tx = Entities.x(target, partialTicks);
                            double ty = Entities.y(target, partialTicks);
                            double tz = Entities.z(target, partialTicks);
                            active.updatePosition(tx, ty, tz);

                            double dist = Math.sqrt((tx - selfX) * (tx - selfX) + (ty - selfY) * (ty - selfY) + (tz - selfZ) * (tz - selfZ));
                            // Distance adaptive scale: grows proportionally with distance so nametag stays large & readable
                            float baseScale = (float) Math.max(1.35F, 0.95F + dist * 0.082F);

                            // Body highlight outline + foot ring
                            outline.invoke(null, target, 0.025D, 0xFFFF4545);
                            ring.invoke(null, tx, ty + 0.05D, tz, 0.65D, 0.035D, 0xFFFF4545);

                            String displayed = PingClanName.display(target, active.targetName);
                            String heroNick = formatHeroNick(displayed);
                            double heroY = ty + 2.75D + Math.max(0.0D, (baseScale - 1.0D) * 0.12D);

                            renderNametag(
                                "§c§l⚔ FOCAR INIMIGO ⚔", 0xFFFF5555,
                                heroNick, 0xFFFFFFFF,
                                "§e" + (int) Math.round(dist) + "m §7• §aFIXO", 0xFFFFFFAA,
                                tx, heroY, tz, baseScale
                            );
                        } else {
                            // Enemy target out of entity render distance: render at last known position
                            double tx = active.x;
                            double ty = active.y;
                            double tz = active.z;
                            double dist = Math.sqrt((tx - selfX) * (tx - selfX) + (ty - selfY) * (ty - selfY) + (tz - selfZ) * (tz - selfZ));
                            float baseScale = (float) Math.min(50.0F, Math.max(1.35F, 0.95F + dist * 0.082F));

                            double beamRadius = Math.max(0.20D, Math.min(1.8D, 0.08D + dist * 0.0025D));
                            double beamTop = Math.max(ty + 256.0D, 260.0D);
                            if (circle != null) {
                                circle.invoke(null, tx, ty + 0.05D, tz, Math.max(0.75D, Math.min(3.5D, 0.5D + dist * 0.003D)), 0x44FF4545);
                            }
                            ring.invoke(null, tx, ty + 0.05D, tz, Math.max(0.75D, Math.min(3.5D, 0.5D + dist * 0.003D)), 0.04D, 0xFFFF4545);
                            beam.invoke(null, tx, tz, ty, beamTop, beamRadius, 0xCCFF3535);

                            String displayed = PingClanName.display(null, active.targetName);
                            String heroNick = formatHeroNick(displayed);
                            double heroY = ty + 2.45D + Math.max(0.0D, (baseScale - 1.0D) * 0.35D);

                            renderNametag(
                                "§c§l⚠ ÚLTIMA POSIÇÃO", 0xFFFF5555,
                                heroNick, 0xFFFFFFFF,
                                "§e" + (int) Math.round(dist) + "m §7• §cFORA DO RENDER", 0xFFFFAAAA,
                                tx, heroY, tz, baseScale
                            );
                        }
                    } else {
                        // Location waypoint
                        double tx = active.x;
                        double ty = active.y;
                        double tz = active.z;
                        double dist = Math.sqrt((tx - selfX) * (tx - selfX) + (ty - selfY) * (ty - selfY) + (tz - selfZ) * (tz - selfZ));
                        float baseScale = (float) Math.min(50.0F, Math.max(1.30F, 0.90F + dist * 0.080F));

                        double beamRadius = Math.max(0.20D, Math.min(1.8D, 0.08D + dist * 0.0025D));
                        double beamTop = Math.max(ty + 256.0D, 260.0D);
                        beam.invoke(null, tx, tz, ty, beamTop, beamRadius, 0xBB36CBE5);
                        if (circle != null) {
                            circle.invoke(null, tx, ty + 0.05D, tz, Math.max(0.75D, Math.min(3.5D, 0.5D + dist * 0.003D)), 0x4436CBE5);
                        }
                        ring.invoke(null, tx, ty + 0.05D, tz, Math.max(0.75D, Math.min(3.5D, 0.5D + dist * 0.003D)), 0.04D, 0xFF36CBE5);

                        double heroY = ty + 2.2D + Math.max(0.0D, (baseScale - 1.0D) * 0.35D);
                        renderNametag(
                            "§b§l📍 PONTO MARCADO", 0xFF55FFFF,
                            "§f§l" + active.author, 0xFFFFFFFF,
                            "§e" + (int) Math.round(dist) + "m §7• §bFIXO", 0xFFB6F7FF,
                            tx, heroY, tz, baseScale
                        );
                    }
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            };

            if (throughWalls != null) {
                throughWalls.invoke(null, drawAction);
            } else {
                drawAction.run();
            }
        } catch (Throwable error) {
            if (!failureLogged) {
                failureLogged = true;
                DebugLog.write("ClanPing", "event=gfx_world_render_failed; cause=" + error.getClass().getSimpleName());
            }
        }
        return true;
    }

    private static void renderNametag(String badgeText, int badgeColor,
                                      String heroText, int heroColor,
                                      String metaText, int metaColor,
                                      double x, double y, double z,
                                      float baseScale) {
        if (label == null) return;
        try {
            double gapY = 0.30D * baseScale;
            // Line 1: Action Badge (top)
            if (badgeText != null && !badgeText.isEmpty()) {
                label.invoke(null, badgeText, x, y + gapY, z, baseScale * 0.72F, badgeColor, true);
            }
            // Line 2: HERO Nick (center, large & bold, true nametag)
            if (heroText != null && !heroText.isEmpty()) {
                label.invoke(null, heroText, x, y, z, baseScale * 1.35F, heroColor, true);
            }
            // Line 3: Meta / Distance (bottom)
            if (metaText != null && !metaText.isEmpty()) {
                label.invoke(null, metaText, x, y - (0.26D * baseScale), z, baseScale * 0.68F, metaColor, true);
            }
        } catch (Throwable ignored) {}
    }

    public static String formatHeroNick(String text) {
        if (text == null || text.trim().isEmpty()) return "§c§lInimigo";
        String clean = text.replaceAll("§.", "").trim();
        if (clean.startsWith("[") && clean.contains("]")) {
            int end = clean.indexOf(']');
            String tag = clean.substring(1, end).trim();
            String nick = clean.substring(end + 1).trim();
            return "§e[" + tag + "] §f§l" + nick;
        }
        return "§f§l" + clean;
    }

    private static synchronized void initialize() {
        if (checked) return;
        checked = true;
        try {
            Class<?> world = Class.forName("dev.xavier.stein.loader.api.WorldRender");
            ring = world.getMethod("ring", double.class, double.class, double.class, double.class, double.class, int.class);
            try {
                circle = world.getMethod("circle", double.class, double.class, double.class, double.class, int.class);
            } catch (NoSuchMethodException ignored) {}
            try {
                throughWalls = world.getMethod("throughWalls", Runnable.class);
            } catch (NoSuchMethodException ignored) {}
            outline = world.getMethod("entityOutline", Object.class, double.class, int.class);
            label = world.getMethod("label", String.class, double.class, double.class, double.class, float.class, int.class, boolean.class);
            beam = world.getMethod("beam", double.class, double.class, double.class, double.class, double.class, int.class);
            available = true;
            DebugLog.write("System", "event=world_render_api; mode=Stein_GFX_compatible");
        } catch (ClassNotFoundException oldLoader) {
            available = false;
            DebugLog.write("System", "event=world_render_api; mode=legacy_loader");
        } catch (ReflectiveOperationException changedApi) {
            available = true;
            if (!failureLogged) {
                failureLogged = true;
                DebugLog.write("System", "event=world_render_api; mode=incompatible_signature");
            }
        }
    }
}
