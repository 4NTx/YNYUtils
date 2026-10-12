package com.yny.utils.modules.pvp;

import dev.xavier.stein.loader.api.Relation;
import dev.xavier.stein.loader.api.Relations;
import dev.xavier.stein.loader.api.Targeting;
import dev.xavier.stein.loader.api.Targeting.Hit;
import dev.xavier.stein.loader.api.Targeting.Ray;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.BooleanSupplier;

public final class HitThroughAllies {
    private final BooleanSupplier enabled;

    public HitThroughAllies(BooleanSupplier enabled, BooleanSupplier diagnostics) {
        this.enabled = enabled;
    }

    public Object targetBehindAllies(Object clickedAlly) {
        if (!enabled.getAsBoolean()) return null;
        Ray ray = Targeting.raycast();
        return firstTargetBeforeBlock(ray, Relations::of, HitThroughAllies::randomAllyRoll);
    }

    public boolean shouldCancel(Object entity) {
        if (entity == null) return false;
        Relation relation = Relations.of(entity);
        return shouldCancel(enabled.getAsBoolean(), relation.friendly());
    }

    static boolean shouldCancel(boolean active, boolean friendly) {
        return active && friendly;
    }

    static Object firstTargetBeforeBlock(Ray ray, Function<Object, Relation> relationOf,
            BooleanSupplier hitFirstAlly) {
        if (ray == null || relationOf == null || hitFirstAlly == null) return null;
        boolean allyChanceUsed = false;
        for (Hit hit : ray.hits) {
            if (hit.distance >= ray.blockDistance) break;
            if (hit.entity == null) continue;
            Relation relation = relationOf.apply(hit.entity);
            if (relation == null || relation == Relation.GROUP || relation == Relation.SELF) continue;
            if (relation == Relation.ALLY) {
                if (!allyChanceUsed) {
                    allyChanceUsed = true;
                    if (hitFirstAlly.getAsBoolean()) return hit.entity;
                }
                continue;
            }
            if (relation.friendly()) continue;
            return hit.entity;
        }
        return null;
    }

    private static boolean randomAllyRoll() {
        return ThreadLocalRandom.current().nextBoolean();
    }
}
