package com.yny.utils.modules.clanping;

import java.lang.reflect.Method;

public final class ClanAllyValidationTest {
    public static void main(String[] args) throws Exception {
        System.out.println("=== Running ClanAllyValidationTest ===");

        // 1. Test isValidClanTag
        check(PingClanName.isValidClanTag("BhS"), "BhS is valid clan tag");
        check(PingClanName.isValidClanTag("YNY"), "YNY is valid clan tag");
        check(PingClanName.isValidClanTag("BL"), "BL (2 chars) is valid clan tag");
        check(PingClanName.isValidClanTag("TREM"), "TREM (4 chars) is valid clan tag");
        check(!PingClanName.isValidClanTag("z"), "single char 'z' is NOT valid clan tag");
        check(!PingClanName.isValidClanTag("v"), "single char 'v' is NOT valid clan tag");
        check(!PingClanName.isValidClanTag(""), "empty string is NOT valid clan tag");
        check(!PingClanName.isValidClanTag("TOOLONGTAG"), "7-char tag is NOT valid clan tag");

        // 2. Test isIgnoredClanTag
        check(PingClanName.isIgnoredClanTag("z"), "'z' is ignored (AFK/status)");
        check(PingClanName.isIgnoredClanTag("oz"), "'oz' is ignored (AFK/status)");
        check(PingClanName.isIgnoredClanTag("sono"), "'sono' is ignored");
        check(PingClanName.isIgnoredClanTag("dormindo"), "'dormindo' is ignored");
        check(PingClanName.isIgnoredClanTag("afk"), "'afk' is ignored");
        check(PingClanName.isIgnoredClanTag("vip"), "'vip' is ignored (rank)");
        check(PingClanName.isIgnoredClanTag("x1"), "'x1' is ignored (duel)");
        check(PingClanName.isIgnoredClanTag("fila"), "'fila' is ignored (queue)");
        check(!PingClanName.isIgnoredClanTag("BhS"), "'BhS' is NOT ignored");
        check(!PingClanName.isIgnoredClanTag("YNY"), "'YNY' is NOT ignored");
        check(!PingClanName.isIgnoredClanTag("AZU"), "'AZU' is NOT ignored");

        // 3. Test extractPrefixBracketTag
        // Clanless player with suffix badge [oz]:
        String tagClanless = PingClanName.extractPrefixBracketTag("BolsonaroJair [oz]", "BolsonaroJair");
        check(tagClanless == null, "BolsonaroJair [oz] should yield null clan tag, got: " + tagClanless);

        String tagTargetClanless = PingClanName.extractPrefixBracketTag("Gbzadaa [oz]", "Gbzadaa");
        check(tagTargetClanless == null, "Gbzadaa [oz] should yield null clan tag, got: " + tagTargetClanless);

        String tagTargetColor = PingClanName.extractPrefixBracketTag("§6Gbzadaa §5[§oz]§r", "Gbzadaa");
        check(tagTargetColor == null, "§6Gbzadaa §5[§oz]§r should yield null clan tag, got: " + tagTargetColor);

        // Player with clan prefix [BhS] and suffix badge [oz]:
        String tagWithClan = PingClanName.extractPrefixBracketTag("[BhS] PIXCAIUFOTOSUMIU [oz]", "PIXCAIUFOTOSUMIU");
        check("BhS".equalsIgnoreCase(tagWithClan), "Expected BhS, got: " + tagWithClan);

        String tagWithColors = PingClanName.extractPrefixBracketTag("§f[§4B§fh§4S§f] §6PIXCAIUFOTOSUMIU §a[§oz]§r", "PIXCAIUFOTOSUMIU");
        check("BhS".equalsIgnoreCase(tagWithColors), "Expected BhS with colors, got: " + tagWithColors);

        // Player with clan prefix [AZU] and suffix badge [oz]:
        String tagAzu = PingClanName.extractPrefixBracketTag("§f[§9AZU§f] §6Gbzadaa §5[§oz]§r", "Gbzadaa");
        check("AZU".equalsIgnoreCase(tagAzu), "Expected AZU, got: " + tagAzu);

        // 4. Test format method (ClanPing & DamageIndicator display)
        String formattedClanless = PingClanName.format("Gbzadaa", "Gbzadaa [oz]", null);
        check(formattedClanless.equals("Gbzadaa [oz]"), "Clanless format should not prepend [oz]: " + formattedClanless);

        String formattedWithClan = PingClanName.format("PIXCAIUFOTOSUMIU", "[BhS] PIXCAIUFOTOSUMIU [oz]", "BhS");
        check(!formattedWithClan.contains("[oz] [BhS]"), "Should not double prefix: " + formattedWithClan);

        System.out.println("ALL TESTS PASSED SUCCESSFULLY in ClanAllyValidationTest!");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("TEST FAILED: " + message);
        }
        System.out.println("  PASS: " + message);
    }
}
