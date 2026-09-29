package com.yny.utils;

import com.yny.utils.config.YNYConfig;
import com.yny.utils.modules.pvp.KnockbackControl;

import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Page;
import dev.xavier.stein.loader.api.SteinMod;

/** Entry point do YNYUtils para o Stein Loader. */
public final class YNYUtils implements SteinMod {

    private static volatile YNYConfig config = new YNYConfig();
    private final KnockbackControl knockbackControl = new KnockbackControl(
            () -> config.knockbackEnabled, () -> config.knockbackPercent);

    @Override
    public void afterStartGame() {
        config = YNYConfig.load();
    }

    @Override
    public void onTickEnd() {
        knockbackControl.ensureInstalled();
    }

    @Override
    public void onPage(Page page) {
        page.title("YNYUtils")
                .icon("combat")
                .section("PvP")
                .option(Option.toggle("Ativar Knockback Control", () -> config.knockbackEnabled, value -> {
                    config.knockbackEnabled = value;
                    YNYConfig.save(config);
                }))
                .option(Option.slider("Knockback", 93, 100, 1, () -> config.knockbackPercent, value -> {
                    config.knockbackPercent = (int) value;
                    YNYConfig.save(config);
                }, value -> (int) value + "%"));
    }
}
