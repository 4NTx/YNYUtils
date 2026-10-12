package com.yny.utils.modules.pvp;

import dev.xavier.stein.loader.api.Relation;
import dev.xavier.stein.loader.api.Relations;
import dev.xavier.stein.loader.api.Targeting;
import dev.xavier.stein.loader.api.Entities;
import com.yny.utils.core.DebugLog;
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
        return firstHostileBeforeBlock(ray, Relations::of);
    }

    /** A successful roll preserves the original vanilla attack on the clicked ally. */
    public boolean allowClickedAlly(Object entity) {
        if (entity == null || !enabled.getAsBoolean()) return false;
        Relation relation = Relations.of(entity);
        if (relation != Relation.ALLY) return false;
        boolean hit = ThreadLocalRandom.current().nextBoolean();
        DebugLog.write("HitThroughAllies", "event=ally_roll; target=" + Entities.name(entity)
                + "; result=" + (hit ? "hit_original" : "skip_to_enemy"));
        return keepOriginalAttack(true, relation, hit);
    }

    static boolean keepOriginalAttack(boolean active, Relation relation, boolean roll) {
        return active && relation == Relation.ALLY && roll;
    }

    public boolean shouldCancel(Object entity) {
        if (entity == null) return false;
        Relation relation = Relations.of(entity);
        return shouldCancel(enabled.getAsBoolean(), relation.friendly());
    }

    static boolean shouldCancel(boolean active, boolean friendly) {
        return active && friendly;
    }

    static Object firstHostileBeforeBlock(Ray ray, Function<Object, Relation> relationOf) {
        if (ray == null || relationOf == null) return null;
        for (Hit hit : ray.hits) {
            if (hit.distance >= ray.blockDistance) break;
            if (hit.entity == null) continue;
            Relation relation = relationOf.apply(hit.entity);
            if (relation == null || relation.friendly()) continue;
            return hit.entity;
        }
        return null;
    }

}
