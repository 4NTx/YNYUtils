package com.yny.utils.modules.pvp;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

import com.yny.utils.core.ReachRange;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

/**
 * Amplia a seleção local de entidades sem alterar hitboxes, movimento ou
 * pacotes. O clique vanilla continua usando {@link Minecraft#objectMouseOver}.
 */
public final class CustomReach {

    public static final double VANILLA_REACH = ReachRange.VANILLA;
    public static final double MAX_REACH = ReachRange.MAXIMUM;

    private final BooleanSupplier enabled;
    private final DoubleSupplier reach;
    private MovingObjectPosition applied;
    private MovingObjectPosition baseline;
    private Entity baselinePointed;
    private World baselineWorld;
    private long epoch;
    private long cachedEpoch = -1;
    private float cachedPartialTicks;
    private double cachedReach;
    private Entity cachedCamera;
    private double cachedX, cachedY, cachedZ;
    private float cachedYaw, cachedPitch;
    private MovingObjectPosition cachedResult;
    private MovingObjectPosition cachedBaseline;

    public CustomReach(BooleanSupplier enabled, DoubleSupplier reach) {
        this.enabled = enabled;
        this.reach = reach;
    }

    public double effectiveReach() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.playerController != null && mc.playerController.extendedReach()) {
            return 6.0D;
        }
        return enabled.getAsBoolean() ? ReachRange.clamp(reach.getAsDouble()) : VANILLA_REACH;
    }

    public void onFrame(float partialTicks) {
        epoch++;
        synchronize(partialTicks);
    }

    /** runTick calcula a mira com partialTicks=1 antes de consumir as teclas. */
    public void onInput() {
        synchronize(1.0F);
    }

    public void onTickEnd() {
        epoch++;
        if (!enabled.getAsBoolean() || ReachRange.clamp(reach.getAsDouble()) == VANILLA_REACH
                || Minecraft.getMinecraft().theWorld == null) {
            configurationChanged();
        }
    }

    /** Panel/keybind invalidam imediatamente a seleção, inclusive ao desligar. */
    public void configurationChanged() {
        epoch++;
        restore();
        baseline = null;
        baselinePointed = null;
        baselineWorld = null;
        cachedBaseline = null;
        cachedCamera = null;
        cachedResult = null;
        cachedEpoch = -1;
    }

    private void synchronize(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        double distance = ReachRange.clamp(reach.getAsDouble());
        Entity camera = mc.getRenderViewEntity();
        if (!enabled.getAsBoolean() || distance == VANILLA_REACH || mc.theWorld == null
                || mc.thePlayer == null || mc.currentScreen != null || camera != mc.thePlayer
                || mc.playerController == null || mc.playerController.isSpectator()
                || mc.playerController.extendedReach()) {
            restore();
            return;
        }

        // Nunca restaura uma seleção antiga por cima do vanilla ou de outro mod.
        if (mc.objectMouseOver != applied) {
            applied = null;
            baseline = mc.objectMouseOver;
            baselinePointed = mc.pointedEntity;
            baselineWorld = mc.theWorld;
        }
        if (baseline != null && baseline.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
            // O vanilla já encontrou o alvo mais próximo: reaproveitar sem nova busca.
            restore();
            return;
        }
        if (cachedEpoch != epoch || cachedPartialTicks != partialTicks || cachedReach != distance
                || cachedBaseline != baseline || cachedCamera != camera || cachedX != camera.posX
                || cachedY != camera.posY || cachedZ != camera.posZ
                || cachedYaw != camera.rotationYaw || cachedPitch != camera.rotationPitch) {
            cachedResult = EntityReachRaycast.select(mc, partialTicks, distance, baseline);
            cachedEpoch = epoch;
            cachedPartialTicks = partialTicks;
            cachedReach = distance;
            cachedBaseline = baseline;
            cachedCamera = camera;
            cachedX = camera.posX;
            cachedY = camera.posY;
            cachedZ = camera.posZ;
            cachedYaw = camera.rotationYaw;
            cachedPitch = camera.rotationPitch;
        }
        if (cachedResult == null) {
            restore();
            return;
        }
        applied = cachedResult;
        mc.objectMouseOver = applied;
        Entity target = applied.entityHit;
        mc.pointedEntity = target instanceof EntityLivingBase || target instanceof EntityItemFrame ? target : null;
    }

    private void restore() {
        Minecraft mc = Minecraft.getMinecraft();
        if (applied != null && mc.objectMouseOver == applied) {
            mc.objectMouseOver = mc.theWorld == baselineWorld ? baseline : null;
            mc.pointedEntity = mc.theWorld == baselineWorld ? baselinePointed : null;
        }
        applied = null;
    }
}
