package com.yny.utils.modules.player;

import com.yny.utils.config.YNYConfig;
import com.yny.utils.core.DebugLog;
import dev.xavier.stein.loader.api.Inventory;
import dev.xavier.stein.loader.api.Stacks;
import java.lang.reflect.Field;
import java.util.Arrays;

public final class ArmorPickupFilter {
    private static final Object[] baseline = new Object[36];
    private static final Object[] knownEquipped = new Object[4];
    private static final boolean[] queued = new boolean[36];
    private static final int[] dropAttempts = new int[36];
    private static final long[] attemptedAt = new long[36];
    private static final Field pendingField;
    private static final Field actionField;
    private static boolean initialized;
    private static long tick;

    static {
        Field pending = null;
        Field action = null;
        try {
            pending = AutoArmor.class.getDeclaredField("pending");
            pending.setAccessible(true);
            action = AutoArmor.class.getDeclaredField("nextClickAt");
            action.setAccessible(true);
        } catch (Exception ignored) {}
        pendingField = pending;
        actionField = action;
    }

    private ArmorPickupFilter() {}

    public static void reset() {
        Arrays.fill(baseline, null);
        Arrays.fill(knownEquipped, null);
        Arrays.fill(queued, false);
        Arrays.fill(dropAttempts, 0);
        Arrays.fill(attemptedAt, 0L);
        initialized = false;
        tick = 0L;
    }

    public static void onWindowItems(int window) {
        if (window == 0) {
            if (!initialized) {
                snapshot();
                initialized = true;
            }
        }
    }

    public static void onSlotChanged(int window, int slot, Object stack, YNYConfig config) {
        if (window != 0 || config == null || !config.autoArmorDiscardReceived) return;
        int index = Inventory.inventoryIndexOf(slot);
        if (index < 0 || index >= 36) return;
        if (index == Inventory.selectedHotbar()) return; // Never discard active held item
        if (stack == null) {
            queued[index] = false;
            baseline[index] = null;
            dropAttempts[index] = 0;
            return;
        }
        if (initialized && !isKnown(stack) && !isEquipped(stack) && reject(stack, config)) {
            queued[index] = true;
        }
    }

    public static void runAutoArmor(AutoArmor armor, YNYConfig config) {
        boolean wasSafe = safe(armor);
        long clickBefore = lastAction(armor);
        armor.onTickEnd();
        boolean armorActed = !wasSafe || clickBefore != lastAction(armor);
        process(config, armor, armorActed);
    }

    private static long lastAction(AutoArmor armor) {
        try {
            return actionField == null ? -1L : actionField.getLong(armor);
        } catch (Exception ignored) {
            return -1L;
        }
    }

    private static boolean process(YNYConfig config, AutoArmor armor, boolean blocked) {
        tick++;
        if (!initialized) {
            snapshot();
            initialized = true;
            return false;
        }
        if (config == null || !config.autoArmorDiscardReceived) {
            Arrays.fill(queued, false);
            Arrays.fill(dropAttempts, 0);
            snapshot();
            return false;
        }

        if (blocked || !safe(armor)) {
            return false;
        }

        // Scan slots 0 to 35 for newly received armor matching discard policy
        // Active held item (selectedHotbar) is never queued or dropped
        int heldSlot = Inventory.selectedHotbar();
        for (int i = 0; i < 36; i++) {
            if (i == heldSlot) continue;
            Object current = Inventory.stack(i);
            if (current == null) {
                queued[i] = false;
                baseline[i] = null;
                dropAttempts[i] = 0;
                continue;
            }
            if (!isKnown(current) && !isEquipped(current)) {
                if (reject(current, config)) {
                    queued[i] = true;
                } else {
                    baseline[i] = current;
                }
            }
        }

        // Refresh known equipped armor
        for (int a = 0; a < 4; a++) {
            knownEquipped[a] = Inventory.armor(a);
        }

        // Process at most 1 drop per tick for smooth execution without packet spam
        for (int i = 0; i < 36; i++) {
            if (i == heldSlot) continue;
            if (!queued[i]) continue;
            Object stack = Inventory.stack(i);
            if (stack == null || !reject(stack, config)) {
                queued[i] = false;
                baseline[i] = stack;
                dropAttempts[i] = 0;
                continue;
            }
            if (dropAttempts[i] >= 2) {
                queued[i] = false;
                baseline[i] = stack;
                continue;
            }
            if (attemptedAt[i] != 0 && tick - attemptedAt[i] < 10) {
                continue;
            }
            int slot = Inventory.slotOf(i);
            if (slot < 0 || Inventory.inventoryIndexOf(slot) != i) {
                continue;
            }
            attemptedAt[i] = tick;
            dropAttempts[i]++;
            baseline[i] = stack; // Prevent re-scan loop
            if (Inventory.drop(slot, true)) {
                queued[i] = false;
                DebugLog.write("AutoArmor", "filtro descartou armadura recebida no slot " + i + ": " + Stacks.name(stack));
                return true;
            } else {
                DebugLog.write("AutoArmor", "filtro: descarte recusado no slot " + i + "; tentativa " + dropAttempts[i]);
            }
        }
        return false;
    }

    private static boolean isKnown(Object stack) {
        if (stack == null) return false;
        for (int i = 0; i < 36; i++) {
            if (baseline[i] == stack) return true;
        }
        for (int i = 0; i < 4; i++) {
            if (knownEquipped[i] == stack) return true;
        }
        return false;
    }

    private static boolean isEquipped(Object stack) {
        if (stack == null) return false;
        for (int i = 0; i < 4; i++) {
            if (Inventory.armor(i) == stack) return true;
        }
        return false;
    }

    private static void snapshot() {
        for (int i = 0; i < 36; i++) {
            baseline[i] = Inventory.stack(i);
        }
        for (int a = 0; a < 4; a++) {
            knownEquipped[a] = Inventory.armor(a);
        }
    }

    private static boolean safe(AutoArmor armor) {
        if (Inventory.windowId() != 0 || Inventory.isScreenOpen() || Inventory.isContainerOpen() || Inventory.cursor() != null) {
            return false;
        }
        if (pendingField != null && armor != null) {
            try {
                if (pendingField.get(armor) != null) return false;
            } catch (Exception ignored) {
                return false;
            }
        }
        return true;
    }

    public static boolean reject(Object stack, YNYConfig config) {
        if (stack == null || config == null || !config.autoArmorDiscardReceived) return false;
        int piece = Inventory.armorPiece(stack);
        if (piece < 0) return false;
        // Never discard armor if the player has an empty or broken slot for this piece!
        Object worn = Inventory.armor(piece);
        if (worn == null || isBrokenStack(worn)) {
            return false;
        }
        int max = Stacks.maxDamage(stack);
        int remaining = max - Stacks.damage(stack);
        boolean enchanted = Stacks.isEnchanted(stack);
        int protection = Stacks.enchantLevel(stack, 0); // 0 = Protection in Minecraft 1.8.9
        return ArmorPickupPolicy.shouldDiscard(config, piece, remaining, max, enchanted, protection);
    }

    private static boolean isBrokenStack(Object stack) {
        if (stack == null) return true;
        int max = Stacks.maxDamage(stack);
        return max > 0 && Stacks.damage(stack) >= max;
    }
}
