package com.yny.targethealth;

import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Page;

/** Configuração e opções do indicador; o entrypoint único é YNYUtils. */
public final class TargetHealthMod {

    static final String ID = "ynydamageindicator";
    private static final String[] RANGES = {"8 blocos", "16 blocos", "32 blocos", "48 blocos", "64 blocos"};
    private static final String[] OPACITIES = {"Baixa", "Média", "Alta", "Sólida"};
    private static final String[] TEMPLATES = {"Clássico", "Corações", "Compacto", "Minimalista"};
    static Settings settings = new Settings();

    public static void initialize() {
        settings = Settings.load();
    }

    public static void addOptions(Page page) {
        page.section("YNY Damage Indicator")
            .section("Exibição")
            .option(Option.toggle("Ativar indicador", () -> settings.enabled, value -> {
                settings.enabled = value;
                Settings.save(settings);
            }))
            .option(Option.toggle("Mostrar nome do alvo", () -> settings.showName, value -> {
                settings.showName = value;
                Settings.save(settings);
            }))
            .option(Option.toggle("Mostrar vida em números", () -> settings.showNumbers, value -> {
                settings.showNumbers = value;
                Settings.save(settings);
            }))
            .option(Option.toggle("Mostrar absorção", () -> settings.showAbsorption, value -> {
                settings.showAbsorption = value;
                Settings.save(settings);
            }))
            .section("Alvos")
            .option(Option.toggle("Mostrar alvos distantes", () -> settings.showDistantTargets, value -> {
                settings.showDistantTargets = value;
                Settings.save(settings);
            }))
            .option(Option.cycle("Alcance visual", RANGES, () -> settings.rangeIndex, value -> {
                settings.rangeIndex = value;
                Settings.save(settings);
            }))
            .option(Option.toggle("Ignorar folhagem", () -> settings.ignoreLeaves, value -> {
                settings.ignoreLeaves = value;
                Settings.save(settings);
            }))
            .section("Aparência")
            .option(Option.cycle("Estilo da HUD", TEMPLATES, () -> settings.templateIndex, value -> {
                settings.templateIndex = value;
                Settings.save(settings);
            }))
            .option(Option.color("Cor de destaque", () -> settings.accentColor, value -> {
                settings.accentColor = value;
                Settings.save(settings);
            }))
            .option(Option.toggle("Cor de vida dinâmica", () -> settings.dynamicHealthColor, value -> {
                settings.dynamicHealthColor = value;
                Settings.save(settings);
            }))
            .option(Option.color("Cor da vida", () -> settings.healthColor, value -> {
                settings.healthColor = value;
                settings.dynamicHealthColor = false;
                Settings.save(settings);
            }))
            .option(Option.cycle("Opacidade do fundo", OPACITIES, () -> settings.opacityIndex, value -> {
                settings.opacityIndex = value;
                Settings.save(settings);
            }))
            .restore(() -> {
                settings = new Settings();
                Settings.save(settings);
            });
    }
}
