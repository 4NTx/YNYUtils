package com.yny.utils.modules.player;

import dev.xavier.stein.loader.api.Inventory;
import dev.xavier.stein.loader.api.Keys;
import dev.xavier.stein.loader.api.Stacks;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Proteção contra descarte acidental de itens e armas no chão (Drop Protection).
 * Impede que o jogador jogue sua espada, arco ou ferramentas por engano no chão,
 * e bloqueia qualquer descarte acidental quando a proteção estiver ativada.
 */
public final class DropProtection {

    private static volatile boolean enabled = false;

    // Cache reflexivo para manipulação segura da tecla de drop vanilla (gameSettings.keyBindDrop)
    private static volatile boolean keyDropLookupAttempted = false;
    private static volatile Object vanillaKeyBindDrop = null;
    private static volatile Field pressTimeField = null;
    private static volatile Field pressedField = null;

    private DropProtection() {}

    public static boolean isEnabled() {
        com.yny.utils.config.YNYConfig config = com.yny.utils.YNYUtils.getConfig();
        return enabled || (config != null && config.dropProtectionEnabled);
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        com.yny.utils.config.YNYConfig config = com.yny.utils.YNYUtils.getConfig();
        if (config != null) {
            config.dropProtectionEnabled = value;
        }
    }

    public static void toggle() {
        setEnabled(!isEnabled());
    }

    /**
     * Verifica se o item fornecido é uma arma ou ferramenta crítica.
     */
    public static boolean isWeapon(Object item) {
        if (item == null) return false;
        try {
            String id = Stacks.id(item);
            if (id == null) return false;
            String lower = id.toLowerCase(java.util.Locale.ROOT);
            return lower.endsWith("_sword") || lower.endsWith("bow")
                    || lower.endsWith("_axe") || lower.endsWith("_pickaxe")
                    || lower.endsWith("_spade") || lower.endsWith("_shovel");
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Verifica se o item atualmente na mão principal é uma arma protegida.
     */
    public static boolean isHoldingWeapon() {
        try {
            Object held = Inventory.heldItem();
            return isWeapon(held);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void enforce() {
        boolean protectActive = isEnabled() || isHoldingWeapon();
        if (!protectActive) {
            return;
        }

        try {
            Keys.Binding drop = Keys.game(Keys.GameKey.DROP);
            if (drop != null) {
                while (drop.consumeClick()) {
                    // Drena e descarta cliques da tecla de drop
                }
            }
        } catch (Throwable ignored) {}

        // Neutraliza o KeyBinding vanilla do Minecraft (keyBindDrop) antes que runTickKeyboard o leia
        suppressVanillaDrop();
    }

    private static void suppressVanillaDrop() {
        try {
            initVanillaKeyDrop();
            if (vanillaKeyBindDrop != null) {
                if (pressTimeField != null) {
                    pressTimeField.setInt(vanillaKeyBindDrop, 0);
                }
                if (pressedField != null) {
                    pressedField.setBoolean(vanillaKeyBindDrop, false);
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void initVanillaKeyDrop() {
        if (keyDropLookupAttempted) return;
        keyDropLookupAttempted = true;
        try {
            Class<?> aveClass = null;
            try {
                aveClass = Class.forName("ave");
            } catch (ClassNotFoundException e) {
                try {
                    aveClass = Class.forName("net.minecraft.client.Minecraft");
                } catch (Throwable ignored) {}
            }
            if (aveClass == null) return;

            Object mc = aveClass.getMethod(aveClass.getName().equals("ave") ? "A" : "getMinecraft").invoke(null);
            if (mc == null) return;

            Field gameSettingsField = null;
            for (Field f : aveClass.getDeclaredFields()) {
                if (f.getName().equals("t") || f.getName().equals("gameSettings")) {
                    gameSettingsField = f;
                    break;
                }
            }
            if (gameSettingsField == null) return;
            gameSettingsField.setAccessible(true);
            Object gs = gameSettingsField.get(mc);
            if (gs == null) return;

            Class<?> gsClass = gs.getClass();
            Field dropField = null;
            for (Field f : gsClass.getDeclaredFields()) {
                if (f.getName().equals("ac") || f.getName().equals("keyBindDrop")) {
                    dropField = f;
                    break;
                }
            }
            if (dropField == null) return;
            dropField.setAccessible(true);
            vanillaKeyBindDrop = dropField.get(gs);

            if (vanillaKeyBindDrop != null) {
                Class<?> kbClass = vanillaKeyBindDrop.getClass();
                for (Field f : kbClass.getDeclaredFields()) {
                    f.setAccessible(true);
                    if (f.getType() == int.class && !Modifier.isStatic(f.getModifiers())
                            && (f.getName().equals("b") || f.getName().equals("pressTime"))) {
                        pressTimeField = f;
                    } else if (f.getType() == boolean.class && !Modifier.isStatic(f.getModifiers())
                            && (f.getName().equals("h") || f.getName().equals("pressed"))) {
                        pressedField = f;
                    }
                }
            }
        } catch (Throwable ignored) {}
    }
}
