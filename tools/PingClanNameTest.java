package com.yny.utils.modules.clanping;

public final class PingClanNameTest {
    public static void main(String[] args) {
        expect("§7[ABC] §fNick", PingClanName.format("Nick", "Nick", "ABC"));
        expect("§b[ABC] Nick", PingClanName.format("Nick", "§b[ABC] Nick", "ABC"));
        expect("Nick", PingClanName.format("Nick", null, null));
        expect("§aOther", PingClanName.format("Nick", "§aOther", ""));
        expect("§7[TREM] §fEnemy", PingClanName.format("Enemy", "Enemy", "[TREM]"));
        expect("§c[TREM] Enemy", PingClanName.format("Enemy", "§c[TREM] Enemy", "TREM"));
        expect("§6[VIP] §c[TREM] Enemy", PingClanName.format("Enemy", "§6[VIP] §c[TREM] Enemy", "TREM"));
        expect("[Lendario] [TREM] Enemy", PingClanName.format("Enemy", "[Lendario] [TREM] Enemy", "[TREM]"));
        expect("[TREM] [VIP] Enemy", PingClanName.format("Enemy", "[TREM] [VIP] Enemy", "TREM"));
        expect("Enemy [TREM]", PingClanName.format("Enemy", "Enemy [TREM]", "TREM"));
        expect("§7[TREM] §f§eEnemy", PingClanName.format("Enemy", "§eEnemy", "TREM"));
        expect("Alvo", PingClanName.format(null, null, null));
        expect("Alvo", PingClanName.format("", "", ""));
        expect("Alvo", PingClanName.display(null));
        expect("Alvo", PingClanName.display(null, null));
        expect("TREM", PingClanName.extractBracketTag("[VIP] [TREM] Nick"));
        expect("TREM", PingClanName.extractBracketTag("[VIP+] [TREM] Nick"));
        expect("TREM", PingClanName.extractBracketTag("[MVP++] [TREM] Nick"));
        expect("TREM", PingClanName.extractBracketTag("[TREM] Nick"));
        if (PingClanName.extractBracketTag("[VIP] Nick") != null) throw new AssertionError("expected null tag for [VIP] Nick");
        if (PingClanName.extractBracketTag("[VIP+] Nick") != null) throw new AssertionError("expected null tag for [VIP+] Nick");
        expect("TREM", PingClanName.extractBracketTag("(TREM) Nick"));
        expect("TREM", PingClanName.extractBracketTag("[VIP+] (TREM) Nick"));
        expect("(TREM) Nick", PingClanName.format("Nick", "(TREM) Nick", "TREM"));
        expect("§6[VIP+] §c[TREM] Enemy", PingClanName.format("Enemy", "§6[VIP+] §c[TREM] Enemy", "TREM"));
        if (PingClanName.extractBracketTag("Nick") != null) throw new AssertionError("expected null tag for plain nick");
        if (PingClanName.extractBracketTag("[x1]  nick [x1]") != null) throw new AssertionError("expected null tag for [x1] queue nick");
        if (PingClanName.extractBracketTag("[X1] nick [X1]") != null) throw new AssertionError("expected null tag for [X1] queue nick");
        if (PingClanName.extractBracketTag("[x1] nick") != null) throw new AssertionError("expected null tag for [x1] prefix");
        if (PingClanName.extractBracketTag("nick [x1]") != null) throw new AssertionError("expected null tag for [x1] suffix");
        if (PingClanName.extractBracketTag("(x1) nick (x1)") != null) throw new AssertionError("expected null tag for (x1)");
        if (PingClanName.extractBracketTag("[1v1] nick [1v1]") != null) throw new AssertionError("expected null tag for [1v1]");
        if (PingClanName.extractBracketTag("[fila] nick [fila]") != null) throw new AssertionError("expected null tag for [fila]");
        expect("ABC", PingClanName.extractBracketTag("[x1] [ABC] nick [x1]"));
        expect("ABC", PingClanName.extractBracketTag("[VIP] [x1] [ABC] nick [x1]"));
        expect("TREM", PingClanName.extractBracketTag("[x1] [TREM] nick [x1]"));
        expect("ABC", PingClanName.extractBracketTag("[ABC] nick [x1]"));
        expect("ABC", PingClanName.extractBracketTag("[x1] nick [ABC]"));

        if (!PingClanName.isIgnoredClanTag("x1")) throw new AssertionError("expected x1 to be ignored");
        if (!PingClanName.isIgnoredClanTag("[x1]")) throw new AssertionError("expected [x1] to be ignored");
        if (!PingClanName.isIgnoredClanTag("X1")) throw new AssertionError("expected X1 to be ignored");
        if (!PingClanName.isIgnoredClanTag("1v1")) throw new AssertionError("expected 1v1 to be ignored");
        if (!PingClanName.isIgnoredClanTag("1x1")) throw new AssertionError("expected 1x1 to be ignored");
        if (!PingClanName.isIgnoredClanTag("fila")) throw new AssertionError("expected fila to be ignored");
        if (!PingClanName.isIgnoredClanTag("x1 fila")) throw new AssertionError("expected x1 fila to be ignored");
        if (!PingClanName.isIgnoredClanTag("VIP")) throw new AssertionError("expected VIP to be ignored");
        if (!PingClanName.isIgnoredClanTag("Lutador2x")) throw new AssertionError("expected Lutador2x to be ignored");
        if (!PingClanName.isIgnoredClanTag("Lutador 2x")) throw new AssertionError("expected Lutador 2x to be ignored");
        if (!PingClanName.isIgnoredClanTag("Lutador")) throw new AssertionError("expected Lutador to be ignored");
        if (!PingClanName.isIgnoredClanTag("Guerreiro3x")) throw new AssertionError("expected Guerreiro3x to be ignored");
        if (!PingClanName.isIgnoredClanTag("Mito2x")) throw new AssertionError("expected Mito2x to be ignored");
        if (!PingClanName.isIgnoredClanTag("Gladiador2x")) throw new AssertionError("expected Gladiador2x to be ignored");
        if (PingClanName.isIgnoredClanTag("ABC")) throw new AssertionError("expected ABC to be valid clan");
        if (PingClanName.isIgnoredClanTag("TREM")) throw new AssertionError("expected TREM to be valid clan");
        if (PingClanName.isIgnoredClanTag("YNY")) throw new AssertionError("expected YNY to be valid clan");

        if (PingClanName.extractBracketTag("[Lutador2x] Nick") != null) throw new AssertionError("expected null tag for [Lutador2x] Nick");
        if (PingClanName.extractBracketTag("[Lutador 2x] Nick") != null) throw new AssertionError("expected null tag for [Lutador 2x] Nick");
        expect("ABC", PingClanName.extractBracketTag("[Lutador2x] [ABC] Nick"));
        expect("ABC", PingClanName.extractBracketTag("[ABC] [Lutador2x] Nick"));
        expect("TREM", PingClanName.extractBracketTag("[VIP] [Lutador2x] [TREM] Nick"));

        expect("[x1]  nick [x1]", PingClanName.format("nick", "[x1]  nick [x1]", "x1"));
        expect("[x1]  nick [x1]", PingClanName.format("nick", "[x1]  nick [x1]", null));
        expect("[x1] [ABC] nick [x1]", PingClanName.format("nick", "[x1] [ABC] nick [x1]", "ABC"));

        // Malformed and edge case brackets
        if (PingClanName.extractBracketTag("") != null) throw new AssertionError("empty string must return null");
        if (PingClanName.extractBracketTag("   ") != null) throw new AssertionError("whitespace must return null");
        if (PingClanName.extractBracketTag("[") != null) throw new AssertionError("unclosed bracket must return null");
        if (PingClanName.extractBracketTag("]") != null) throw new AssertionError("unopened bracket must return null");
        if (PingClanName.extractBracketTag("[]") != null) throw new AssertionError("empty brackets must return null");
        if (PingClanName.extractBracketTag("[  ]") != null) throw new AssertionError("whitespace brackets must return null");
        if (PingClanName.extractBracketTag("()") != null) throw new AssertionError("empty parens must return null");
        if (PingClanName.extractBracketTag("(  )") != null) throw new AssertionError("whitespace parens must return null");
        if (PingClanName.extractBracketTag("(x1)") != null) throw new AssertionError("(x1) must return null");
        if (PingClanName.extractBracketTag("[1x1]") != null) throw new AssertionError("[1x1] must return null");
        if (PingClanName.extractBracketTag("[2v2]") != null) throw new AssertionError("[2v2] must return null");
        if (PingClanName.extractBracketTag("[pvp]") != null) throw new AssertionError("[pvp] must return null");
        if (PingClanName.extractBracketTag("[glad]") != null) throw new AssertionError("[glad] must return null");
        if (PingClanName.extractBracketTag("[mito]") != null) throw new AssertionError("[mito] must return null");
        if (PingClanName.extractBracketTag("[magnata]") != null) throw new AssertionError("[magnata] must return null");
        expect("ABC", PingClanName.extractBracketTag("(x1) (ABC)"));
        expect("TREM", PingClanName.extractBracketTag("[VIP] (x1) (TREM) [x1]"));
        if (PingClanName.extractBracketTag("[VIP] [x1]") != null) throw new AssertionError("expected null tag for [VIP] [x1]");

        // Clans containing X1 or rank prefixes as substrings
        if (PingClanName.isIgnoredClanTag("MAX1")) throw new AssertionError("MAX1 should be valid clan");
        if (PingClanName.isIgnoredClanTag("PROX")) throw new AssertionError("PROX should be valid clan");
        if (PingClanName.isIgnoredClanTag("FX1")) throw new AssertionError("FX1 should be valid clan");

        // isDuelTarget & hasDuelOrQueueTag
        if (!PingClanName.hasDuelOrQueueTag("[x1]  nick [x1]")) throw new AssertionError("expected [x1]  nick [x1] to have duel tag");
        if (!PingClanName.hasDuelOrQueueTag("[X1] nick [X1]")) throw new AssertionError("expected [X1] nick [X1] to have duel tag");
        if (!PingClanName.hasDuelOrQueueTag("(x1) nick (x1)")) throw new AssertionError("expected (x1) nick (x1) to have duel tag");
        if (!PingClanName.hasDuelOrQueueTag("[1v1] nick")) throw new AssertionError("expected [1v1] to have duel tag");
        if (!PingClanName.hasDuelOrQueueTag("[fila] nick")) throw new AssertionError("expected [fila] to have duel tag");
        if (!PingClanName.hasDuelOrQueueTag("[x1 fila] nick")) throw new AssertionError("expected [x1 fila] to have duel tag");
        if (!PingClanName.hasDuelOrQueueTag("[duelo] nick")) throw new AssertionError("expected [duelo] to have duel tag");
        if (!PingClanName.hasDuelOrQueueTag("[x1] [ABC] nick [x1]")) throw new AssertionError("expected [x1] [ABC] nick [x1] to have duel tag");
        if (PingClanName.hasDuelOrQueueTag("[VIP] [ABC] nick")) throw new AssertionError("VIP ABC should not have duel tag");
        if (PingClanName.hasDuelOrQueueTag("[ABC] nick")) throw new AssertionError("plain clan should not have duel tag");
        if (PingClanName.hasDuelOrQueueTag("nick")) throw new AssertionError("plain nick should not have duel tag");

        if (!PingClanName.isDuelTarget(null, "[x1]  nick [x1]")) throw new AssertionError("isDuelTarget failed for [x1]  nick [x1]");
        if (!PingClanName.isDuelTarget(null, "[X1] nick [X1]")) throw new AssertionError("isDuelTarget failed for [X1] nick [X1]");
        if (PingClanName.isDuelTarget(null, "[VIP] [ABC] nick")) throw new AssertionError("isDuelTarget should be false for [VIP] [ABC]");
        if (PingClanName.isDuelTarget(null, "nick")) throw new AssertionError("isDuelTarget should be false for plain nick");

        System.out.println("PASS: clan in enemy ping, no duplicate tag, fallback, rank extraction, [x1] queue filtering, isDuelTarget checks");
    }

    private static void expect(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError("expected " + expected + ", got " + actual);
    }
}
