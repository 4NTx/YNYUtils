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
import java.lang.reflect.Method;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.BooleanSupplier;

public final class HitThroughAllies {
    private final BooleanSupplier enabled;

    private static volatile boolean clansLookupAttempted = false;
    private static volatile Method clansMineMethod = null;
    private static volatile Method clansOfMethod = null;

    private static void initClansReflection() {
        if (clansLookupAttempted) return;
        clansLookupAttempted = true;
        try {
            Class<?> clansClass = Class.forName("com.yny.utils.team.Clans");
            clansMineMethod = clansClass.getMethod("mine");
            clansOfMethod = clansClass.getMethod("of", String.class);
        } catch (Throwable ignored) {}
    }

    public HitThroughAllies(BooleanSupplier enabled, BooleanSupplier diagnostics) {
        this.enabled = enabled;
    }

    private static final ThreadLocal<Boolean> IN_REDIRECTION = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }
    };

    public Object targetBehindAllies(Object clickedAlly) {
        if (!enabled.getAsBoolean() || isDuelTarget(clickedAlly)) return null;
        if (Boolean.TRUE.equals(IN_REDIRECTION.get())) return null;
        IN_REDIRECTION.set(Boolean.TRUE);
        try {
            Ray ray = null;
            try {
                Query query = Targeting.query().reach(Math.min(3.8D, Math.max(3.0D, Targeting.attackReach()))).living().margin(0.35D).passThrough(HitThroughWeb::isWebBlock).cutAtBlock();
                ray = Targeting.raycast(query);
            } catch (Throwable ignored) {
            }
            if (ray == null || ray.hits == null || ray.hits.isEmpty()) {
                ray = Targeting.raycast();
            }
            return firstHostileBeforeBlock(ray, Relations::of);
        } finally {
            IN_REDIRECTION.set(Boolean.FALSE);
        }
    }

    /** Mesmo clã nunca sofre ataque (0%), aliado tem 50% de chance de sofrer ataque. */
    public boolean allowClickedAlly(Object entity) {
        if (entity == null || !enabled.getAsBoolean()) return false;
        if (isDuelTarget(entity)) return true;
        if (isSameClanMember(entity)) return false;
        try {
            Relation relation = Relations.of(entity);
            if (relation == Relation.GROUP) {
                return false;
            }
            if (relation == Relation.ALLY) {
                return keepOriginalAttack(true, relation, ThreadLocalRandom.current().nextBoolean());
            }
            if (isFriendly(entity, Relations::of)) {
                return false;
            }
            return keepOriginalAttack(true, relation, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean keepOriginalAttack(boolean active, Relation relation, boolean roll) {
        return active && relation == Relation.ALLY && roll;
    }

    public boolean shouldCancel(Object entity) {
        if (entity == null) return false;
        if (isDuelTarget(entity)) return false;
        if (isSameClanMember(entity)) return true;
        try {
            Relation relation = Relations.of(entity);
            if (relation == Relation.GROUP || relation == Relation.ALLY) return true;
            if (isFriendly(entity, Relations::of)) return true;
            return relation != null && shouldCancel(enabled.getAsBoolean(), relation.friendly());
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean shouldCancel(boolean active, boolean friendly) {
        return active && friendly;
    }

    static boolean isDuelTarget(Object entity) {
        if (entity == null) return false;
        try {
            return PingClanName.isDuelTarget(entity);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean isSameClanMember(Object entity) {
        if (entity == null) return false;
        if (isDuelTarget(entity)) return false;
        try {
            String name = Entities.name(entity);
            return PingClanName.isSameClan(entity, name);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean isFriendly(Object entity, Function<Object, Relation> relationOf) {
        if (entity == null) return false;
        try {
            if (entity == Entities.self()) return true;
        } catch (Throwable ignored) {}
        if (isDuelTarget(entity)) return false;
        if (relationOf != null) {
            try {
                Relation relation = relationOf.apply(entity);
                if (relation != null && (relation.friendly() || relation == Relation.GROUP || relation == Relation.ALLY)) {
                    return true;
                }
            } catch (Throwable ignored) {}
        }
        if (isSameClanMember(entity)) return true;
        try {
            String name = Entities.name(entity);
            if (name != null && !name.isEmpty()) {
                Relation relName = Relations.of(name);
                if (relName != null && (relName.friendly() || relName == Relation.GROUP || relName == Relation.ALLY)) {
                    return true;
                }
                String selfNick = dev.xavier.stein.loader.api.Players.self();
                if (selfNick != null) {
                    String selfTeam = dev.xavier.stein.loader.api.Players.team(selfNick);
                    String targetTeam = dev.xavier.stein.loader.api.Players.team(name);
                    if (selfTeam != null && !selfTeam.isEmpty() && targetTeam != null && !targetTeam.isEmpty() && selfTeam.equalsIgnoreCase(targetTeam)) {
                        return true;
                    }
                }
                initClansReflection();
                if (clansMineMethod != null && clansOfMethod != null) {
                    try {
                        String mine = (String) clansMineMethod.invoke(null);
                        String tag = (String) clansOfMethod.invoke(null, name);
                        if (mine != null && tag != null && mine.equalsIgnoreCase(tag)) {
                            return true;
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    static Object firstHostileBeforeBlock(Ray ray, Function<Object, Relation> relationOf) {
        if (ray == null || ray.hits == null || ray.hits.isEmpty() || relationOf == null) return null;
        java.util.List<Hit> hits = new java.util.ArrayList<>(ray.hits);
        hits.sort(new java.util.Comparator<Hit>() {
            @Override
            public int compare(Hit a, Hit b) {
                return Double.compare(a.distance, b.distance);
            }
        });
        double maxReach = 3.8D;
        try {
            maxReach = Math.min(3.8D, Math.max(3.0D, dev.xavier.stein.loader.api.Targeting.attackReach()));
        } catch (Throwable ignored) {}

        Object self = null;
        try {
            self = Entities.self();
        } catch (Throwable ignored) {}

        for (Hit hit : hits) {
            if (hit == null) continue;
            if (hit.distance >= ray.blockDistance) break;
            if (hit.distance > maxReach) break;
            if (hit.entity == null || (self != null && hit.entity == self)) continue;
            try {
                if (!Entities.isPlayer(hit.entity) && !Entities.isLiving(hit.entity)) continue;
            } catch (Throwable ignored) {}
            if (isDuelTarget(hit.entity)) return hit.entity;
            if (isSameClanMember(hit.entity)) continue;
            if (isFriendly(hit.entity, relationOf)) continue;
            return hit.entity;
        }
        return null;
    }
}
