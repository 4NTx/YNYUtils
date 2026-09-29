package com.yny.utils.modules.pvp;

import com.yny.utils.core.ReachGeometry;
import com.google.common.base.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EntitySelectors;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/** Seleção somente leitura, seguindo filtros/caixas de EntityRenderer 1.8.9. */
final class EntityReachRaycast {
    private static final Predicate<Entity> COLLIDABLE = entity ->
            EntitySelectors.NOT_SPECTATING.apply(entity) && entity.canBeCollidedWith();

    private EntityReachRaycast() {
    }

    static MovingObjectPosition select(Minecraft mc, float partialTicks, double reach,
            MovingObjectPosition vanilla) {
        Entity camera = mc.getRenderViewEntity();
        Vec3 eyes = camera.getPositionEyes(partialTicks);
        Vec3 look = camera.getLook(partialTicks);
        Vec3 end = eyes.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
        // MISS pode ter descartado a entidade (>3) e escondido o resultado de blocos.
        // Reutilizar BLOCK; somente nesse outro caso repetir a consulta de blocos.
        MovingObjectPosition block = vanilla != null && vanilla.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                ? vanilla : camera.rayTrace(mc.playerController.getBlockReachDistance(), partialTicks);
        double blockSquared = block != null && block.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                ? eyes.squareDistanceTo(block.hitVec) : Double.POSITIVE_INFINITY;
        double nearestSquared = reach * reach;
        Entity nearest = null;
        Vec3 contact = null;
        AxisAlignedBB search = camera.getEntityBoundingBox()
                .addCoord(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach).expand(1, 1, 1);

        for (Entity entity : mc.theWorld.getEntitiesInAABBexcluding(camera, search, COLLIDABLE)) {
            double border = entity.getCollisionBorderSize();
            AxisAlignedBB box = entity.getEntityBoundingBox().expand(border, border, border);
            Vec3 hit = ReachGeometry.contact(eyes, end, box);
            if (hit == null) {
                continue;
            }
            double distanceSquared = eyes.squareDistanceTo(hit);
            if (!ReachGeometry.beforeBlock(distanceSquared, blockSquared)) {
                continue;
            }
            if (entity == camera.ridingEntity && !box.isVecInside(eyes)) {
                continue;
            }
            if (distanceSquared < nearestSquared || nearest == null && distanceSquared <= nearestSquared) {
                nearestSquared = distanceSquared;
                nearest = entity;
                contact = hit;
            }
        }
        return nearest == null ? null : new MovingObjectPosition(nearest, contact);
    }
}
