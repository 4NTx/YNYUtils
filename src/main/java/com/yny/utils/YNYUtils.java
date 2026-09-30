package com.yny.utils;

import com.yny.utils.config.YNYConfig;
import com.yny.utils.core.ToggleKey;
import com.yny.utils.modules.pvp.KnockbackControl;
import com.yny.utils.modules.player.AutoArmor;
import com.yny.utils.modules.player.AutoConsumables;
import com.yny.utils.ui.PvpStatusHud;
import com.yny.targethealth.TargetHealthElement;
import com.yny.targethealth.TargetHealthMod;

import dev.xavier.stein.loader.api.Hud;
import dev.xavier.stein.loader.api.ModConfig;
import dev.xavier.stein.loader.api.ModContext;
import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Page;
import dev.xavier.stein.loader.api.SteinMod;

/** Entry point do YNYUtils para o Stein Loader. */
public final class YNYUtils implements SteinMod {

    private static volatile YNYConfig config = new YNYConfig();
    private final ModContext context = ModContext.of(this);
    private final ModConfig<YNYConfig> configStore = context.config(YNYConfig.class);
    private final KnockbackControl knockbackControl = new KnockbackControl(
            () -> config.knockbackEnabled, () -> config.knockbackPercent,
            () -> config.knockbackPreserveVertical, () -> config.knockbackJumpReset,
            () -> config.pvpDiagnosticsEnabled);
    private final PvpStatusHud pvpStatusHud = new PvpStatusHud(
            () -> config.knockbackStatusHudEnabled,
            () -> config.autoArmorStatusHudEnabled, () -> config.autoConsumablesStatusHudEnabled);
    private final ToggleKey knockbackToggleKey = new ToggleKey();
    private final ToggleKey autoArmorToggleKey = new ToggleKey();
    private final ToggleKey autoConsumablesToggleKey = new ToggleKey();
    private final AutoArmor autoArmor = new AutoArmor(() -> config.autoArmorEnabled,
            () -> config.autoArmorPreventiveThreshold, () -> config.autoArmorPreferredMaterial,
            () -> config.autoArmorIgnoreUnenchanted,
            () -> config.autoArmorUseDamagedReserves,
            () -> config.autoArmorDropUnenchantedOld, () -> config.autoArmorDropStuckOld,
            pvpStatusHud::showAutoArmorStatus);
    private final AutoConsumables autoConsumables = new AutoConsumables(() -> config.autoConsumablesEnabled,
            () -> config.goldenAppleMode, () -> config.goldenApplePreference, () -> config.potionMode,
            () -> config.autoStrengthPotion, () -> config.autoSpeedPotion,
            () -> config.potionRefreshSeconds, () -> config.consumableCombatSeconds,
            () -> config.goldenAppleCooldownSeconds, () -> config.pvpDiagnosticsEnabled,
            pvpStatusHud::showAutoConsumablesStatus);
    private final TargetHealthElement targetHealthElement = new TargetHealthElement();
    @Override
    public void afterStartGame() {
        config = configStore.get();
        config.sanitize();
        TargetHealthMod.initialize();
        Hud.register(pvpStatusHud);
        Hud.register(targetHealthElement);
    }

    @Override
    public void onTickEnd() {
        knockbackControl.installIfPending();
        knockbackControl.onTickEnd();
        updateToggleKey();
        autoArmor.onTickEnd();
        autoConsumables.onTickEnd();
        targetHealthElement.updateDistantTarget();
    }

    @Override
    public void onShutdown() {
        knockbackControl.reset();
        configStore.save();
        TargetHealthMod.save();
    }

    @Override
    public void onJoinGame() {
        knockbackControl.requestInstallation();
        autoArmor.resetSession();
        autoConsumables.resetSession();
    }

    @Override
    public void onDisconnected(String title, String reason) {
        knockbackControl.onDisconnected();
        autoConsumables.resetSession();
    }

    @Override
    public void onPage(Page page) {
        page.title("YNYUtils")
                .icon("combat")
                .section("PvP")
                .option(Option.toggle("Reduzir Knockback", () -> config.knockbackEnabled, value -> {
                    config.knockbackEnabled = value;
                    pvpStatusHud.showKnockback(value);
                    markConfigChanged();
                }))
                .option(Option.slider("Velocidade recebida", 93, 100, 1, () -> config.knockbackPercent, value -> {
                    config.knockbackPercent = (int) value;
                    markConfigChanged();
                }, value -> (int) value + "% (redução: " + (100 - (int) value) + "%)"))
                .option(Option.toggle("Manter impulso vertical vanilla", () -> config.knockbackPreserveVertical,
                        value -> {
                            config.knockbackPreserveVertical = value;
                            markConfigChanged();
                        }))
                .option(Option.key("Tecla para alternar redução", () -> config.knockbackToggleKey, value -> {
                    config.knockbackToggleKey = value;
                    markConfigChanged();
                }))
                .option(Option.toggle("Mostrar notificação do KB", () -> config.knockbackStatusHudEnabled, value -> {
                    config.knockbackStatusHudEnabled = value;
                    markConfigChanged();
                }))
                .section("Jump Reset")
                .option(Option.toggle("Pulinho ao receber KB no chão (experimental)",
                        () -> config.knockbackJumpReset, value -> {
                            config.knockbackJumpReset = value;
                            markConfigChanged();
                        }))
                .option(Option.toggle("Logs detalhados KB/Capira/Pot",
                        () -> config.pvpDiagnosticsEnabled, value -> {
                            config.pvpDiagnosticsEnabled = value;
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
                }))
                .option(Option.toggle("Último recurso: descartar peça presa no cursor",
                        () -> config.autoArmorDropStuckOld, value -> {
                            config.autoArmorDropStuckOld = value;
                            markConfigChanged();
                }));
        page.section("Auto Consumíveis")
                .option(Option.toggle("Ligar/desligar Auto Consumíveis", () -> config.autoConsumablesEnabled,
                        value -> {
                            config.autoConsumablesEnabled = value;
                            pvpStatusHud.showAutoConsumables(value);
                            markConfigChanged();
                        }))
                .option(Option.key("Tecla para alternar Auto Consumíveis", () -> config.autoConsumablesToggleKey,
                        value -> {
                            config.autoConsumablesToggleKey = value;
                            markConfigChanged();
                        }))
                .option(Option.toggle("Mostrar notificação na HUD", () -> config.autoConsumablesStatusHudEnabled,
                        value -> {
                            config.autoConsumablesStatusHudEnabled = value;
                            markConfigChanged();
                        }))
                .option(Option.cycle("Maçã dourada", new String[] {
                        "Desativada", "Econômico: ao perder vida", "Hard: durante PvP"
                }, () -> config.goldenAppleMode, value -> {
                    config.goldenAppleMode = value;
                    markConfigChanged();
                }))
                .option(Option.cycle("Preferência de maçã", new String[] {
                        "Priorizar encantada", "Só encantada", "Só normal"
                }, () -> config.goldenApplePreference, value -> {
                    config.goldenApplePreference = value;
                    markConfigChanged();
                }))
                .option(Option.slider("Intervalo mínimo entre maçãs", 1, 30, 1,
                        () -> config.goldenAppleCooldownSeconds, value -> {
                            config.goldenAppleCooldownSeconds = (int) value;
                            markConfigChanged();
                        }, value -> (int) value + " s"))
                .option(Option.cycle("Poções de força/velocidade", new String[] {
                        "Desativadas", "Econômico: ao expirar", "Hard: durante PvP"
                }, () -> config.potionMode, value -> {
                    config.potionMode = value;
                    markConfigChanged();
                }))
                .option(Option.toggle("Usar poção de força", () -> config.autoStrengthPotion, value -> {
                    config.autoStrengthPotion = value;
                    markConfigChanged();
                }))
                .option(Option.toggle("Usar poção de velocidade", () -> config.autoSpeedPotion, value -> {
                    config.autoSpeedPotion = value;
                    markConfigChanged();
                }))
                .option(Option.slider("Renovar efeito quando restarem", 1, 10, 1,
                        () -> config.potionRefreshSeconds, value -> {
                            config.potionRefreshSeconds = (int) value;
                            markConfigChanged();
                        }, value -> (int) value + " s"))
                .option(Option.slider("Duração do estado de PvP", 1, 15, 1,
                        () -> config.consumableCombatSeconds, value -> {
                            config.consumableCombatSeconds = (int) value;
                            markConfigChanged();
                        }, value -> (int) value + " s"));
        TargetHealthMod.addOptions(page);
    }

    @Override
    public void onModelsReloaded() {
        pvpStatusHud.invalidateFontMetrics();
    }

    @Override
    public boolean onAttackEntity(Object target) {
        autoConsumables.onAttack(target);
        return false;
    }

    @Override
    public boolean onEntityHurt(Object entity) {
        if (entity == net.minecraft.client.Minecraft.getMinecraft().thePlayer) {
            autoConsumables.onHurt();
            knockbackControl.onLocalHurt();
        }
        return false;
    }

    @Override
    public void onHealthChanged(Object entity, float oldHealth, float newHealth, float oldAbsorption,
            float newAbsorption) {
        if (entity == net.minecraft.client.Minecraft.getMinecraft().thePlayer) {
            autoConsumables.onHealthChanged(oldHealth, newHealth);
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
        if (autoArmorToggleKey.wasPressed(config.autoArmorToggleKey)) {
            config.autoArmorEnabled = !config.autoArmorEnabled;
            pvpStatusHud.showAutoArmor(config.autoArmorEnabled);
            markConfigChanged();
        }
        if (autoConsumablesToggleKey.wasPressed(config.autoConsumablesToggleKey)) {
            config.autoConsumablesEnabled = !config.autoConsumablesEnabled;
            pvpStatusHud.showAutoConsumables(config.autoConsumablesEnabled);
            markConfigChanged();
        }
    }

    /** O SDK agrupa mudanças rápidas e grava o JSON atomicamente. */
    private void markConfigChanged() {
        configStore.saveSoon();
    }
}
