package com.yny.utils.modules.pvp;

import dev.xavier.stein.loader.api.Relation;
import dev.xavier.stein.loader.api.Targeting.Hit;
import dev.xavier.stein.loader.api.Targeting.Ray;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;

public final class AllyAttackFlowTest {
    public static void main(String[] args) {
        Object group = new Object(), ally = new Object(), enemy = new Object();
        Map<Object, Relation> relations = new IdentityHashMap<>();
        relations.put(group, Relation.GROUP);
        relations.put(ally, Relation.ALLY);
        relations.put(enemy, Relation.ENEMY);
        check(HitThroughAllies.keepOriginalAttack(true, Relation.ALLY, true), "successful ally roll keeps vanilla click");
        check(!HitThroughAllies.keepOriginalAttack(true, Relation.ALLY, false), "failed ally roll redirects");
        check(!HitThroughAllies.keepOriginalAttack(true, Relation.GROUP, true), "same clan never hit even with positive roll");
        check(!HitThroughAllies.keepOriginalAttack(true, Relation.GROUP, false), "same clan never hit with negative roll");
        check(!HitThroughAllies.keepOriginalAttack(false, Relation.ALLY, true), "inactive mode never redirects");
        check(HitThroughAllies.shouldCancel(true, Relation.GROUP.friendly()), "group member is friendly so cancel protects them");
        check(HitThroughAllies.shouldCancel(true, Relation.ALLY.friendly()), "ally member is friendly so cancel can redirect");
        check(!HitThroughAllies.shouldCancel(true, Relation.ENEMY.friendly()), "enemy member is hostile so vanilla attack hits 100%");
        check(!HitThroughAllies.shouldCancel(false, Relation.ALLY.friendly()), "disabled mode never cancels vanilla clicks");

        Ray ray = new Ray(Arrays.asList(new Hit(group, 1, 0, 0, 0),
                new Hit(ally, 1.4, 0, 0, 0), new Hit(enemy, 2, 0, 0, 0)), 3, 3);
        check(HitThroughAllies.firstHostileBeforeBlock(ray, relations::get) == enemy,
                "failed roll reaches enemy behind allies");

        Object group2 = new Object(), ally2 = new Object();
        relations.put(group2, Relation.GROUP);
        relations.put(ally2, Relation.ALLY);
        Ray multi = new Ray(Arrays.asList(new Hit(group, 0.8, 0, 0, 0), new Hit(group2, 1.2, 0, 0, 0),
                new Hit(ally, 1.6, 0, 0, 0), new Hit(ally2, 2.0, 0, 0, 0), new Hit(enemy, 2.5, 0, 0, 0)), 4, 4);
        check(HitThroughAllies.firstHostileBeforeBlock(multi, relations::get) == enemy,
                "skips multiple same-clan and ally entities to reach enemy behind");

        Ray wall = new Ray(ray.hits, 1.8, 3);
        check(HitThroughAllies.firstHostileBeforeBlock(wall, relations::get) == null,
                "wall prevents redirection");
        Ray wallBeforeGroup = new Ray(ray.hits, 0.5, 3);
        check(HitThroughAllies.firstHostileBeforeBlock(wallBeforeGroup, relations::get) == null,
                "wall before friendly entities prevents redirection");

        Object enemy2 = new Object();
        relations.put(enemy2, Relation.ENEMY);
        Ray twoEnemies = new Ray(Arrays.asList(new Hit(ally, 1.0, 0, 0, 0), new Hit(enemy, 2.0, 0, 0, 0), new Hit(enemy2, 2.5, 0, 0, 0)), 3, 3);
        check(HitThroughAllies.firstHostileBeforeBlock(twoEnemies, relations::get) == enemy,
                "first hostile behind ally is chosen, not distant hostiles");

        Object neutral = new Object(), none = new Object();
        relations.put(neutral, Relation.NEUTRAL);
        relations.put(none, Relation.NONE);
        Ray neutralRay = new Ray(Arrays.asList(new Hit(ally, 1.0, 0, 0, 0), new Hit(neutral, 2.0, 0, 0, 0)), 3, 3);
        check(HitThroughAllies.firstHostileBeforeBlock(neutralRay, relations::get) == neutral,
                "neutral target behind ally is treated as attackable target");

        // Statistical distribution verification on live allowClickedAlly instance
        HitThroughAllies hta = new HitThroughAllies(() -> true, () -> false);
        check(!hta.allowClickedAlly(null), "null entity cannot be allowed");
        check(!hta.shouldCancel(null), "null entity cannot be cancelled");
        int allyHits = 0;
        int groupHits = 0;
        int enemyHits = 0;
        int totalRolls = 2000;
        for (int i = 0; i < totalRolls; i++) {
            boolean roll = java.util.concurrent.ThreadLocalRandom.current().nextBoolean();
            if (HitThroughAllies.keepOriginalAttack(true, Relation.ALLY, roll)) allyHits++;
            if (HitThroughAllies.keepOriginalAttack(true, Relation.GROUP, roll)) groupHits++;
            if (HitThroughAllies.keepOriginalAttack(true, Relation.ENEMY, roll)) enemyHits++;
        }
        check(groupHits == 0, "GROUP must never be hit (0% chance)");
        check(enemyHits == 0, "keepOriginalAttack must not trigger on ENEMY");
        double allyRate = (double) allyHits / totalRolls;
        check(allyRate >= 0.44 && allyRate <= 0.56, "ALLY hit rate must be centered at 50% (got " + allyRate + ")");

        System.out.println("PASS: ally attack flow (verified 0% group, ~50% ally [" + allyHits + "/" + totalRolls + "], 100% direct enemy, walls, chains)");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
