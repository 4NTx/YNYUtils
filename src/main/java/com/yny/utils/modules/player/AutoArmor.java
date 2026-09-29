package com.yny.utils.modules.player;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

import dev.xavier.stein.loader.api.Inventory;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemArmor.ArmorMaterial;
import net.minecraft.item.ItemStack;

/** Reposição de armadura orientada a inventário e confirmada pelo servidor. */
public final class AutoArmor {
    private static final int[] PRIORITY = {
        Inventory.CHESTPLATE, Inventory.LEGGINGS, Inventory.HELMET, Inventory.BOOTS
    };
    private static final ArmorMaterial[] PREFERRED_MATERIALS = {
        null, ArmorMaterial.DIAMOND, ArmorMaterial.IRON, ArmorMaterial.CHAIN,
        ArmorMaterial.GOLD, ArmorMaterial.LEATHER
    };
    private static final long FIRST_RETRY_TICKS = 2L;
    private static final long SECOND_RETRY_TICKS = 10L;
    private static final long LATER_RETRY_TICKS = 20L;
    private static final long NO_ITEM_RECHECK_TICKS = 4L;
    private static final long CLICK_DELAY_TICKS = 1L;
    // A normal window click should be reflected locally/server-side in a few ticks.
    // Never leave the utility blocked for seconds if the SDK callback is missed.
    private static final long SERVER_WAIT_TIMEOUT_TICKS = 8L;

    private final BooleanSupplier enabled;
    private final IntSupplier preventiveThreshold;
    private final IntSupplier preferredMaterial;
    private final BooleanSupplier ignoreUnenchanted;
    private final BooleanSupplier dropUnenchantedOld;
    private final Consumer<String> status;
    private final long[] retryAt = new long[4];
    private final int[] failedAttempts = new int[4];
    private long tick;
    private long nextClickAt;
    private Pending pending;

    public AutoArmor(BooleanSupplier enabled, IntSupplier preventiveThreshold, IntSupplier preferredMaterial,
            BooleanSupplier ignoreUnenchanted, BooleanSupplier dropUnenchantedOld, Consumer<String> status) {
        this.enabled = enabled;
        this.preventiveThreshold = preventiveThreshold;
        this.preferredMaterial = preferredMaterial;
        this.ignoreUnenchanted = ignoreUnenchanted;
        this.dropUnenchantedOld = dropUnenchantedOld;
        this.status = status;
    }

    /** Chamado no fim do tick; não abre GUI e não altera movimento ou item cursor. */
    public void onTickEnd() {
        tick++;
        if (pending != null) {
            observeCurrentInventory();
            advancePending();
            return;
        }
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

        int threshold = Math.max(0, Math.min(50, preventiveThreshold.getAsInt()));
        for (int piece : PRIORITY) {
            ItemStack worn = asStack(Inventory.armor(piece));
            if (worn != null && (threshold == 0 || durabilityPercent(worn) > threshold)) {
                continue;
            }
            if (tick < retryAt[piece]) {
                continue;
            }

            Choice replacement = bestReplacement(piece, worn, threshold);
            if (replacement == null) {
                retryAt[piece] = tick + NO_ITEM_RECHECK_TICKS;
                continue;
            }

            beginEquip(piece, replacement, worn);
            return;
        }
    }

    private void beginEquip(int piece, Choice choice, ItemStack worn) {
        Pending operation = new Pending(piece, choice.inventoryIndex, choice.stack,
                worn, dropUnenchantedOld.getAsBoolean() && worn != null && !worn.isItemEnchanted());
        pending = operation; // arm before windowClick can be observed
        nextClickAt = tick + CLICK_DELAY_TICKS;
        if (!Inventory.equip(choice.inventoryIndex)) {
            pending = null;
            scheduleRetry(piece);
            status.accept("Auto Armor: troca não iniciada");
        }
    }

    /** Stein chama este callback depois de aplicar a atualização vinda do servidor. */
    public void onSlotChanged(int windowId, int slot, Object value) {
        if (pending == null) {
            return;
        }
        ItemStack stack = asStack(value);
        if (windowId == -1 && slot == -1) {
            if (pending.rejected && stack == null) {
                finishRejected();
            }
            return;
        }
        if (windowId != 0) {
            return;
        }

        int inventoryIndex = Inventory.inventoryIndexOf(slot);
        if (pending.dropping) {
            if (inventoryIndex == pending.sourceIndex && stack == null) {
                pending.confirmed = true;
            }
            return;
        }
        if (inventoryIndex == pending.sourceIndex) {
            pending.sourceSeen = true;
            pending.source = copy(stack);
        }
        if (inventoryIndex == Inventory.ARMOR_START + pending.piece) {
            pending.armorSeen = true;
            pending.armor = copy(stack);
        }
        confirmIfAuthoritative();
    }

    /** Um snapshot completo permite confirmar ou reconhecer a rejeição/desync. */
    public void onWindowItems(int windowId) {
        if (pending == null || windowId != 0) {
            return;
        }
        if (pending.dropping) {
            if (Inventory.stack(pending.sourceIndex) == null) {
                pending.confirmed = true;
            } else {
                pending.rejected = true;
            }
            return;
        }
        pending.sourceSeen = true;
        pending.source = copy(asStack(Inventory.stack(pending.sourceIndex)));
        pending.armorSeen = true;
        pending.armor = copy(asStack(Inventory.armor(pending.piece)));
        if (matchesExpected(pending.source, pending.expectedSource)
                && matchesExpected(pending.armor, pending.expectedArmor)
                && Inventory.cursor() == null) {
            pending.confirmed = true;
        } else {
            pending.rejected = true;
        }
    }

    /** Limpa uma operação interrompida pela troca de mundo/servidor. */
    public void resetSession() {
        pending = null;
        nextClickAt = 0L;
        for (int i = 0; i < retryAt.length; i++) {
            retryAt[i] = 0L;
            failedAttempts[i] = 0;
        }
    }

    /** Poll local slots as well as callbacks; some Stein builds omit a callback on a normal click. */
    private void observeCurrentInventory() {
        if (pending.dropping) {
            if (Inventory.cursor() == null && Inventory.stack(pending.sourceIndex) == null) {
                pending.confirmed = true;
            }
            return;
        }
        ItemStack source = asStack(Inventory.stack(pending.sourceIndex));
        ItemStack armor = asStack(Inventory.armor(pending.piece));
        if (Inventory.cursor() == null
                && matchesExpected(source, pending.expectedSource)
                && matchesExpected(armor, pending.expectedArmor)) {
            pending.confirmed = true;
        }
    }

    private void confirmIfAuthoritative() {
        if (pending.sourceSeen && pending.armorSeen
                && matchesExpected(pending.source, pending.expectedSource)
                && matchesExpected(pending.armor, pending.expectedArmor)) {
            // Source + armor slot updates are emitted after the server accepted all clicks.
            pending.confirmed = true;
        }
    }

    private void advancePending() {
        if (pending.rejected) {
            if (Inventory.cursor() == null) {
                finishRejected();
            }
            return;
        }
        if (pending.confirmed) {
            if (pending.dropping) {
                status.accept("Auto Armor: peça antiga descartada");
                pending = null;
                return;
            }
            failedAttempts[pending.piece] = 0;
            if (pending.dropOld && enabled.getAsBoolean()
                    && matchesExpected(asStack(Inventory.stack(pending.sourceIndex)), pending.oldArmor)
                    && Inventory.cursor() == null) {
                pending.dropping = true;
                pending.confirmed = false;
                pending.sourceSeen = false;
                if (Inventory.drop(pending.sourceIndex, true)) {
                    return;
                }
                pending.dropping = false;
            }
            status.accept("Auto Armor: " + pieceName(pending.piece) + " confirmada pelo servidor");
            pending = null;
            return;
        }

        pending.age++;
        if (pending.age >= SERVER_WAIT_TIMEOUT_TICKS && Inventory.cursor() == null) {
            scheduleRetry(pending.piece);
            pending = null;
        }
    }

    private void finishRejected() {
        if (pending == null) {
            return;
        }
        scheduleRetry(pending.piece);
        pending = null;
    }

    private void scheduleRetry(int piece) {
        int failures = ++failedAttempts[piece];
        long delay = failures == 1 ? FIRST_RETRY_TICKS
                : failures == 2 ? SECOND_RETRY_TICKS : LATER_RETRY_TICKS;
        retryAt[piece] = tick + delay;
        if (failures == 2) {
            status.accept("Auto Armor: sem confirmação; tentando novamente");
        } else if (failures >= 4) {
            status.accept("Auto Armor: troca não confirmada; verificando novamente");
        }
    }

    private Choice bestReplacement(int piece, ItemStack worn, int threshold) {
        Choice best = null;
        int preference = Math.max(0, Math.min(PREFERRED_MATERIALS.length - 1, preferredMaterial.getAsInt()));
        boolean preventive = worn != null && threshold > 0 && durabilityPercent(worn) <= threshold;
        for (int slot = Inventory.HOTBAR_START; slot < Inventory.ARMOR_START; slot++) {
            ItemStack candidate = asStack(Inventory.stack(slot));
            if (candidate == null || Inventory.armorPiece(candidate) != piece || isBroken(candidate)) {
                continue;
            }
            if (ignoreUnenchanted.getAsBoolean() && !candidate.isItemEnchanted()) {
                continue;
            }
            if (preventive && !isPreventiveUpgrade(candidate, worn)) {
                continue;
            }
            Choice choice = new Choice(slot, candidate.copy(), score(candidate), isPreferred(candidate, preference));
            if (best == null || compare(choice, best) > 0) {
                best = choice;
            }
        }
        return best;
    }

    /** Requires more remaining durability and no loss of base armor points. */
    private static boolean isPreventiveUpgrade(ItemStack candidate, ItemStack worn) {
        return Inventory.armorValue(candidate) >= Inventory.armorValue(worn)
                && durabilityPercent(candidate) > durabilityPercent(worn);
    }

    private static int compare(Choice left, Choice right) {
        if (left.preferred != right.preferred) {
            return left.preferred ? 1 : -1;
        }
        int quality = Long.compare(left.score, right.score);
        return quality != 0 ? quality : Integer.compare(right.inventoryIndex, left.inventoryIndex);
    }

    private static long score(ItemStack stack) {
        return protectionScore(stack) + (long) durabilityPercent(stack) * 1_000L;
    }

    private static long protectionScore(ItemStack stack) {
        long armor = (long) Inventory.armorValue(stack) * 1_000_000L;
        long general = (long) level(Enchantment.protection, stack) * 250_000L;
        long specialized = (long) (level(Enchantment.fireProtection, stack)
                + level(Enchantment.blastProtection, stack)
                + level(Enchantment.projectileProtection, stack)
                + level(Enchantment.featherFalling, stack)) * 55_000L;
        long utility = (long) level(Enchantment.thorns, stack) * 25_000L
                + (long) level(Enchantment.unbreaking, stack) * 70_000L;
        return armor + general + specialized + utility;
    }

    private static int level(Enchantment enchantment, ItemStack stack) {
        return enchantment == null ? 0 : Math.max(0, Math.min(20,
                EnchantmentHelper.getEnchantmentLevel(enchantment.effectId, stack)));
    }

    private static boolean isPreferred(ItemStack stack, int preference) {
        if (preference <= 0 || preference >= PREFERRED_MATERIALS.length
                || !(stack.getItem() instanceof ItemArmor)) {
            return false;
        }
        return ((ItemArmor) stack.getItem()).getArmorMaterial() == PREFERRED_MATERIALS[preference];
    }

    private static boolean isBroken(ItemStack stack) {
        int maxDamage = stack.getMaxDamage();
        return maxDamage > 0 && stack.getItemDamage() >= maxDamage;
    }

    private static int durabilityPercent(ItemStack stack) {
        int maxDamage = stack.getMaxDamage();
        if (maxDamage <= 0 || !stack.isItemStackDamageable()) {
            return 100;
        }
        return Math.max(0, Math.min(100, (int) ((long) (maxDamage - stack.getItemDamage()) * 100L / maxDamage)));
    }

    private static boolean matchesExpected(ItemStack actual, ItemStack expected) {
        if (actual == null || expected == null) {
            return actual == expected;
        }
        return ItemStack.areItemStacksEqual(actual, expected);
    }

    private static ItemStack asStack(Object value) {
        return value instanceof ItemStack ? (ItemStack) value : null;
    }

    private static ItemStack copy(ItemStack stack) {
        return stack == null ? null : stack.copy();
    }

    private static String pieceName(int piece) {
        switch (piece) {
            case Inventory.HELMET: return "elmo";
            case Inventory.CHESTPLATE: return "peitoral";
            case Inventory.LEGGINGS: return "calças";
            case Inventory.BOOTS: return "botas";
            default: return "peça";
        }
    }

    private static final class Choice {
        final int inventoryIndex;
        final ItemStack stack;
        final long score;
        final boolean preferred;

        Choice(int inventoryIndex, ItemStack stack, long score, boolean preferred) {
            this.inventoryIndex = inventoryIndex;
            this.stack = stack;
            this.score = score;
            this.preferred = preferred;
        }
    }

    private static final class Pending {
        final int piece;
        final int sourceIndex;
        final ItemStack replacement;
        final ItemStack oldArmor;
        final ItemStack expectedSource;
        final ItemStack expectedArmor;
        final boolean dropOld;
        ItemStack source;
        ItemStack armor;
        boolean sourceSeen;
        boolean armorSeen;
        boolean confirmed;
        boolean rejected;
        boolean dropping;
        int age;

        Pending(int piece, int sourceIndex, ItemStack replacement, ItemStack oldArmor, boolean dropOld) {
            this.piece = piece;
            this.sourceIndex = sourceIndex;
            this.replacement = replacement.copy();
            this.oldArmor = copy(oldArmor);
            this.expectedSource = copy(oldArmor);
            this.expectedArmor = replacement.copy();
            this.dropOld = dropOld;
        }
    }
}
