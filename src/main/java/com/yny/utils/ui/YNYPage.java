package com.yny.utils.ui;

import com.yny.targethealth.TargetHealthMod;
import com.yny.utils.YNYUtils;
import com.yny.utils.config.YNYConfig;
import com.yny.utils.core.DebugLog;
import com.yny.utils.modules.player.ArmorThreshold;
import com.yny.utils.team.SteinCombat;

import dev.xavier.stein.loader.api.Chat;
import dev.xavier.stein.loader.api.ModConfig;
import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Page;

import java.lang.reflect.Field;
import java.util.Locale;

/**
 * Construtor visual unificado e ergonomicamente estruturado da interface gráfica do YNYUtils.
 * Organizado em módulos temáticos completos: cada recurso reúne suas opções principais e
 * ajustes finos no mesmo card, evitando fragmentação e facilitando a navegação no menu do Insert.
 */
public final class YNYPage {

    private YNYPage() {}

    public static void build(YNYUtils yny, Page page) {
        YNYConfig config = YNYUtils.getConfig();
        ModConfig<YNYConfig> store = YNYUtils.getConfigStore();
        if (page == null || config == null) return;

        page.title("YNYUtils").icon("combat");

        // 1. Knockback, Combate & Defesa (KB, Resets, Aliados e AutoProtect)
        buildKnockbackAndDefense(page, config);

        // 2. Auto Armor & Inventário (Troca de armadura + regras de descarte e InvMove)
        buildAutoArmor(page, config);

        // 3. Auto Consumíveis (Capira, Poções e timings de combate)
        buildAutoConsumables(page, config);

        // 4. Mob Aura & Farm (Guardians & Mobs)
        buildMobAura(page, config);

        // 5. Damage Indicator (Vida do Alvo, visual e filtros)
        TargetHealthMod.addOptions(page);

        // 6. Destaque de Armadura Quebrada (Elmo, Bota & Foco em Inimigos)
        buildBrokenArmor(page, config);

        // 7. Mira no Arco & Trajetória (Aim Assist Balístico)
        buildBowAim(page, config);

        // 8. Clan Ping (Marcação Tática, Marcadores 3D e Conexão)
        buildClanPing(page, config, yny);

        // 9. Relações & Listas do Clã (Sincronização dos Líderes e Cores)
        SteinCombat.addOptions(page);

        // 10. Diagnósticos & Logs do Sistema
        buildDiagnostics(page, config);
    }

    // -----------------------------------------------------------------------------
    // SEÇÃO 1: Knockback, Combate & Defesa
    // -----------------------------------------------------------------------------
    private static void buildKnockbackAndDefense(Page page, YNYConfig config) {
        page.section("Knockback, Combate & Defesa")
            .option(Option.toggle("Reduzir Knockback recebido", () -> config.knockbackEnabled, value -> {
                config.knockbackEnabled = value;
                if (PvpStatusHud.instance != null) {
                    PvpStatusHud.instance.showKnockback(value);
                }
                markConfigChanged();
            }))
            .option(Option.slider("Knockback recebido", 8.0, 100.0, 1.0, () -> config.knockbackPercent, value -> {
                config.knockbackPercent = Math.max(8, (int) value);
                markConfigChanged();
            }, value -> (int) value + "% (redução: " + (100 - (int) value) + "%)"))
            .option(Option.toggle("Manter impulso vertical normal", () -> config.knockbackPreserveVertical, value -> {
                config.knockbackPreserveVertical = value;
                markConfigChanged();
            }))
            .option(Option.key("Tecla para alternar knockback", () -> config.knockbackToggleKey, value -> {
                config.knockbackToggleKey = value;
                markConfigChanged();
            }))
            .option(Option.toggle("Toggle Sprint (Auto-Sprint permanente)",
                YNYPage::getToggleSprint,
                YNYPage::setToggleSprint))
            .option(Option.toggle("Bloquear descarte de item (Drop Protection)",
                () -> config.dropProtectionEnabled,
                value -> {
                    config.dropProtectionEnabled = value;
                    com.yny.utils.modules.player.DropProtection.setEnabled(value);
                    try {
                        dev.xavier.stein.loader.api.Chat.actionBar(value ? "§aDrop Protection: ATIVADO" : "§cDrop Protection: DESATIVADO");
                    } catch (Throwable ignored) {}
                    markConfigChanged();
                }))
            .option(Option.toggle("Usar Jump Reset (desativa Shift Reset)", () -> config.knockbackJumpReset, value -> {
                config.knockbackJumpReset = value;
                if (value) config.knockbackShiftReset = false;
                if (PvpStatusHud.instance != null) {
                    PvpStatusHud.instance.showReset(true, value);
                }
                markConfigChanged();
            }))
            .option(Option.key("Tecla para alternar Jump Reset", () -> config.knockbackJumpResetToggleKey, value -> {
                config.knockbackJumpResetToggleKey = value;
                markConfigChanged();
            }))
            .option(Option.toggle("Usar Shift Reset (desativa Jump Reset)", () -> config.knockbackShiftReset, value -> {
                config.knockbackShiftReset = value;
                if (value) config.knockbackJumpReset = false;
                if (PvpStatusHud.instance != null) {
                    PvpStatusHud.instance.showReset(false, value);
                }
                markConfigChanged();
            }))
            .option(Option.key("Tecla para alternar Shift Reset", () -> config.knockbackShiftResetToggleKey, value -> {
                config.knockbackShiftResetToggleKey = value;
                markConfigChanged();
            }))
            .option(Option.toggle("Ataque atravessar aliados (100% Clã / 50% Aliados)", () -> config.hitThroughAlliesEnabled, value -> {
                config.hitThroughAlliesEnabled = value;
                markConfigChanged();
            }))
            .option(Option.toggle("Mostrar status do knockback na HUD", () -> config.knockbackStatusHudEnabled, value -> {
                config.knockbackStatusHudEnabled = value;
                markConfigChanged();
            }))
            .option(Option.toggle("Defender automaticamente com espada (AutoProtect)", () -> config.autoProtectEnabled, value -> {
                config.autoProtectEnabled = value;
                markConfigChanged();
            }))
            .option(Option.key("Atalho para alternar defesa automática", () -> config.autoProtectToggleKey, value -> {
                config.autoProtectToggleKey = value;
                markConfigChanged();
            }))
            .option(Option.toggle("Defender por vida baixa em combate", () -> config.autoProtectLowHealthEnabled, value -> {
                config.autoProtectLowHealthEnabled = value;
                markConfigChanged();
            }))
            .option(Option.slider("Defender quando vida chegar a", 1.0, 16.0, 1.0,
                () -> config.autoProtectLowHealth, value -> {
                config.autoProtectLowHealth = (int) value;
                config.sanitize();
                markConfigChanged();
            }, value -> (int) value + " HP"))
            .option(Option.slider("Soltar defesa quando vida chegar a", 2.0, 20.0, 1.0,
                () -> config.autoProtectResumeHealth, value -> {
                config.autoProtectResumeHealth = (int) value;
                config.sanitize();
                markConfigChanged();
            }, value -> (int) value + " HP"))
            .option(Option.toggle("Defender ao abrir janela/baú (parado)", () -> config.autoProtectWindow, value -> {
                config.autoProtectWindow = value;
                markConfigChanged();
            }))
            .option(Option.toggle("Trava de Segurança CPS (Teto Anti-Cheat)", () -> config.cpsCapEnabled, value -> {
                config.cpsCapEnabled = value;
                markConfigChanged();
            }))
            .option(Option.slider("Limite Máximo de CPS (Teto Anti-Cheat)", 10.0, 25.0, 1.0,
                () -> (double) config.maxCps, value -> {
                config.maxCps = (int) Math.round(value);
                config.sanitize();
                markConfigChanged();
            }, value -> (int) Math.round(value) + " CPS"));
    }

    // -----------------------------------------------------------------------------
    // SEÇÃO 2: Auto Armor & Inventário
    // -----------------------------------------------------------------------------
    private static void buildAutoArmor(Page page, YNYConfig config) {
        page.section("Auto Armor & Inventário")
            .option(Option.toggle("Ativar troca automática de armadura", () -> config.autoArmorEnabled, value -> {
                config.autoArmorEnabled = value;
                if (PvpStatusHud.instance != null) {
                    PvpStatusHud.instance.showAutoArmor(value);
                }
                markConfigChanged();
            }))
            .option(Option.key("Tecla para alternar Auto Armor", () -> config.autoArmorToggleKey, value -> {
                config.autoArmorToggleKey = value;
                markConfigChanged();
            }))
            .option(Option.cycle("Material preferido para reposição",
                new String[] { "Melhor disponível", "Diamante", "Ferro", "Malha", "Ouro", "Couro" },
                () -> config.autoArmorPreferredMaterial, value -> {
                config.autoArmorPreferredMaterial = value;
                markConfigChanged();
            }))
            .option(Option.slider("Repor armadura abaixo desta durabilidade", 0.0, 7.0, 1.0,
                () -> ArmorThreshold.toSlider(config.autoArmorPreventiveThreshold), value -> {
                config.autoArmorPreventiveThreshold = ArmorThreshold.fromSlider((int) value);
                markConfigChanged();
            }, value -> (int) value == 0 ? "Só quando quebrar" : ArmorThreshold.fromSlider((int) value) + "% restante"))
            .option(Option.toggle("Alerta sonoro preventivo ao danificar armadura", () -> config.autoArmorAlertEnabled, value -> {
                config.autoArmorAlertEnabled = value;
                markConfigChanged();
            }))
            .option(Option.toggle("Mover-se enquanto o inventário está aberto (InvMove)", () -> config.inventoryMoveEnabled, value -> {
                config.inventoryMoveEnabled = value;
                markConfigChanged();
            }))
            .option(Option.toggle("Mostrar status do Auto Armor na HUD", () -> config.autoArmorStatusHudEnabled, value -> {
                config.autoArmorStatusHudEnabled = value;
                markConfigChanged();
            }))
            .option(Option.toggle("Ativar filtro de armaduras recebidas", () -> config.autoArmorDiscardReceived, value -> {
                config.autoArmorDiscardReceived = value;
                markConfigChanged();
            }))
            .option(Option.toggle("Descartar recebida com 8% ou menos de durabilidade", () -> config.autoArmorDiscardRed8, value -> {
                config.autoArmorDiscardRed8 = value;
                markConfigChanged();
            }))
            .option(Option.toggle("Descartar recebida sem encantamentos", () -> config.autoArmorDiscardUnenchanted, value -> {
                config.autoArmorDiscardUnenchanted = value;
                markConfigChanged();
            }))
            .option(Option.toggle("Descartar recebida com Proteção abaixo de IV", () -> config.autoArmorDiscardBelowP4, value -> {
                config.autoArmorDiscardBelowP4 = value;
                markConfigChanged();
            }))
            .option(Option.toggle("Usar somente reservas encantadas", () -> config.autoArmorIgnoreUnenchanted, value -> {
                config.autoArmorIgnoreUnenchanted = value;
                markConfigChanged();
            }))
            .option(Option.toggle("Aceitar reserva danificada se a peça quebrar", () -> config.autoArmorUseDamagedReserves, value -> {
                config.autoArmorUseDamagedReserves = value;
                markConfigChanged();
            }))
            .option(Option.toggle("Descartar peça antiga sem encantamentos após confirmar troca", () -> config.autoArmorDropUnenchantedOld, value -> {
                config.autoArmorDropUnenchantedOld = value;
                markConfigChanged();
            }))
            .option(Option.toggle("Soltar defesa ao mover no inventário (InvMove)", () -> config.inventoryMoveSuspendUse, value -> {
                config.inventoryMoveSuspendUse = value;
                markConfigChanged();
            }))
            .option(Option.toggle("Último recurso: descartar peça antiga presa no cursor", () -> config.autoArmorDropStuckOld, value -> {
                config.autoArmorDropStuckOld = value;
                markConfigChanged();
            }));
    }

    // -----------------------------------------------------------------------------
    // SEÇÃO 3: Auto Consumíveis (Capira & Poções)
    // -----------------------------------------------------------------------------
    private static void buildAutoConsumables(Page page, YNYConfig config) {
        page.section("Auto Consumíveis (Capira & Poções)")
            .option(Option.toggle("Ativar uso automático de maçãs e poções", () -> config.autoConsumablesEnabled, value -> {
                config.autoConsumablesEnabled = value;
                if (PvpStatusHud.instance != null) {
                    PvpStatusHud.instance.showAutoConsumables(value);
                }
                markConfigChanged();
            }))
            .option(Option.key("Tecla para alternar Auto Consumíveis", () -> config.autoConsumablesToggleKey, value -> {
                config.autoConsumablesToggleKey = value;
                markConfigChanged();
            }))
            .option(Option.cycle("Quando usar maçãs douradas",
                new String[] { "Desativada", "Econômico: após dano, quando o efeito acabar", "Combate: renovar efeito perto do fim" },
                () -> config.goldenAppleMode, value -> {
                config.goldenAppleMode = value;
                markConfigChanged();
            }))
            .option(Option.cycle("Qual maçã escolher",
                new String[] { "Priorizar encantada", "Só encantada", "Só normal" },
                () -> config.goldenApplePreference, value -> {
                config.goldenApplePreference = value;
                markConfigChanged();
            }))
            .option(Option.slider("Pausa mínima entre usos de maçã", 1.0, 30.0, 1.0,
                () -> config.goldenAppleCooldownSeconds, value -> {
                config.goldenAppleCooldownSeconds = (int) value;
                markConfigChanged();
            }, value -> (int) value + " s"))
            .option(Option.cycle("Quando usar poções de força/velocidade",
                new String[] { "Desativadas", "Econômico: após dano, quando o efeito acabar", "Combate: renovar efeito perto do fim" },
                () -> config.potionMode, value -> {
                config.potionMode = value;
                markConfigChanged();
            }))
            .option(Option.toggle("Permitir poção de força", () -> config.autoStrengthPotion, value -> {
                config.autoStrengthPotion = value;
                markConfigChanged();
            }))
            .option(Option.toggle("Permitir poção de velocidade", () -> config.autoSpeedPotion, value -> {
                config.autoSpeedPotion = value;
                markConfigChanged();
            }))
            .option(Option.slider("Combate: renovar efeito quando restarem", 1.0, 10.0, 1.0, () -> config.potionRefreshSeconds, value -> {
                config.potionRefreshSeconds = (int) value;
                markConfigChanged();
            }, value -> (int) value + " s"))
            .option(Option.slider("Combate: janela após último hit", 1.0, 15.0, 1.0, () -> config.consumableCombatSeconds, value -> {
                config.consumableCombatSeconds = (int) value;
                markConfigChanged();
            }, value -> (int) value + " s"))
            .option(Option.toggle("Mostrar status dos consumíveis na HUD", () -> config.autoConsumablesStatusHudEnabled, value -> {
                config.autoConsumablesStatusHudEnabled = value;
                markConfigChanged();
            }));
    }

    // -----------------------------------------------------------------------------
    // SEÇÃO 4: Mob Aura & Farm
    // -----------------------------------------------------------------------------
    private static void buildMobAura(Page page, YNYConfig config) {
        page.section("Mob Aura & Farm (Withers, Guardians & Mobs)")
            .option(Option.toggle("Ligar/desligar Mob Aura", () -> config.mobAuraEnabled, value -> {
                config.mobAuraEnabled = value;
                if (config.mobAuraStatusHudEnabled) {
                    Chat.actionBar(value ? "§aMob Aura: ATIVADA" : "§cMob Aura: DESATIVADA");
                }
                markConfigChanged();
            }))
            .option(Option.key("Tecla para alternar Mob Aura", () -> config.mobAuraToggleKey, value -> {
                config.mobAuraToggleKey = value;
                markConfigChanged();
            }))
            .option(Option.toggle("Focar Wither primeiro", () -> config.mobAuraWitherPriority, value -> {
                config.mobAuraWitherPriority = value;
                markConfigChanged();
            }))
            .option(Option.toggle("Focar Guardian primeiro", () -> config.mobAuraGuardianPriority, value -> {
                config.mobAuraGuardianPriority = value;
                markConfigChanged();
            }))
            .option(Option.cycle("Alvos simultâneos", new String[] { "1 mob", "2 mobs" },
                () -> Math.max(0, config.mobAuraTargets - 1), value -> {
                config.mobAuraTargets = value + 1;
                markConfigChanged();
            }))
            .option(Option.slider("Velocidade de ataque (CPS)", 5.0, 20.0, 1.0,
                () -> config.mobAuraCps, value -> {
                config.mobAuraCps = (int) value;
                markConfigChanged();
            }, value -> (int) value + " CPS"))
            .option(Option.slider("Alcance da Mob Aura (Máx 3.5)", 2.5, 3.5, 0.1,
                () -> config.mobAuraReach, value -> {
                config.mobAuraReach = value;
                markConfigChanged();
            }, value -> String.format(Locale.ROOT, "%.1f blocos", (double) value)))
            .option(Option.toggle("Não levar KB do Wither (Explosões/Ataques)", () -> config.witherKnockbackProtect, value -> {
                config.witherKnockbackProtect = value;
                markConfigChanged();
            }))
            .option(Option.toggle("Não levar KB do Guardian (Raio/Ataques)", () -> config.guardianBeamProtect, value -> {
                config.guardianBeamProtect = value;
                markConfigChanged();
            }))
            .option(Option.toggle("Não levar KB de outros Mobs", () -> config.mobKnockbackEnabled, value -> {
                config.mobKnockbackEnabled = value;
                markConfigChanged();
            }))
            .option(Option.toggle("Atacar mobs passivos", () -> config.mobAuraAttackPassives, value -> {
                config.mobAuraAttackPassives = value;
                markConfigChanged();
            }))
            .option(Option.toggle("Marcador visual nos alvos da Mob Aura", () -> config.mobAuraVisuals, value -> {
                config.mobAuraVisuals = value;
                markConfigChanged();
            }))
            .option(Option.toggle("Prioridade PvP na Mob Aura (pausar perto de inimigos)", () -> config.mobAuraPvpSafety, value -> {
                config.mobAuraPvpSafety = value;
                markConfigChanged();
            }))
            .option(Option.slider("Raio de ameaça PvP para pausar Mob Aura", 3.0, 10.0, 1.0,
                () -> config.mobAuraPvpThreatRadius, value -> {
                config.mobAuraPvpThreatRadius = value;
                markConfigChanged();
            }, value -> (int) value + " blocos"))
            .option(Option.toggle("Notificação de Mob Aura na Action Bar", () -> config.mobAuraStatusHudEnabled, value -> {
                config.mobAuraStatusHudEnabled = value;
                markConfigChanged();
            }));
    }

    // -----------------------------------------------------------------------------
    // SEÇÃO 6: Destaque de Armadura Quebrada (Elmo, Bota & Foco em Inimigos)
    // -----------------------------------------------------------------------------
    private static void buildBrokenArmor(Page page, YNYConfig config) {
        page.section("Destaque de Armadura Quebrada (Elmo & Bota)")
            .option(Option.toggle("Destacar jogadores com peça quebrada", () -> config.brokenArmorHighlight, value -> {
                config.brokenArmorHighlight = value;
                markConfigChanged();
            }))
            .option(Option.toggle("Brilho visível através das paredes (Raio-X / ESP)", () -> config.brokenArmorThroughWalls, value -> {
                config.brokenArmorThroughWalls = value;
                markConfigChanged();
            }))
            .option(Option.toggle("Exibir texto 3D flutuante (Ex: SEM ELMO, SEM BOTA)", () -> config.brokenArmorTags, value -> {
                config.brokenArmorTags = value;
                markConfigChanged();
            }))
            .option(Option.toggle("SÓ quem NÃO for do mesmo clã (ignorar clã e aliados)", () -> config.brokenArmorOnlyEnemies, value -> {
                config.brokenArmorOnlyEnemies = value;
                markConfigChanged();
            }))
            .option(Option.slider("Distância máxima de destaque", 16.0, 96.0, 4.0,
                () -> config.brokenArmorMaxDistance, value -> {
                config.brokenArmorMaxDistance = (int) value;
                markConfigChanged();
            }, value -> (int) value + " blocos"))
            .option(Option.toggle("Alerta na HUD: MEU SET quebrou (Elmo/Peça)", () -> config.brokenArmorAlertChat, value -> {
                BrokenArmorHud.setState(value);
            }))
            .option(Option.key("Tecla para alternar alerta do MEU SET", () -> config.selfArmorAlertToggleKey, value -> {
                config.selfArmorAlertToggleKey = value;
                markConfigChanged();
            }))
            .option(Option.action("Testar Alerta de Armadura Própria", "Disparar Alerta Teste", () -> {
                BrokenArmorHud.notifySelfBroken("CAPACETE & BOTA", 0xFFFF3333);
            }))
            .option(Option.toggle("Modo Foco (Hitar adversário sem capacete/peça)", () -> config.focusModeEnabled, value -> {
                com.yny.utils.modules.pvp.FocusMode.setState(value);
            }))
            .option(Option.key("Tecla para alternar Modo Foco", () -> config.focusToggleKey, value -> {
                config.focusToggleKey = value;
                markConfigChanged();
            }))
            .option(Option.toggle("Focar apenas quem estiver sem capacete", () -> config.focusOnlyHelmet, value -> {
                config.focusOnlyHelmet = value;
                markConfigChanged();
            }))
            .option(Option.slider("Tempo sem capacete para focar", 1.0, 5.0, 1.0, () -> config.focusHelmetTimeSeconds, value -> {
                config.focusHelmetTimeSeconds = (int) value;
                markConfigChanged();
            }, value -> (int) value + " s"))
            .option(Option.slider("Alcance do Modo Foco (Máx 3.5)", 2.5, 3.5, 0.1, () -> config.focusReach, value -> {
                config.focusReach = value;
                markConfigChanged();
            }, value -> String.format(Locale.ROOT, "%.1f blocos", (double) value)))
            .option(Option.toggle("Marcador visual 3D de FOCO no alvo", () -> config.focusVisuals, value -> {
                config.focusVisuals = value;
                markConfigChanged();
            }));
    }

    // -----------------------------------------------------------------------------
    // SEÇÃO 7: Trajetória Visual do Arco (Linha 3D + Ponto de Impacto)
    // -----------------------------------------------------------------------------
    private static void buildBowAim(Page page, YNYConfig config) {
        page.section("Trajetória do Arco (Linha 3D + Ponto de Impacto)")
            .option(Option.toggle("Trajetória Visual da Flecha (Linha 3D + Impacto)", () -> config.bowTrajectoryEnabled, value -> {
                config.bowTrajectoryEnabled = value;
                markConfigChanged();
            }));
    }

    // -----------------------------------------------------------------------------
    // SEÇÃO 8: Clan Ping (Comunicação Tática)
    // -----------------------------------------------------------------------------
    private static void buildClanPing(Page page, YNYConfig config, YNYUtils yny) {
        page.section("Clan Ping (Marcação Tática & Pings)")
            .option(Option.action("Clã Detectado",
                com.yny.utils.modules.clanping.PingClanName.getDetectedSelfClan() != null ? "[" + com.yny.utils.modules.clanping.PingClanName.getDetectedSelfClan() + "]" : "Buscar na Sidebar",
                () -> {
                    String found = com.yny.utils.modules.clanping.PingClanName.extractClanFromSidebar();
                    if (found != null) {
                        com.yny.utils.modules.clanping.PingClanName.setDetectedSelfClan(found);
                    }
                }))
            .option(Option.toggle("Ativar Clan Ping", () -> config.clanPingEnabled, value -> {
                config.clanPingEnabled = value;
                markConfigChanged();
            }))
            .option(Option.key("Atalho: marcar local", () -> config.clanPingLocationKey, value -> {
                config.clanPingLocationKey = value;
                markConfigChanged();
            }))
            .option(Option.key("Atalho: marcar inimigo", () -> config.clanPingEnemyKey, value -> {
                config.clanPingEnemyKey = value;
                markConfigChanged();
            }))
            .option(Option.key("Atalho: remover meu ping", () -> config.clanPingCancelKey, value -> {
                config.clanPingCancelKey = value;
                markConfigChanged();
            }))
            .option(Option.toggle("Usar clique do meio para ping rápido", () -> config.clanPingMiddleClick, value -> {
                config.clanPingMiddleClick = value;
                markConfigChanged();
            }))
            .option(Option.toggle("Exibir direção e distância do ping", () -> config.clanPingDistantIndicator, value -> {
                config.clanPingDistantIndicator = value;
                markConfigChanged();
            }))
            .option(Option.toggle("Exibir cartão do Clan Ping na tela", () -> config.clanPingHudEnabled, value -> {
                config.clanPingHudEnabled = value;
                markConfigChanged();
            }))
            .option(Option.toggle("Tocar som ao receber ping", () -> config.clanPingSound, value -> {
                config.clanPingSound = value;
                markConfigChanged();
            }))
            .option(Option.slider("Duração do ping de local", 10.0, 180.0, 5.0, () -> config.clanPingLocationSeconds, value -> {
                config.clanPingLocationSeconds = (int) value;
                markConfigChanged();
            }, value -> (int) value + " s"))
            .option(Option.slider("Duração do ping de inimigo", 10.0, 180.0, 5.0, () -> config.clanPingEnemySeconds, value -> {
                config.clanPingEnemySeconds = (int) value;
                markConfigChanged();
            }, value -> (int) value + " s"))
            .option(Option.slider("Alcance para encontrar alvo/local", 8.0, 64.0, 8.0, () -> config.clanPingTargetRange, value -> {
                config.clanPingTargetRange = (int) value;
                markConfigChanged();
            }, value -> (int) value + " blocos"))
            .option(Option.slider("Pausa mínima entre pings", 1.0, 5.0, 1.0, () -> config.clanPingCooldownSeconds, value -> {
                config.clanPingCooldownSeconds = (int) value;
                markConfigChanged();
            }, value -> (int) value + " s"))
            .option(Option.slider("Tamanho dos marcadores 3D", 0.75, 2.0, 0.25, () -> config.clanPingMarkerScale, value -> {
                config.clanPingMarkerScale = (float) value;
                markConfigChanged();
            }, value -> String.format(Locale.ROOT, "%.2fx", value)))
            .option(Option.action("Token para enviar pings do clã", "Configurar token", () -> {
                try {
                    Field f = YNYUtils.class.getDeclaredField("clanPing");
                    f.setAccessible(true);
                    Object cp = f.get(yny != null ? yny : YNYUtils.instance);
                    if (cp != null) {
                        cp.getClass().getMethod("openTokenScreen").invoke(cp);
                    }
                } catch (Throwable ignored) {}
            }))
            .option(Option.action("Estado da conexão e envio", "Ver no chat", () -> {
                try {
                    Field f = YNYUtils.class.getDeclaredField("clanPing");
                    f.setAccessible(true);
                    Object cp = f.get(yny != null ? yny : YNYUtils.instance);
                    if (cp != null) {
                        cp.getClass().getMethod("showStatus").invoke(cp);
                    }
                } catch (Throwable ignored) {}
            }));
    }

    // -----------------------------------------------------------------------------
    // SEÇÃO 8: Diagnósticos & Logs do Sistema
    // -----------------------------------------------------------------------------
    private static void buildDiagnostics(Page page, YNYConfig config) {
        page.section("Diagnósticos & Logs do Sistema")
            .option(Option.toggle("Registrar detalhes de combate e consumíveis em log", () -> config.pvpDiagnosticsEnabled, value -> {
                config.pvpDiagnosticsEnabled = value;
                DebugLog.setEnabled(value);
                markConfigChanged();
            }));
    }

    private static boolean getToggleSprint() {
        try {
            Object instance = com.yny.utils.team.SteinCombat.instance;
            if (instance == null) return true;
            java.lang.reflect.Field sprintField = instance.getClass().getDeclaredField("sprint");
            sprintField.setAccessible(true);
            Object sprint = sprintField.get(instance);
            if (sprint == null) return true;
            java.lang.reflect.Field toggledField = sprint.getClass().getDeclaredField("toggled");
            toggledField.setAccessible(true);
            return toggledField.getBoolean(sprint);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static void setToggleSprint(boolean value) {
        try {
            Object instance = com.yny.utils.team.SteinCombat.instance;
            if (instance == null) return;
            java.lang.reflect.Field sprintField = instance.getClass().getDeclaredField("sprint");
            sprintField.setAccessible(true);
            Object sprint = sprintField.get(instance);
            if (sprint != null) {
                java.lang.reflect.Field toggledField = sprint.getClass().getDeclaredField("toggled");
                toggledField.setAccessible(true);
                toggledField.setBoolean(sprint, value);
            }
            java.lang.reflect.Field configField = instance.getClass().getDeclaredField("config");
            configField.setAccessible(true);
            Object config = configField.get(instance);
            if (config != null) {
                java.lang.reflect.Field tsField = config.getClass().getDeclaredField("toggleSprint");
                tsField.setAccessible(true);
                tsField.setBoolean(config, value);
            }
            java.lang.reflect.Method saveMethod = instance.getClass().getDeclaredMethod("save");
            saveMethod.setAccessible(true);
            saveMethod.invoke(instance);
        } catch (Throwable ignored) {}
    }

    private static void markConfigChanged() {
        ModConfig<YNYConfig> store = YNYUtils.getConfigStore();
        if (store != null) {
            store.saveSoon();
        }
    }
}
