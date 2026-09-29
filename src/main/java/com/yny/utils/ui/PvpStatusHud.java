package com.yny.utils.ui;

import java.util.Locale;
import java.util.function.BooleanSupplier;

import dev.xavier.stein.loader.api.HudElement;
import dev.xavier.stein.loader.api.HudPlacement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

/** Notificações temporárias de KB e Reach, posicionáveis pelo HUD do Stein. */
public final class PvpStatusHud implements HudElement {

    private static final long DISPLAY_NANOS = 2_500_000_000L;
    private static final String KB_ENABLED = "Knockback: ATIVADO";
    private static final String KB_DISABLED = "Knockback: DESATIVADO";
    private static final String WIDEST_MESSAGE = "Reach: DESATIVADO (3.9 blocos)";

    private final BooleanSupplier knockbackNotificationsEnabled;
    private final BooleanSupplier reachNotificationsEnabled;
    private String message = KB_ENABLED;
    private int color = 0x55FF55;
    private long visibleUntil;
    private boolean reachMessage;

    public PvpStatusHud(BooleanSupplier knockbackNotificationsEnabled, BooleanSupplier reachNotificationsEnabled) {
        this.knockbackNotificationsEnabled = knockbackNotificationsEnabled;
        this.reachNotificationsEnabled = reachNotificationsEnabled;
    }

    public void showKnockback(boolean enabled) {
        message = enabled ? KB_ENABLED : KB_DISABLED;
        color = enabled ? 0x55FF55 : 0xFF5555;
        reachMessage = false;
        visibleUntil = System.nanoTime() + DISPLAY_NANOS;
    }

    public void showReach(boolean enabled, double distance) {
        message = "Reach: " + (enabled ? "ATIVADO" : "DESATIVADO") + " ("
                + String.format(Locale.ROOT, "%.1f", distance) + " blocos)";
        color = enabled ? 0x55AAFF : 0xFF5555;
        reachMessage = true;
        visibleUntil = System.nanoTime() + DISPLAY_NANOS;
    }

    @Override
    public String id() {
        // Mantém a chave anterior para preservar posição/escala já configuradas.
        return "ynyutils.knockback-status";
    }

    @Override
    public String name() {
        return "Notificações de PvP";
    }

    @Override
    public boolean layout(boolean preview, float partialTicks) {
        boolean enabled = reachMessage ? reachNotificationsEnabled.getAsBoolean()
                : knockbackNotificationsEnabled.getAsBoolean();
        return preview || enabled && System.nanoTime() < visibleUntil;
    }

    @Override
    public int width() {
        FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
        return Math.max(font.getStringWidth(WIDEST_MESSAGE),
                Math.max(font.getStringWidth(KB_ENABLED), font.getStringWidth(KB_DISABLED)));
    }

    @Override
    public int height() {
        return 10;
    }

    @Override
    public void draw(boolean alignRight, float partialTicks) {
        int x = alignRight ? -width() : 0;
        Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(message, x, 0, color);
    }

    @Override
    public HudPlacement defaultPlacement() {
        return new HudPlacement(2, 0, -6, 6, 1.0F, true, false);
    }
}
