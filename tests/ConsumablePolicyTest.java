package com.yny.utils.modules.player;

public final class ConsumablePolicyTest {
    private static int checks;

    public static void main(String[] args) {
        check(!due(0, true, 1, 0, 0), "OFF permanece inativo");
        check(!due(1, false, 0, 0, 0), "econômico não usa sem dano novo");
        check(due(1, false, 1, 0, 0), "econômico usa após dano e efeito expirado");
        check(!due(1, false, 1, 0, 1), "econômico não renova efeito ativo");
        check(!due(1, false, 1, 1, 0), "econômico não repete sem novo dano");
        check(due(1, false, 2, 1, 0), "novo dano arma uso após expiração");
        check(!due(1, true, 1, 1, 0), "combate não substitui dano novo no econômico");
        check(!due(2, false, 0, 0, 0), "combate não usa fora da janela PvP");
        check(due(2, true, 0, 0, 0), "combate usa efeito ausente");
        check(due(2, true, 0, 0, 60), "combate renova no limite configurado");
        check(!due(2, true, 0, 0, 61), "combate não renova antes do limite");
        System.out.println("PASS: " + checks + " verificações Auto Consumíveis");
    }

    private static boolean due(int mode, boolean combat, long damage, long consumed, int remaining) {
        return ConsumablePolicy.shouldUse(mode, combat, damage, consumed, remaining, 60);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
