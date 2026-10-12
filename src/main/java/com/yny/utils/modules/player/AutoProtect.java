package com.yny.utils.modules.player;

import com.yny.utils.config.YNYConfig;
import com.yny.utils.core.DebugLog;
import com.yny.utils.core.ToggleKey;
import dev.xavier.stein.loader.api.Chat;
import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Inventory;
import dev.xavier.stein.loader.api.Keys;
import dev.xavier.stein.loader.api.ModConfig;
import dev.xavier.stein.loader.api.Screens;
import dev.xavier.stein.loader.api.Stacks;
import dev.xavier.stein.loader.api.Keys.GameKey;

/** Owns sword defense without competing with InvMove or AutoConsumables. */
public final class AutoProtect {
    private static final ToggleKey toggleKey = new ToggleKey();
    private static boolean lowHealthActive;
    private static boolean holding;
    private static int previousSlot = -1;
    private static int swordSlot = -1;

    private AutoProtect() { }

    public static void checkToggleKey(YNYConfig config, ModConfig<YNYConfig> store) {
        if (config == null || config.autoProtectToggleKey == 0) return;
        if (toggleKey.wasPressed(config.autoProtectToggleKey)) {
            config.autoProtectEnabled = !config.autoProtectEnabled;
            if (!config.autoProtectEnabled) {
                reset();
            }
            Chat.actionBar(config.autoProtectEnabled ? "§aDefesa Automática: ATIVADA" : "§cDefesa Automática: DESATIVADA");
            DebugLog.write("AutoProtect", "event=toggle_key; enabled=" + config.autoProtectEnabled + "; key=" + config.autoProtectToggleKey);
            if (store != null) {
                store.saveSoon();
            }
        }
    }

    public static void onTick(YNYConfig config, boolean useKeyReserved) {
        Object self = Entities.self();
        if (config == null || !config.autoProtectEnabled || self == null || !Entities.isAlive(self)) {
            reset();
            return;
        }
        float health = Entities.health(self);
        if (config.autoProtectLowHealthEnabled) {
            if (health <= config.autoProtectLowHealth) lowHealthActive = true;
            if (health >= config.autoProtectResumeHealth) lowHealthActive = false;
        } else {
            lowHealthActive = false;
        }

        boolean screen = Screens.isOpen();
        boolean movingInScreen = shouldReleaseForMovement(screen, moving());
        boolean shouldDefend = (lowHealthActive || (config.autoProtectWindow && screen))
                && !movingInScreen && !useKeyReserved;
        if (!shouldDefend) {
            release(movingInScreen ? "invmove_moving" : useKeyReserved ? "use_key_reserved" : "condition_ended");
            return;
        }

        int selected = Inventory.selectedHotbar();
        if (!sword(Inventory.stack(selected))) {
            int found = -1;
            for (int slot = 0; slot < 9; slot++) {
                if (sword(Inventory.stack(slot))) { found = slot; break; }
            }
            if (found < 0) {
                release("no_sword_in_hotbar");
                return;
            }
            if (previousSlot < 0) previousSlot = selected;
            Inventory.selectHotbar(found);
            selected = Inventory.selectedHotbar();
            if (selected != found) return;
        }
        swordSlot = selected;
        if (!Inventory.isUsingItem()) Inventory.useHeldItem();
        if (Inventory.isUsingItem()) {
            Keys.game(GameKey.USE).hold(true);
            if (!holding) DebugLog.write("AutoProtect", "event=defense_started; reason="
                    + (lowHealthActive ? "low_health" : "window") + "; health=" + health + "; slot=" + selected);
            holding = true;
        }
    }

    public static boolean isControllingUseKey() { return holding; }

    public static void reset() {
        release("reset");
        lowHealthActive = false;
    }

    private static void release(String reason) {
        if (holding) {
            Keys.game(GameKey.USE).hold(false);
            DebugLog.write("AutoProtect", "event=defense_released; reason=" + reason);
            holding = false;
        }
        if (previousSlot >= 0 && swordSlot >= 0 && Inventory.selectedHotbar() == swordSlot) {
            Inventory.selectHotbar(previousSlot);
        }
        previousSlot = -1;
        swordSlot = -1;
    }

    static boolean shouldReleaseForMovement(boolean screen, boolean moving) {
        return screen && moving;
    }

    private static boolean moving() {
        return Keys.game(GameKey.FORWARD).isDown() || Keys.game(GameKey.BACK).isDown()
                || Keys.game(GameKey.LEFT).isDown() || Keys.game(GameKey.RIGHT).isDown()
                || Keys.game(GameKey.JUMP).isDown() || Keys.game(GameKey.SPRINT).isDown();
    }

    private static boolean sword(Object item) {
        String id = item == null ? null : Stacks.id(item);
        return id != null && id.endsWith("_sword");
    }
}
