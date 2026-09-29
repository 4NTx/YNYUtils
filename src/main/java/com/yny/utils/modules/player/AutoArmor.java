package com.yny.utils.modules.player;

import java.util.function.BooleanSupplier;

import dev.xavier.stein.loader.api.Inventory;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

/** Repõe peças quebradas com um clique de inventário vanilla por tick. */
public final class AutoArmor {
    private static final int[] PRIORITY = {
        Inventory.CHESTPLATE, Inventory.LEGGINGS, Inventory.HELMET, Inventory.BOOTS
    };
    private static final long RETRY_DELAY_TICKS = 20L;
    private static final long NO_ITEM_RECHECK_TICKS = 20L;
    private static final long CLICK_DELAY_TICKS = 2L;

    private final BooleanSupplier enabled;
    private final long[] retryAt = new long[4];
    private long tick;
    private long nextClickAt;

    public AutoArmor(BooleanSupplier enabled) {
        this.enabled = enabled;
    }

    /** Chamado no fim do tick; nunca abre telas nem mexe no cursor do jogador. */
    public void onTickEnd() {
        tick++;
        if (!enabled.getAsBoolean()) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null || !mc.thePlayer.isEntityAlive()
                || mc.playerController == null || mc.playerController.isInCreativeMode()
                || mc.playerController.isSpectator() || mc.currentScreen != null
                || Inventory.isContainerOpen() || Inventory.windowId() != 0 || Inventory.cursor() != null) {
            return;
        }
        if (tick < nextClickAt) {
            return;
        }

        for (int piece : PRIORITY) {
            if (Inventory.armor(piece) != null) {
                continue;
            }
            if (tick < retryAt[piece]) {
                continue;
            }

            int replacement = bestReplacement(piece);
            if (replacement < 0) {
                retryAt[piece] = tick + NO_ITEM_RECHECK_TICKS;
                continue;
            }

            // O cooldown também protege contra slot vazio por latência/rejeição do servidor.
            retryAt[piece] = tick + RETRY_DELAY_TICKS;
            nextClickAt = tick + CLICK_DELAY_TICKS;
            Inventory.equip(replacement);
            return; // no máximo uma ação de inventário por tick
        }
    }

    private static int bestReplacement(int piece) {
        int bestSlot = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int slot = Inventory.HOTBAR_START; slot < Inventory.ARMOR_START; slot++) {
            Object value = Inventory.stack(slot);
            if (!(value instanceof ItemStack) || Inventory.armorPiece(value) != piece) {
                continue;
            }

            ItemStack stack = (ItemStack) value;
            int maxDamage = stack.getMaxDamage();
            if (maxDamage > 0 && stack.getItemDamage() >= maxDamage) {
                continue;
            }
            int durability = maxDamage <= 0 || !stack.isItemStackDamageable() ? 1000
                    : (int) ((long) (maxDamage - stack.getItemDamage()) * 1000L / maxDamage);
            int score = Inventory.armorValue(value) * 1001 + durability;
            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }
}
