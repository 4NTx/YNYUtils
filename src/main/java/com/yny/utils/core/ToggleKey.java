package com.yny.utils.core;

import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/** Detecta somente a borda de pressionamento de uma tecla configurável. */
public final class ToggleKey {

    private boolean wasDown;

    public boolean wasPressed(int keyCode) {
        boolean down = keyCode != Keyboard.KEY_NONE && Minecraft.getMinecraft().currentScreen == null
                && Keyboard.isKeyDown(keyCode);
        boolean pressed = down && !wasDown;
        wasDown = down;
        return pressed;
    }
}
