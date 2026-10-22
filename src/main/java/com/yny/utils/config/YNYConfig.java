package com.yny.utils.config;

public final class YNYConfig {
   public volatile boolean knockbackEnabled = true;
   public volatile int knockbackPercent = 93;
   public volatile boolean knockbackPreserveVertical = true;
   public volatile boolean knockbackShiftReset;
   public volatile boolean knockbackJumpReset;
   public volatile int knockbackShiftResetToggleKey;
   public volatile int knockbackJumpResetToggleKey;
   public volatile boolean hitThroughAlliesEnabled = true;
   public volatile boolean pvpDiagnosticsEnabled = true;
   public volatile int knockbackToggleKey = 39;
   public volatile boolean knockbackStatusHudEnabled = true;
   public volatile boolean autoArmorEnabled = true;
   public volatile int autoArmorToggleKey = 25;
   public volatile boolean autoArmorStatusHudEnabled = true;
   public volatile int autoArmorPreventiveThreshold = 30;
   public volatile boolean autoArmorDiscardReceived;
   public volatile boolean autoArmorDiscardRed8 = true;
   public volatile boolean autoArmorDiscardUnenchanted = true;
   public volatile boolean autoArmorDiscardBelowP4 = true;
   public volatile int autoArmorPreferredMaterial = 1;
   public volatile boolean autoArmorIgnoreUnenchanted = true;
   public volatile boolean autoArmorUseDamagedReserves = true;
   public volatile boolean autoArmorDropUnenchantedOld = true;
   public volatile boolean autoArmorDropStuckOld = true;
   public volatile boolean autoArmorAlertEnabled = true;
   public volatile boolean autoConsumablesEnabled = true;
   public volatile boolean inventoryMoveEnabled = true;
   public volatile boolean inventoryMoveSuspendUse = true;
   public volatile boolean autoProtectEnabled = true;
   public volatile boolean autoProtectWindow = true;
   public volatile boolean autoProtectLowHealthEnabled = true;
   public volatile int autoProtectLowHealth = 10;
   public volatile int autoProtectResumeHealth = 14;
   public volatile int autoProtectToggleKey = 0;
   public volatile int autoConsumablesToggleKey;
   public volatile boolean autoConsumablesStatusHudEnabled = true;
   public volatile int goldenAppleMode = 1;
   public volatile int goldenApplePreference;
   public volatile int potionMode = 2;
   public volatile boolean autoStrengthPotion = true;
   public volatile boolean autoSpeedPotion = true;
   public volatile int potionRefreshSeconds = 3;
   public volatile int consumableCombatSeconds = 8;
   public volatile int goldenAppleCooldownSeconds = 3;
   public volatile boolean clanPingEnabled = true;
   public volatile boolean clanPingHudEnabled = true;
   public volatile int clanPingLocationKey = 34;
   public volatile int clanPingEnemyKey = 47;
   public volatile boolean clanPingMiddleClick = true;
   public volatile boolean clanPingSound = true;
   public volatile boolean clanPingDistantIndicator = true;
   public volatile int clanPingCooldownSeconds = 2;
   public volatile float clanPingMarkerScale = 1.0F;
   public volatile int clanPingCancelKey = 35;
   public volatile int clanPingLocationSeconds = 45;
   public volatile int clanPingEnemySeconds = 60;
   public volatile int clanPingTargetRange = 64;

   // Mob Combat & Anti-Knockback
   public volatile boolean mobKnockbackEnabled = false;
   public volatile boolean guardianBeamProtect = true;
   public volatile boolean guardianOnlyKnockback = true;
   public volatile boolean witherKnockbackProtect = true;

   // Mob Aura
   public volatile boolean mobAuraEnabled = true;
   public volatile int mobAuraTargets = 2;
   public volatile boolean mobAuraGuardianPriority = true;
   public volatile boolean mobAuraWitherPriority = true;
   public volatile double mobAuraReach = 3.0D;
   public volatile int mobAuraCps = 12;
   public volatile int mobAuraToggleKey = 0;
   public volatile boolean mobAuraStatusHudEnabled = true;
   public volatile boolean mobAuraAttackPassives = false;
   public volatile boolean mobAuraVisuals = false;
   public volatile boolean mobAuraPvpSafety = true;
   public volatile double mobAuraPvpThreatRadius = 6.0D;

   // Destaque de Armadura Quebrada (Elmo, Bota, etc.)
   public volatile boolean brokenArmorHighlight = true;
   public volatile boolean brokenArmorThroughWalls = true;
   public volatile boolean brokenArmorTags = true;
   public volatile boolean brokenArmorOnlyEnemies = true;
   public volatile boolean brokenArmorAlertChat = true;
   public volatile int brokenArmorMaxDistance = 48;
   public volatile int selfArmorAlertToggleKey = 0;

   // Modo Foco em Armadura Quebrada (focar e hitar adversário sem capacete / armadura)
   public volatile boolean focusModeEnabled = true;
   public volatile boolean focusOnlyHelmet = false;
   public volatile int focusHelmetTimeSeconds = 1;
   public volatile double focusReach = 3.0D;
   public volatile int focusToggleKey = 0;
   public volatile boolean focusVisuals = true;

   // Proteção de Descarte de Armas
   public volatile boolean dropProtectionEnabled = false;

   // Trajetória Visual do Arco
   public volatile boolean bowAimAssistEnabled = false;
   public volatile boolean bowTrajectoryEnabled = true;
   public volatile double bowAimSmoothness = 0.35D;
   public volatile double bowAimMaxDistance = 50.0D;
   public volatile double bowAimFov = 50.0D;
   public volatile boolean bowLeadTarget = true;

   // AutoClick
   public volatile boolean autoclickEnabled = false;
   public volatile int autoclickMode = 0;
   public volatile int autoclickCps = 12;
   public volatile int autoclickToggleKey = 0;
   public volatile boolean autoclickStatusHudEnabled = true;

   // Trava de Seguranca CPS (Anti-Cheat Hard Cap - desativado para cliques e menus livres)
   public volatile boolean cpsCapEnabled = false;
   public volatile int maxCps = 25;

   public void sanitize() {
      this.inventoryMoveSuspendUse = true;
      this.autoProtectLowHealth = Math.max(1, Math.min(16, this.autoProtectLowHealth));
      this.autoProtectResumeHealth = Math.max(this.autoProtectLowHealth + 1, Math.min(20, this.autoProtectResumeHealth));
      this.knockbackPercent = Math.max(8, Math.min(100, this.knockbackPercent));
      this.autoArmorPreventiveThreshold = com.yny.utils.modules.player.ArmorThreshold.normalize(this.autoArmorPreventiveThreshold);
      this.autoArmorPreferredMaterial = Math.max(0, Math.min(5, this.autoArmorPreferredMaterial));
      this.goldenAppleMode = Math.max(0, Math.min(2, this.goldenAppleMode));
      this.goldenApplePreference = Math.max(0, Math.min(2, this.goldenApplePreference));
      this.potionMode = Math.max(0, Math.min(2, this.potionMode));
      this.potionRefreshSeconds = Math.max(1, Math.min(10, this.potionRefreshSeconds));
      this.consumableCombatSeconds = Math.max(1, Math.min(15, this.consumableCombatSeconds));
      this.goldenAppleCooldownSeconds = Math.max(1, Math.min(30, this.goldenAppleCooldownSeconds));
      this.clanPingLocationSeconds = Math.max(10, Math.min(180, this.clanPingLocationSeconds));
      this.clanPingEnemySeconds = Math.max(10, Math.min(180, this.clanPingEnemySeconds));
      this.clanPingTargetRange = Math.max(8, Math.min(64, this.clanPingTargetRange));
      this.clanPingCooldownSeconds = Math.max(1, Math.min(5, this.clanPingCooldownSeconds));
      this.clanPingMarkerScale = Math.max(0.75F, Math.min(2.0F, this.clanPingMarkerScale));
      this.mobAuraTargets = Math.max(1, Math.min(2, this.mobAuraTargets));
      this.mobAuraReach = Math.max(2.5D, Math.min(3.5D, this.mobAuraReach));
      this.mobAuraCps = Math.max(1, Math.min(20, this.mobAuraCps));
      this.mobAuraPvpThreatRadius = Math.max(3.0D, Math.min(12.0D, this.mobAuraPvpThreatRadius));
      this.brokenArmorMaxDistance = Math.max(16, Math.min(96, this.brokenArmorMaxDistance));
      this.focusHelmetTimeSeconds = Math.max(1, Math.min(5, this.focusHelmetTimeSeconds));
      this.focusReach = Math.max(2.5D, Math.min(3.5D, this.focusReach));
      this.bowAimMaxDistance = Math.max(10.0D, Math.min(80.0D, this.bowAimMaxDistance));
      this.bowAimFov = Math.max(15.0D, Math.min(120.0D, this.bowAimFov));
      this.bowAimSmoothness = Math.max(0.05D, Math.min(1.0D, this.bowAimSmoothness));
      this.autoclickMode = Math.max(0, Math.min(1, this.autoclickMode));
      this.autoclickCps = Math.max(1, Math.min(30, this.autoclickCps));
      this.autoclickEnabled = false;
      this.maxCps = Math.max(1, Math.min(25, this.maxCps));
      this.cpsCapEnabled = false;
   }
}

