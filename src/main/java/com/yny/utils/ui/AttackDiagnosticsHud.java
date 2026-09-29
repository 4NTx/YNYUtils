package com.yny.utils.ui;

import java.util.Locale;
import java.util.function.BooleanSupplier;

import dev.xavier.stein.loader.api.HudElement;
import dev.xavier.stein.loader.api.HudPlacement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

/** Diagnóstico transitório. Formatação ocorre no evento, nunca a cada desenho. */
public final class AttackDiagnosticsHud implements HudElement {
    private final BooleanSupplier enabled;
    private String target = "Alvo: jogador";
    private String distance = "Contato: 3.50 | limite: 3.9";
    private String state = "Pacote de ataque escrito (cliente)";
    private String damage = "Dano do alvo: ainda não observado";
    private long visibleUntil;

    public AttackDiagnosticsHud(BooleanSupplier enabled) {
        this.enabled = enabled;
    }

    public void show(String name, double contact, double limit) {
        FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
        target = font.trimStringToWidth("Alvo: " + name, width());
        distance = Double.isFinite(contact)
                ? String.format(Locale.ROOT, "Contato: %.2f | limite: %.1f", contact, limit)
                : String.format(Locale.ROOT, "Contato: indisponível | limite: %.1f", limit);
        damage = "Dano do alvo: ainda não observado";
        visibleUntil = System.nanoTime() + 3_000_000_000L;
    }

    public void observeDamage() {
        damage = "Dano observado (autoria não confirmada)";
    }

    public void clear() {
        visibleUntil = 0;
    }

    @Override
    public String id() {
        return "ynyutils.attack-diagnostics";
    }

    @Override
    public String name() {
        return "Diagnóstico de ataques";
    }

    @Override
    public boolean layout(boolean preview, float partialTicks) {
        return preview || enabled.getAsBoolean() && System.nanoTime() < visibleUntil;
    }

    @Override
    public int width() {
        return 230;
    }

    @Override
    public int height() {
        return 40;
    }

    @Override
    public void draw(boolean alignRight, float partialTicks) {
        FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
        int x = alignRight ? -width() : 0;
        font.drawStringWithShadow(target, x, 0, 0xFFFFFF);
        font.drawStringWithShadow(distance, x, 10, 0x55AAFF);
        font.drawStringWithShadow(state, x, 20, 0xAAAAAA);
        font.drawStringWithShadow(damage, x, 30, 0xAAAAAA);
    }

    @Override
    public HudPlacement defaultPlacement() {
        return new HudPlacement(0, 0, 6, 20, 1.0F, false, false);
    }
}
