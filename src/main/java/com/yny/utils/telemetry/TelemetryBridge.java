package com.yny.utils.telemetry;

import dev.xavier.stein.loader.api.Hud;
import dev.xavier.stein.loader.api.Page;

/**
 * Ponto de ponte entre o ciclo de vida do SteinLoader no YNYUtils e o subsistema de telemetria/HUD.
 * Garante a fixação obrigatória e invariável dos HUDs táticos do clã.
 */
public final class TelemetryBridge {

    private static boolean initialized = false;
    private static int hudLockTickCounter = 0;

    private static final class HudLockRule {
        final String id;
        final int anchorX;
        final int anchorY;
        final int x;
        final int y;
        final float scale;
        final boolean showInChat;

        HudLockRule(String id, int anchorX, int anchorY, int x, int y, float scale, boolean showInChat) {
            this.id = id;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.x = x;
            this.y = y;
            this.scale = scale;
            this.showInChat = showInChat;
        }
    }

    private static final HudLockRule[] LOCKED_HUDS = new HudLockRule[] {
        new HudLockRule("ynyutils.watermark", 0, 2, 0, 0, 0.75F, true)
    };

    private TelemetryBridge() {}

    public static void afterStartGame() {
        if (initialized) return;
        initialized = true;
        try {
            TelemetryIdentity identity = TelemetryIdentity.get();
            TelemetryConfig config = TelemetryConfig.get();
            Hud.register(new WatermarkHud(identity, config));
            Hud.register(new com.yny.utils.ui.BrokenArmorHud());
            ensureBrokenArmorHudPlacement();
            enforceHudLock();
            TelemetryService.get().recordLaunch();
        } catch (Throwable t) {
            // Proteção estrita contra falhas em inicialização
        }
    }

    private static void ensureBrokenArmorHudPlacement() {
        try {
            Class<?> regClass = Class.forName("dev.xavier.stein.loader.hud.HudRegistry");
            java.lang.reflect.Field field = regClass.getDeclaredField("placements");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> map = (java.util.Map<String, Object>) field.get(null);
            if (map != null) {
                Object p = map.get("ynyutils.broken-armor-alerts");
                if (p != null) {
                    Class<?> pClass = p.getClass();
                    java.lang.reflect.Field fEnabled = pClass.getDeclaredField("enabled");
                    fEnabled.setAccessible(true);
                    java.lang.reflect.Field fY = pClass.getDeclaredField("y");
                    fY.setAccessible(true);
                    if (fY.getInt(p) == 48 && !fEnabled.getBoolean(p)) {
                        fEnabled.setBoolean(p, true);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    public static void ensureHudEnabled() {
        enforceHudLock();
    }

    public static void enforceHudLock() {
        try {
            Class<?> regClass = Class.forName("dev.xavier.stein.loader.hud.HudRegistry");
            java.lang.reflect.Field field = regClass.getDeclaredField("placements");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> map = (java.util.Map<String, Object>) field.get(null);
            if (map != null) {
                Class<?> placementClass = Class.forName("dev.xavier.stein.loader.hud.Placement");
                java.lang.reflect.Field fAnchorX = placementClass.getDeclaredField("anchorX");
                java.lang.reflect.Field fAnchorY = placementClass.getDeclaredField("anchorY");
                java.lang.reflect.Field fX = placementClass.getDeclaredField("x");
                java.lang.reflect.Field fY = placementClass.getDeclaredField("y");
                java.lang.reflect.Field fScale = placementClass.getDeclaredField("scale");
                java.lang.reflect.Field fEnabled = placementClass.getDeclaredField("enabled");
                java.lang.reflect.Field fShowInChat = placementClass.getDeclaredField("showInChat");

                fAnchorX.setAccessible(true);
                fAnchorY.setAccessible(true);
                fX.setAccessible(true);
                fY.setAccessible(true);
                fScale.setAccessible(true);
                fEnabled.setAccessible(true);
                fShowInChat.setAccessible(true);

                for (HudLockRule rule : LOCKED_HUDS) {
                    Object p = map.get(rule.id);
                    if (p == null) {
                        java.lang.reflect.Constructor<?> ctor = placementClass.getDeclaredConstructor();
                        ctor.setAccessible(true);
                        p = ctor.newInstance();
                        map.put(rule.id, p);
                    }
                    fAnchorX.setInt(p, rule.anchorX);
                    fAnchorY.setInt(p, rule.anchorY);
                    fX.setInt(p, rule.x);
                    fY.setInt(p, rule.y);
                    fScale.setFloat(p, rule.scale);
                    fEnabled.setBoolean(p, true);
                    fShowInChat.setBoolean(p, rule.showInChat);
                }
            }
        } catch (Throwable ignored) {}
    }

    public static void onJoinGame() {
        try {
            enforceHudLock();
            TelemetryService.get().recordServerJoin();
        } catch (Throwable t) {
            // Proteção estrita
        }
    }

    public static void onTickEnd() {
        try {
            hudLockTickCounter++;
            if (hudLockTickCounter % 20 == 0) {
                enforceHudLock();
            }
            TelemetryService.get().refreshClan();
        } catch (Throwable ignored) { }
    }

    public static void onDisconnected() {
        try {
            TelemetryService.get().recordServerLeave();
        } catch (Throwable t) {
            // Proteção estrita
        }
    }

    public static void onShutdown() {
        try {
            TelemetryService.get().shutdown();
        } catch (Throwable t) {
            // Proteção estrita
        }
    }

    public static void addOptions(Page page) {
        // Identificação & Auditoria removida do menu a pedido da liderança
    }
}
