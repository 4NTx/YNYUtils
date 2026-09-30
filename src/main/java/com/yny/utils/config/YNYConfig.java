package com.yny.utils.config;

/** Configuração persistida em config/ynyutils.json. */
public final class YNYConfig {

    public volatile boolean knockbackEnabled = true;
    public volatile int knockbackPercent = 100;
    public volatile boolean knockbackPreserveVertical;
    public volatile boolean knockbackJumpReset;
    public volatile boolean pvpDiagnosticsEnabled;
    public volatile int knockbackToggleKey;
    public volatile boolean knockbackStatusHudEnabled = true;
    public volatile boolean autoArmorEnabled;
    public volatile int autoArmorToggleKey;
    public volatile boolean autoArmorStatusHudEnabled = true;
    /** 0 mantém a reposição apenas para slots vazios/quebrados. */
    public volatile int autoArmorPreventiveThreshold;
    /** 0 = melhor disponível; 1..5 = diamante, ferro, malha, ouro, couro. */
    public volatile int autoArmorPreferredMaterial;
    public volatile boolean autoArmorIgnoreUnenchanted;
    public volatile boolean autoArmorUseDamagedReserves = true;
    public volatile boolean autoArmorDropUnenchantedOld;
    /** Último recurso: descartar uma peça antiga comprovadamente presa no cursor. */
    public volatile boolean autoArmorDropStuckOld = true;
    public volatile boolean autoConsumablesEnabled;
    public volatile int autoConsumablesToggleKey;
    public volatile boolean autoConsumablesStatusHudEnabled = true;
    /** 0 = off, 1 = health lost, 2 = while in PvP. */
    public volatile int goldenAppleMode = 1;
    /** 0 = prioritize enchanted, 1 = enchanted only, 2 = regular only. */
    public volatile int goldenApplePreference;
    /** 0 = off, 1 = when effect expires, 2 = in PvP when effect is low. */
    public volatile int potionMode = 1;
    public volatile boolean autoStrengthPotion = true;
    public volatile boolean autoSpeedPotion = true;
    public volatile int potionRefreshSeconds = 3;
    public volatile int consumableCombatSeconds = 5;
    public volatile int goldenAppleCooldownSeconds = 8;

    public void sanitize() {
        knockbackPercent = Math.max(93, Math.min(100, knockbackPercent));
        autoArmorPreventiveThreshold = Math.max(0, Math.min(50,
                Math.round(autoArmorPreventiveThreshold / 5.0F) * 5));
        autoArmorPreferredMaterial = Math.max(0, Math.min(5, autoArmorPreferredMaterial));
        goldenAppleMode = Math.max(0, Math.min(2, goldenAppleMode));
        goldenApplePreference = Math.max(0, Math.min(2, goldenApplePreference));
        potionMode = Math.max(0, Math.min(2, potionMode));
        potionRefreshSeconds = Math.max(1, Math.min(10, potionRefreshSeconds));
        consumableCombatSeconds = Math.max(1, Math.min(15, consumableCombatSeconds));
        goldenAppleCooldownSeconds = Math.max(1, Math.min(30, goldenAppleCooldownSeconds));
    }
}
