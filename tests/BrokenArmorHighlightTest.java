package com.yny.utils.modules.pvp;

import com.yny.utils.config.YNYConfig;
import com.yny.utils.modules.pvp.BrokenArmorHighlight.ArmorStatus;

public final class BrokenArmorHighlightTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Running Broken Armor Highlight Tests ===");

        testConfigDefaultsAndSanitization();
        testArmorStatusEvaluation();
        testArmorMethodReflection();
        testClanFilter();

        System.out.println("PASS: All Broken Armor Highlight tests passed successfully!");
    }

    private static void testClanFilter() {
        check(!BrokenArmorHighlight.isSameClanOrAlly(null, "EnemyPlayer"), "Null player must return false");
        System.out.println("  PASS: Clan filter safeguards verified.");
    }

    private static void testConfigDefaultsAndSanitization() {
        YNYConfig config = new YNYConfig();
        check(config.brokenArmorHighlight, "brokenArmorHighlight default must be true");
        check(config.brokenArmorThroughWalls, "brokenArmorThroughWalls default must be true");
        check(config.brokenArmorTags, "brokenArmorTags default must be true");
        check(config.brokenArmorOnlyEnemies, "brokenArmorOnlyEnemies default must be true");
        check(config.brokenArmorAlertChat, "brokenArmorAlertChat default must be true");
        check(config.brokenArmorMaxDistance == 48, "brokenArmorMaxDistance default must be 48");

        config.brokenArmorMaxDistance = 500;
        config.sanitize();
        check(config.brokenArmorMaxDistance == 96, "Sanitize must clamp max distance to 96");

        config.brokenArmorMaxDistance = -10;
        config.sanitize();
        check(config.brokenArmorMaxDistance == 16, "Sanitize must clamp min distance to 16");

        System.out.println("  PASS: Config defaults and sanitization verified.");
    }

    private static void testArmorStatusEvaluation() {
        Object item = new Object();

        // 1. Full armor (boots, legs, chest, helm all present)
        ArmorStatus full = BrokenArmorHighlight.evaluateArmor(item, item, item, item);
        check(!full.hasMissingPiece, "Full armor must not be flagged as missing");
        check(full.missingCount == 0, "Missing count must be 0 for full armor");

        // 2. Missing Helmet only (rush de PvP - cenário principal do usuário)
        ArmorStatus noHelm = BrokenArmorHighlight.evaluateArmor(item, item, item, null);
        check(noHelm.hasMissingPiece, "Missing helmet must be flagged");
        check(noHelm.missingCount == 1, "Missing count must be 1");
        check(noHelm.tag.contains("SEM ELMO"), "Tag must contain 'SEM ELMO': " + noHelm.tag);
        check((noHelm.outlineColor & 0x00FFFFFF) == 0x00FFD700, "Outline color must be gold for missing helmet");

        // 3. Missing Boots only (rush de PvP - bota quebrada)
        ArmorStatus noBoots = BrokenArmorHighlight.evaluateArmor(null, item, item, item);
        check(noBoots.hasMissingPiece, "Missing boots must be flagged");
        check(noBoots.missingCount == 1, "Missing count must be 1");
        check(noBoots.tag.contains("SEM BOTA"), "Tag must contain 'SEM BOTA': " + noBoots.tag);
        check((noBoots.outlineColor & 0x00FFFFFF) == 0x00FF7700, "Outline color must be orange for missing boots");

        // 4. Missing Helmet & Boots
        ArmorStatus noHelmNoBoots = BrokenArmorHighlight.evaluateArmor(null, item, item, null);
        check(noHelmNoBoots.hasMissingPiece, "Missing helmet & boots must be flagged");
        check(noHelmNoBoots.missingCount == 2, "Missing count must be 2");
        check(noHelmNoBoots.tag.contains("SEM ELMO & BOTA"), "Tag must indicate both helmet & boots: " + noHelmNoBoots.tag);

        // 5. Missing Chestplate only
        ArmorStatus noChest = BrokenArmorHighlight.evaluateArmor(item, item, null, item);
        check(noChest.hasMissingPiece, "Missing chest must be flagged");
        check(noChest.missingCount == 1, "Missing count must be 1");
        check(noChest.tag.contains("SEM PEITORAL"), "Tag must contain 'SEM PEITORAL': " + noChest.tag);

        // 6. Missing Leggings only
        ArmorStatus noLegs = BrokenArmorHighlight.evaluateArmor(item, null, item, item);
        check(noLegs.hasMissingPiece, "Missing legs must be flagged");
        check(noLegs.missingCount == 1, "Missing count must be 1");
        check(noLegs.tag.contains("SEM CALÇA"), "Tag must contain 'SEM CALÇA': " + noLegs.tag);

        // 7. Missing all 4 pieces (completely naked)
        ArmorStatus naked = BrokenArmorHighlight.evaluateArmor(null, null, null, null);
        check(naked.hasMissingPiece, "Naked must be flagged");
        check(naked.missingCount == 4, "Missing count must be 4");
        check(naked.tag.contains("SEM ARMADURA"), "Tag must indicate no armor: " + naked.tag);

        System.out.println("  PASS: Armor piece status evaluation and tags verified.");
    }

    private static void testArmorMethodReflection() {
        DummyPlayerWithArmor player = new DummyPlayerWithArmor("BootItem", "LegItem", "ChestItem", null);
        // slot 0: boots, slot 1: legs, slot 2: chest, slot 3: helm
        Object b = BrokenArmorHighlight.getArmorItem(player, 0);
        Object l = BrokenArmorHighlight.getArmorItem(player, 1);
        Object c = BrokenArmorHighlight.getArmorItem(player, 2);
        Object h = BrokenArmorHighlight.getArmorItem(player, 3);

        check("BootItem".equals(b), "Slot 0 must return boots");
        check("LegItem".equals(l), "Slot 1 must return legs");
        check("ChestItem".equals(c), "Slot 2 must return chest");
        check(h == null, "Slot 3 must return null (missing helmet)");

        ArmorStatus status = BrokenArmorHighlight.evaluateArmor(b, l, c, h);
        check(status.hasMissingPiece, "Player must be detected as missing armor");
        check(status.tag.contains("SEM ELMO"), "Tag must detect missing helmet");

        System.out.println("  PASS: Armor reflection methods on Notch/MCP compatibility verified.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("CHECK FAILED: " + message);
        }
    }

    public static final class DummyPlayerWithArmor {
        private final Object boots;
        private final Object legs;
        private final Object chest;
        private final Object helm;

        public DummyPlayerWithArmor(Object boots, Object legs, Object chest, Object helm) {
            this.boots = boots;
            this.legs = legs;
            this.chest = chest;
            this.helm = helm;
        }

        // Notch method: q(int slot)
        public Object q(int slot) {
            switch (slot) {
                case 0: return boots;
                case 1: return legs;
                case 2: return chest;
                case 3: return helm;
                default: return null;
            }
        }
    }
}
