package com.yny.utils.modules.player;

import dev.xavier.stein.loader.api.Inventory;

/**
 * Utilitário de apoio para equipar e ordenar peças de armadura no inventário.
 */
public final class AutoArmorHelper {
    private AutoArmorHelper() {}

    /**
     * Equipa a armadura. Se a peça estiver na hotbar (0-8) e o slot do corpo estiver vazio,
     * utiliza Inventory.move diretamente no slot do container para contornar a limitação
     * do vanilla de não equipar itens da hotbar via quickMove (shift-click).
     */
    public static boolean performEquip(int piece, int inventoryIndex, Object worn) {
        if (inventoryIndex < 9 && worn == null) {
            int fromSlot = Inventory.slotOf(inventoryIndex);
            int toSlot = Inventory.slotOf(36 + piece);
            if (fromSlot >= 0 && toSlot >= 0) {
                return Inventory.move(fromSlot, toSlot);
            }
        }
        return Inventory.equip(inventoryIndex);
    }

    /**
     * Prioriza slots do inventário principal (9-35) sobre a hotbar ativa (0-8).
     */
    public static int compareSlots(int idx0, int idx1) {
        boolean main0 = idx0 >= 9;
        boolean main1 = idx1 >= 9;
        if (main0 != main1) {
            return main0 ? 1 : -1;
        }
        return Integer.compare(idx1, idx0);
    }
}
