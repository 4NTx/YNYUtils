package com.yny.utils.modules.pvp;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

import dev.xavier.stein.loader.api.Targeting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.entity.Entity;

/**
 * Diagnóstico visual e somente leitura do alvo que o Minecraft já está mirando.
 * Não muda raycast, hitbox, alcance de ataque, pacotes ou movimento.
 */
public final class ReachDebug {

    public static final double VANILLA_REACH = 3.0D;
    public static final double MAX_REACH = 3.9D;

    private final BooleanSupplier enabled;
    private final DoubleSupplier configuredReach;
    private Snapshot snapshot;

    public ReachDebug(BooleanSupplier enabled, DoubleSupplier configuredReach) {
        this.enabled = enabled;
        this.configuredReach = configuredReach;
    }

    /** Atualiza no máximo uma vez por tick, mantendo o desenho do HUD barato. */
    public void update() {
        if (!enabled.getAsBoolean()) {
            snapshot = null;
            return;
        }
        Object pointed = Targeting.pointedEntity();
        if (!(pointed instanceof Entity)) {
            snapshot = null;
            return;
        }

        Entity entity = (Entity) pointed;
        double diagnosticReach = clamp(configuredReach.getAsDouble());
        double distance = distanceToPointedEntity(entity, diagnosticReach);
        if (Double.isNaN(distance)) {
            snapshot = null;
            return;
        }
        snapshot = new Snapshot(entity.getDisplayName().getUnformattedText(), distance,
                Targeting.attackReach(), diagnosticReach);
    }

    /** Desenha somente o último diagnóstico calculado; não executa raycast. */
    public void draw() {
        Snapshot value = snapshot;
        if (value == null) {
            return;
        }
        FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
        int x = 4;
        int y = 4;
        font.drawStringWithShadow("§bReach Debug", x, y, 0xFFFFFF);
        font.drawStringWithShadow("Target: §f" + value.name, x, y + 10, 0xFFFFFF);
        font.drawStringWithShadow(String.format(Locale.ROOT, "Distance: §f%.2f", value.distance), x, y + 20, 0xFFFFFF);
        font.drawStringWithShadow(String.format(Locale.ROOT, "Vanilla Reach: §f%.2f", value.vanillaReach), x, y + 30,
                0xFFFFFF);
        font.drawStringWithShadow(String.format(Locale.ROOT, "Configured Debug Reach: §f%.2f", value.debugReach), x,
                y + 40, 0xFFFFFF);
    }

    private static double distanceToPointedEntity(Entity target, double reach) {
        Targeting.Ray ray = Targeting.raycast(reach);
        for (Targeting.Hit hit : ray.hits) {
            if (hit.entity == target && hit.distance < ray.blockDistance) {
                return hit.distance;
            }
        }
        return Double.NaN;
    }

    private static double clamp(double reach) {
        if (Double.isNaN(reach)) {
            return VANILLA_REACH;
        }
        return Math.max(VANILLA_REACH, Math.min(MAX_REACH, reach));
    }

    private static final class Snapshot {
        final String name;
        final double distance;
        final double vanillaReach;
        final double debugReach;

        Snapshot(String name, double distance, double vanillaReach, double debugReach) {
            this.name = name;
            this.distance = distance;
            this.vanillaReach = vanillaReach;
            this.debugReach = debugReach;
        }
    }
}
