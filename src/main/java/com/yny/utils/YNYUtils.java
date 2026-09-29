package com.yny.utils;

import com.yny.utils.config.YNYConfig;
import com.yny.utils.core.ToggleKey;
import com.yny.utils.modules.pvp.KnockbackControl;
import com.yny.utils.modules.pvp.CustomReach;
import com.yny.utils.ui.PvpStatusHud;

import dev.xavier.stein.loader.api.Hud;
import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Page;
import dev.xavier.stein.loader.api.SteinMod;

/** Entry point do YNYUtils para o Stein Loader. */
public final class YNYUtils implements SteinMod {

    private static final int CONFIG_SAVE_DELAY_TICKS = 20;

    private static volatile YNYConfig config = new YNYConfig();
    private final KnockbackControl knockbackControl = new KnockbackControl(
            () -> config.knockbackEnabled, () -> config.knockbackPercent);
    private final CustomReach customReach = new CustomReach(
            () -> config.customReachEnabled, () -> config.customReachDistance);
    private final PvpStatusHud pvpStatusHud = new PvpStatusHud(
            () -> config.knockbackStatusHudEnabled, () -> config.customReachStatusHudEnabled);
    private final ToggleKey knockbackToggleKey = new ToggleKey();
    private final ToggleKey customReachToggleKey = new ToggleKey();
    private boolean configDirty;
    private int configSaveDelay;

    @Override
    public void afterStartGame() {
        config = YNYConfig.load();
        Hud.register(pvpStatusHud);
    }

    @Override
    public void onTickEnd() {
        knockbackControl.installIfPending();
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
                    pvpStatusHud.showKnockback(value);
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
                .option(Option.toggle("Mostrar notificação do KB", () -> config.knockbackStatusHudEnabled, value -> {
                    config.knockbackStatusHudEnabled = value;
                    markConfigChanged();
                }))
                .section("Custom Reach")
                .option(Option.toggle("Custom Reach", () -> config.customReachEnabled, value -> {
                    config.customReachEnabled = value;
                    pvpStatusHud.showReach(value, config.customReachDistance);
                    markConfigChanged();
                }))
                .option(Option.slider("Alcance", CustomReach.VANILLA_REACH, CustomReach.MAX_REACH, 0.1,
                        () -> config.customReachDistance, value -> {
                            config.customReachDistance = value;
                            markConfigChanged();
                        }, value -> String.format("%.1f blocos", value)))
                .option(Option.key("Tecla para alternar Reach", () -> config.customReachToggleKey, value -> {
                    config.customReachToggleKey = value;
                    markConfigChanged();
                }))
                .option(Option.toggle("Mostrar notificação do Reach", () -> config.customReachStatusHudEnabled, value -> {
                    config.customReachStatusHudEnabled = value;
                    markConfigChanged();
                }));
    }

    @Override
    public void onOverlay(float partialTicks) {
        customReach.apply();
    }

    private void updateToggleKey() {
        if (knockbackToggleKey.wasPressed(config.knockbackToggleKey)) {
            config.knockbackEnabled = !config.knockbackEnabled;
            pvpStatusHud.showKnockback(config.knockbackEnabled);
            markConfigChanged();
        }
        updateCustomReachToggleKey();
    }

    private void updateCustomReachToggleKey() {
        if (customReachToggleKey.wasPressed(config.customReachToggleKey)) {
            config.customReachEnabled = !config.customReachEnabled;
            pvpStatusHud.showReach(config.customReachEnabled, config.customReachDistance);
            markConfigChanged();
        }
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
