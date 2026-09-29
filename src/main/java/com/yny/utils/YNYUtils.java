package com.yny.utils;

import com.yny.utils.config.YNYConfig;
import com.yny.utils.modules.pvp.KnockbackControl;

import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Page;
import dev.xavier.stein.loader.api.SteinMod;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/** Entry point do YNYUtils para o Stein Loader. */
public final class YNYUtils implements SteinMod {

    private static volatile YNYConfig config = new YNYConfig();
    private final KnockbackControl knockbackControl = new KnockbackControl(
            () -> config.knockbackEnabled, () -> config.knockbackPercent);
    private boolean toggleKeyWasDown;

    @Override
    public void afterStartGame() {
        config = YNYConfig.load();
    }

    @Override
    public void onTickEnd() {
        knockbackControl.ensureInstalled();
        updateToggleKey();
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
                .option(Option.slider("Velocidade recebida", 93, 100, 1, () -> config.knockbackPercent, value -> {
                    config.knockbackPercent = (int) value;
                    YNYConfig.save(config);
                }, value -> (int) value + "% (redução: " + (100 - (int) value) + "%)"))
                .option(Option.key("Tecla para alternar", () -> config.knockbackToggleKey, value -> {
                    config.knockbackToggleKey = value;
                    YNYConfig.save(config);
                }));
    }

    private void updateToggleKey() {
        int key = config.knockbackToggleKey;
        boolean down = key != Keyboard.KEY_NONE && Minecraft.getMinecraft().currentScreen == null
                && Keyboard.isKeyDown(key);
        if (down && !toggleKeyWasDown) {
            config.knockbackEnabled = !config.knockbackEnabled;
            YNYConfig.save(config);
        }
        toggleKeyWasDown = down;
    }
}
