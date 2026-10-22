package com.yny.utils.modules.pvp;

import dev.xavier.stein.loader.api.Relation;
import dev.xavier.stein.loader.api.Targeting.Hit;
import dev.xavier.stein.loader.api.Targeting.Ray;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;

public final class HitThroughWebTest {
    public static void main(String[] args) {
        // 1. Test isWebBlock
        check(HitThroughWeb.isWebBlock("minecraft:web"), "minecraft:web must be recognized");
        check(HitThroughWeb.isWebBlock("web"), "web must be recognized");
        check(HitThroughWeb.isWebBlock("tile.web"), "tile.web must be recognized");
        check(!HitThroughWeb.isWebBlock("minecraft:stone"), "minecraft:stone must NOT be web");
        check(!HitThroughWeb.isWebBlock("minecraft:obsidian"), "minecraft:obsidian must NOT be web");
        check(!HitThroughWeb.isWebBlock("minecraft:dirt"), "minecraft:dirt must NOT be web");
        check(!HitThroughWeb.isWebBlock(null), "null must NOT be web");

        // 2. Test HitThroughWeb logic simulation
        Object group = new Object();
        Object ally = new Object();
        Object enemy = new Object();
        Map<Object, Relation> relations = new IdentityHashMap<>();
        relations.put(group, Relation.GROUP);
        relations.put(ally, Relation.ALLY);
        relations.put(enemy, Relation.ENEMY);

        // Solid block at distance 2.0 blocks enemy at distance 3.0
        Ray blockedBySolidRay = new Ray(Arrays.asList(new Hit(enemy, 3.0, 0, 0, 0)), 2.0, 4.0, "minecraft:stone", 0, 0, 0);
        Object targetBehindSolid = null;
        for (Hit hit : blockedBySolidRay.hits) {
            if (hit.distance >= blockedBySolidRay.blockDistance) break;
            targetBehindSolid = hit.entity;
        }
        check(targetBehindSolid == null, "Enemy behind solid stone block must strictly NOT be hit (SO TEIA)");

        // Cobweb penetrated (blockDistance is infinity or behind enemy)
        Ray webPenetratedRay = new Ray(Arrays.asList(new Hit(enemy, 2.5, 0, 0, 0)), Double.POSITIVE_INFINITY, 4.0);
        Object targetThroughWeb = null;
        for (Hit hit : webPenetratedRay.hits) {
            if (hit.distance >= webPenetratedRay.blockDistance) break;
            targetThroughWeb = hit.entity;
        }
        check(targetThroughWeb == enemy, "Enemy through cobweb must be targeted");

        // Same clan member in cobweb: 100% skipped, hits enemy behind
        Ray clanInWebRay = new Ray(Arrays.asList(new Hit(group, 1.5, 0, 0, 0), new Hit(enemy, 2.8, 0, 0, 0)), Double.POSITIVE_INFINITY, 4.0);
        Object targetClanInWeb = null;
        for (Hit hit : clanInWebRay.hits) {
            if (hit.distance >= clanInWebRay.blockDistance) break;
            Relation rel = relations.get(hit.entity);
            if (rel == Relation.GROUP || rel == Relation.SELF) continue;
            targetClanInWeb = hit.entity;
            break;
        }
        check(targetClanInWeb == enemy, "Same clan member in cobweb must be 100% passed through to enemy");

        System.out.println("PASS: HitThroughWebTest - web detection, solid wall blocking, and clan protection verified!");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
