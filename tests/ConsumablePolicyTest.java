package com.yny.utils.modules.player;

public final class ConsumablePolicyTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        check(!due(0, true, 1, 0, 0), "OFF permanece inativo");
        check(!due(1, false, 0, 0, 0), "econômico não usa sem dano novo");
        check(due(1, true, 1, 0, 0), "econômico usa após dano e efeito expirado");
        check(!due(1, false, 1, 0, 1), "econômico não renova efeito ativo");
        check(!due(1, false, 1, 1, 0), "econômico não repete sem novo dano");
        check(due(1, true, 2, 1, 0), "novo dano arma uso após expiração");
        check(!due(1, false, 2, 1, 0), "hit antigo não provoca uso fora da janela PvP");
        check(!due(1, true, 1, 1, 0), "combate não substitui dano novo no econômico");
        check(!due(2, false, 0, 0, 0), "combate não usa fora da janela PvP");
        check(due(2, true, 0, 0, 0), "combate usa efeito ausente");
        check(due(2, true, 0, 0, 60), "combate renova no limite configurado");
        check(!due(2, true, 0, 0, 61), "combate não renova antes do limite");
        check(!ConsumablePolicy.isDamageWithoutHurt(20F, 20F, 20F, 0F),
                "expiração da absorção não é dano de vida");
        check(ConsumablePolicy.isDamageWithoutHurt(20F, 18F, 20F, 20F), "queda de vida é dano");
        AutoConsumables consumables = new AutoConsumables(() -> true, () -> 1, () -> 0,
                () -> 1, () -> true, () -> true, () -> 3, () -> 8, () -> 3,
                () -> false, ignored -> { });
        consumables.onHealthChanged(20F, 20F, 20F, 0F);
        check(damageEpoch(consumables) == 0L, "expiração da absorção não arma consumível");
        consumables.onHurt();
        consumables.onHealthChanged(20F, 20F, 4F, 2F);
        check(damageEpoch(consumables) == 1L, "hit na absorção arma apenas uma vez");
        consumables.onHealthChanged(20F, 18F, 2F, 2F);
        check(damageEpoch(consumables) == 1L, "hurt e vida no mesmo tick não duplicam dano");
        System.out.println("PASS: " + checks + " verificações Auto Consumíveis");
    }

    private static long damageEpoch(AutoConsumables consumables) throws Exception {
        java.lang.reflect.Field field = AutoConsumables.class.getDeclaredField("damageEpoch");
        field.setAccessible(true);
        return field.getLong(consumables);
    }

    private static boolean due(int mode, boolean combat, long damage, long consumed, int remaining) {
        return ConsumablePolicy.shouldUse(mode, combat, damage, consumed, remaining, 60);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
