package com.yny.utils.ui;

import java.util.function.BooleanSupplier;
import java.util.Locale;

import dev.xavier.stein.loader.api.HudElement;
import dev.xavier.stein.loader.api.HudPlacement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

/** Notificação temporária, posicionável no editor de HUD do Stein Loader. */
public final class KnockbackStatusHud implements HudElement {

    private static final long DISPLAY_NANOS = 2_500_000_000L;
    private static final String ENABLED_MESSAGE = "Knockback: ATIVADO";
    private static final String DISABLED_MESSAGE = "Knockback: DESATIVADO";
    private static final String MAX_MESSAGE = "Reach: DESATIVADO (3.9 blocos)";

    private final BooleanSupplier knockbackEnabled;
    private final BooleanSupplier reachEnabled;
    private String message = ENABLED_MESSAGE;
    private int color = 0x55FF55;
    private long visibleUntil;
    private boolean reachMessage;

    public KnockbackStatusHud(BooleanSupplier knockbackEnabled, BooleanSupplier reachEnabled) {
        this.knockbackEnabled = knockbackEnabled;
        this.reachEnabled = reachEnabled;
    }

    /** Mostra a mudança de estado por um curto período. */
    public void showKnockback(boolean enabled) {
        message = enabled ? ENABLED_MESSAGE : DISABLED_MESSAGE;
        color = enabled ? 0x55FF55 : 0xFF5555;
        reachMessage = false;
        visibleUntil = System.nanoTime() + DISPLAY_NANOS;
    }

    /** Mostra o estado e o alcance configurado do Custom Reach. */
    public void showReach(boolean enabled, double distance) {
        message = "Reach: " + (enabled ? "ATIVADO" : "DESATIVADO") + " ("
                + String.format(Locale.ROOT, "%.1f", distance) + " blocos)";
        color = enabled ? 0x55AAFF : 0xFF5555;
        reachMessage = true;
        visibleUntil = System.nanoTime() + DISPLAY_NANOS;
    }

    @Override
    public String id() {
        return "ynyutils.knockback-status";
    }

    @Override
    public String name() {
        return "Notificações de PvP";
    }

    @Override
    public boolean layout(boolean preview, float partialTicks) {
        boolean enabled = reachMessage ? reachEnabled.getAsBoolean() : knockbackEnabled.getAsBoolean();
        return preview || enabled && System.nanoTime() < visibleUntil;
    }

    @Override
    public int width() {
        FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
        // A caixa não muda entre KB e Reach; a posição configurada não oscila.
        return Math.max(font.getStringWidth(MAX_MESSAGE),
                Math.max(font.getStringWidth(ENABLED_MESSAGE), font.getStringWidth(DISABLED_MESSAGE)));
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
