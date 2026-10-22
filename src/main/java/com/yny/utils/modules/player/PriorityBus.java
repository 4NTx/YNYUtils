package com.yny.utils.modules.player;

import dev.xavier.stein.loader.api.Inventory;

/**
 * Barramento de Prioridade (Priority Bus) para sincronização e coordenação harmoniosa
 * de ações de cursor, hotbar, consumíveis, armadura e bloqueio defensivo.
 *
 * Hierarquia de prioridade estrita:
 * 1. Cursor Failsafe: se há item preso no cursor fora de qualquer GUI, tem prioridade absoluta de devolução.
 * 2. AutoConsumables: o consumo de poção/capira retém a hotbar e a tecla USE até o término sem interrupção.
 * 3. AutoArmor: troca atômica sem corrida de pacotes; bloqueia ações concorrentes e respeita período de estabilização pós-troca.
 * 4. AutoProtect: bloqueio defensivo com espada, cedendo imediatamente para consumíveis ou trocas de armadura.
 */
public final class PriorityBus {

    private static volatile int activeConsumableSlot = -1;
    private static volatile int activeSwordSlot = -1;
    private static volatile boolean armorSwapInFlight = false;
    private static volatile long armorSwapExpiryNanos = 0L;

    private PriorityBus() {}

    /**
     * Prioridade 1: Cursor Failsafe.
     * Retorna true se houver item preso no cursor fora de qualquer tela.
     */
    public static boolean isCursorBusy() {
        try {
            return Inventory.cursor() != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Prioridade 2: AutoConsumables ativo (usando maçã dourada ou poção).
     */
    public static boolean isConsuming() {
        return activeConsumableSlot >= 0;
    }

    public static int getActiveConsumableSlot() {
        return activeConsumableSlot;
    }

    public static void setActiveConsumableSlot(int slot) {
        activeConsumableSlot = slot;
    }

    /**
     * Prioridade 3: Troca de armadura em voo (execução atômica ou período de estabilização de rede).
     */
    public static boolean isArmorSwapping() {
        return armorSwapInFlight || System.nanoTime() < armorSwapExpiryNanos;
    }

    public static void setArmorSwapping(boolean inFlight) {
        armorSwapInFlight = inFlight;
        if (inFlight) {
            // Janela de proteção de 150ms (~3 ticks) para aguardar estabilização dos pacotes do servidor
            armorSwapExpiryNanos = System.nanoTime() + 150_000_000L;
        }
    }

    /**
     * Prioridade 4: AutoProtect ativo.
     */
    public static boolean isSwordDefending() {
        return activeSwordSlot >= 0;
    }

    public static int getSwordSlot() {
        return activeSwordSlot;
    }

    public static void setSwordSlot(int slot) {
        activeSwordSlot = slot;
    }

    /**
     * Verifica se o AutoArmor pode realizar uma troca segura de armadura.
     * Não troca se o cursor estiver ocupado ou se o jogador estiver consumindo poção/capira.
     */
    public static boolean canSwapArmor() {
        return !isCursorBusy() && !isConsuming();
    }

    /**
     * Verifica se o AutoConsumables pode iniciar o uso de item.
     * Não inicia se o cursor estiver ocupado ou se uma troca de armadura estiver em voo/estabilização.
     */
    public static boolean canUseConsumable() {
        return !isCursorBusy() && !isArmorSwapping();
    }

    /**
     * Verifica se o AutoProtect pode defender.
     * Não defende se o cursor estiver ocupado, se estiver consumindo ou trocando armadura.
     */
    public static boolean canDefend() {
        return !isCursorBusy() && !isConsuming() && !isArmorSwapping();
    }

    /**
     * Reseta todo o estado de prioridade (ex: ao morrer, trocar de servidor ou desconectar).
     */
    public static void reset() {
        activeConsumableSlot = -1;
        activeSwordSlot = -1;
        armorSwapInFlight = false;
        armorSwapExpiryNanos = 0L;
    }
}
