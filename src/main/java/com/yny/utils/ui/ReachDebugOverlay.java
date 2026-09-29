package com.yny.utils.ui;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.entity.Entity;

/** Sobreposição diagnóstica temporária do Custom Reach; visível apenas enquanto ligado. */
public final class ReachDebugOverlay {
    private Entity lastTarget;
    private long lastContact = Long.MIN_VALUE;
    private long lastLimit = Long.MIN_VALUE;
    private boolean lastSelected;
    private boolean lastBlocked;
    private boolean lastWithinLimit;
    private String targetLine = "Alvo: nenhum na mira";
    private String rangeLine = "";
    private String resultLine = "";

    public void draw(boolean active, Entity target, double contact, double limit, boolean selected,
            boolean blocked, double blockDistance) {
        if (!active) {
            return;
        }

        long contactHundredths = Double.isFinite(contact) ? Math.round(contact * 100.0D) : Long.MIN_VALUE;
        long limitTenths = Math.round(limit * 10.0D);
        boolean withinLimit = target != null && Double.isFinite(contact) && contact <= limit + 0.00001D;
        if (target != lastTarget || contactHundredths != lastContact || limitTenths != lastLimit
                || selected != lastSelected || blocked != lastBlocked || withinLimit != lastWithinLimit) {
            lastTarget = target;
            lastContact = contactHundredths;
            lastLimit = limitTenths;
            lastSelected = selected;
            lastBlocked = blocked;
            lastWithinLimit = withinLimit;
            if (target != null) {
                targetLine = "Alvo: " + target.getName();
                rangeLine = String.format(Locale.ROOT, "Contato: %.2f | limite: %.1f blocos",
                        contactHundredths / 100.0D, limitTenths / 10.0D);
                resultLine = selected ? "Estado: selecionado pelo cliente"
                        : withinLimit ? "Dentro do limite; mira não aplicada"
                                : "Estado: FORA do limite configurado";
            } else if (blocked) {
                targetLine = String.format(Locale.ROOT, "Obstrução: bloco a %.2f blocos", blockDistance);
                rangeLine = String.format(Locale.ROOT, "Limite configurado: %.1f blocos", limitTenths / 10.0D);
                resultLine = "Bloco cruza a mira";
            } else {
                targetLine = "Alvo: nenhum na mira";
                rangeLine = String.format(Locale.ROOT, "Limite configurado: %.1f blocos", limitTenths / 10.0D);
                resultLine = "Aponte para uma entidade colidível";
            }
        }

        FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
        font.drawStringWithShadow("CUSTOM REACH DEBUG (temporário)", 5, 5, 0x55FFFF);
        font.drawStringWithShadow(targetLine, 5, 16, 0xFFFFFF);
        font.drawStringWithShadow(rangeLine, 5, 27, 0xFFFF55);
        font.drawStringWithShadow(resultLine, 5, 38, target != null && withinLimit ? 0x55FF55 : 0xFF7777);
    }
}
