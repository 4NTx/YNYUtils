package com.yny.utils.modules.player;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

import com.yny.utils.core.DebugLog;

import dev.xavier.stein.loader.api.Inventory;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemArmor.ArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Reposição de armadura orientada a inventário e confirmada pelo servidor. */
public final class AutoArmor {
    private static final int[] PRIORITY = {
        Inventory.CHESTPLATE, Inventory.LEGGINGS, Inventory.HELMET, Inventory.BOOTS
    };
    private static final ArmorMaterial[] PREFERRED_MATERIALS = {
        null, ArmorMaterial.DIAMOND, ArmorMaterial.IRON, ArmorMaterial.CHAIN,
        ArmorMaterial.GOLD, ArmorMaterial.LEATHER
    };
    private static final long FIRST_RETRY_TICKS = 1L;
    private static final long SECOND_RETRY_TICKS = 3L;
    private static final long LATER_RETRY_TICKS = 10L;
    private static final long NO_ITEM_RECHECK_TICKS = 4L;
    private static final long CLICK_DELAY_TICKS = 1L;
    // Keep only one armor swap in flight until the server has had time to acknowledge both slots.
    // If a Stein build omits the slot callback, the local prediction gets a bounded fallback below.
    private static final long SERVER_WAIT_TIMEOUT_TICKS = 8L;
    private static final int CURSOR_RETURN_TIMEOUT_TICKS = 8;
    private static final int CURSOR_RETURN_MAX_ATTEMPTS = 3;
    private static final int CURSOR_NO_SLOT_GRACE_TICKS = 8;
    private static final int MIN_DISTINGUISHABLE_DAMAGE_GAP = 32;

    private final BooleanSupplier enabled;
    private final IntSupplier preventiveThreshold;
    private final IntSupplier preferredMaterial;
    private final BooleanSupplier ignoreUnenchanted;
    private final BooleanSupplier useDamagedReserves;
    private final BooleanSupplier dropUnenchantedOld;
    private final BooleanSupplier dropStuckOld;
    private final Consumer<String> status;
    private final long[] retryAt = new long[4];
    private final int[] failedAttempts = new int[4];
    private final long[] lastNoChoiceLog = new long[4];
    private final boolean[] preventiveFailed = new boolean[4];
    private final ItemStack[] failedWornArmor = new ItemStack[4];
    private long tick;
    private long nextClickAt;
    private Pending pending;
    private String lastPauseReason;

    public AutoArmor(BooleanSupplier enabled, IntSupplier preventiveThreshold, IntSupplier preferredMaterial,
            BooleanSupplier ignoreUnenchanted, BooleanSupplier useDamagedReserves,
            BooleanSupplier dropUnenchantedOld, BooleanSupplier dropStuckOld,
            Consumer<String> status) {
        this.enabled = enabled;
        this.preventiveThreshold = preventiveThreshold;
        this.preferredMaterial = preferredMaterial;
        this.ignoreUnenchanted = ignoreUnenchanted;
        this.useDamagedReserves = useDamagedReserves;
        this.dropUnenchantedOld = dropUnenchantedOld;
        this.dropStuckOld = dropStuckOld;
        this.status = status;
    }

    /** Chamado no fim do tick; não abre GUI e não altera movimento ou item cursor. */
    public void onTickEnd() {
        tick++;
        if (pending != null) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.currentScreen != null || Inventory.isScreenOpen() || Inventory.isContainerOpen()) {
                pending.manualIntervened = true;
                pending.serverOldCursor = null;
            }
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
            String reason = "pausado: player=" + (mc.thePlayer != null)
                    + "; world=" + (mc.theWorld != null) + "; alive="
                    + (mc.thePlayer != null && mc.thePlayer.isEntityAlive()) + "; controller="
                    + (mc.playerController != null) + "; creative="
                    + (mc.playerController != null && mc.playerController.isInCreativeMode())
                    + "; spectator=" + (mc.playerController != null && mc.playerController.isSpectator())
                    + "; screen=" + (mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getName())
                    + "; container=" + Inventory.isContainerOpen() + "; window=" + Inventory.windowId()
                    + "; cursor=" + describe(asStack(Inventory.cursor()));
            if (!reason.equals(lastPauseReason)) {
                log("tick=" + tick + " " + reason);
                lastPauseReason = reason;
            }
            return;
        }
        lastPauseReason = null;
        if (tick < nextClickAt) {
            return;
        }

        int threshold = Math.max(0, Math.min(50, preventiveThreshold.getAsInt()));
        for (int piece : PRIORITY) {
            ItemStack worn = asStack(Inventory.armor(piece));
            if (preventiveFailed[piece]) {
                if (worn == null || isBroken(worn)) {
                    preventiveFailed[piece] = false;
                    failedWornArmor[piece] = null;
                    log(pieceName(piece) + " quebrou após falha preventiva; iniciando reposição urgente");
                } else if (!matchesArmor(worn, failedWornArmor[piece])) {
                    // A different piece was equipped manually or by another system.
                    preventiveFailed[piece] = false;
                    failedWornArmor[piece] = null;
                } else {
                    // Avoid repeating a rejected preventive click. Keep watching this slot;
                    // as soon as it breaks/disappears, the urgent replacement path below runs.
                    continue;
                }
            }
            // A destroyed item must never be protected by the preventive threshold.
            if (worn != null && !isBroken(worn)
                    && (threshold == 0 || durabilityPercent(worn) > threshold)) {
                continue;
            }
            // Retry backoff applies only to preventive attempts. A broken/missing piece is
            // urgent and must be replaced immediately, even after several failed swaps.
            if (worn != null && !isBroken(worn) && tick < retryAt[piece]) {
                continue;
            }

            Choice replacement = bestReplacement(piece, worn, threshold);
            if (replacement == null) {
                if (lastNoChoiceLog[piece] == 0L || tick - lastNoChoiceLog[piece] >= 100L) {
                    log("sem reserva elegível para " + pieceName(piece) + "; worn=" + describe(worn)
                            + "; threshold=" + threshold + "%; ignoreUnenchanted="
                            + ignoreUnenchanted.getAsBoolean() + "; damagedReserves="
                            + useDamagedReserves.getAsBoolean());
                    lastNoChoiceLog[piece] = tick;
                }
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
            log("equip() recusou " + pieceName(piece) + " source=" + choice.inventoryIndex
                    + " worn=" + describe(operation.oldArmor) + " reserve=" + describe(choice.stack));
            pending = null;
            deferPreventiveUntilBroken(operation);
            scheduleRetry(piece);
            status.accept("Auto Armor: troca não iniciada");
        } else {
            log("iniciada " + pieceName(piece) + " source=" + choice.inventoryIndex
                    + " mode=" + (worn == null || isBroken(worn) ? "reposicao urgente" : "preventiva")
                    + " worn=" + describe(operation.oldArmor) + " reserve=" + describe(choice.stack));
            // windowClick updates the client prediction synchronously. Do not treat that as a
            // server confirmation or start another piece in the same tick.
            observeCurrentInventory();
        }
    }

    /** Stein chama este callback depois de aplicar a atualização vinda do servidor. */
    public void onSlotChanged(int windowId, int slot, Object value) {
        if (pending == null) {
            return;
        }
        ItemStack stack = asStack(value);
        if (windowId == -1 && slot == -1) {
            if (stack != null && pending.serverOldCursor == null && !pending.manualIntervened
                    && pending.oldArmor != null && pending.localApplied
                    && matchesOldIdentityAndDamage(stack, pending)) {
                pending.serverOldCursor = copy(stack);
                log("cursor do servidor identificado como peça antiga: " + describe(stack));
            }
            if ((pending.cursorReturnIndex >= 0 || pending.cursorDropIssued) && stack == null) {
                pending.cursorClearSeen = true;
            }
            if (pending.rejected && stack == null) {
                finishRejected();
            }
            return;
        }
        if (windowId != 0) {
            return;
        }

        int inventoryIndex = Inventory.inventoryIndexOf(slot);
        if (inventoryIndex != pending.sourceIndex
                && inventoryIndex != Inventory.ARMOR_START + pending.piece
                && inventoryIndex != pending.cursorReturnIndex) {
            return;
        }
        if (inventoryIndex == pending.cursorReturnIndex && matchesOldOrObservedCursor(stack, pending)) {
            pending.cursorReturnSlotSeen = true;
        }
        if (pending.dropping) {
            if (inventoryIndex == pending.sourceIndex && stack == null) {
                pending.serverConfirmed = true;
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
        if (pending.cursorReturnIndex >= 0) {
            pending.cursorClearSeen = Inventory.cursor() == null;
            pending.cursorReturnSlotSeen = matchesOldOrObservedCursor(
                    asStack(Inventory.stack(pending.cursorReturnIndex)), pending);
        }
        if (pending.dropping) {
            if (Inventory.stack(pending.sourceIndex) == null) {
                pending.serverConfirmed = true;
            } else {
                pending.rejected = true;
            }
            return;
        }
        pending.sourceSeen = true;
        pending.source = copy(asStack(Inventory.stack(pending.sourceIndex)));
        pending.armorSeen = true;
        pending.armor = copy(asStack(Inventory.armor(pending.piece)));
        if (matchesEquipState(pending.source, pending.armor, pending)
                && Inventory.cursor() == null) {
            boolean firstConfirmation = !pending.serverConfirmed;
            pending.serverConfirmed = true;
            pending.localApplied = true;
            if (firstConfirmation) {
                logConfirmation("snapshot", pending);
            }
        } else {
            // A full-window snapshot can arrive while the server is still processing the
            // equip click (or while a worn piece breaks). Do not abort the pending swap on
            // this transient state; let slot callbacks or the bounded timeout decide it.
            // Aguarda callbacks dos slots ou o timeout limitado; snapshot parcial é comum.
        }
    }

    /** Limpa uma operação interrompida pela troca de mundo/servidor. */
    public void resetSession() {
        pending = null;
        nextClickAt = 0L;
        lastPauseReason = null;
        for (int i = 0; i < retryAt.length; i++) {
            retryAt[i] = 0L;
            failedAttempts[i] = 0;
            preventiveFailed[i] = false;
            failedWornArmor[i] = null;
        }
    }

    /** Poll local slots as well as callbacks; some Stein builds omit a callback on a normal click. */
    private void observeCurrentInventory() {
        if (pending.dropping) {
            return;
        }
        ItemStack source = asStack(Inventory.stack(pending.sourceIndex));
        ItemStack armor = asStack(Inventory.armor(pending.piece));
        boolean coherent = matchesEquipState(source, armor, pending);
        if (Inventory.cursor() == null && coherent) {
            pending.localApplied = true;
        }
    }

    private void confirmIfAuthoritative() {
        if (pending.serverConfirmed) {
            return;
        }
        if (pending.sourceSeen && pending.armorSeen
                && matchesEquipState(pending.source, pending.armor, pending)) {
            // Source + armor slot updates are emitted after the server accepted all clicks.
            pending.serverConfirmed = true;
            pending.localApplied = true;
            logConfirmation("slots", pending);
        }
    }

    private void advancePending() {
        pending.age++;
        if (pending.rejected) {
            if (Inventory.cursor() == null) {
                finishRejected();
            }
            return;
        }
        if (pending.dropping && pending.serverConfirmed) {
            status.accept("Auto Armor: peça antiga descartada");
            pending = null;
            return;
        }
        if (pending.localApplied && pending.serverConfirmed) {
            // The server can put the old piece on the cursor after confirming the new one.
            // Never release the operation while that item is still being carried.
            if (pending.age < 3) {
                return;
            }
            ArmorSwapSafety.Outcome outcome = settleOldArmorCursor();
            if (outcome == ArmorSwapSafety.Outcome.WAIT) {
                return;
            }
            if (outcome == ArmorSwapSafety.Outcome.ARMOR_CHANGED
                    || outcome == ArmorSwapSafety.Outcome.DESTINATION_UNKNOWN) {
                log("troca com resultado incerto em " + pieceName(pending.piece)
                        + "; outcome=" + outcome + "; cursor="
                        + describe(asStack(Inventory.cursor())) + "; destino="
                        + (pending.cursorReturnIndex < 0 ? "n/a"
                                : describe(asStack(Inventory.stack(pending.cursorReturnIndex))))
                        + "; armor=" + describe(asStack(Inventory.armor(pending.piece))));
                status.accept("Auto Armor: verificando " + pieceName(pending.piece) + " novamente");
                nextClickAt = tick + CLICK_DELAY_TICKS;
                pending = null;
                return;
            }
            failedAttempts[pending.piece] = 0;
            preventiveFailed[pending.piece] = false;
            failedWornArmor[pending.piece] = null;
            if (pending.dropOld && pending.cursorReturnIndex < 0 && enabled.getAsBoolean()
                    && matchesExpected(asStack(Inventory.stack(pending.sourceIndex)), pending.oldArmor)
                    && Inventory.cursor() == null) {
                pending.dropping = true;
                pending.serverConfirmed = false;
                pending.sourceSeen = false;
                if (Inventory.drop(pending.sourceIndex, true)) {
                    return;
                }
                pending.dropping = false;
            }
            status.accept("Auto Armor: " + pieceName(pending.piece) + " equipada");
            log("concluída " + pieceName(pending.piece) + "; armor="
                    + describe(asStack(Inventory.armor(pending.piece))));
            pending = null;
            return;
        }

        if (pending.age >= SERVER_WAIT_TIMEOUT_TICKS && Inventory.cursor() != null) {
            if (!pending.cursorTimeoutLogged) {
                log("FALLBACK BLOQUEADO: cursor não vazio após timeout em " + pieceName(pending.piece)
                        + "; cursor=" + describe(asStack(Inventory.cursor())) + "; source="
                        + describe(asStack(Inventory.stack(pending.sourceIndex))) + "; armor="
                        + describe(asStack(Inventory.armor(pending.piece))) + "; pending="
                        + pendingSummary(pending));
                status.accept("Auto Armor: cursor ocupado; troca pausada");
                pending.cursorTimeoutLogged = true;
            }
            return;
        }
        if (pending.age >= SERVER_WAIT_TIMEOUT_TICKS) {
            if (pending.localApplied
                    && matchesEquipState(asStack(Inventory.stack(pending.sourceIndex)),
                            asStack(Inventory.armor(pending.piece)), pending)) {
                // The SDK's local inventory is still coherent, but no authoritative callback arrived.
                // Keep the equipped piece and never risk an automatic drop on an unconfirmed swap.
                failedAttempts[pending.piece] = 0;
                status.accept("Auto Armor: peça equipada; confirmação atrasada");
                log("fallback local após timeout " + pieceName(pending.piece) + "; source="
                        + describe(asStack(Inventory.stack(pending.sourceIndex))) + "; armor="
                        + describe(asStack(Inventory.armor(pending.piece))));
                pending = null;
            } else {
                log("timeout sem troca coerente " + pieceName(pending.piece) + "; source="
                        + describe(asStack(Inventory.stack(pending.sourceIndex))) + "; armor="
                        + describe(asStack(Inventory.armor(pending.piece))) + "; cursor="
                        + describe(asStack(Inventory.cursor())));
                deferPreventiveUntilBroken(pending);
                scheduleRetry(pending.piece);
                pending = null;
            }
        }
    }

    private ArmorSwapSafety.Outcome settleOldArmorCursor() {
        Minecraft mc = Minecraft.getMinecraft();
        boolean mayAct = ArmorSwapSafety.mayReturnCursor(mc.thePlayer != null
                        && mc.thePlayer.isEntityAlive(), mc.currentScreen != null
                        || Inventory.isScreenOpen(), Inventory.isContainerOpen(), Inventory.windowId());
        if (!mayAct) {
            if (mc.currentScreen != null || Inventory.isScreenOpen() || Inventory.isContainerOpen()) {
                pending.manualIntervened = true;
                pending.serverOldCursor = null;
            }
            if (!pending.manualPauseLogged) {
                log("recuperação pausada para intervenção do jogador: " + pieceName(pending.piece)
                        + "; screen=" + (mc.currentScreen == null ? "none"
                                : mc.currentScreen.getClass().getName())
                        + "; window=" + Inventory.windowId());
                pending.manualPauseLogged = true;
            }
            return ArmorSwapSafety.Outcome.WAIT;
        }
        pending.manualPauseLogged = false;
        ItemStack cursor = asStack(Inventory.cursor());
        if (pending.cursorDropIssued) {
            if (cursor == null && (pending.cursorClearSeen || ++pending.cursorDropAge >= CURSOR_RETURN_TIMEOUT_TICKS)) {
                log("descarte do cursor concluído: " + pieceName(pending.piece)
                        + "; confirmação servidor=" + pending.cursorClearSeen);
                return matchesReplacementArmor(asStack(Inventory.armor(pending.piece)), pending)
                        ? ArmorSwapSafety.Outcome.CONFIRMED : ArmorSwapSafety.Outcome.ARMOR_CHANGED;
            }
            if (cursor != null && ++pending.cursorDropAge == CURSOR_RETURN_TIMEOUT_TICKS) {
                log("descarte do cursor não confirmado; peça ainda no cursor: " + describe(cursor));
                status.accept("Auto Armor: cursor ainda ocupado; intervenção manual necessária");
            }
            return ArmorSwapSafety.Outcome.WAIT;
        }
        if (cursor != null) {
            if (!matchesOldOrObservedCursor(cursor, pending)) {
                if (!pending.cursorRecoveryWarned) {
                    log("cursor ocupado por outro item durante " + pieceName(pending.piece)
                            + "; item=" + describe(cursor) + "; antigo=" + describe(pending.oldArmor));
                    status.accept("Auto Armor: cursor ocupado; troca pausada");
                    pending.cursorRecoveryWarned = true;
                }
                return ArmorSwapSafety.Outcome.WAIT;
            }
            if (pending.cursorReturnIndex >= 0
                    && ++pending.cursorRecoveryAge < CURSOR_RETURN_TIMEOUT_TICKS) {
                return ArmorSwapSafety.Outcome.WAIT;
            }
            if (pending.cursorReturnAttempts >= CURSOR_RETURN_MAX_ATTEMPTS) {
                if (dropStuckCursor(cursor, true)) {
                    return ArmorSwapSafety.Outcome.WAIT;
                }
                if (!pending.cursorRecoveryWarned) {
                    log("devolução da peça antiga não confirmada após "
                            + CURSOR_RETURN_MAX_ATTEMPTS + " tentativas; cursor=" + describe(cursor));
                    status.accept("Auto Armor: peça no cursor; resolva no inventário");
                    pending.cursorRecoveryWarned = true;
                }
                return ArmorSwapSafety.Outcome.WAIT;
            }
            int destination = Inventory.stack(pending.sourceIndex) == null
                    ? pending.sourceIndex : Inventory.firstEmpty();
            int slot = destination < 0 ? -1 : Inventory.slotOf(destination);
            if (slot < 0 || Inventory.stack(destination) != null || !Inventory.accepts(slot, cursor)) {
                if (++pending.cursorNoSlotAge >= CURSOR_NO_SLOT_GRACE_TICKS
                        && dropStuckCursor(cursor, false)) {
                    return ArmorSwapSafety.Outcome.WAIT;
                }
                if (!pending.cursorRecoveryWarned) {
                    log("sem slot seguro para devolver peça antiga de " + pieceName(pending.piece)
                            + "; cursor=" + describe(cursor) + "; source="
                            + describe(asStack(Inventory.stack(pending.sourceIndex))));
                    status.accept("Auto Armor: peça antiga no cursor; inventário cheio");
                    pending.cursorRecoveryWarned = true;
                }
                return ArmorSwapSafety.Outcome.WAIT;
            }
            pending.cursorNoSlotAge = 0;
            pending.cursorReturnIndex = destination;
            pending.cursorRecoveryAge = 0;
            pending.cursorReturnAttempts++;
            pending.cursorClearSeen = false;
            pending.cursorReturnSlotSeen = false;
            log("devolvendo peça antiga do cursor: " + pieceName(pending.piece)
                    + " -> inventário " + destination + "; item=" + describe(cursor));
            if (!Inventory.pickup(slot)) {
                pending.cursorReturnIndex = -1;
                log("clique de devolução recusado para " + pieceName(pending.piece));
            }
            return ArmorSwapSafety.Outcome.WAIT;
        }
        if (pending.cursorReturnIndex >= 0) {
            ArmorSwapSafety.Outcome outcome = ArmorSwapSafety.returned(true,
                    matchesReplacementArmor(asStack(Inventory.armor(pending.piece)), pending),
                    matchesOldOrObservedCursor(asStack(Inventory.stack(pending.cursorReturnIndex)), pending),
                    pending.cursorClearSeen, pending.cursorReturnSlotSeen,
                    ++pending.cursorRecoveryAge, CURSOR_RETURN_TIMEOUT_TICKS);
            if (outcome != ArmorSwapSafety.Outcome.WAIT) {
                log("devolução " + outcome + ": " + pieceName(pending.piece)
                        + "; destino=" + describe(asStack(Inventory.stack(pending.cursorReturnIndex)))
                        + "; armor=" + describe(asStack(Inventory.armor(pending.piece))));
            }
            return outcome;
        }
        return matchesReplacementArmor(asStack(Inventory.armor(pending.piece)), pending)
                ? ArmorSwapSafety.Outcome.CONFIRMED : ArmorSwapSafety.Outcome.ARMOR_CHANGED;
    }

    private boolean dropStuckCursor(ItemStack cursor, boolean retriesExhausted) {
        boolean owned = pending.serverOldCursor != null
                && matchesObservedCursor(cursor, pending.serverOldCursor);
        boolean eligible = ArmorSwapSafety.mayDropStuckOld(dropStuckOld.getAsBoolean(),
                pending.manualIntervened, owned, pending.serverConfirmed,
                matchesReplacementArmor(asStack(Inventory.armor(pending.piece)), pending),
                retriesExhausted, pending.cursorNoSlotAge, CURSOR_NO_SLOT_GRACE_TICKS);
        if (!eligible) {
            return false;
        }
        pending.cursorDropIssued = true; // never send a second drop click for this exchange
        pending.cursorDropAge = 0;
        pending.cursorClearSeen = false;
        log("ÚLTIMO RECURSO: descartando peça antiga presa no cursor: " + describe(cursor)
                + "; tentativas de devolução=" + pending.cursorReturnAttempts
                + "; sem slot ticks=" + pending.cursorNoSlotAge);
        status.accept("Auto Armor: peça antiga presa descartada");
        if (Inventory.dropCursor()) {
            return true;
        }
        log("descarte do cursor recusado pelo SDK; peça preservada no cursor");
        status.accept("Auto Armor: não foi possível liberar o cursor");
        return false;
    }

    private void finishRejected() {
        if (pending == null) {
            return;
        }
        log("troca rejeitada/desync " + pieceName(pending.piece) + "; tentativa="
                + (failedAttempts[pending.piece] + 1));
        deferPreventiveUntilBroken(pending);
        scheduleRetry(pending.piece);
        pending = null;
    }

    private void deferPreventiveUntilBroken(Pending operation) {
        if (!operation.preventive || operation.oldArmor == null) {
            return;
        }
        preventiveFailed[operation.piece] = true;
        failedWornArmor[operation.piece] = copy(operation.oldArmor);
        log("fallback ativado para " + pieceName(operation.piece)
                + ": aguardando a peça quebrar para tentar reposição urgente");
        status.accept("Auto Armor: " + pieceName(operation.piece)
                + " será reposta ao quebrar");
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
        Choice preventiveFallback = null;
        StringBuilder inventoryScan = new StringBuilder();
        int preference = Math.max(0, Math.min(PREFERRED_MATERIALS.length - 1, preferredMaterial.getAsInt()));
        boolean preventive = worn != null && !isBroken(worn)
                && threshold > 0 && durabilityPercent(worn) <= threshold;
        for (int slot = Inventory.HOTBAR_START; slot < Inventory.ARMOR_START; slot++) {
            ItemStack candidate = asStack(Inventory.stack(slot));
            if (candidate == null) {
                continue;
            }
            int candidatePiece = Inventory.armorPiece(candidate);
            String rejection = "eligible";
            if (candidatePiece != piece) rejection = "wrong-piece:" + candidatePiece;
            else if (isBroken(candidate)) rejection = "broken";
            else if (ignoreUnenchanted.getAsBoolean() && !candidate.isItemEnchanted()) rejection = "unenchanted";
            else if ((worn == null || isBroken(worn)) && !useDamagedReserves.getAsBoolean()
                    && durabilityPercent(candidate) < 100) rejection = "damaged-reserve-disabled";
            else if (preventive && isPreventiveUpgrade(candidate, worn)) rejection = "preventive-upgrade";
            else if (preventive && durabilityPercent(candidate) > durabilityPercent(worn)
                    && Inventory.armorValue(candidate) < Inventory.armorValue(worn)) rejection = "preventive-fallback-lower-armor";
            else if (preventive) rejection = "preventive-not-durable-enough";
            if (inventoryScan.length() > 0) inventoryScan.append(" | ");
            inventoryScan.append("slot=").append(slot).append(':').append(describe(candidate))
                    .append(" piece=").append(candidatePiece).append(" ench=")
                    .append(candidate.isItemEnchanted()).append(" armor=")
                    .append(Inventory.armorValue(candidate)).append(" remaining=")
                    .append(durabilityPercent(candidate)).append("% reason=").append(rejection);
            if (candidatePiece != piece || isBroken(candidate)) {
                continue;
            }
            if (ignoreUnenchanted.getAsBoolean() && !candidate.isItemEnchanted()) {
                continue;
            }
            boolean emergencyReplacement = worn == null || isBroken(worn);
            if (emergencyReplacement && !useDamagedReserves.getAsBoolean()
                    && durabilityPercent(candidate) < 100) {
                continue;
            }
            Choice choice = new Choice(slot, candidate.copy(), score(candidate), isPreferred(candidate, preference));
            if (preventive && !isPreventiveUpgrade(candidate, worn)) {
                // At the preventive threshold, do not leave a piece on until it breaks just
                // because the only fresh reserve has fewer base armor points (e.g. iron
                // leggings replacing nearly-broken diamond leggings). Preserve durability;
                // prefer the strict upgrade when one exists, otherwise keep this fallback.
                if (durabilityPercent(candidate) > durabilityPercent(worn)
                        && (preventiveFallback == null || compare(choice, preventiveFallback) > 0)) {
                    preventiveFallback = choice;
                }
                continue;
            }
            if (best == null || compare(choice, best) > 0) {
                best = choice;
            }
        }
        if (best != null || preventiveFallback != null || lastNoChoiceLog[piece] == 0L
                || tick - lastNoChoiceLog[piece] >= 100L) {
            log("scan reservas " + pieceName(piece) + "; worn=" + describe(worn) + "; preventive="
                    + preventive + "; slots=" + (inventoryScan.length() == 0
                            ? "nenhuma armadura nos slots 0-35" : inventoryScan.toString()));
        }
        return best != null ? best : preventiveFallback;
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

    /**
     * The previously worn piece may break on the server while the preventive swap is in flight.
     * In that case it disappears instead of returning to the source slot, but the new piece can
     * still have been equipped successfully.
     */
    private static boolean matchesEquipState(ItemStack source, ItemStack armor, Pending operation) {
        if (!matchesReplacementArmor(armor, operation)) {
            return false;
        }
        return matchesFormerArmor(source, operation)
                || operation.oldArmor != null && source == null;
    }

    private static boolean matchesReplacementArmor(ItemStack actual, Pending operation) {
        if (!matchesArmor(actual, operation.expectedArmor)) {
            return false;
        }
        return !sameKindDifferentDamage(operation.oldArmor, operation.expectedArmor)
                || ArmorSwapSafety.closerDamage(actual.getItemDamage(),
                        operation.expectedArmor.getItemDamage(), operation.oldArmor.getItemDamage());
    }

    private static boolean matchesFormerArmor(ItemStack actual, Pending operation) {
        if (!matchesArmor(actual, operation.oldArmor)) {
            return false;
        }
        return actual == null || !sameKindDifferentDamage(operation.oldArmor, operation.expectedArmor)
                || ArmorSwapSafety.closerDamage(actual.getItemDamage(),
                        operation.oldArmor.getItemDamage(), operation.expectedArmor.getItemDamage());
    }

    private static boolean matchesOldOrObservedCursor(ItemStack actual, Pending operation) {
        return matchesFormerArmor(actual, operation)
                || operation.serverOldCursor != null && !operation.manualIntervened
                && matchesObservedCursor(actual, operation.serverOldCursor);
    }

    /** O servidor pode atualizar NBT/durabilidade da peça velha durante o combate. */
    private static boolean matchesOldIdentityAndDamage(ItemStack actual, Pending operation) {
        if (actual == null || operation.oldArmor == null
                || actual.getItem() != operation.oldArmor.getItem()
                || actual.stackSize != operation.oldArmor.stackSize
                || Inventory.armorPiece(actual) != operation.piece) {
            return false;
        }
        return !sameItemDifferentDamage(operation.oldArmor, operation.expectedArmor)
                || ArmorSwapSafety.closerDamage(actual.getItemDamage(),
                        operation.oldArmor.getItemDamage(), operation.expectedArmor.getItemDamage());
    }

    private static boolean matchesObservedCursor(ItemStack actual, ItemStack observed) {
        return actual != null && observed != null && actual.getItem() == observed.getItem()
                && actual.stackSize == observed.stackSize
                && actual.getItemDamage() == observed.getItemDamage();
    }

    private static boolean sameItemDifferentDamage(ItemStack oldArmor, ItemStack replacement) {
        return oldArmor != null && replacement != null && oldArmor.getItem() == replacement.getItem()
                && Math.abs((long) oldArmor.getItemDamage() - replacement.getItemDamage())
                        >= MIN_DISTINGUISHABLE_DAMAGE_GAP;
    }

    private static boolean sameKindDifferentDamage(ItemStack oldArmor, ItemStack replacement) {
        return oldArmor != null && replacement != null && matchesArmor(oldArmor, replacement)
                && Math.abs((long) oldArmor.getItemDamage() - replacement.getItemDamage())
                        >= MIN_DISTINGUISHABLE_DAMAGE_GAP;
    }

    /**
     * The equipped stack can lose durability while PvP is ongoing, even before the server's
     * slot acknowledgement arrives. Match its identity and NBT, not its mutable damage value.
     */
    private static boolean matchesArmor(ItemStack actual, ItemStack expected) {
        if (actual == null || expected == null) {
            return actual == expected;
        }
        if (actual.getItem() != expected.getItem() || actual.stackSize != expected.stackSize) {
            return false;
        }
        NBTTagCompound actualTag = actual.getTagCompound();
        NBTTagCompound expectedTag = expected.getTagCompound();
        return actualTag == null ? expectedTag == null : actualTag.equals(expectedTag);
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

    private static String describe(ItemStack stack) {
        if (stack == null) {
            return "vazio";
        }
        int maximum = stack.getMaxDamage();
        String durability = maximum > 0
                ? (maximum - stack.getItemDamage()) + "/" + maximum
                : "indestrutível";
        return stack.getItem().getUnlocalizedName() + "[" + durability + ";meta=" + stack.getMetadata()
                + ";count=" + stack.stackSize + ";ench=" + stack.isItemEnchanted() + "]";
    }

    private static String pendingSummary(Pending operation) {
        return "piece=" + pieceName(operation.piece) + "; sourceIndex=" + operation.sourceIndex
                + "; old=" + describe(operation.oldArmor) + "; expectedSource="
                + describe(operation.expectedSource) + "; expectedArmor=" + describe(operation.expectedArmor)
                + "; age=" + operation.age + "; local=" + operation.localApplied
                + "; server=" + operation.serverConfirmed + "; sourceSeen=" + operation.sourceSeen
                + "; armorSeen=" + operation.armorSeen + "; preventive=" + operation.preventive
                + "; dropping=" + operation.dropping + "; rejected=" + operation.rejected;
    }

    private static void logConfirmation(String source, Pending operation) {
        String detail = operation.source == null && operation.oldArmor != null
                ? "; origem vazia, verificando cursor" : "";
        log(source + " confirmou " + pieceName(operation.piece) + detail
                + "; reserve=" + describe(operation.armor));
    }

    /** Transitions only (no per-tick spam); visible in .minecraft/logs/latest.log. */
    private static void log(String message) {
        DebugLog.write("AutoArmor", message);
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
        final boolean preventive;
        ItemStack source;
        ItemStack armor;
        boolean sourceSeen;
        boolean armorSeen;
        boolean localApplied;
        boolean serverConfirmed;
        boolean rejected;
        boolean dropping;
        boolean cursorTimeoutLogged;
        boolean cursorRecoveryWarned;
        boolean manualPauseLogged;
        boolean cursorClearSeen;
        boolean cursorReturnSlotSeen;
        boolean manualIntervened;
        boolean cursorDropIssued;
        ItemStack serverOldCursor;
        int cursorReturnIndex = -1;
        int cursorRecoveryAge;
        int cursorReturnAttempts;
        int cursorNoSlotAge;
        int cursorDropAge;
        int age;

        Pending(int piece, int sourceIndex, ItemStack replacement, ItemStack oldArmor, boolean dropOld) {
            this.piece = piece;
            this.sourceIndex = sourceIndex;
            this.replacement = replacement.copy();
            this.oldArmor = copy(oldArmor);
            this.expectedSource = copy(oldArmor);
            this.expectedArmor = replacement.copy();
            this.dropOld = dropOld;
            this.preventive = oldArmor != null && !isBroken(oldArmor);
        }
    }
}
