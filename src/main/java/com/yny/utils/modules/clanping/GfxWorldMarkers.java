package com.yny.utils.modules.clanping;

import com.yny.utils.core.DebugLog;
import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Relation;
import dev.xavier.stein.loader.api.Relations;
import java.lang.reflect.Method;

/** Uses WorldRender when present; the old Loader keeps the existing drawing path. */
public final class GfxWorldMarkers {
    private static boolean checked;
    private static boolean available;
    private static boolean failureLogged;
    private static Method ring, outline, label, beam;

    private GfxWorldMarkers() { }

    public static boolean render(ClanPing ping, float partialTicks) {
        if (!checked) initialize();
        if (!available) return false;
        try {
            Object self = Entities.self();
            if (self != null) {
                for (Object player : Entities.players()) {
                    if (player != self && Entities.isAlive(player) && Relations.of(player) == Relation.GROUP) {
                        ring.invoke(null, Entities.x(player, partialTicks), Entities.y(player, partialTicks) + 0.025,
                                Entities.z(player, partialTicks), 0.43D, 0.025D, 0xDC46DC96);
                    }
                }
            }
            ClanPing.Active active = ping.active;
            if (active == null || active.expired()) return true;
            if (active.enemy) {
                Object target = Entities.player(active.targetName);
                if (target == null) {
                    for (Object p : Entities.players()) {
                        if (active.targetName.equalsIgnoreCase(Entities.name(p))) {
                            target = p;
                            break;
                        }
                    }
                }
                String name = PingClanName.display(target, active.targetName);
                if (target != null && Entities.isAlive(target)) {
                    outline.invoke(null, target, 0.025D, 0xFFFF4545);
                    label.invoke(null, "§c◆ ATACAR ESTE INIMIGO §7• " + name,
                            Entities.x(target, partialTicks), Entities.y(target, partialTicks) + 2.3D,
                            Entities.z(target, partialTicks), 1.0F, 0xFFFF6565, true);
                } else {
                    ring.invoke(null, active.x, active.y + 0.05D, active.z, 0.65D, 0.035D, 0xFFFF4545);
                    label.invoke(null, "§c◆ ULTIMA POSICAO §7• " + name,
                            active.x, active.y + 2.0D, active.z, 1.0F, 0xFFFF8585, true);
                }
            } else {
                beam.invoke(null, active.x, active.z, active.y, active.y + 24.0D, 0.035D, 0xBB36CBE5);
                ring.invoke(null, active.x, active.y + 0.05D, active.z, 0.65D, 0.035D, 0xFF36CBE5);
                label.invoke(null, "LOCAL MARCADO", active.x, active.y + 2.0D, active.z,
                        1.0F, 0xFFB6F7FF, true);
            }
        } catch (Throwable error) {
            if (!failureLogged) {
                failureLogged = true;
                DebugLog.write("ClanPing", "event=gfx_world_render_failed; cause=" + error.getClass().getSimpleName());
            }
        }
        return true;
    }

    private static synchronized void initialize() {
        if (checked) return;
        checked = true;
        try {
            Class<?> world = Class.forName("dev.xavier.stein.loader.api.WorldRender");
            ring = world.getMethod("ring", double.class, double.class, double.class, double.class, double.class, int.class);
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
