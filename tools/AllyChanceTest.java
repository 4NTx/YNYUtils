package com.yny.utils.modules.pvp;

import dev.xavier.stein.loader.api.Relation;
import dev.xavier.stein.loader.api.Targeting.Hit;
import dev.xavier.stein.loader.api.Targeting.Ray;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class AllyChanceTest {
    public static void main(String[] args) {
        Object group = new Object();
        Object ally1 = new Object();
        Object ally2 = new Object();
        Object enemy = new Object();
        Object enemy2 = new Object();
        Map<Object, Relation> relations = new IdentityHashMap<Object, Relation>();
        relations.put(group, Relation.GROUP);
        relations.put(ally1, Relation.ALLY);
        relations.put(ally2, Relation.ALLY);
        relations.put(enemy, Relation.ENEMY);
        relations.put(enemy2, Relation.ENEMY);
        Ray ray = new Ray(Arrays.asList(hit(group, 1), hit(ally1, 1.4), hit(ally2, 1.8),
                hit(enemy, 2.2), hit(enemy2, 2.6)), 3.0, 3.0);

        AtomicInteger rolls = new AtomicInteger();
        check(HitThroughAllies.firstTargetBeforeBlock(ray, relations::get, () -> {
            rolls.incrementAndGet(); return true;
        }) == ally1, "50% success selects the first non-group ally");
        check(rolls.get() == 1, "group does not roll and selected ally rolls once");

        rolls.set(0);
        check(HitThroughAllies.firstTargetBeforeBlock(ray, relations::get, () -> {
            rolls.incrementAndGet(); return false;
        }) == enemy, "50% miss skips allies and reaches first enemy");
        check(rolls.get() == 1, "multiple allies still use only one roll");

        Ray groupThenEnemy = new Ray(Arrays.asList(hit(group, 1), hit(enemy, 2)), 3.0, 3.0);
        rolls.set(0);
        check(HitThroughAllies.firstTargetBeforeBlock(groupThenEnemy, relations::get, () -> {
            rolls.incrementAndGet(); return true;
        }) == enemy, "same clan is always ignored");
        check(rolls.get() == 0, "same clan does not consume the ally chance");

        Ray wall = new Ray(Arrays.asList(hit(group, 1), hit(ally1, 1.4), hit(enemy, 2.2)), 1.8, 3.0);
        check(HitThroughAllies.firstTargetBeforeBlock(wall, relations::get, () -> false) == null,
                "does not select an entity behind a block");
        Ray nullThenEnemy = new Ray(Arrays.asList(hit(null, 0.5), hit(enemy, 1.0)), 3.0, 3.0);
        check(HitThroughAllies.firstTargetBeforeBlock(nullThenEnemy, relations::get, () -> false) == enemy,
                "null ray entries do not block target selection");
        check(HitThroughAllies.firstTargetBeforeBlock(null, relations::get, () -> false) == null,
                "null ray is safe");
        check(HitThroughAllies.firstTargetBeforeBlock(ray, null, () -> false) == null,
                "null relation provider is safe");
        check(HitThroughAllies.firstTargetBeforeBlock(ray, relations::get, null) == null,
                "null random source is safe");
        check(HitThroughAllies.shouldCancel(true, true), "active mode redirects friendly click");
        check(!HitThroughAllies.shouldCancel(false, true), "inactive mode preserves vanilla click");
        System.out.println("ally chance, same-clan exclusion, one-roll rule, block limit: PASS");
    }

    private static Hit hit(Object entity, double distance) {
        return new Hit(entity, distance, 0, 0, 0);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
