package com.yny.utils;

import com.yny.targethealth.TargetHealthElement;
import com.yny.targethealth.TargetHealthMod;
import com.yny.utils.config.YNYConfig;
import com.yny.utils.core.ToggleKey;
import com.yny.utils.modules.clanping.ClanPing;
import com.yny.utils.modules.player.AutoArmor;
import com.yny.utils.modules.player.ArmorThreshold;
import com.yny.utils.modules.player.ArmorPickupOptions;
import com.yny.utils.modules.player.ArmorPickupFilter;
import com.yny.utils.modules.player.AutoConsumables;
import com.yny.utils.modules.player.InventoryMove;
import com.yny.utils.modules.pvp.AllyGroundMarker;
import com.yny.utils.modules.pvp.HitThroughAllies;
import com.yny.utils.modules.pvp.KnockbackControl;
import com.yny.utils.modules.pvp.KnockbackJumpReset;
import com.yny.utils.modules.pvp.KnockbackShiftReset;
import com.yny.utils.ui.PvpStatusHud;
import dev.xavier.stein.loader.api.Hud;
import dev.xavier.stein.loader.api.Keys;
import dev.xavier.stein.loader.api.ModConfig;
import dev.xavier.stein.loader.api.ModContext;
import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Page;
import dev.xavier.stein.loader.api.SteinMod;
import dev.xavier.stein.loader.api.Targeting;

public final class YNYUtils implements SteinMod {
   private static volatile YNYConfig config = new YNYConfig();
   private final ModContext context = ModContext.of(this);
   private final ModConfig<YNYConfig> configStore = this.context.config(YNYConfig.class);
   private final KnockbackShiftReset knockbackShiftReset = new KnockbackShiftReset(() -> config.knockbackShiftReset && !config.knockbackJumpReset);
   private final KnockbackJumpReset knockbackJumpReset = new KnockbackJumpReset(() -> config.knockbackJumpReset);
   private final KnockbackControl knockbackControl = new KnockbackControl(
      () -> true,
      () -> 92,
      () -> false,
      () -> config.pvpDiagnosticsEnabled,
      (var1, var2) -> {
         this.knockbackShiftReset.onVelocity(var1, var2);
         this.knockbackJumpReset.onVelocity(var1, var2);
      },
      () -> {
         this.knockbackShiftReset.onVelocityApplied();
         this.knockbackJumpReset.onVelocityApplied();
      }
   );
   private final HitThroughAllies hitThroughAllies = new HitThroughAllies(() -> config.hitThroughAlliesEnabled, () -> config.pvpDiagnosticsEnabled);
   private final AllyGroundMarker allyGroundMarker = new AllyGroundMarker();
   private final PvpStatusHud pvpStatusHud = new PvpStatusHud(
      () -> config.knockbackStatusHudEnabled, () -> config.autoArmorStatusHudEnabled, () -> config.autoConsumablesStatusHudEnabled
   );
   private final ToggleKey knockbackToggleKey = new ToggleKey();
   private final ToggleKey shiftResetToggleKey = new ToggleKey();
   private final ToggleKey jumpResetToggleKey = new ToggleKey();
   private final ToggleKey autoArmorToggleKey = new ToggleKey();
   private final ToggleKey autoConsumablesToggleKey = new ToggleKey();
   private final AutoArmor autoArmor = new AutoArmor(
      () -> config.autoArmorEnabled,
      () -> config.autoArmorPreventiveThreshold,
      () -> config.autoArmorPreferredMaterial,
      () -> config.autoArmorIgnoreUnenchanted,
      () -> config.autoArmorUseDamagedReserves,
      () -> config.autoArmorDropUnenchantedOld,
      () -> config.autoArmorDropStuckOld,
      this.pvpStatusHud::showAutoArmorStatus
   );
   private final AutoConsumables autoConsumables = new AutoConsumables(
      () -> config.autoConsumablesEnabled,
      () -> config.goldenAppleMode,
      () -> config.goldenApplePreference,
      () -> config.potionMode,
      () -> config.autoStrengthPotion,
      () -> config.autoSpeedPotion,
      () -> config.potionRefreshSeconds,
      () -> config.consumableCombatSeconds,
      () -> config.goldenAppleCooldownSeconds,
      () -> config.pvpDiagnosticsEnabled,
      this.pvpStatusHud::showAutoConsumablesStatus
   );
   private final InventoryMove inventoryMove = new InventoryMove(
      () -> config.inventoryMoveEnabled, () -> true, this.autoConsumables::isControllingUseKey
   );
   private final TargetHealthElement targetHealthElement = new TargetHealthElement();
   private final ClanPing clanPing = new ClanPing(
      this.context,
      () -> config.clanPingEnabled,
      () -> config.clanPingHudEnabled,
      () -> config.clanPingLocationSeconds,
      () -> config.clanPingEnemySeconds,
      () -> config.clanPingTargetRange,
      () -> config.clanPingSound,
      () -> config.clanPingDistantIndicator,
      () -> config.clanPingCooldownSeconds,
      () -> config.clanPingMarkerScale
   );

   public void afterStartGame() {
      config = (YNYConfig)this.configStore.get();
      config.sanitize();
      TargetHealthMod.initialize();
      Hud.register(this.pvpStatusHud);
      Hud.register(this.targetHealthElement);
      Hud.register(this.clanPing.hud());
      this.clanPing.start();
   }

   public void onTickEnd() {
      this.knockbackControl.installIfPending();
      this.knockbackControl.onTickEnd();
      this.knockbackShiftReset.onTickEnd();
      this.knockbackJumpReset.onTickEnd();
      this.updateToggleKey();
      ArmorPickupFilter.runAutoArmor(this.autoArmor, config);
      this.autoConsumables.onTickEnd();
      this.inventoryMove.onTickEnd();
      this.targetHealthElement.updateDistantTarget();
      this.targetHealthElement.updateTarget();
      this.clanPing.onTickEnd();
   }

   public void onShutdown() {
      this.knockbackControl.reset();
      this.knockbackShiftReset.reset();
      this.knockbackJumpReset.reset();
      this.configStore.save();
      TargetHealthMod.save();
      this.clanPing.stop();
   }

   public void onJoinGame() {
      this.knockbackControl.requestInstallation();
      this.knockbackShiftReset.reset();
      this.knockbackJumpReset.reset();
      this.autoArmor.resetSession();
      ArmorPickupFilter.reset();
      this.autoConsumables.resetSession();
      this.clanPing.onJoinGame();
   }

   public void onDisconnected(String var1, String var2) {
      this.knockbackControl.onDisconnected();
      this.knockbackShiftReset.reset();
      this.knockbackJumpReset.reset();
      ArmorPickupFilter.reset();
      this.autoConsumables.resetSession();
      this.clanPing.onDisconnected();
   }

   public void onPage(Page var1) {
      var1.title("YNYUtils")
         .icon("combat")
         .section("Ataque e aliados")
         .option(Option.toggle("50% no primeiro aliado; grupo/clã sempre ignorado", () -> config.hitThroughAlliesEnabled, var1x -> {
            config.hitThroughAlliesEnabled = var1x;
            this.markConfigChanged();
         }))
         .section("Knockback: escolha Shift Reset ou Jump Reset")
         .option(Option.toggle("Usar Shift Reset (desativa Jump Reset)", () -> config.knockbackShiftReset, var1x -> {
            config.knockbackShiftReset = var1x;
            if (var1x) {
               config.knockbackJumpReset = false;
            }

            this.pvpStatusHud.showReset(false, var1x);
            this.markConfigChanged();
         }))
         .option(Option.toggle("Usar Jump Reset (desativa Shift Reset)", () -> config.knockbackJumpReset, var1x -> {
            config.knockbackJumpReset = var1x;
            if (var1x) {
               config.knockbackShiftReset = false;
            }

            this.pvpStatusHud.showReset(true, var1x);
            this.markConfigChanged();
         }))
         .option(Option.key("Tecla para alternar Shift Reset", () -> config.knockbackShiftResetToggleKey, var1x -> {
            config.knockbackShiftResetToggleKey = var1x;
            this.markConfigChanged();
         }))
         .option(Option.key("Tecla para alternar Jump Reset", () -> config.knockbackJumpResetToggleKey, var1x -> {
            config.knockbackJumpResetToggleKey = var1x;
            this.markConfigChanged();
         }))
         .option(Option.toggle("Registrar detalhes de combate e consumíveis", () -> config.pvpDiagnosticsEnabled, var1x -> {
            config.pvpDiagnosticsEnabled = var1x;
            this.markConfigChanged();
         }))
         .section("Armadura e inventário")
         .option(Option.toggle("Ativar troca automática de armadura", () -> config.autoArmorEnabled, var1x -> {
            config.autoArmorEnabled = var1x;
            this.pvpStatusHud.showAutoArmor(var1x);
            this.markConfigChanged();
         }))
         .option(Option.key("Tecla para alternar Auto Armor", () -> config.autoArmorToggleKey, var1x -> {
            config.autoArmorToggleKey = var1x;
            this.markConfigChanged();
         }))
         .option(Option.toggle("Mostrar status do Auto Armor na HUD", () -> config.autoArmorStatusHudEnabled, var1x -> {
            config.autoArmorStatusHudEnabled = var1x;
            this.markConfigChanged();
         }))
         .option(Option.toggle("Mover-se enquanto o inventário está aberto", () -> config.inventoryMoveEnabled, var1x -> {
            config.inventoryMoveEnabled = var1x;
            this.markConfigChanged();
         }))
         .option(
            Option.cycle(
               "Material preferido para reposição",
               new String[]{"Melhor disponível", "Diamante", "Ferro", "Malha", "Ouro", "Couro"},
               () -> config.autoArmorPreferredMaterial,
               var1x -> {
                  config.autoArmorPreferredMaterial = var1x;
                  this.markConfigChanged();
               }
            )
         )
         .option(Option.slider("Repor armadura abaixo desta durabilidade", 0.0, 8.0, 1.0, () -> ArmorThreshold.toSlider(config.autoArmorPreventiveThreshold), var1x -> {
            config.autoArmorPreventiveThreshold = ArmorThreshold.fromSlider((int)var1x);
            this.markConfigChanged();
         }, var0 -> (int)var0 == 0 ? "Só quando quebrar" : ArmorThreshold.fromSlider((int)var0) + "% restante"))
         .option(Option.toggle("Usar somente reservas encantadas", () -> config.autoArmorIgnoreUnenchanted, var1x -> {
            config.autoArmorIgnoreUnenchanted = var1x;
            this.markConfigChanged();
         }))
         .option(Option.toggle("Aceitar reserva danificada se a peça quebrar", () -> config.autoArmorUseDamagedReserves, var1x -> {
            config.autoArmorUseDamagedReserves = var1x;
            this.markConfigChanged();
         }))
         .option(Option.toggle("Descartar peça antiga sem encantamentos após confirmar troca", () -> config.autoArmorDropUnenchantedOld, var1x -> {
            config.autoArmorDropUnenchantedOld = var1x;
            this.markConfigChanged();
         }))
         .option(Option.toggle("Último recurso: descartar peça antiga presa no cursor", () -> config.autoArmorDropStuckOld, var1x -> {
            config.autoArmorDropStuckOld = var1x;
            this.markConfigChanged();
         }));
      ArmorPickupOptions.add(var1, config, this.configStore);
      var1.section("Consumíveis: maçãs e poções")
         .option(Option.toggle("Ativar uso automático de maçãs e poções", () -> config.autoConsumablesEnabled, var1x -> {
            config.autoConsumablesEnabled = var1x;
            this.pvpStatusHud.showAutoConsumables(var1x);
            this.markConfigChanged();
         }))
         .option(Option.key("Tecla para alternar Auto Consumíveis", () -> config.autoConsumablesToggleKey, var1x -> {
            config.autoConsumablesToggleKey = var1x;
            this.markConfigChanged();
         }))
         .option(Option.toggle("Mostrar status dos consumíveis na HUD", () -> config.autoConsumablesStatusHudEnabled, var1x -> {
            config.autoConsumablesStatusHudEnabled = var1x;
            this.markConfigChanged();
         }))
         .option(
            Option.cycle(
               "Quando usar maçãs douradas",
               new String[]{"Desativada", "Econômico: após dano, quando o efeito acabar", "Combate: renovar efeito perto do fim"},
               () -> config.goldenAppleMode,
               var1x -> {
                  config.goldenAppleMode = var1x;
                  this.markConfigChanged();
               }
            )
         )
         .option(
            Option.cycle("Qual maçã escolher", new String[]{"Priorizar encantada", "Só encantada", "Só normal"}, () -> config.goldenApplePreference, var1x -> {
               config.goldenApplePreference = var1x;
               this.markConfigChanged();
            })
         )
         .option(Option.slider("Pausa mínima entre usos de maçã", 1.0, 30.0, 1.0, () -> config.goldenAppleCooldownSeconds, var1x -> {
            config.goldenAppleCooldownSeconds = (int)var1x;
            this.markConfigChanged();
         }, var0 -> (int)var0 + " s"))
         .option(
            Option.cycle(
               "Quando usar poções de força/velocidade",
               new String[]{"Desativadas", "Econômico: após dano, quando o efeito acabar", "Combate: renovar efeito perto do fim"},
               () -> config.potionMode,
               var1x -> {
                  config.potionMode = var1x;
                  this.markConfigChanged();
               }
            )
         )
         .option(Option.toggle("Permitir poção de força", () -> config.autoStrengthPotion, var1x -> {
            config.autoStrengthPotion = var1x;
            this.markConfigChanged();
         }))
         .option(Option.toggle("Permitir poção de velocidade", () -> config.autoSpeedPotion, var1x -> {
            config.autoSpeedPotion = var1x;
            this.markConfigChanged();
         }))
         .option(Option.slider("Combate: renovar efeito quando restarem", 1.0, 10.0, 1.0, () -> config.potionRefreshSeconds, var1x -> {
            config.potionRefreshSeconds = (int)var1x;
            this.markConfigChanged();
         }, var0 -> (int)var0 + " s"))
         .option(Option.slider("Combate: janela após último hit", 1.0, 15.0, 1.0, () -> config.consumableCombatSeconds, var1x -> {
            config.consumableCombatSeconds = (int)var1x;
            this.markConfigChanged();
         }, var0 -> (int)var0 + " s"));
      TargetHealthMod.addOptions(var1);
      var1.section("Clan Ping: marcar e localizar")
         .option(Option.toggle("Ativar Clan Ping: marcar e localizar", () -> config.clanPingEnabled, var1x -> {
            config.clanPingEnabled = var1x;
            this.markConfigChanged();
         }))
         .option(Option.key("Atalho: marcar local", () -> config.clanPingLocationKey, var1x -> {
            config.clanPingLocationKey = var1x;
            this.markConfigChanged();
         }))
         .option(Option.key("Atalho: marcar inimigo", () -> config.clanPingEnemyKey, var1x -> {
            config.clanPingEnemyKey = var1x;
            this.markConfigChanged();
         }))
         .option(Option.toggle("Usar clique do meio para ping rápido", () -> config.clanPingMiddleClick, var1x -> {
            config.clanPingMiddleClick = var1x;
            this.markConfigChanged();
         }))
         .option(Option.toggle("Tocar som ao receber ping: marcar e localizar", () -> config.clanPingSound, var1x -> {
            config.clanPingSound = var1x;
            this.markConfigChanged();
         }))
         .option(Option.toggle("Exibir direção e distância do ping", () -> config.clanPingDistantIndicator, var1x -> {
            config.clanPingDistantIndicator = var1x;
            this.markConfigChanged();
         }))
         .option(Option.key("Atalho: remover meu ping", () -> config.clanPingCancelKey, var1x -> {
            config.clanPingCancelKey = var1x;
            this.markConfigChanged();
         }))
         .option(Option.toggle("Exibir cartão do Clan Ping: marcar e localizar", () -> config.clanPingHudEnabled, var1x -> {
            config.clanPingHudEnabled = var1x;
            this.markConfigChanged();
         }))
         .option(Option.slider("Duração do ping de local", 10.0, 180.0, 5.0, () -> config.clanPingLocationSeconds, var1x -> {
            config.clanPingLocationSeconds = (int)var1x;
            this.markConfigChanged();
         }, var0 -> (int)var0 + " s"))
         .option(Option.slider("Duração do ping de inimigo", 10.0, 180.0, 5.0, () -> config.clanPingEnemySeconds, var1x -> {
            config.clanPingEnemySeconds = (int)var1x;
            this.markConfigChanged();
         }, var0 -> (int)var0 + " s"))
         .option(Option.slider("Alcance máximo da busca visual do Clan Ping: marcar e localizar", 8.0, 64.0, 8.0, () -> config.clanPingTargetRange, var1x -> {
            config.clanPingTargetRange = (int)var1x;
            this.markConfigChanged();
         }, var0 -> (int)var0 + " blocos"))
         .option(Option.slider("Pausa mínima entre pings", 1.0, 5.0, 1.0, () -> config.clanPingCooldownSeconds, var1x -> {
            config.clanPingCooldownSeconds = (int)var1x;
            this.markConfigChanged();
         }, var0 -> (int)var0 + " s"))
         .option(Option.slider("Tamanho dos marcadores", 0.75, 2.0, 0.25, () -> config.clanPingMarkerScale, var1x -> {
            config.clanPingMarkerScale = (float)var1x;
            this.markConfigChanged();
         }, var0 -> String.format("%.2fx", var0)))
         .option(Option.action("Token para enviar pings do clã", "Configurar token", this.clanPing::openTokenScreen))
         .option(Option.action("Estado da conexão e envio", "Ver no chat", this.clanPing::showStatus));
   }

   public void onModelsReloaded() {
      this.pvpStatusHud.invalidateFontMetrics();
   }

   public void onRenderWorld(float var1) {
      this.clanPing.renderWorld(var1);
      this.allyGroundMarker.renderWorld(var1);
   }

   public void onOverlayTop(float var1) {
      this.clanPing.drawDistantIndicator();
   }

   public void onMouseInput() {
      if (config.clanPingMiddleClick && Keys.mousePressed() && Keys.mouseButton() == 2) {
         this.clanPing.createQuickPing();
      }
   }

   public boolean onAttackEntity(Object var1) {
      if (this.hitThroughAllies.shouldCancel(var1)) {
         Object var2 = this.hitThroughAllies.targetBehindAllies(var1);
         if (var2 != null) {
            this.autoConsumables.onAttack(var2);
            Targeting.attack(var2);
         }

         return true;
      } else {
         this.autoConsumables.onAttack(var1);
         return false;
      }
   }

   public boolean onEntityHurt(Object var1) {
      if (var1 == ave.A().h) {
         this.autoConsumables.onHurt();
      }

      return false;
   }

   public void onHealthChanged(Object var1, float var2, float var3, float var4, float var5) {
      this.targetHealthElement.onHealthChanged(var1, var2, var3, var4, var5);
      if (var1 == ave.A().h) {
         this.autoConsumables.onHealthChanged(var2, var3, var4, var5);
      }
   }

   public void onSlotChanged(int var1, int var2, Object var3) {
      this.autoArmor.onSlotChanged(var1, var2, var3);
      ArmorPickupFilter.onSlotChanged(var1, var2, var3, config);
   }

   public void onWindowItems(int var1) {
      this.autoArmor.onWindowItems(var1);
      ArmorPickupFilter.onWindowItems(var1);
   }

   private void updateToggleKey() {
      if (this.shiftResetToggleKey.wasPressed(config.knockbackShiftResetToggleKey)) {
         config.knockbackShiftReset = !config.knockbackShiftReset;
         if (config.knockbackShiftReset) {
            config.knockbackJumpReset = false;
         }

         this.pvpStatusHud.showReset(false, config.knockbackShiftReset);
         this.markConfigChanged();
      }

      if (this.jumpResetToggleKey.wasPressed(config.knockbackJumpResetToggleKey)) {
         config.knockbackJumpReset = !config.knockbackJumpReset;
         if (config.knockbackJumpReset) {
            config.knockbackShiftReset = false;
         }

         this.pvpStatusHud.showReset(true, config.knockbackJumpReset);
         this.markConfigChanged();
      }

      if (this.autoArmorToggleKey.wasPressed(config.autoArmorToggleKey)) {
         config.autoArmorEnabled = !config.autoArmorEnabled;
         this.pvpStatusHud.showAutoArmor(config.autoArmorEnabled);
         this.markConfigChanged();
      }

      if (this.autoConsumablesToggleKey.wasPressed(config.autoConsumablesToggleKey)) {
         config.autoConsumablesEnabled = !config.autoConsumablesEnabled;
         this.pvpStatusHud.showAutoConsumables(config.autoConsumablesEnabled);
         this.markConfigChanged();
      }

      if (this.clanPing.wasLocationPressed(config.clanPingLocationKey)) {
         this.clanPing.createLocationFromCrosshair();
      }

      if (this.clanPing.wasEnemyPressed(config.clanPingEnemyKey)) {
         this.clanPing.createEnemyFromCrosshair();
      }

      if (this.clanPing.wasCancelPressed(config.clanPingCancelKey)) {
         this.clanPing.cancelOwnPing();
      }
   }

   private void markConfigChanged() {
      this.configStore.saveSoon();
   }

   public String onSendChat(String var1) {
      return this.clanPing.onSendChat(var1);
   }

   public void onEntityDeath(Object var1) {
      this.clanPing.onEntityDeath(var1);
   }
}






