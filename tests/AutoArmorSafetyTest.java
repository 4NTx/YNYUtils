package com.yny.utils.modules.player;

/** Deterministic checks for delayed and reordered inventory acknowledgements. */
public final class AutoArmorSafetyTest {
    private static int checks;

    public static void main(String[] args) {
        check(ArmorSwapSafety.mayReturnCursor(true, false, false, 0), "window 0 livre");
        check(!ArmorSwapSafety.mayReturnCursor(true, true, false, 0), "inventário manual aberto");
        check(!ArmorSwapSafety.mayReturnCursor(true, false, true, 0), "container aberto");
        check(!ArmorSwapSafety.mayReturnCursor(true, false, false, 1), "outra janela");
        check(!ArmorSwapSafety.mayReturnCursor(false, false, false, 0), "jogador morto");

        check(ArmorSwapSafety.closerDamage(8, 0, 324), "peça nova com dano de combate");
        check(ArmorSwapSafety.closerDamage(328, 324, 0), "peça antiga no cursor");
        check(!ArmorSwapSafety.closerDamage(170, 0, 324), "dano próximo da peça antiga");
        check(!ArmorSwapSafety.closerDamage(162, 0, 324), "empate é ambíguo");

        check(drop(true, false, true, true, true, true, 0),
                "descarta peça antiga confirmada após tentativas esgotadas");
        check(drop(true, false, true, true, true, false, 8),
                "descarta peça antiga confirmada após inventário cheio");
        check(!drop(false, false, true, true, true, true, 8), "opção desligada preserva item");
        check(!drop(true, true, true, true, true, true, 8), "intervenção manual bloqueia descarte");
        check(!drop(true, false, false, true, true, true, 8), "item alheio nunca é descartado");
        check(!drop(true, false, true, false, true, true, 8), "troca não confirmada não descarta");
        check(!drop(true, false, true, true, false, true, 8), "sem reserva equipada não descarta");
        check(!drop(true, false, true, true, true, false, 7), "aguarda espaço antes de descartar");

        check(result(false, true, true, true, true, 20) == ArmorSwapSafety.Outcome.WAIT,
                "item ainda no cursor nunca conclui");
        check(result(true, true, true, false, false, 1) == ArmorSwapSafety.Outcome.WAIT,
                "predição local aguarda servidor");
        check(result(true, true, true, true, false, 2) == ArmorSwapSafety.Outcome.WAIT,
                "callback do cursor chegou antes do slot");
        check(result(true, true, true, false, true, 2) == ArmorSwapSafety.Outcome.WAIT,
                "callback do slot chegou antes do cursor");
        check(result(true, true, true, true, true, 2) == ArmorSwapSafety.Outcome.CONFIRMED,
                "ambos callbacks confirmam devolução");
        check(result(true, true, true, false, false, 8) == ArmorSwapSafety.Outcome.LOCAL_STABLE,
                "sem callbacks, estado local estável após timeout");
        check(result(true, true, false, true, false, 8) == ArmorSwapSafety.Outcome.DESTINATION_UNKNOWN,
                "destino vazio não equivale a devolução");
        check(result(true, false, true, true, true, 1) == ArmorSwapSafety.Outcome.ARMOR_CHANGED,
                "armadura nova desapareceu durante confirmação");
        System.out.println("PASS: " + checks + " verificações Auto Armor");
    }

    private static ArmorSwapSafety.Outcome result(boolean cursorEmpty, boolean armorExpected,
            boolean oldAtDestination, boolean cursorCallback, boolean slotCallback, int waitedTicks) {
        return ArmorSwapSafety.returned(cursorEmpty, armorExpected, oldAtDestination,
                cursorCallback, slotCallback, waitedTicks, 8);
    }

    private static boolean drop(boolean enabled, boolean manual, boolean owned,
            boolean swapConfirmed, boolean equipped, boolean exhausted, int noSlotTicks) {
        return ArmorSwapSafety.mayDropStuckOld(enabled, manual, owned, swapConfirmed,
                equipped, exhausted, noSlotTicks, 8);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
