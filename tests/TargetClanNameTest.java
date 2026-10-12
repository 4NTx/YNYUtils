package com.yny.targethealth;

public final class TargetClanNameTest {
    public static void main(String[] args) {
        equal("§7[YnY] §fJogador", TargetClanName.format("Jogador", "Jogador", "YnY"));
        equal("[YnY] Jogador", TargetClanName.format("Jogador", "[YnY] Jogador", "YnY"));
        equal("Jogador", TargetClanName.format("Jogador", null, null));
        equal("Alvo", TargetClanName.format(null, null, null));
        System.out.println("PASS: clã no nome do Damage Indicator");
    }

    private static void equal(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError("expected=" + expected + ", actual=" + actual);
    }
}
