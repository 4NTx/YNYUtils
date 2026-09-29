package com.yny.utils.modules.pvp;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

import dev.xavier.stein.loader.api.Targeting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/**
 * Amplia a seleção local de entidades sem alterar hitboxes, movimento ou
 * pacotes. O clique vanilla continua usando {@link Minecraft#objectMouseOver}.
 */
public final class CustomReach {

    public static final double VANILLA_REACH = 3.0D;
    public static final double MAX_REACH = 3.9D;

    private final BooleanSupplier enabled;
    private final DoubleSupplier reach;

    public CustomReach(BooleanSupplier enabled, DoubleSupplier reach) {
        this.enabled = enabled;
        this.reach = reach;
    }

    /**
     * Chamado depois do raycast vanilla do quadro. Só substitui o resultado se
     * uma entidade estiver na linha da mira antes de qualquer bloco.
     */
    public void apply() {
        if (!enabled.getAsBoolean()) {
            return;
        }
        Targeting.Ray ray = Targeting.raycast(clamp(reach.getAsDouble()));
        Targeting.Hit hit = ray.first();
        if (hit == null || !(hit.entity instanceof Entity)) {
            return;
        }

        Entity entity = (Entity) hit.entity;
        Minecraft mc = Minecraft.getMinecraft();
        mc.objectMouseOver = new MovingObjectPosition(entity, new Vec3(hit.x, hit.y, hit.z));
        mc.pointedEntity = entity;
    }

    private static double clamp(double value) {
        if (Double.isNaN(value)) {
            return VANILLA_REACH;
        }
        return Math.max(VANILLA_REACH, Math.min(MAX_REACH, value));
    }
}
