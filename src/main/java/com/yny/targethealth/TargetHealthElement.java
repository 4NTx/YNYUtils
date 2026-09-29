package com.yny.targethealth;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBush;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockVine;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import dev.xavier.stein.loader.api.HudElement;
import dev.xavier.stein.loader.api.HudPlacement;
import dev.xavier.stein.loader.api.Option;

public final class TargetHealthElement implements HudElement {

    private static final int WIDTH = 132;
    private static final int HEIGHT = 31;
    private static final int BAR_WIDTH = 126;
    private static final int BAR_HEIGHT = 6;
    private static final double[] RANGES = {8.0D, 16.0D, 32.0D, 48.0D, 64.0D};
    private static final int[] OPACITIES = {90, 130, 176, 220};

    private EntityLivingBase target;
    private EntityLivingBase namedTarget;
    private String name = "Target";
    private String healthText = "20 / 20";
    private float health = 20.0F;
    private float maximum = 20.0F;
    private float absorption;
    private EntityLivingBase distantTarget;
    private int scanCooldown;

    public TargetHealthElement() {
    }

    public void updateDistantTarget() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.getRenderViewEntity() == null) {
            distantTarget = null;
            return;
        }
        if (!TargetHealthMod.settings.showDistantTargets) {
            distantTarget = null;
            return;
        }
        if (directTarget(mc) != null) {
            distantTarget = null;
            scanCooldown = 0;
            return;
        }
        if (scanCooldown > 0) {
            scanCooldown--;
            return;
        }
        Entity camera = mc.getRenderViewEntity();
        double range = range();
        scanCooldown = range >= 48.0D ? 3 : 1;
        Vec3 start = camera.getPositionEyes(1.0F);
        Vec3 look = camera.getLook(1.0F);
        Vec3 end = start.addVector(look.xCoord * range, look.yCoord * range, look.zCoord * range);
        double limit = blockDistance(mc, start, end, look, range);
        EntityLivingBase nearest = null;
        double nearestDistance = limit;
        List<?> entities = mc.theWorld.loadedEntityList;
        for (int index = 0, size = entities.size(); index < size; index++) {
            Object value = entities.get(index);
            if (!(value instanceof EntityLivingBase)) {
                continue;
            }
            EntityLivingBase candidate = (EntityLivingBase) value;
            if (candidate == camera || candidate == mc.thePlayer || candidate.isDead || !candidate.canBeCollidedWith()) {
                continue;
            }
            AxisAlignedBB box = candidate.getEntityBoundingBox().expand(0.3D, 0.3D, 0.3D);
            MovingObjectPosition intercept = box.calculateIntercept(start, end);
            if (intercept == null) {
                continue;
            }
            double distance = start.distanceTo(intercept.hitVec);
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        distantTarget = nearest;
    }

    private static double blockDistance(Minecraft mc, Vec3 start, Vec3 end, Vec3 direction, double range) {
        Vec3 origin = start;
        for (int pass = 0; pass < 64; pass++) {
            MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(origin, end, false, false, true);
            if (hit == null) {
                return range;
            }
            Block block = mc.theWorld.getBlockState(hit.getBlockPos()).getBlock();
            if (!TargetHealthMod.settings.ignoreLeaves || !isFoliage(block)) {
                return start.distanceTo(hit.hitVec);
            }
            origin = hit.hitVec.addVector(direction.xCoord * 0.01D, direction.yCoord * 0.01D,
                    direction.zCoord * 0.01D);
        }
        return range;
    }

    private static boolean isFoliage(Block block) {
        return block instanceof BlockLeaves || block instanceof BlockBush || block instanceof BlockVine;
    }

    void updateTarget() {
        target = findTarget();
        if (target == null) {
            return;
        }
        if (target != namedTarget) {
            namedTarget = target;
            name = Minecraft.getMinecraft().fontRendererObj.trimStringToWidth(
                    target.getDisplayName().getUnformattedText(), BAR_WIDTH);
        }
        float nextHealth = Math.max(0.0F, target.getHealth());
        float nextMaximum = Math.max(1.0F, target.getMaxHealth());
        float nextAbsorption = Math.max(0.0F, target.getAbsorptionAmount());
        if (health != nextHealth || maximum != nextMaximum) {
            healthText = format(nextHealth) + " / " + format(nextMaximum);
        }
        health = nextHealth;
        maximum = nextMaximum;
        absorption = nextAbsorption;
    }

    @Override
    public String id() {
        return TargetHealthMod.ID + ".target";
    }

    @Override
    public String name() {
        return "Vida do alvo";
    }

    @Override
    public boolean layout(boolean preview, float partialTicks) {
        if (!TargetHealthMod.settings.enabled) {
            return false;
        }
        updateTarget();
        if (target == null && !preview) {
            return false;
        }
        if (target == null) {
            namedTarget = null;
            name = "Zombie";
            health = 14.0F;
            maximum = 20.0F;
            absorption = 0.0F;
            healthText = "14 / 20";
        }
        return true;
    }

    private EntityLivingBase findTarget() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityLivingBase direct = directTarget(mc);
        return direct != null ? direct : distantTarget != null && !distantTarget.isDead ? distantTarget : null;
    }

    private static EntityLivingBase directTarget(Minecraft mc) {
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit != null && hit.entityHit instanceof EntityLivingBase && hit.entityHit != mc.thePlayer) {
            return (EntityLivingBase) hit.entityHit;
        }
        Entity entity = mc.pointedEntity;
        if (entity instanceof EntityLivingBase && entity != mc.thePlayer) {
            return (EntityLivingBase) entity;
        }
        return null;
    }

    @Override
    public int width() {
        int template = template();
        return template == 2 ? 104 : template == 3 ? 88 : WIDTH;
    }

    @Override
    public int height() {
        int template = template();
        return template == 1 ? 35 : template == 2 ? 20 : template == 3 ? 8 : HEIGHT;
    }

    @Override
    public void draw(boolean alignRight, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRendererObj;
        int width = width();
        int x = alignRight ? -width : 0;
        int template = template();
        if (template == 1) {
            drawHearts(font, x, width);
            return;
        }
        if (template == 2) {
            drawCompact(font, x, width);
            return;
        }
        if (template == 3) {
            drawMinimal(font, x, width);
            return;
        }

        int barWidth = BAR_WIDTH;
        Gui.drawRect(x, 0, x + WIDTH, HEIGHT, backgroundColor());
        Gui.drawRect(x, 0, x + WIDTH, 1, 0xFF000000 | Option.rgb(TargetHealthMod.settings.accentColor));
        if (TargetHealthMod.settings.showName) {
            int nameWidth = WIDTH - 6;
            if (TargetHealthMod.settings.showNumbers) {
                nameWidth -= font.getStringWidth(healthText) + 5;
            }
            String clippedName = font.trimStringToWidth(name, Math.max(0, nameWidth));
            font.drawStringWithShadow(clippedName, x + 3, 4, 0xFFFFFF);
        }

        int barX = x + 3;
        int barY = 18;
        Gui.drawRect(barX, barY, barX + barWidth, barY + BAR_HEIGHT, 0xFF3B2020);
        int filled = Math.round(barWidth * Math.min(health / maximum, 1.0F));
        Gui.drawRect(barX, barY, barX + filled, barY + BAR_HEIGHT, barColor(health / maximum));

        if (TargetHealthMod.settings.showAbsorption && absorption > 0.0F) {
            int absorptionWidth = Math.round(barWidth * Math.min(absorption / maximum, 1.0F));
            Gui.drawRect(barX + filled, barY, Math.min(barX + barWidth, barX + filled + absorptionWidth),
                    barY + BAR_HEIGHT, 0xFFE7BE43);
        }
        if (TargetHealthMod.settings.showNumbers) {
            font.drawStringWithShadow(healthText, x + WIDTH - 3 - font.getStringWidth(healthText), 4, 0xFFFFFF);
        }
    }

    private static String format(float value) {
        return value == Math.round(value) ? Integer.toString(Math.round(value))
                : String.format(Locale.ROOT, "%.1f", value);
    }

    private static int healthColor(float percent) {
        if (percent > 0.5F) return 0xFF45B85A;
        if (percent > 0.25F) return 0xFFE0A93B;
        return 0xFFCF4848;
    }

    private void drawHearts(FontRenderer font, int x, int width) {
        Gui.drawRect(x, 0, x + width, height(), backgroundColor());
        Gui.drawRect(x, 0, x + width, 1, 0xFF000000 | Option.rgb(TargetHealthMod.settings.accentColor));
        if (TargetHealthMod.settings.showName) {
            font.drawStringWithShadow(font.trimStringToWidth(name, width - 6), x + 3, 4, 0xFFFFFF);
        }
        int filled = Math.min(10, (int) Math.ceil(10.0F * health / maximum));
        int color = barColor(health / maximum);
        for (int index = 0; index < 10; index++) {
            font.drawStringWithShadow("❤", x + 3 + index * 12, 17, index < filled ? color : 0xFF4A3030);
        }
        if (TargetHealthMod.settings.showAbsorption && absorption > 0.0F) {
            int extra = Math.min(10 - filled, (int) Math.ceil(10.0F * absorption / maximum));
            for (int index = 0; index < extra; index++) {
                font.drawStringWithShadow("❤", x + 3 + (filled + index) * 12, 17, 0xFFE7BE43);
            }
        }
        if (TargetHealthMod.settings.showNumbers) {
            font.drawStringWithShadow(healthText, x + 3, 27, 0xFFFFFF);
        }
    }

    private void drawCompact(FontRenderer font, int x, int width) {
        Gui.drawRect(x, 0, x + width, height(), backgroundColor());
        Gui.drawRect(x, 0, x + width, 1, 0xFF000000 | Option.rgb(TargetHealthMod.settings.accentColor));
        String value = healthText;
        int valueWidth = font.getStringWidth(value);
        if (TargetHealthMod.settings.showName) {
            int reservedWidth = TargetHealthMod.settings.showNumbers ? valueWidth + 8 : 6;
            font.drawStringWithShadow(font.trimStringToWidth(name, Math.max(0, width - reservedWidth)),
                    x + 3, 4, 0xFFFFFF);
        }
        if (TargetHealthMod.settings.showNumbers) {
            font.drawStringWithShadow(value, x + width - valueWidth - 3, 4, barColor(health / maximum));
        }
        int filled = Math.round((width - 6) * Math.min(health / maximum, 1.0F));
        Gui.drawRect(x + 3, 15, x + width - 3, 17, 0xFF3B2020);
        Gui.drawRect(x + 3, 15, x + 3 + filled, 17, barColor(health / maximum));
        if (TargetHealthMod.settings.showAbsorption && absorption > 0.0F) {
            int extra = Math.round((width - 6) * Math.min(absorption / maximum, 1.0F));
            Gui.drawRect(x + 3 + filled, 15, Math.min(x + width - 3, x + 3 + filled + extra), 17, 0xFFE7BE43);
        }
    }

    private void drawMinimal(FontRenderer font, int x, int width) {
        int color = barColor(health / maximum);
        int filled = Math.round(width * Math.min(health / maximum, 1.0F));
        Gui.drawRect(x, 0, x + width, height(), backgroundColor());
        Gui.drawRect(x, 0, x + filled, height(), color);
        if (TargetHealthMod.settings.showAbsorption && absorption > 0.0F) {
            int extra = Math.round(width * Math.min(absorption / maximum, 1.0F));
            Gui.drawRect(x + filled, 0, Math.min(x + width, x + filled + extra), height(), 0xFFE7BE43);
        }
        Gui.drawRect(x, 0, x + width, 1, 0xFF000000 | Option.rgb(TargetHealthMod.settings.accentColor));
        if (TargetHealthMod.settings.showNumbers) {
            font.drawStringWithShadow(healthText, x + (width - font.getStringWidth(healthText)) / 2, 0, 0xFFFFFF);
        }
    }

    private static int template() {
        return Math.max(0, Math.min(TargetHealthMod.settings.templateIndex, 3));
    }

    private static double range() {
        int index = TargetHealthMod.settings.rangeIndex;
        return RANGES[Math.max(0, Math.min(index, RANGES.length - 1))];
    }

    private static int backgroundColor() {
        int index = TargetHealthMod.settings.opacityIndex;
        int alpha = OPACITIES[Math.max(0, Math.min(index, OPACITIES.length - 1))];
        return alpha << 24 | 0x101010;
    }

    private static int barColor(float percent) {
        return TargetHealthMod.settings.dynamicHealthColor ? healthColor(percent)
                : 0xFF000000 | Option.rgb(TargetHealthMod.settings.healthColor);
    }

    @Override
    public HudPlacement defaultPlacement() {
        return new HudPlacement(1, 1, 0, 28, 1.0F, true, false);
    }
}
