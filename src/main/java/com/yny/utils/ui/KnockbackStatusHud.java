package com.yny.utils.ui;

import dev.xavier.stein.loader.api.HudElement;
import dev.xavier.stein.loader.api.HudPlacement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

/** Notificação temporária, posicionável no editor de HUD do Stein Loader. */
public final class KnockbackStatusHud implements HudElement {

    private static final long DISPLAY_NANOS = 2_500_000_000L;
    private static final String ENABLED_MESSAGE = "Knockback: ATIVADO";
    private static final String DISABLED_MESSAGE = "Knockback: DESATIVADO";

    private String message = ENABLED_MESSAGE;
    private int color = 0x55FF55;
    private long visibleUntil;

    /** Mostra a mudança de estado por um curto período. */
    public void show(boolean enabled) {
        message = enabled ? ENABLED_MESSAGE : DISABLED_MESSAGE;
        color = enabled ? 0x55FF55 : 0xFF5555;
        visibleUntil = System.nanoTime() + DISPLAY_NANOS;
    }

    @Override
    public String id() {
        return "ynyutils.knockback-status";
    }

    @Override
    public String name() {
        return "Notificação do Knockback";
    }

    @Override
    public boolean layout(boolean preview, float partialTicks) {
        return preview || System.nanoTime() < visibleUntil;
    }

    @Override
    public int width() {
        FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
        // A caixa não muda quando o texto troca; assim a posição configurada no
        // editor não oscila entre "ATIVADO" e "DESATIVADO".
        return Math.max(font.getStringWidth(ENABLED_MESSAGE), font.getStringWidth(DISABLED_MESSAGE));
    }

    @Override
    public int height() {
        return 10;
    }

    @Override
    public void draw(boolean alignRight, float partialTicks) {
        FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
        int x = alignRight ? -width() : 0;
        font.drawStringWithShadow(message, x, 0, color);
    }

    @Override
    public HudPlacement defaultPlacement() {
        return new HudPlacement(2, 0, -6, 6, 1.0F, true, false);
    }
}
