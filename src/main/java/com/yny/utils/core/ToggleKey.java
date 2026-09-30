package com.yny.utils.core;

import net.minecraft.client.Minecraft;
import dev.xavier.stein.loader.api.Keys;

/** Detecta somente a borda de pressionamento de uma tecla configurável. */
public final class ToggleKey {

    private boolean wasDown;

    public boolean wasPressed(int keyCode) {
        boolean down = keyCode != Keys.KEY_NONE && Minecraft.getMinecraft().currentScreen == null
                && Keys.isDown(keyCode);
        boolean pressed = down && !wasDown;
        wasDown = down;
        return pressed;
    }
}
