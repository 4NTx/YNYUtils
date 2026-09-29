package com.yny.utils;

import com.yny.utils.config.YNYConfig;
import com.yny.utils.core.ToggleKey;
import com.yny.utils.modules.pvp.AttackDiagnostics;
import com.yny.utils.modules.pvp.KnockbackControl;
import com.yny.utils.modules.pvp.CustomReach;
import com.yny.utils.modules.player.AutoArmor;
import com.yny.utils.ui.PvpStatusHud;
import com.yny.utils.ui.AttackDiagnosticsHud;
import com.yny.targethealth.TargetHealthElement;
import com.yny.targethealth.TargetHealthMod;
import net.minecraft.entity.Entity;

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
            () -> config.knockbackStatusHudEnabled, () -> config.customReachStatusHudEnabled,
            () -> config.autoArmorStatusHudEnabled);
    private final ToggleKey knockbackToggleKey = new ToggleKey();
    private final ToggleKey customReachToggleKey = new ToggleKey();
    private final ToggleKey autoArmorToggleKey = new ToggleKey();
    private final AutoArmor autoArmor = new AutoArmor(() -> config.autoArmorEnabled,
            () -> config.autoArmorPreventiveThreshold, () -> config.autoArmorPreferredMaterial,
            () -> config.autoArmorIgnoreUnenchanted,
            () -> config.autoArmorUseDamagedReserves,
            () -> config.autoArmorDropUnenchantedOld, pvpStatusHud::showAutoArmorStatus);
    private final AttackDiagnosticsHud attackDiagnosticsHud = new AttackDiagnosticsHud(
            () -> config.attackDiagnosticsEnabled);
    private final AttackDiagnostics attackDiagnostics = new AttackDiagnostics(
            () -> config.attackDiagnosticsEnabled, customReach::effectiveReach, attackDiagnosticsHud);
    private final TargetHealthElement targetHealthElement = new TargetHealthElement();
    private boolean configDirty;
    private int configSaveDelay;

    @Override
    public void afterStartGame() {
        config = YNYConfig.load();
        TargetHealthMod.initialize();
        Hud.register(pvpStatusHud);
        Hud.register(attackDiagnosticsHud);
        Hud.register(targetHealthElement);
    }

    @Override
    public void onTickEnd() {
        knockbackControl.installIfPending();
        updateToggleKey();
        autoArmor.onTickEnd();
        customReach.onTickEnd();
        attackDiagnostics.onTickEnd();
        targetHealthElement.updateDistantTarget();
        saveConfigIfDue();
    }

    @Override
    public void onJoinGame() {
        knockbackControl.requestInstallation();
        customReach.configurationChanged();
        attackDiagnostics.reset();
        autoArmor.resetSession();
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
                    customReach.configurationChanged();
                    pvpStatusHud.showReach(value, config.customReachDistance);
                    markConfigChanged();
                }))
                .option(Option.slider("Alcance", CustomReach.VANILLA_REACH, CustomReach.MAX_REACH, 0.1,
                        () -> config.customReachDistance, value -> {
                            config.customReachDistance = value;
                            customReach.configurationChanged();
                            markConfigChanged();
                        }, value -> String.format("%.1f blocos", value)))
                .option(Option.key("Tecla para alternar Reach", () -> config.customReachToggleKey, value -> {
                    config.customReachToggleKey = value;
                    markConfigChanged();
                }))
                .option(Option.toggle("Mostrar notificação do Reach", () -> config.customReachStatusHudEnabled, value -> {
                    config.customReachStatusHudEnabled = value;
                    markConfigChanged();
                }))
                .option(Option.toggle("Diagnóstico de ataques", () -> config.attackDiagnosticsEnabled, value -> {
                    config.attackDiagnosticsEnabled = value;
                    attackDiagnostics.reset();
                    attackDiagnostics.onTickEnd();
                    markConfigChanged();
                }))
                .section("Auto Armor")
                .option(Option.toggle("Ligar/desligar Auto Armor", () -> config.autoArmorEnabled, value -> {
                    config.autoArmorEnabled = value;
                    pvpStatusHud.showAutoArmor(value);
                    markConfigChanged();
                }))
                .option(Option.key("Tecla para alternar Auto Armor", () -> config.autoArmorToggleKey, value -> {
                    config.autoArmorToggleKey = value;
                    markConfigChanged();
                }))
                .option(Option.toggle("Mostrar notificação do Auto Armor", () -> config.autoArmorStatusHudEnabled,
                        value -> {
                            config.autoArmorStatusHudEnabled = value;
                            markConfigChanged();
                }))
                .option(Option.cycle("Material preferido", new String[] {
                        "Melhor disponível", "Diamante", "Ferro", "Malha", "Ouro", "Couro"
                }, () -> config.autoArmorPreferredMaterial, value -> {
                    config.autoArmorPreferredMaterial = value;
                    markConfigChanged();
                }))
                .option(Option.slider("Troca preventiva abaixo de", 0, 50, 5,
                        () -> config.autoArmorPreventiveThreshold, value -> {
                            config.autoArmorPreventiveThreshold = (int) value;
                            markConfigChanged();
                        }, value -> (int) value == 0 ? "Só quando quebrar" : (int) value + "% restante"))
                .option(Option.toggle("Ignorar reservas sem encantamentos", () -> config.autoArmorIgnoreUnenchanted,
                        value -> {
                            config.autoArmorIgnoreUnenchanted = value;
                            markConfigChanged();
                        }))
                .option(Option.toggle("Usar reservas danificadas se a armadura quebrar",
                        () -> config.autoArmorUseDamagedReserves, value -> {
                            config.autoArmorUseDamagedReserves = value;
                            markConfigChanged();
                        }))
                .option(Option.toggle("Descartar antiga sem encantamentos após troca confirmada",
                        () -> config.autoArmorDropUnenchantedOld, value -> {
                            config.autoArmorDropUnenchantedOld = value;
                            markConfigChanged();
                        }));
        TargetHealthMod.addOptions(page);
    }

    @Override
    public void onOverlay(float partialTicks) {
        customReach.onFrame(partialTicks);
    }

    @Override
    public void onModelsReloaded() {
        pvpStatusHud.invalidateFontMetrics();
    }

    @Override
    public void onMouseInput() {
        customReach.onInput();
    }

    @Override
    public void onKeyInput() {
        customReach.onInput();
    }

    @Override
    public boolean onAttackEntity(Object target) {
        if (target instanceof Entity) {
            attackDiagnostics.onAttack((Entity) target);
        }
        return false;
    }

    @Override
    public boolean onEntityHurt(Object entity) {
        if (entity instanceof Entity) {
            attackDiagnostics.observeDamage((Entity) entity);
        }
        return false;
    }

    @Override
    public void onHealthChanged(Object entity, float oldHealth, float newHealth, float oldAbsorption,
            float newAbsorption) {
        if (entity instanceof Entity && oldHealth + oldAbsorption > newHealth + newAbsorption) {
            attackDiagnostics.observeDamage((Entity) entity);
        }
    }

    @Override
    public void onSlotChanged(int windowId, int slot, Object stack) {
        autoArmor.onSlotChanged(windowId, slot, stack);
    }

    @Override
    public void onWindowItems(int windowId) {
        autoArmor.onWindowItems(windowId);
    }

    private void updateToggleKey() {
        if (knockbackToggleKey.wasPressed(config.knockbackToggleKey)) {
            config.knockbackEnabled = !config.knockbackEnabled;
            pvpStatusHud.showKnockback(config.knockbackEnabled);
            markConfigChanged();
        }
        updateCustomReachToggleKey();
        if (autoArmorToggleKey.wasPressed(config.autoArmorToggleKey)) {
            config.autoArmorEnabled = !config.autoArmorEnabled;
            pvpStatusHud.showAutoArmor(config.autoArmorEnabled);
            markConfigChanged();
        }
    }

    private void updateCustomReachToggleKey() {
        if (customReachToggleKey.wasPressed(config.customReachToggleKey)) {
            config.customReachEnabled = !config.customReachEnabled;
            customReach.configurationChanged();
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
