package com.yny.utils.modules.player;

import com.yny.utils.config.YNYConfig;
import dev.xavier.stein.loader.api.ModConfig;
import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Page;

/** Settings owned by YNYUtils, independent of Stein Combat. */
public final class AutoProtectOptions {
    private AutoProtectOptions() { }

    public static void add(Page page, YNYConfig config, ModConfig<YNYConfig> store) {
        page.section("Defesa automática (YNYUtils)")
            .option(Option.toggle("Defender automaticamente com espada", () -> config.autoProtectEnabled,
                value -> { config.autoProtectEnabled = value; store.saveSoon(); }))
            .option(Option.key("Atalho para alternar defesa automática", () -> config.autoProtectToggleKey,
                value -> { config.autoProtectToggleKey = value; store.saveSoon(); }))
            .option(Option.toggle("Defender ao abrir janela, parado", () -> config.autoProtectWindow,
                value -> { config.autoProtectWindow = value; store.saveSoon(); }))
            .option(Option.toggle("Defender por vida baixa", () -> config.autoProtectLowHealthEnabled,
                value -> { config.autoProtectLowHealthEnabled = value; store.saveSoon(); }))
            .option(Option.slider("Defender quando vida chegar a", 1, 16, 1,
                () -> config.autoProtectLowHealth,
                value -> { config.autoProtectLowHealth = (int) value; config.sanitize(); store.saveSoon(); },
                value -> (int) value + " HP"))
            .option(Option.slider("Soltar defesa quando vida chegar a", 2, 20, 1,
                () -> config.autoProtectResumeHealth,
                value -> { config.autoProtectResumeHealth = (int) value; config.sanitize(); store.saveSoon(); },
                value -> (int) value + " HP"));
    }
}
