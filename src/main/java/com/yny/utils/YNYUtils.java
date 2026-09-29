package com.yny.utils;

import com.yny.utils.config.YNYConfig;
import com.yny.utils.modules.pvp.KnockbackControl;
import com.yny.utils.modules.pvp.ReachDebug;
import com.yny.utils.ui.KnockbackStatusHud;

import dev.xavier.stein.loader.api.Hud;
import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Page;
import dev.xavier.stein.loader.api.SteinMod;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/** Entry point do YNYUtils para o Stein Loader. */
public final class YNYUtils implements SteinMod {

    private static final int CONFIG_SAVE_DELAY_TICKS = 20;

    private static volatile YNYConfig config = new YNYConfig();
    private final KnockbackControl knockbackControl = new KnockbackControl(
            () -> config.knockbackEnabled, () -> config.knockbackPercent);
    private final ReachDebug reachDebug = new ReachDebug(
            () -> config.reachDebugEnabled, () -> config.reachDebugReach);
    private final KnockbackStatusHud knockbackStatusHud = new KnockbackStatusHud();
    private boolean toggleKeyWasDown;
    private boolean configDirty;
    private int configSaveDelay;

    @Override
    public void afterStartGame() {
        config = YNYConfig.load();
        Hud.register(knockbackStatusHud);
    }

    @Override
    public void onTickEnd() {
        knockbackControl.installIfPending();
        reachDebug.update();
        updateToggleKey();
        saveConfigIfDue();
    }

    @Override
    public void onJoinGame() {
        knockbackControl.requestInstallation();
    }

    @Override
    public void onPage(Page page) {
        page.title("YNYUtils")
                .icon("combat")
                .section("PvP")
                .option(Option.toggle("Ativar Knockback Control", () -> config.knockbackEnabled, value -> {
                    config.knockbackEnabled = value;
                    knockbackStatusHud.show(value);
                    markConfigChanged();
                }))
                .option(Option.slider("Velocidade recebida", 93, 100, 1, () -> config.knockbackPercent, value -> {
                    config.knockbackPercent = (int) value;
                    markConfigChanged();
                }, value -> (int) value + "% (redução: " + (100 - (int) value) + "%)"))
                .option(Option.key("Tecla para alternar", () -> config.knockbackToggleKey, value -> {
                    config.knockbackToggleKey = value;
                    markConfigChanged();
                }))
                .section("Diagnóstico")
                .option(Option.toggle("Reach Debug", () -> config.reachDebugEnabled, value -> {
                    config.reachDebugEnabled = value;
                    markConfigChanged();
                }))
                .option(Option.slider("Alcance de diagnóstico", ReachDebug.VANILLA_REACH, ReachDebug.MAX_REACH, 0.1,
                        () -> config.reachDebugReach, value -> {
                            config.reachDebugReach = value;
                            markConfigChanged();
                        }, value -> String.format("%.1f blocos", value)));
    }

    @Override
    public void onOverlay(float partialTicks) {
        reachDebug.draw();
    }

    private void updateToggleKey() {
        int key = config.knockbackToggleKey;
        boolean down = key != Keyboard.KEY_NONE && Minecraft.getMinecraft().currentScreen == null
                && Keyboard.isKeyDown(key);
        if (down && !toggleKeyWasDown) {
            config.knockbackEnabled = !config.knockbackEnabled;
            knockbackStatusHud.show(config.knockbackEnabled);
            markConfigChanged();
        }
        toggleKeyWasDown = down;
    }

    /** Agrupa mudanças rápidas de UI/keybind em uma única escrita no disco. */
    private void markConfigChanged() {
        configDirty = true;
        configSaveDelay = CONFIG_SAVE_DELAY_TICKS;
    }

    private void saveConfigIfDue() {
        if (!configDirty) {
            return;
        }
        if (configSaveDelay > 0) {
            configSaveDelay--;
            return;
        }
        YNYConfig.save(config);
        configDirty = false;
    }
}
