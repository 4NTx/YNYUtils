package com.yny.utils.ui;

import java.util.Locale;
import java.util.function.BooleanSupplier;

import dev.xavier.stein.loader.api.HudElement;
import dev.xavier.stein.loader.api.HudPlacement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

/** Notificações temporárias de módulos PvP, posicionáveis pelo HUD do Stein. */
public final class PvpStatusHud implements HudElement {

    private static final long DISPLAY_NANOS = 2_500_000_000L;
    private static final String KB_ENABLED = "Knockback: ATIVADO";
    private static final String KB_DISABLED = "Knockback: DESATIVADO";
    private static final String WIDEST_MESSAGE = "Reach: DESATIVADO (3.9 blocos)";
    private static final String AUTO_ARMOR_ENABLED = "Auto Armor: ATIVADO";
    private static final String AUTO_ARMOR_DISABLED = "Auto Armor: DESATIVADO";

    private final BooleanSupplier knockbackNotificationsEnabled;
    private final BooleanSupplier reachNotificationsEnabled;
    private final BooleanSupplier autoArmorNotificationsEnabled;
    private String message = KB_ENABLED;
    private int color = 0x55FF55;
    private long visibleUntil;
    private boolean reachMessage;
    private boolean autoArmorMessage;
    private FontRenderer measuredFont;
    private boolean measuredUnicode;
    private int measuredWidth;

    public void invalidateFontMetrics() {
        measuredFont = null;
    }

    public PvpStatusHud(BooleanSupplier knockbackNotificationsEnabled, BooleanSupplier reachNotificationsEnabled,
            BooleanSupplier autoArmorNotificationsEnabled) {
        this.knockbackNotificationsEnabled = knockbackNotificationsEnabled;
        this.reachNotificationsEnabled = reachNotificationsEnabled;
        this.autoArmorNotificationsEnabled = autoArmorNotificationsEnabled;
    }

    public void showKnockback(boolean enabled) {
        message = enabled ? KB_ENABLED : KB_DISABLED;
        color = enabled ? 0x55FF55 : 0xFF5555;
        reachMessage = false;
        autoArmorMessage = false;
        visibleUntil = System.nanoTime() + DISPLAY_NANOS;
    }

    public void showReach(boolean enabled, double distance) {
        message = "Reach: " + (enabled ? "ATIVADO" : "DESATIVADO") + " ("
                + String.format(Locale.ROOT, "%.1f", distance) + " blocos)";
        color = enabled ? 0x55AAFF : 0xFF5555;
        reachMessage = true;
        autoArmorMessage = false;
        visibleUntil = System.nanoTime() + DISPLAY_NANOS;
    }

    public void showAutoArmor(boolean enabled) {
        message = enabled ? AUTO_ARMOR_ENABLED : AUTO_ARMOR_DISABLED;
        color = enabled ? 0x55FF55 : 0xFF5555;
        reachMessage = false;
        autoArmorMessage = true;
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
                : autoArmorMessage ? autoArmorNotificationsEnabled.getAsBoolean()
                        : knockbackNotificationsEnabled.getAsBoolean();
        return preview || enabled && System.nanoTime() < visibleUntil;
    }

    @Override
    public int width() {
        FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
        if (font != measuredFont || font.getUnicodeFlag() != measuredUnicode) {
            measuredWidth = Math.max(Math.max(font.getStringWidth(WIDEST_MESSAGE),
                    Math.max(font.getStringWidth(KB_ENABLED), font.getStringWidth(KB_DISABLED))),
                    Math.max(font.getStringWidth(AUTO_ARMOR_ENABLED), font.getStringWidth(AUTO_ARMOR_DISABLED)));
            measuredFont = font;
            measuredUnicode = font.getUnicodeFlag();
        }
        return measuredWidth;
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
