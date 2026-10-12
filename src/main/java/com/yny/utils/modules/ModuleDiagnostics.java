package com.yny.utils.modules;

import com.yny.utils.core.DebugLog;
import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Relation;
import dev.xavier.stein.loader.api.Relations;
import dev.xavier.stein.loader.api.Stacks;
import net.minecraft.item.ItemStack;
import net.minecraft.client.Minecraft;

/** Event-focused diagnostics shared by modules that cannot own a logger instance. */
public final class ModuleDiagnostics {
    private static volatile int lastInventoryControl = -1;
    private static volatile long lastAllySnapshot;
    private static volatile int lastShiftTicks = -1;
    private ModuleDiagnostics() { }

    public static void inventoryState(boolean controlling) {
        int next = controlling ? 1 : 0;
        if (lastInventoryControl == next) return;
        lastInventoryControl = next;
        String state = controlling ? "active" : "released";
        if (controlling) {
            try {
                Minecraft mc = Minecraft.getMinecraft();
                String screen = mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName();
                state += "; screen=" + screen + "; keys[w=" + mc.gameSettings.keyBindForward.isKeyDown()
                        + ",a=" + mc.gameSettings.keyBindLeft.isKeyDown() + ",s=" + mc.gameSettings.keyBindBack.isKeyDown()
                        + ",d=" + mc.gameSettings.keyBindRight.isKeyDown() + ",jump=" + mc.gameSettings.keyBindJump.isKeyDown()
                        + ",sprint=" + mc.gameSettings.keyBindSprint.isKeyDown() + "]";
            } catch (Throwable ignored) { state += "; input=unavailable"; }
        }
        DebugLog.state("InventoryMove", "key_control", state);
    }

    public static void inventoryUpdate(int window, int slot, Object value) {
        String item = "empty";
        if (value instanceof ItemStack) {
            ItemStack stack = (ItemStack)value;
            try { item = Stacks.name(stack); } catch (Throwable ignored) { item = "item"; }
            if (Stacks.isDamageable(stack)) item += "; durability=" + Math.max(0, Stacks.maxDamage(stack) - Stacks.damage(stack)) + "/" + Stacks.maxDamage(stack);
        }
        DebugLog.write("Inventory", "slot_update; window=" + window + "; slot=" + slot + "; item=" + item);
    }

    public static void inventorySnapshot(int window) {
        DebugLog.write("Inventory", "window_snapshot_received; window=" + window);
    }

    public static void combatEvent(String event, Object entity) {
        if (entity == null) {
            DebugLog.write("Combat", "event=" + event + "; entity=none");
            return;
        }
        String name;
        try { name = Entities.displayName(entity); } catch (Throwable ignored) { name = entity.getClass().getSimpleName(); }
        Relation relation;
        try { relation = Relations.of(entity); } catch (Throwable ignored) { relation = Relation.NONE; }
        DebugLog.write("Combat", "event=" + event + "; entity=" + name + "; relation=" + relation.name());
    }

    public static void healthEvent(Object entity, float oldHealth, float newHealth,
                                   float oldAbsorption, float newAbsorption) {
        if (Math.abs(oldHealth - newHealth) < 0.01F && Math.abs(oldAbsorption - newAbsorption) < 0.01F) return;
        combatEvent("health_change", entity);
        DebugLog.write("DamageIndicator", "health_event; health=" + oldHealth + "->" + newHealth
                + "; absorption=" + oldAbsorption + "->" + newAbsorption);
    }

    public static void systemEvent(String event, String detail) {
        DebugLog.write("System", "event=" + event + (detail == null || detail.isEmpty() ? "" : "; detail=" + detail));
    }

    public static void knockbackShiftVelocity(int x, int z) {
        if (x != 0 || z != 0) DebugLog.write("KnockbackShiftReset", "velocity_received; x=" + x + "; z=" + z);
    }

    public static void knockbackShiftState(int ticks) {
        if (lastShiftTicks == ticks) return;
        lastShiftTicks = ticks;
        DebugLog.write("KnockbackShiftReset", "release_countdown=" + ticks);
    }

    public static void allyMarkerSnapshot() {
        long now = System.currentTimeMillis();
        if (now - lastAllySnapshot < 2000L) return;
        lastAllySnapshot = now;
        int allies = 0;
        try {
            for (Object player : Entities.players()) if (Relations.of(player) == Relation.GROUP) allies++;
            DebugLog.state("AllyGroundMarker", "loaded_group_players", Integer.toString(allies));
        } catch (Throwable ignored) { }
    }
    public static void clanPing(String event) {
        DebugLog.write("ClanPing", "event=" + event);
    }

    public static void clanPingStatus(String status) {
        DebugLog.write("ClanPing", "transport=" + String.valueOf(status));
    }
}








