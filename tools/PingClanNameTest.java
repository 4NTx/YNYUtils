package com.yny.utils.modules.clanping;

public final class PingClanNameTest {
    public static void main(String[] args) {
        expect("§7[ABC] §fNick", PingClanName.format("Nick", "Nick", "ABC"));
        expect("§b[ABC] Nick", PingClanName.format("Nick", "§b[ABC] Nick", "ABC"));
        expect("Nick", PingClanName.format("Nick", null, null));
        expect("§aOther", PingClanName.format("Nick", "§aOther", ""));
        expect("§7[TREM] §fEnemy", PingClanName.format("Enemy", "Enemy", "[TREM]"));
        expect("§c[TREM] Enemy", PingClanName.format("Enemy", "§c[TREM] Enemy", "TREM"));
        expect("Alvo", PingClanName.format(null, null, null));
        expect("Alvo", PingClanName.format("", "", ""));
        expect("Alvo", PingClanName.display(null));
        expect("Alvo", PingClanName.display(null, null));
        System.out.println("PASS: clan in enemy ping, no duplicate tag, fallback");
    }

    private static void expect(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError("expected " + expected + ", got " + actual);
    }
}
