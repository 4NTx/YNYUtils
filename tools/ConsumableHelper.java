package com.yny.utils.modules.player;

import dev.xavier.stein.loader.api.Inventory;
import dev.xavier.stein.loader.api.Keys;
import dev.xavier.stein.loader.api.Keys.GameKey;
import dev.xavier.stein.loader.api.Stacks;

/**
 * Utilitário de apoio para garantir uso contínuo de consumíveis, restauração do slot da arma
 * e sincronização estrita com o PriorityBus.
 */
public final class ConsumableHelper {
    private ConsumableHelper() {}

    /**
     * Mantém a tecla de uso (clique direito) travada via Stein Loader para evitar
     * que o polling do Minecraft cancele a animação de comer/beber quando o jogador
     * não está segurando fisicamente o botão do mouse.
     * Ao liberar a tecla, atualiza imediatamente o PriorityBus (mesmo se o subsistema de teclas falhar).
     */
    public static void holdUseKey(boolean hold) {
        if (!hold) {
            PriorityBus.setActiveConsumableSlot(-1);
        }
        try {
            Keys.game(GameKey.USE).hold(hold);
        } catch (Throwable ignored) {}
    }

    /**
     * Determina o slot para restaurar após usar o consumível. Notifica o PriorityBus
     * sobre o slot do consumível em uso para evitar que o AutoArmor selecione esse slot como pivô.
     */
    public static int resolveRestoreSlot(int selected, int consumableSlot) {
        PriorityBus.setActiveConsumableSlot(consumableSlot);
        if (selected >= 0 && selected <= 8 && selected != consumableSlot) {
            return selected;
        }
        try {
            for (int s = 0; s < 9; s++) {
                if (s != consumableSlot) {
                    Object item = Inventory.stack(s);
                    String id = item == null ? null : Stacks.id(item);
                    if (id != null && id.endsWith("_sword")) {
                        return s;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return consumableSlot == 0 ? 1 : 0;
    }
}
