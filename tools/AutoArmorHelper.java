package com.yny.utils.modules.player;

import java.lang.reflect.Method;
import com.yny.utils.YNYUtils;
import com.yny.utils.config.YNYConfig;
import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Inventory;
import dev.xavier.stein.loader.api.Screens;
import dev.xavier.stein.loader.api.Stacks;

/**
 * Utilitário de apoio para equipar armaduras, coordenação anti-desync,
 * recuperação segura de cursor órfão e alerta sonoro preventivo de quebra de armadura.
 */
public final class AutoArmorHelper {

    private static final long[] lastAlertTicks = new long[4];
    private static long lastGlobalAlertTick = 0L;
    private static Method playSoundMethod;
    private static boolean playSoundResolved;

    private AutoArmorHelper() {}

    /**
     * Equipa armadura sem nunca colocar peças antigas no cursor do mouse e com tratamento anti-desync:
     * 1. Hotbar (0..8): usa Inventory.swapHotbar(armorSlot, inventoryIndex) - pacote único, modo 2,
     *    atômico, zero cursor, funciona tanto para equipar em slot vazio quanto para troca de set!
     * 2. Inventário principal (9..35):
     *    - Se o slot estiver vazio (worn == null): Inventory.quickMove(fromSlot) (shift-clique, modo 1).
     *    - Se for troca de set (worn != null): realiza troca com pivô na hotbar usando swapHotbar (modo 2)
     *      em 3 passos: (bag -> pivot, armor -> pivot, bag -> pivot). O cursor NUNCA é tocado!
     */
    public static boolean performEquip(int piece, int inventoryIndex, Object worn) {
        try {
            // Anti-desync: não agir se o cursor estiver com item preso ou se o jogador estiver consumindo
            if (PriorityBus.isCursorBusy() || !PriorityBus.canSwapArmor()) {
                recoverOrphanedCursor();
                return false;
            }

            // Anti-desync: telas ou containers manuais abertos suspendem a operação
            if (Inventory.isScreenOpen() || Inventory.isContainerOpen() || Inventory.windowId() != 0 || Screens.isOpen()) {
                return false;
            }

            int armorSlot = Inventory.slotOf(36 + piece);
            if (armorSlot < 0) {
                return false;
            }

            // Anti-desync: se esperava slot de armadura vazio mas já foi preenchido, aborta
            Object currentWorn = Inventory.armor(piece);
            if (worn == null && currentWorn != null) {
                return false;
            }

            // Caso 1: Item reserva está na hotbar (0-8)
            if (inventoryIndex < 9) {
                PriorityBus.setArmorSwapping(true);
                try {
                    return Inventory.swapHotbar(armorSlot, inventoryIndex);
                } finally {
                    PriorityBus.setArmorSwapping(false);
                }
            }

            // Caso 2: Item reserva está na bolsa (9-35)
            int fromSlot = Inventory.slotOf(inventoryIndex);
            if (fromSlot < 0) {
                return false;
            }

            // Anti-desync: verificar se o item ainda se encontra no slot da bolsa
            if (Inventory.stack(inventoryIndex) == null) {
                return false;
            }

            // Reposição normal (slot do corpo vazio): shift-clique direto
            if (worn == null) {
                PriorityBus.setArmorSwapping(true);
                try {
                    return Inventory.quickMove(fromSlot);
                } finally {
                    PriorityBus.setArmorSwapping(false);
                }
            }

            // Troca de set (worn != null): pivô via swapHotbar (modo 2) para manter o cursor 100% nulo
            int pivot = findHotbarPivot();
            if (pivot >= 0) {
                PriorityBus.setArmorSwapping(true);
                try {
                    // Passo 1: mover nova armadura (da bolsa) para o pivot na hotbar
                    if (!Inventory.swapHotbar(fromSlot, pivot)) {
                        return false;
                    }
                    // Passo 2: trocar armadura equipada com o pivot
                    if (!Inventory.swapHotbar(armorSlot, pivot)) {
                        // Rollback passo 1: devolver nova armadura do pivot para a bolsa
                        Inventory.swapHotbar(fromSlot, pivot);
                        return false;
                    }
                    // Passo 3: mover armadura antiga do pivot para a bolsa
                    boolean ok = Inventory.swapHotbar(fromSlot, pivot);
                    if (Inventory.cursor() != null) {
                        recoverOrphanedCursor();
                    }
                    return ok;
                } finally {
                    PriorityBus.setArmorSwapping(false);
                }
            }

            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Localiza um slot seguro na hotbar para servir de pivô temporário.
     * Prefere um slot vazio; evita a mão selecionada, slots de consumíveis em uso e espada de defesa.
     */
    public static int findHotbarPivot() {
        return findHotbarPivot(-1);
    }

    public static int findHotbarPivot(int excludeSlot) {
        try {
            int selected = Inventory.selectedHotbar();
            int busyConsumable = PriorityBus.getActiveConsumableSlot();
            int swordSlot = PriorityBus.getSwordSlot();

            // 1. Preferência máxima: slot vazio não selecionado e não reservado
            for (int i = 0; i < 9; i++) {
                if (i == selected || i == excludeSlot || i == busyConsumable || i == swordSlot) {
                    continue;
                }
                if (Inventory.stack(i) == null) {
                    return i;
                }
            }

            // 2. Qualquer slot vazio na hotbar
            for (int i = 0; i < 9; i++) {
                if (Inventory.stack(i) == null) {
                    return i;
                }
            }

            // 3. Fallback: slot ocupado diferente do ativo e não reservado
            for (int i = 8; i >= 0; i--) {
                if (i != selected && i != excludeSlot && i != busyConsumable && i != swordSlot) {
                    return i;
                }
            }

            // 4. Último fallback: qualquer slot != selected
            for (int i = 8; i >= 0; i--) {
                if (i != selected) {
                    return i;
                }
            }
        } catch (Throwable ignored) {}
        return 0;
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

    /**
     * Garante que o AutoArmor normal nunca seja pausado durante combate / bloqueio com espada.
     */
    public static boolean shouldPauseForUsingItem(Object player) {
        return false;
    }

    /**
     * Recupera imediatamente qualquer item órfão preso no cursor do mouse quando nenhuma tela estiver aberta,
     * devolvendo-o com segurança máxima para slot de armadura vago, mesclando em stack compatível,
     * colocando em slot vazio da mochila ou descartando no chão apenas se expressamente habilitado em dropStuckOld.
     */
    public static boolean recoverOrphanedCursor() {
        try {
            if (Inventory.cursor() == null) {
                return false;
            }
            if (Inventory.isScreenOpen() || Inventory.isContainerOpen() || Inventory.windowId() != 0 || Screens.isOpen()) {
                return false;
            }

            Object cursor = Inventory.cursor();

            // 1. Se for armadura e o slot correspondente do corpo estiver vazio: equipa
            int piece = Inventory.armorPiece(cursor);
            if (piece >= 0 && Inventory.armor(piece) == null) {
                int armorSlot = Inventory.slotOf(36 + piece);
                if (armorSlot >= 0 && Inventory.pickup(armorSlot)) {
                    return true;
                }
            }

            // 2. Tenta mesclar em stacks do mesmo tipo que ainda tenham espaço
            for (int i = 0; i < 36; i++) {
                Object stack = Inventory.stack(i);
                if (stack != null && Stacks.sameKind(stack, cursor)) {
                    int count = Stacks.count(stack);
                    int max = Stacks.maxCount(stack);
                    if (count < max) {
                        int slot = Inventory.slotOf(i);
                        if (slot >= 0 && Inventory.pickup(slot)) {
                            if (Inventory.cursor() == null) {
                                return true;
                            }
                        }
                    }
                }
            }

            // 3. Devolve para o primeiro slot vazio do inventário
            int emptyIdx = Inventory.firstEmpty();
            if (emptyIdx >= 0) {
                int slot = Inventory.slotOf(emptyIdx);
                if (slot >= 0 && Inventory.pickup(slot)) {
                    return true;
                }
            }

            // 4. Se todo o inventário estiver 100% cheio, só descarta se a opção dropStuckOld estiver ativada
            YNYConfig config = YNYUtils.getConfig();
            if (config != null && !config.autoArmorDropStuckOld) {
                return false;
            }
            return Inventory.dropCursor();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Alerta preventivo de quebra de armadura: emite um som sutil de aviso ("random.orb")
     * quando a peça equipada atinge o limiar crítico de durabilidade prestes a quebrar/trocar.
     * Totalmente desacoplado de renderizador e executado sem alocações de GC.
     */
    public static void checkPreventiveArmorAlert() {
        try {
            YNYConfig config = YNYUtils.getConfig();
            if (config == null || !config.autoArmorAlertEnabled) {
                return;
            }

            int threshold = ArmorThreshold.normalize(config.autoArmorPreventiveThreshold);
            int alertThreshold = threshold > 0 ? threshold : 15;
            long now = System.currentTimeMillis();

            for (int piece = 0; piece < 4; piece++) {
                Object worn = Inventory.armor(piece);
                if (worn == null || !Stacks.isDamageable(worn)) {
                    continue;
                }

                int max = Stacks.maxDamage(worn);
                if (max <= 0) continue;

                int damage = Stacks.damage(worn);
                int remaining = max - damage;
                int percent = (remaining * 100) / max;

                boolean critical = (percent <= alertThreshold) || (remaining <= 25 && remaining > 0) || damage >= max;

                if (critical) {
                    if (now - lastAlertTicks[piece] >= 8000L && now - lastGlobalAlertTick >= 2000L) { // Debounce de 8 segundos por peça e 2s global anti-spam
                        lastAlertTicks[piece] = now;
                        lastGlobalAlertTick = now;
                        playSubtleAlertSound();
                        break; // Toca no máximo 1 som por tick para evitar sobreposição auditiva
                    }
                } else if (percent > alertThreshold + 5) {
                    lastAlertTicks[piece] = 0L;
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Reseta os estados de debounce do alerta preventivo (ex: ao morrer ou desconectar).
     */
    public static void reset() {
        for (int i = 0; i < 4; i++) {
            lastAlertTicks[i] = 0L;
        }
        lastGlobalAlertTick = 0L;
    }

    /**
     * Toca o som sutil de aviso ("random.orb") usando reflexão dinâmica para compatibilidade
     * total com MCP named e Notch obfuscado do Minecraft 1.8.9.
     */
    public static void playSubtleAlertSound() {
        try {
            Object self = Entities.self();
            if (self == null) return;
            if (playSoundMethod == null && !playSoundResolved) {
                Class<?> cur = self.getClass();
                while (cur != null && cur != Object.class) {
                    for (Method m : cur.getDeclaredMethods()) {
                        Class<?>[] params = m.getParameterTypes();
                        if (params.length == 3 && params[0] == String.class
                                && params[1] == float.class && params[2] == float.class) {
                            m.setAccessible(true);
                            playSoundMethod = m;
                            break;
                        }
                    }
                    if (playSoundMethod != null) break;
                    cur = cur.getSuperclass();
                }
                playSoundResolved = true;
            }
            if (playSoundMethod != null) {
                playSoundMethod.invoke(self, "random.orb", 0.6F, 1.8F);
            }
        } catch (Throwable ignored) {}
    }
}
