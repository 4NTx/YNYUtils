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
        check(!HitThroughAllies.keepOriginalAttack(true, Relation.GROUP, true), "same clan never hit");
        check(!HitThroughAllies.keepOriginalAttack(false, Relation.ALLY, true), "inactive mode never redirects");
        Ray ray = new Ray(Arrays.asList(new Hit(group, 1, 0, 0, 0),
                new Hit(ally, 1.4, 0, 0, 0), new Hit(enemy, 2, 0, 0, 0)), 3, 3);
        check(HitThroughAllies.firstHostileBeforeBlock(ray, relations::get) == enemy,
                "failed roll reaches enemy behind allies");
        Ray wall = new Ray(ray.hits, 1.8, 3);
        check(HitThroughAllies.firstHostileBeforeBlock(wall, relations::get) == null,
                "wall prevents redirection");
        System.out.println("PASS: ally attack flow");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
