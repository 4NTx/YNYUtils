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

    public static volatile YNYUtils instance;
    private static volatile YNYConfig config = new YNYConfig();
    private final ModContext context = ModContext.of(this);
    private final ModConfig<YNYConfig> configStore = context.config(YNYConfig.class);

    public YNYUtils() {
        instance = this;
    }

    public static YNYConfig getConfig() {
        return config;
    }

    public static ModConfig<YNYConfig> getConfigStore() {
        return instance != null ? instance.configStore : null;
    }

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
            () -> com.yny.utils.modules.player.ArmorThreshold.normalize(config.autoArmorPreventiveThreshold),
            () -> config.autoArmorPreferredMaterial,
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
    private final com.yny.utils.modules.player.InventoryMove inventoryMove = new com.yny.utils.modules.player.InventoryMove(
            () -> config.inventoryMoveEnabled, () -> config.inventoryMoveSuspendUse,
            autoConsumables::isControllingUseKey);
    private final TargetHealthElement targetHealthElement = new TargetHealthElement();
    @Override
    public void afterStartGame() {
        config = configStore.get();
        config.sanitize();
        com.yny.utils.core.DebugLog.setEnabled(config.pvpDiagnosticsEnabled);
        TargetHealthMod.initialize();
        Hud.register(pvpStatusHud);
        Hud.register(targetHealthElement);
        Hud.register(new com.yny.utils.ui.BrokenArmorHud());
        com.yny.utils.telemetry.TelemetryBridge.afterStartGame();
    }

    @Override
    public void onTickEnd() {
        com.yny.utils.ui.BrokenArmorHud.checkSelfArmorBreak();
        com.yny.utils.modules.player.AutoArmorHelper.recoverOrphanedCursor();
        com.yny.utils.modules.player.AutoArmorHelper.checkPreventiveArmorAlert();
        knockbackControl.installIfPending();
        knockbackControl.onTickEnd();
        updateToggleKey();
        com.yny.utils.modules.player.ArmorPickupFilter.runAutoArmor(autoArmor, config);
        autoConsumables.onTickEnd();
        inventoryMove.onTickEnd();
        com.yny.utils.modules.player.AutoProtect.onTick(config, autoConsumables.isControllingUseKey());
        targetHealthElement.updateDistantTarget();
        targetHealthElement.updateTarget();
        com.yny.utils.modules.pvp.MobAura.onTickEnd();
        com.yny.utils.modules.pvp.BrokenArmorHighlight.onTickEnd();
        com.yny.utils.modules.pvp.BowAimAssist.onTickEnd();
        com.yny.utils.modules.pvp.FocusMode.onTickEnd();
        com.yny.utils.modules.pvp.HitThroughWeb.onTickEnd();
        com.yny.utils.modules.player.DropProtection.enforce();
    }

    @Override
    public void onRenderWorld(float partialTicks) {
        com.yny.utils.modules.pvp.MobAura.onRenderWorld(partialTicks);
        com.yny.utils.modules.pvp.BrokenArmorHighlight.onRenderWorld(partialTicks);
        com.yny.utils.modules.pvp.BowAimAssist.onRenderWorld(partialTicks);
        com.yny.utils.modules.pvp.FocusMode.onRenderWorld(partialTicks);
    }

    @Override
    public void onShutdown() {
        com.yny.utils.modules.player.AutoProtect.reset();
        com.yny.utils.modules.player.PriorityBus.reset();
        knockbackControl.reset();
        com.yny.utils.modules.pvp.MobAura.reset();
        com.yny.utils.modules.pvp.BrokenArmorHighlight.reset();
        com.yny.utils.modules.pvp.BowAimAssist.reset();
        com.yny.utils.modules.pvp.FocusMode.reset();
        com.yny.utils.ui.BrokenArmorHud.reset();
        configStore.save();
        TargetHealthMod.save();
        com.yny.utils.telemetry.TelemetryBridge.onShutdown();
    }

    @Override
    public void onJoinGame() {
        com.yny.utils.modules.player.AutoProtect.reset();
        com.yny.utils.modules.player.PriorityBus.reset();
        knockbackControl.requestInstallation();
        autoArmor.resetSession();
        autoConsumables.resetSession();
        com.yny.utils.modules.player.ArmorPickupFilter.reset();
        com.yny.utils.modules.pvp.FocusMode.reset();
        com.yny.utils.ui.BrokenArmorHud.reset();
        com.yny.utils.telemetry.TelemetryBridge.onJoinGame();
    }

    @Override
    public void onDisconnected(String title, String reason) {
        com.yny.utils.modules.player.AutoProtect.reset();
        com.yny.utils.modules.player.PriorityBus.reset();
        knockbackControl.onDisconnected();
        autoConsumables.resetSession();
        com.yny.utils.modules.pvp.FocusMode.reset();
        com.yny.utils.ui.BrokenArmorHud.reset();
        com.yny.utils.telemetry.TelemetryBridge.onDisconnected();
    }

    @Override
    public void onPage(Page page) {
        com.yny.utils.ui.YNYPage.build(this, page);
    }

    @Override
    public void onModelsReloaded() {
        pvpStatusHud.invalidateFontMetrics();
    }

    @Override
    public void onMouseInput() {
        com.yny.utils.modules.pvp.HitThroughWeb.onMouseInput();
        com.yny.utils.modules.pvp.FocusMode.onMouseInput();
    }

    @Override
    public boolean onAttackEntity(Object target) {
        autoConsumables.onAttack(target);
        if (com.yny.utils.modules.pvp.FocusMode.onAttackEntity(target)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean onEntityHurt(Object entity) {
        if (entity == dev.xavier.stein.loader.api.Entities.self()) {
            autoConsumables.onHurt();
            knockbackControl.onLocalHurt();
        }
        return false;
    }

    @Override
    public void onHealthChanged(Object entity, float oldHealth, float newHealth, float oldAbsorption,
            float newAbsorption) {
        targetHealthElement.onHealthChanged(entity, oldHealth, newHealth, oldAbsorption, newAbsorption);
        if (entity == dev.xavier.stein.loader.api.Entities.self()) {
            autoConsumables.onHealthChanged(oldHealth, newHealth, oldAbsorption, newAbsorption);
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

    @Override
    public Object onChat(Object chatComponent) {
        String text = dev.xavier.stein.loader.api.Chat.formatted(chatComponent);
        com.yny.utils.modules.clanping.PingClanName.processIncomingChat(text);
        return chatComponent;
    }

    @Override
    public String onSendChat(String message) {
        if (com.yny.utils.core.YNYCommands.handleChatCommand(message)) {
            return null;
        }
        com.yny.utils.modules.clanping.PingClanName.processOutgoingChat(message);
        com.yny.utils.modules.clanping.PingClanName.clearCaches();
        dev.xavier.stein.loader.api.Relations.changed();
        return message;
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
        com.yny.utils.ui.BrokenArmorHud.updateToggleKey();
        com.yny.utils.modules.pvp.MobAura.updateToggleKey();
        com.yny.utils.modules.pvp.FocusMode.updateToggleKey();
    }

    /** O SDK agrupa mudanças rápidas e grava o JSON atomicamente. */
    private void markConfigChanged() {
        configStore.saveSoon();
    }
}



