package com.yny.targethealth;

import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Page;
import dev.xavier.stein.loader.api.ModConfig;

/** Configuração e opções do indicador; o entrypoint único é YNYUtils. */
public final class TargetHealthMod {

    static final String ID = "ynydamageindicator";
    private static final String[] RANGES = {"8 blocos", "16 blocos", "32 blocos", "48 blocos", "64 blocos"};
    private static final String[] OPACITIES = {"Baixa", "Média", "Alta", "Sólida"};
    private static final String[] TEMPLATES = {"Clássico", "Corações", "Compacto", "Minimalista"};
    static Settings settings = new Settings();
    private static ModConfig<Settings> config;

    public static void initialize() {
        config = ModConfig.of(ID, Settings.class);
        settings = config.get();
    }

    public static void save() {
        if (config != null) {
            config.save();
        }
    }

    public static void addOptions(Page page) {
        page.section("Damage Indicator: vida e equipamento do alvo")
            .option(Option.toggle("Exibir Damage Indicator: vida e equipamento do alvo", () -> settings.enabled, value -> {
                settings.enabled = value;
                config.saveSoon();
            }))
            .option(Option.toggle("Exibir nome do alvo", () -> settings.showName, value -> {
                settings.showName = value;
                config.saveSoon();
            }))
            .option(Option.toggle("Exibir vida atual e máxima", () -> settings.showNumbers, value -> {
                settings.showNumbers = value;
                config.saveSoon();
            }))
            .option(Option.toggle("Exibir corações de absorção", () -> settings.showAbsorption, value -> {
                settings.showAbsorption = value;
                config.saveSoon();
            }))
            .option(Option.toggle("Exibir item da mão e durabilidade", () -> settings.showHeldItem, value -> {
                settings.showHeldItem = value;
                config.saveSoon();
            }))
            .option(Option.toggle("Exibir armaduras e durabilidade", () -> settings.showArmorDurability, value -> {
                settings.showArmorDurability = value;
                config.saveSoon();
            }))
            .option(Option.toggle("Exibir distância até o alvo", () -> settings.showDistance, value -> {
                settings.showDistance = value;
                config.saveSoon();
            }))
            .option(Option.toggle("Buscar alvos além da mira", () -> settings.showDistantTargets, value -> {
                settings.showDistantTargets = value;
                config.saveSoon();
            }))
            .option(Option.cycle("Alcance máximo da busca visual", RANGES, () -> settings.rangeIndex, value -> {
                settings.rangeIndex = value;
                config.saveSoon();
            }))
            .option(Option.toggle("Ver através de folhagem", () -> settings.ignoreLeaves, value -> {
                settings.ignoreLeaves = value;
                config.saveSoon();
            }))
            .option(Option.toggle("Ignorar manequins (armor stands)", () -> settings.ignoreArmorStands, value -> {
                settings.ignoreArmorStands = value;
                config.saveSoon();
            }))
            .option(Option.toggle("Não exibir aliados nem grupo/clã", () -> settings.ignoreAllies, value -> {
                settings.ignoreAllies = value;
                config.saveSoon();
            }))
            .option(Option.cycle("Aparência do indicador", TEMPLATES, () -> settings.templateIndex, value -> {
                settings.templateIndex = value;
                config.saveSoon();
            }))
            .option(Option.color("Cor de destaque da HUD", () -> settings.accentColor, value -> {
                settings.accentColor = value;
                config.saveSoon();
            }))
            .option(Option.toggle("Mudar cor conforme a vida", () -> settings.dynamicHealthColor, value -> {
                settings.dynamicHealthColor = value;
                config.saveSoon();
            }))
            .option(Option.color("Cor fixa da barra de vida", () -> settings.healthColor, value -> {
                settings.healthColor = value;
                settings.dynamicHealthColor = false;
                config.saveSoon();
            }))
            .option(Option.cycle("Transparência do fundo", OPACITIES, () -> settings.opacityIndex, value -> {
                settings.opacityIndex = value;
                config.saveSoon();
            }))
            .restore(() -> {
                settings = config.reset();
            });
    }
}

