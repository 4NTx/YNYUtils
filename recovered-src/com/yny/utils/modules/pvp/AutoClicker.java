package com.yny.utils.modules.pvp;

import java.util.Locale;

import com.yny.utils.YNYUtils;
import com.yny.utils.config.YNYConfig;
import com.yny.utils.core.DebugLog;
import com.yny.utils.ui.PvpStatusHud;

import dev.xavier.stein.loader.api.Chat;
import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Inventory;
import dev.xavier.stein.loader.api.Keys;
import dev.xavier.stein.loader.api.ModConfig;
import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Page;
import dev.xavier.stein.loader.api.Screens;

/**
 * Módulo AutoClick nativo do YNYUtils.
 * Executa cliques com taxa configurável de CPS (1 a 30) a cada frame,
 * suportando modo segurando o botão esquerdo (Hold) ou constante (Always on).
 * Notifica diretamente o HUD de status de PvP (PvpStatusHud).
 */
public final class AutoClicker {

    private static final long STALL_NS = 100_000_000L;
    private static final int MAX_PER_FRAME = 8;
    private static long next;
    private static boolean wasToggleKeyDown;

    private AutoClicker() {}

    /**
     * Chamado a cada frame na thread de renderização do jogo.
     */
    public static void onRenderWorld(float partialTicks) {
        YNYConfig config = YNYUtils.getConfig();
        if (config == null || !config.autoclickEnabled) {
            next = 0L;
            return;
        }

        boolean always = config.autoclickMode == 1;
        boolean trigger = always || Keys.isMouseDown(0);

        if (Screens.isOpen() || Entities.self() == null || !trigger || handBusy()) {
            next = 0L;
            return;
        }

        long now = System.nanoTime();
        long interval = 1_000_000_000L / Math.max(1, config.autoclickCps);

        if (next == 0L) {
            if (!always) {
                next = now + interval; // Segurando: o clique físico é o primeiro, o autoclick vem depois
                return;
            }
            next = now;
        }

        if (now - next > STALL_NS) {
            next = now;
        }

        for (int n = 0; now >= next && n < MAX_PER_FRAME; n++) {
            Keys.click(0);
            next += interval;
        }

        if (now >= next) {
            next = now + interval;
        }
    }

    /**
     * Chamado a cada tick para verificar atalho de teclado (Keybind).
     */
    public static void updateToggleKey() {
        YNYConfig config = YNYUtils.getConfig();
        if (config == null || config.autoclickToggleKey == 0) {
            wasToggleKeyDown = false;
            return;
        }

        boolean down = !Screens.isOpen() && Keys.isDown(config.autoclickToggleKey);
        if (down && !wasToggleKeyDown) {
            config.autoclickEnabled = !config.autoclickEnabled;
            notifyStatus(config.autoclickEnabled);
            DebugLog.write("AutoClicker", "event=toggle_key; enabled=" + config.autoclickEnabled
                    + "; key=" + config.autoclickToggleKey);
            markConfigChanged();
        }
        wasToggleKeyDown = down;
    }

    /**
     * Envia notificação para o PvpStatusHud (mesmo HUD de Auto Armor).
     */
    public static void notifyStatus(boolean enabled) {
        YNYConfig config = YNYUtils.getConfig();
        if (config != null && !config.autoclickStatusHudEnabled) {
            return;
        }
        try {
            Class<?> hud = Class.forName("com.yny.utils.ui.PvpStatusHud");
            hud.getMethod("notifyAutoclick", boolean.class).invoke(null, enabled);
        } catch (Throwable t) {
            Chat.actionBar(enabled ? "§aAutoClick: ATIVADO" : "§cAutoClick: DESATIVADO");
        }
    }

    public static void reset() {
        next = 0L;
        wasToggleKeyDown = false;
    }

    private static boolean handBusy() {
        if (Inventory.isUsingItem()) {
            return true;
        }
        try {
            Class<?> ap = Class.forName("com.yny.utils.modules.player.AutoProtect");
            java.lang.reflect.Method m = ap.getMethod("isControllingUseKey");
            if (Boolean.TRUE.equals(m.invoke(null))) {
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Adiciona todas as configurações do AutoClick diretamente na página do YNYUtils.
     */
    public static void addOptions(Page page) {
        YNYConfig config = YNYUtils.getConfig();
        if (page == null || config == null) return;

        page.section("AutoClick")
                .option(Option.toggle("Ligar/desligar AutoClick", () -> config.autoclickEnabled, value -> {
                    config.autoclickEnabled = value;
                    notifyStatus(value);
                    markConfigChanged();
                }))
                .option(Option.key("Tecla para alternar AutoClick", () -> config.autoclickToggleKey, value -> {
                    config.autoclickToggleKey = value;
                    markConfigChanged();
                }))
                .option(Option.cycle("Modo de ativação", new String[] { "Segurando o botão", "Constante" },
                        () -> Math.max(0, Math.min(1, config.autoclickMode)), value -> {
                    config.autoclickMode = value;
                    markConfigChanged();
                }))
                .option(Option.slider("Cliques por segundo (CPS)", 1, 30, 1,
                        () -> config.autoclickCps, value -> {
                    config.autoclickCps = (int) Math.round(value);
                    markConfigChanged();
                }, value -> (int) Math.round(value) + " CPS"))
                .option(Option.toggle("Notificação de AutoClick no HUD", () -> config.autoclickStatusHudEnabled, value -> {
                    config.autoclickStatusHudEnabled = value;
                    markConfigChanged();
                }));
    }

    private static void markConfigChanged() {
        ModConfig<YNYConfig> store = YNYUtils.getConfigStore();
        if (store != null) {
            store.saveSoon();
        }
    }
}
