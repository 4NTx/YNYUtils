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
        page.section("Damage Indicator")
            .option(Option.toggle("Ativar indicador", () -> settings.enabled, value -> {
                settings.enabled = value;
                config.saveSoon();
            }))
            .option(Option.toggle("Mostrar nome do alvo", () -> settings.showName, value -> {
                settings.showName = value;
                config.saveSoon();
            }))
            .option(Option.toggle("Mostrar vida em números", () -> settings.showNumbers, value -> {
                settings.showNumbers = value;
                config.saveSoon();
            }))
            .option(Option.toggle("Mostrar absorção", () -> settings.showAbsorption, value -> {
                settings.showAbsorption = value;
                config.saveSoon();
            }))
            .option(Option.toggle("Mostrar alvos distantes", () -> settings.showDistantTargets, value -> {
                settings.showDistantTargets = value;
                config.saveSoon();
            }))
            .option(Option.cycle("Alcance visual", RANGES, () -> settings.rangeIndex, value -> {
                settings.rangeIndex = value;
                config.saveSoon();
            }))
            .option(Option.toggle("Ignorar folhagem", () -> settings.ignoreLeaves, value -> {
                settings.ignoreLeaves = value;
                config.saveSoon();
            }))
            .option(Option.cycle("Estilo da HUD", TEMPLATES, () -> settings.templateIndex, value -> {
                settings.templateIndex = value;
                config.saveSoon();
            }))
            .option(Option.color("Cor de destaque", () -> settings.accentColor, value -> {
                settings.accentColor = value;
                config.saveSoon();
            }))
            .option(Option.toggle("Cor de vida dinâmica", () -> settings.dynamicHealthColor, value -> {
                settings.dynamicHealthColor = value;
                config.saveSoon();
            }))
            .option(Option.color("Cor da vida", () -> settings.healthColor, value -> {
                settings.healthColor = value;
                settings.dynamicHealthColor = false;
                config.saveSoon();
            }))
            .option(Option.cycle("Opacidade do fundo", OPACITIES, () -> settings.opacityIndex, value -> {
                settings.opacityIndex = value;
                config.saveSoon();
            }))
            .restore(() -> {
                settings = config.reset();
            });
    }
}
