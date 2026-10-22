package com.yny.utils.modules.player;

import com.yny.utils.config.YNYConfig;
import com.yny.utils.core.DebugLog;
import com.yny.utils.core.SafeEntities;
import com.yny.utils.core.ToggleKey;
import java.util.List;
import com.yny.utils.modules.clanping.PingClanName;
import dev.xavier.stein.loader.api.Chat;
import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Inventory;
import dev.xavier.stein.loader.api.Keys;
import dev.xavier.stein.loader.api.ModConfig;
import dev.xavier.stein.loader.api.Relation;
import dev.xavier.stein.loader.api.Relations;
import dev.xavier.stein.loader.api.Screens;
import dev.xavier.stein.loader.api.Stacks;
import dev.xavier.stein.loader.api.Keys.GameKey;

/** Owns sword defense without competing with InvMove or AutoConsumables. */
public final class AutoProtect {
    private static final int COMBAT_WINDOW = 60;
    private static final int OVERRIDE_TICKS = 60;
    private static final double ENEMY_RADIUS = 6.0D;

    private static final ToggleKey toggleKey = new ToggleKey();
    private static boolean lowHealthActive;
    private static boolean holding;
    private static int previousSlot = -1;
    private static int swordSlot = -1;
    private static int combatTicks;
    private static float lastHealth = -1.0F;
    private static int manualOverrideTicks;

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
        if (Entities.hurtTime(self) > 0 || (lastHealth >= 0.0F && health < lastHealth)) {
            combatTicks = COMBAT_WINDOW;
        } else if (combatTicks > 0) {
            combatTicks--;
        }
        lastHealth = health;

        if (manualOverrideTicks > 0) {
            manualOverrideTicks--;
        }

        Object currentScreen = Screens.current();
        boolean screen = currentScreen != null;

        // Se uma tela de livro estiver aberta (GuiScreenBook / leitura), solta a defesa e pausa
        if (isBookScreen(currentScreen)) {
            if (holding || swordSlot >= 0) {
                release("book_screen");
            }
            manualOverrideTicks = OVERRIDE_TICKS;
            return;
        }

        // Se o jogador estiver segurando um livro na mão ou no cursor (clicou num livro no inventário)
        Object held = Inventory.heldItem();
        Object cursor = Inventory.cursor();
        if (isBook(held) || isBook(cursor)) {
            if (holding || swordSlot >= 0) {
                release("book_interaction");
            }
            manualOverrideTicks = OVERRIDE_TICKS;
            return;
        }

        // Se o jogador trocou de slot manualmente (dentro de telas ou fora dela):
        if (holding && swordSlot >= 0 && Inventory.selectedHotbar() != swordSlot) {
            release("manual_slot_switch");
            manualOverrideTicks = OVERRIDE_TICKS;
            return;
        }

        boolean fighting = combatTicks > 0 || isEnemyNear(self, ENEMY_RADIUS);

        if (config.autoProtectLowHealthEnabled) {
            if (!lowHealthActive) {
                if (health <= config.autoProtectLowHealth && fighting) {
                    lowHealthActive = true;
                }
            } else if (health >= config.autoProtectResumeHealth) {
                lowHealthActive = false;
                manualOverrideTicks = 0;
            }
        } else {
            lowHealthActive = false;
        }

        boolean movingInScreen = shouldReleaseForMovement(screen, moving());
        boolean lowHealthDefense = lowHealthActive && fighting && manualOverrideTicks == 0;
        boolean containerScreen = isContainerScreen(currentScreen);
        boolean windowDefense = config.autoProtectWindow && containerScreen && manualOverrideTicks == 0;

        boolean shouldDefend = (lowHealthDefense || windowDefense)
                && !movingInScreen && !useKeyReserved && PriorityBus.canDefend();

        if (!shouldDefend) {
            release(movingInScreen ? "invmove_moving"
                    : useKeyReserved ? "use_key_reserved"
                    : !PriorityBus.canDefend() ? "priority_busy"
                    : manualOverrideTicks > 0 ? "manual_override"
                    : !fighting && lowHealthActive ? "combat_ended"
                    : "condition_ended");
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
        PriorityBus.setSwordSlot(selected);
        if (!Inventory.isUsingItem()) Inventory.useHeldItem();
        Keys.game(GameKey.USE).hold(true);
        if (!holding && DebugLog.isEnabled()) DebugLog.write("AutoProtect", "event=defense_started; reason="
                + (lowHealthActive ? "low_health" : "window") + "; health=" + health + "; slot=" + selected);
        holding = true;
    }

    public static boolean isEnemyNear(Object self, double radius) {
        if (self == null) return false;
        try {
            List<Object> players = SafeEntities.safePlayers();
            if (players == null || players.isEmpty()) return false;
            for (Object player : players) {
                if (player == null || player == self || !Entities.isAlive(player)) continue;
                if (Entities.distance(self, player) > radius) {
                    continue;
                }
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
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static boolean isControllingUseKey() { return holding; }

    public static void reset() {
        release("reset");
        lowHealthActive = false;
        combatTicks = 0;
        manualOverrideTicks = 0;
        lastHealth = -1.0F;
        PriorityBus.setSwordSlot(-1);
    }

    private static void release(String reason) {
        if (holding) {
            Keys.game(GameKey.USE).hold(false);
            if (DebugLog.isEnabled()) DebugLog.write("AutoProtect", "event=defense_released; reason=" + reason);
            holding = false;
        }
        if (previousSlot >= 0 && swordSlot >= 0 && Inventory.selectedHotbar() == swordSlot) {
            Inventory.selectHotbar(previousSlot);
        }
        previousSlot = -1;
        swordSlot = -1;
        PriorityBus.setSwordSlot(-1);
    }

    static boolean shouldReleaseForMovement(boolean screen, boolean moving) {
        return screen && moving;
    }

    private static boolean moving() {
        return Keys.game(GameKey.FORWARD).isDown() || Keys.game(GameKey.BACK).isDown()
                || Keys.game(GameKey.LEFT).isDown() || Keys.game(GameKey.RIGHT).isDown()
                || Keys.game(GameKey.JUMP).isDown() || Keys.game(GameKey.SPRINT).isDown();
    }

    public static boolean sword(Object item) {
        String id = item == null ? null : Stacks.id(item);
        return id != null && id.endsWith("_sword");
    }

    public static boolean isBook(Object item) {
        if (item == null) return false;
        String id = Stacks.id(item);
        if (id == null) return false;
        String lower = id.toLowerCase();
        return lower.endsWith("_book") || lower.equals("minecraft:book") || lower.equals("book")
                || lower.contains("book") || lower.contains("livro");
    }

    public static boolean isBookScreen(Object screen) {
        if (screen == null) return false;
        String name = screen.getClass().getSimpleName();
        if ("GuiScreenBook".equals(name) || "ayo".equals(name) || name.contains("Book") || name.contains("Livro")) {
            return true;
        }
        Class<?> sc = screen.getClass();
        while (sc != null && sc != Object.class) {
            String sname = sc.getSimpleName();
            if ("GuiScreenBook".equals(sname) || "ayo".equals(sname) || sname.contains("Book") || sname.contains("Livro")) {
                return true;
            }
            sc = sc.getSuperclass();
        }
        return false;
    }

    public static boolean isContainerScreen(Object screen) {
        if (screen == null) return false;
        if (isBookScreen(screen)) return false;

        try {
            Screens.Kind kind = Screens.currentKind();
            if (kind == Screens.Kind.CHAT || kind == Screens.Kind.PAUSE
                    || kind == Screens.Kind.OPTIONS || kind == Screens.Kind.STEIN
                    || kind == Screens.Kind.MAIN_MENU || kind == Screens.Kind.NONE
                    || kind == Screens.Kind.SINGLEPLAYER || kind == Screens.Kind.MULTIPLAYER
                    || kind == Screens.Kind.DISCONNECTED) {
                return false;
            }

            if (kind == Screens.Kind.INVENTORY || kind == Screens.Kind.CONTAINER) {
                return true;
            }
        } catch (Throwable ignored) {}

        try {
            if (Inventory.isScreenOpen()) {
                return true;
            }
        } catch (Throwable ignored) {}

        Class<?> sc = screen.getClass();
        while (sc != null && sc != Object.class) {
            String sname = sc.getSimpleName();
            if ("GuiContainer".equals(sname) || "ayl".equals(sname)) {
                return true;
            }
            sc = sc.getSuperclass();
        }
        return false;
    }
}
