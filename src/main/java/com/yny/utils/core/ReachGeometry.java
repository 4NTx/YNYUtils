package com.yny.utils.core;

import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/** Geometria vanilla; distância até o contato, não até o centro da entidade. */
public final class ReachGeometry {
    private ReachGeometry() {
    }

    public static Vec3 contact(Vec3 eyes, Vec3 end, AxisAlignedBB box) {
        if (box.isVecInside(eyes)) {
            return eyes;
        }
        MovingObjectPosition intercept = box.calculateIntercept(eyes, end);
        return intercept == null ? null : intercept.hitVec;
    }

    public static boolean beforeBlock(double squaredDistance, double squaredBlockDistance) {
        return squaredDistance < squaredBlockDistance;
    }
}
