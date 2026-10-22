package com.yny.utils.modules.clanping;

import java.lang.reflect.Constructor;

public final class PingWorldTest {
    private static int checks = 0;

    public static void main(String[] args) throws Exception {
        testIdExtraction();
        testDimensionNames();
        testSanitization();
        testWorldMetaAndHudDetail();
        System.out.println("PASS: " + checks + " verificações PingWorld");
    }

    private static ClanPing.Active createMockActive(String id, boolean enemy) throws Exception {
        Constructor<?>[] ctors = ClanPing.Active.class.getDeclaredConstructors();
        Constructor<?> ctor = ctors[0];
        ctor.setAccessible(true);
        // (String id, String author, String uuid, String targetName, double x, double y, double z, boolean enemy, int seconds, String server)
        return (ClanPing.Active) ctor.newInstance(id, "Leader", "target-uuid", "EnemyNick", 10.0, 70.0, 20.0, enemy, 100, "mush.com.br");
    }

    private static void testIdExtraction() throws Exception {
        String netherId = PingWorld.buildPingId(-1, "Nether");
        check(netherId.contains("__dim-1"), "netherId contém __dim-1");
        check(netherId.contains("__wNether"), "netherId contém __wNether");

        ClanPing.Active mockActive = createMockActive(netherId, false);
        check(PingWorld.getPingDimension(mockActive) == -1, "dimensão extraída é -1");
        check("Nether".equals(PingWorld.getPingWorldName(mockActive)), "nome do mundo extraído é Nether");

        String endId = PingWorld.buildPingId(1, "The End");
        ClanPing.Active mockEnd = createMockActive(endId, true);
        check(PingWorld.getPingDimension(mockEnd) == 1, "dimensão extraída é 1");
        check("TheEnd".equals(PingWorld.getPingWorldName(mockEnd)), "nome do mundo sanitizado é TheEnd");

        ClanPing.Active mockLegacy = createMockActive("legacy-uuid-plain", false);
        check(PingWorld.getPingDimension(mockLegacy) == 0, "legacy dimensão default é 0");
        check("Mundo Normal".equals(PingWorld.getPingWorldName(mockLegacy)), "legacy mundo default é Mundo Normal");

        check(!mockActive.expired(), "Active.expired() é permanentemente false");
        check(mockActive.fade() == 1.0F, "Active.fade() é permanentemente 1.0F");
    }

    private static void testDimensionNames() {
        check("Nether".equals(PingWorld.getDimensionName(-1)), "dim -1 é Nether");
        check("The End".equals(PingWorld.getDimensionName(1)), "dim 1 é The End");
        check("Mundo Normal".equals(PingWorld.getDimensionName(0)), "dim 0 é Mundo Normal");
    }

    private static void testSanitization() {
        check("MundoNormal".equals(PingWorld.sanitizeWorld("")), "vazio sanitize para MundoNormal");
        check("MundoNormal".equals(PingWorld.sanitizeWorld(null)), "null sanitize para MundoNormal");
        check("Gladiador1".equals(PingWorld.sanitizeWorld("§6[Gladiador-1]")), "limpa caracteres de cor e especiais");
        check("Arena".equals(PingWorld.cleanColors("§a§lArena")), "cleanColors remove § códigos");
    }

    private static void testWorldMetaAndHudDetail() throws Exception {
        String sameDimId = PingWorld.buildPingId(0, "Mundo Normal");
        ClanPing.Active sameActive = createMockActive(sameDimId, false);
        String meta = PingWorld.worldMeta(sameActive, 42);
        check(meta.contains("FIXO"), "worldMeta contém FIXO");
        check(meta.contains("42m"), "worldMeta contém distância");

        String detail = PingWorld.hudDetail(sameActive, "↑", 42);
        check(detail.contains("FIXO"), "hudDetail contém FIXO");
        check(detail.contains("42m"), "hudDetail contém distância");

        String diffDimId = PingWorld.buildPingId(-1, "Nether");
        ClanPing.Active diffActive = createMockActive(diffDimId, false);
        String diffMeta = PingWorld.worldMeta(diffActive, 100);
        check(diffMeta.contains("FIXO"), "diffMeta contém FIXO");
        check(diffMeta.contains("Nether"), "diffMeta avisa mundo Nether");

        String diffDetail = PingWorld.hudDetail(diffActive, "↑", 100);
        check(diffDetail.contains("OUTRO MUNDO"), "diffDetail avisa OUTRO MUNDO");
        check(diffDetail.contains("Nether"), "diffDetail avisa Nether");
    }

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) {
            throw new AssertionError("FALHA: " + label);
        }
    }
}
