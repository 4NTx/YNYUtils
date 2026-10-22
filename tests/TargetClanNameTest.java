package com.yny.targethealth;

public final class TargetClanNameTest {
    public static void main(String[] args) {
        equal("§7[YnY] §fJogador", TargetClanName.format("Jogador", "Jogador", "YnY"));
        equal("[YnY] Jogador", TargetClanName.format("Jogador", "[YnY] Jogador", "YnY"));
        equal("Jogador", TargetClanName.format("Jogador", null, null));
        equal("Alvo", TargetClanName.format(null, null, null));
        equal("[x1]  Jogador [x1]", TargetClanName.format("Jogador", "[x1]  Jogador [x1]", "x1"));
        equal("[x1] [YnY] Jogador [x1]", TargetClanName.format("Jogador", "[x1] [YnY] Jogador [x1]", "YnY"));
        System.out.println("PASS: clã no nome do Damage Indicator, filtrando [x1]");
    }

    private static void equal(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError("expected=" + expected + ", actual=" + actual);
    }
}
