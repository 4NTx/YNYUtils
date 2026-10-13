package com.yny.utils.modules.pvp;

import dev.xavier.stein.loader.api.Relation;
import dev.xavier.stein.loader.api.Relations;
import dev.xavier.stein.loader.api.Targeting;
import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Targeting.Hit;
import dev.xavier.stein.loader.api.Targeting.Query;
import dev.xavier.stein.loader.api.Targeting.Ray;
import com.yny.utils.core.DebugLog;
import com.yny.utils.modules.clanping.PingClanName;
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
        Ray ray = null;
        try {
            Query query = Targeting.query().reach(Math.max(4.5D, Targeting.attackReach() + 1.2D)).living().margin(0.35D).cutAtBlock();
            ray = Targeting.raycast(query);
        } catch (Throwable ignored) {
        }
        if (ray == null || ray.hits == null || ray.hits.isEmpty()) {
            ray = Targeting.raycast();
        }
        return firstHostileBeforeBlock(ray, Relations::of);
    }

    /** A successful roll preserves the original vanilla attack on the clicked ally. */
    public boolean allowClickedAlly(Object entity) {
        if (entity == null || !enabled.getAsBoolean()) return false;
        if (isSameClanMember(entity)) return false;
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
        if (isSameClanMember(entity)) return true;
        Relation relation = Relations.of(entity);
        if (relation == Relation.GROUP) return true;
        return relation != null && shouldCancel(enabled.getAsBoolean(), relation.friendly());
    }

    static boolean shouldCancel(boolean active, boolean friendly) {
        return active && friendly;
    }

    static boolean isSameClanMember(Object entity) {
        if (entity == null) return false;
        try {
            Relation rel = Relations.of(entity);
            if (rel == Relation.GROUP || rel == Relation.SELF) return true;
        } catch (Throwable ignored) {}
        try {
            String name = Entities.name(entity);
            return PingClanName.isSameClan(entity, name);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static Object firstHostileBeforeBlock(Ray ray, Function<Object, Relation> relationOf) {
        if (ray == null || relationOf == null) return null;
        for (Hit hit : ray.hits) {
            if (hit.distance >= ray.blockDistance) break;
            if (hit.entity == null) continue;
            Relation relation = relationOf.apply(hit.entity);
            if (relation != null && relation.friendly()) continue;
            if (isSameClanMember(hit.entity)) continue;
            return hit.entity;
        }
        return null;
    }
}
