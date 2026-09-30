package com.yny.utils.modules.player;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

import com.yny.utils.core.DebugLog;

import dev.xavier.stein.loader.api.Inventory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

/** Uses only normal hotbar selection and the vanilla right-click item-use path. */
public final class AutoConsumables {
    private static final int MODE_OFF = 0;
    private static final int MODE_ECONOMIC = 1;
    private static final int MODE_HARD = 2;
    private static final long TICKS_PER_SECOND = 20L;
    private static final long MIN_USE_TICKS = 32L;
    private static final long MAX_USE_TICKS = 48L;
    private static final long FAILED_USE_RETRY_TICKS = 40L;
    private static final int MAX_USE_RESTARTS = 2;
    private static final long POTION_CONFIRM_TICKS = 60L;
    private static final long SERVER_EFFECT_CONFIRM_TICKS = 40L;
    private static final int CAPIRA_REGEN_AMPLIFIER = 4;
    private static final int CAPIRA_REGEN_MIN_TICKS = 60;

    private final BooleanSupplier enabled;
    private final IntSupplier appleMode;
    private final IntSupplier applePreference;
    private final IntSupplier potionMode;
    private final BooleanSupplier strengthEnabled;
    private final BooleanSupplier speedEnabled;
    private final IntSupplier potionRefreshSeconds;
    private final IntSupplier combatSeconds;
    private final IntSupplier appleCooldownSeconds;
    private final BooleanSupplier diagnostics;
    private final java.util.function.Consumer<String> status;
    private final String[] lastDecision = new String[3];
    private final long[] lastDecisionAt = new long[3];
    private long tick;
    private long combatUntil;
    private long nextAppleTick;
    private final long[] nextFailedUseRetryTick = new long[3];
    private long strengthConfirmUntil;
    private long speedConfirmUntil;
    private int strengthDurationBeforeUse;
    private int speedDurationBeforeUse;
    private boolean healthLossPending;
    private PendingUse pendingUse;
    private PendingConfirmation pendingConfirmation;

    public AutoConsumables(BooleanSupplier enabled, IntSupplier appleMode, IntSupplier applePreference,
            IntSupplier potionMode, BooleanSupplier strengthEnabled, BooleanSupplier speedEnabled,
            IntSupplier potionRefreshSeconds, IntSupplier combatSeconds, IntSupplier appleCooldownSeconds,
            BooleanSupplier diagnostics, java.util.function.Consumer<String> status) {
        this.enabled = enabled;
        this.appleMode = appleMode;
        this.applePreference = applePreference;
        this.potionMode = potionMode;
        this.strengthEnabled = strengthEnabled;
        this.speedEnabled = speedEnabled;
        this.potionRefreshSeconds = potionRefreshSeconds;
        this.combatSeconds = combatSeconds;
        this.appleCooldownSeconds = appleCooldownSeconds;
        this.diagnostics = diagnostics;
        this.status = status;
    }

    public void onAttack(Object target) {
        if (target instanceof EntityPlayer && target != Minecraft.getMinecraft().thePlayer) {
            markCombat();
            trace(0, "combat-attack", "alvo=" + ((EntityPlayer) target).getName());
        }
    }

    public void onHurt() {
        markCombat();
        trace(0, "combat-hurt", "hurt recebido");
    }

    public void onHealthChanged(float oldHealth, float newHealth) {
        if (newHealth < oldHealth) {
            healthLossPending = true;
            markCombat();
            trace(0, "health-loss", "vida=" + oldHealth + "->" + newHealth);
        }
    }

    public void resetSession() {
        cancelPendingUse(true);
        pendingConfirmation = null;
        combatUntil = 0L;
        nextAppleTick = 0L;
        for (int index = 0; index < nextFailedUseRetryTick.length; index++) {
            nextFailedUseRetryTick[index] = 0L;
        }
        strengthConfirmUntil = 0L;
        speedConfirmUntil = 0L;
        healthLossPending = false;
        for (int index = 0; index < lastDecision.length; index++) {
            lastDecision[index] = null;
            lastDecisionAt[index] = 0L;
        }
    }

    public void onTickEnd() {
        tick++;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        if (pendingConfirmation != null) {
            checkServerConfirmation(player);
            return;
        }
        if (pendingUse != null) {
            String abort = pendingAbortReason(mc, player);
            if (abort != null) {
                log("uso cancelado: " + abort);
                cancelPendingUse(true);
                return;
            }
            // Minecraft.runTick solta o item se a tecla de uso não estiver pressionada.
            mc.gameSettings.keyBindUseItem.pressed = true;
            finishOrContinueUse(player);
            return;
        }
        String blocked = guardReason(mc, player);
        if (blocked != null) {
            trace(0, "blocked-" + blocked, "ativo=" + enabled.getAsBoolean()
                    + "; healthLossPending=" + healthLossPending
                    + "; held=" + (player == null ? "sem-jogador" : describe(player.getHeldItem())));
            trace(1, "blocked-" + blocked, "auto pot pausado");
            trace(2, "blocked-" + blocked, "auto pot pausado");
            return;
        }

        boolean inCombat = tick < combatUntil;
        int appleSetting = clamp(appleMode.getAsInt(), 0, 2);
        boolean appleDue = appleSetting == MODE_HARD ? inCombat
                : appleSetting == MODE_ECONOMIC && healthLossPending && player.getHealth() < player.getMaxHealth();
        if (!appleDue) {
            trace(0, "not-due", "mode=" + appleSetting
                    + "; combat=" + inCombat + "; healthLossPending=" + healthLossPending
                    + "; health=" + player.getHealth() + "/" + player.getMaxHealth()
                    + "; cooldown=" + Math.max(0L, nextAppleTick - tick));
        }
        if (player.getHealth() >= player.getMaxHealth()) {
            healthLossPending = false;
        }

        // Highest priority by design: don't waste a potion use window when an apple is due.
        if (appleDue && tick < nextFailedUseRetryTick[0]) {
            trace(0, "retry-wait", "restam=" + (nextFailedUseRetryTick[0] - tick) + " ticks");
        } else if (appleDue && tick >= nextAppleTick) {
            int appleSlot = findGoldenApple();
            if (appleSlot < 0) {
                trace(0, "no-hotbar-apple", "preferencia=" + applePreference.getAsInt()
                        + "; hotbar=" + hotbarSummary());
            }
            if (appleSlot >= 0 && isEnchantedApple(appleSlot) && hasCapiraEffect(player)) {
                // Match the macro's capira guard: Regen V (effect 10, amplifier 4+) with >3s left.
                trace(0, "capira-effect-active", "slot=" + appleSlot);
                healthLossPending = false;
                appleSlot = -1;
            }
            if (appleSlot >= 0 && beginUse(appleSlot, player)) {
                trace(0, "use-started", "slot=" + appleSlot);
                status.accept("Auto Consumíveis: maçã dourada");
                return;
            }
        } else if (appleDue) {
            trace(0, "apple-cooldown", "restam=" + (nextAppleTick - tick) + " ticks");
        }

        int potionSetting = clamp(potionMode.getAsInt(), 0, 2);
        if (potionSetting == MODE_OFF) {
            trace(1, "mode-off", "potions OFF");
            trace(2, "mode-off", "potions OFF");
            return;
        }
        boolean hardTrigger = potionSetting == MODE_HARD && inCombat;
        int refreshTicks = (int) seconds(potionRefreshSeconds.getAsInt());
        int potionEffectId = Potion.damageBoost.getId();
        int potionSlot = -1;
        if (tick >= nextFailedUseRetryTick[1]) {
            potionSlot = findPotion(player, Potion.damageBoost, strengthEnabled.getAsBoolean(), hardTrigger,
                    refreshTicks);
        } else {
            trace(1, "retry-wait", "restam=" + (nextFailedUseRetryTick[1] - tick) + " ticks");
        }
        if (potionSlot < 0) {
            potionEffectId = Potion.moveSpeed.getId();
            if (tick >= nextFailedUseRetryTick[2]) {
                potionSlot = findPotion(player, Potion.moveSpeed, speedEnabled.getAsBoolean(), hardTrigger,
                        refreshTicks);
            } else {
                trace(2, "retry-wait", "restam=" + (nextFailedUseRetryTick[2] - tick) + " ticks");
            }
        } else {
            trace(2, "deferred-strength-priority", "forca slot=" + potionSlot);
        }
        if (potionSlot >= 0 && beginUse(potionSlot, player, potionEffectId)) {
            trace(potionEffectId == Potion.damageBoost.getId() ? 1 : 2,
                    "use-started", "slot=" + potionSlot);
            status.accept("Auto Consumíveis: poção");
        }
    }

    private void markCombat() {
        int duration = clamp(combatSeconds.getAsInt(), 1, 15);
        combatUntil = tick + duration * TICKS_PER_SECOND;
    }

    private String guardReason(Minecraft mc, EntityPlayer player) {
        if (!enabled.getAsBoolean()) return "module-off";
        if (player == null || mc.theWorld == null) return "no-world";
        if (!player.isEntityAlive()) return "dead";
        if (mc.currentScreen != null) return "screen-" + mc.currentScreen.getClass().getSimpleName();
        if (mc.playerController == null) return "no-controller";
        if (mc.playerController.isSpectator()) return "spectator";
        if (mc.playerController.isInCreativeMode()) return "creative";
        if (Inventory.isContainerOpen()) return "container-open";
        if (Inventory.windowId() != 0) return "window-" + Inventory.windowId();
        if (Inventory.cursor() != null) return "cursor-occupied";
        if (player.isUsingItem()) return "manual-item-use";
        return null;
    }

    private String pendingAbortReason(Minecraft mc, EntityPlayer player) {
        if (!enabled.getAsBoolean()) return "module-off";
        if (player == null || mc.theWorld == null) return "no-world";
        if (!player.isEntityAlive()) return "dead";
        if (mc.currentScreen != null) return "screen-open";
        if (mc.playerController == null) return "no-controller";
        if (Inventory.isContainerOpen() || Inventory.windowId() != 0) return "container-open";
        if (Inventory.cursor() != null) return "cursor-occupied";
        return null;
    }

    private String hotbarSummary() {
        StringBuilder summary = new StringBuilder();
        for (int slot = 0; slot < 9; slot++) {
            if (slot > 0) summary.append(',');
            summary.append(slot).append(':').append(describe(hotbarStack(slot)));
        }
        return summary.toString();
    }

    private void trace(int kind, String reason, String detail) {
        if (!diagnostics.getAsBoolean()) {
            return;
        }
        if (!reason.equals(lastDecision[kind]) || tick - lastDecisionAt[kind] >= TICKS_PER_SECOND) {
            lastDecision[kind] = reason;
            lastDecisionAt[kind] = tick;
            DebugLog.write(kind == 0 ? "AutoCapira" : kind == 1 ? "AutoPotForca" : "AutoPotVelo",
                    "tick=" + tick + "; reason=" + reason + "; " + detail);
        }
    }

    private boolean beginUse(int hotbarSlot, EntityPlayer player) {
        return beginUse(hotbarSlot, player, -1);
    }

    private boolean beginUse(int hotbarSlot, EntityPlayer player, int potionEffectId) {
        int previousSlot = Inventory.selectedHotbar();
        if (previousSlot < 0 || previousSlot > 8) {
            previousSlot = hotbarSlot;
        }
        Inventory.selectHotbar(hotbarSlot);
        // O servidor precisa receber C09 (slot selecionado) antes do C08 de uso.
        // updateController faz a sincronização vanilla imediatamente; sem isso,
        // o uso local começa, mas o servidor ainda pode estar vendo a espada.
        mc().playerController.updateController();
        ItemStack held = player.getHeldItem();
        if (held != null && isConsumable(held)) {
            int startingCount = held.stackSize;
            boolean changed = mc().playerController.sendUseItem(player, mc().theWorld, held);
            boolean using = player.isUsingItem();
            // sendUseItem returns false for eat/drink actions in 1.8.9 when the same stack is
            // retained, even though the use packet was sent. Hold one complete vanilla use
            // window instead of restoring immediately and oscillating back to the sword.
            int effectDuration = 0;
            if (potionEffectId >= 0) {
                Potion wanted = potionEffectId == Potion.damageBoost.getId() ? Potion.damageBoost
                        : potionEffectId == Potion.moveSpeed.getId() ? Potion.moveSpeed : null;
                PotionEffect active = wanted == null ? null : player.getActivePotionEffect(wanted);
                effectDuration = active == null ? 0 : active.getDuration();
            }
            pendingUse = new PendingUse(previousSlot, hotbarSlot, tick, using, held.getItem(), held.getMetadata(),
                    startingCount, potionEffectId, effectDuration,
                    effectDuration(player, Potion.regeneration), effectDuration(player, Potion.absorption));
            mc().gameSettings.keyBindUseItem.pressed = true;
            log("uso iniciado: slot=" + hotbarSlot + ", item=" + held.getItem().getUnlocalizedName()
                    + ", count=" + startingCount + ", localUsing=" + using
                    + ", effectId=" + potionEffectId + ", previousSlot=" + previousSlot
                    + ", controllerChanged=" + changed + ", useKeyHeld=true");
            return true;
        }
        log("uso não enviado: slot=" + hotbarSlot + ", held="
                + (held == null ? "vazio" : held.getItem().getUnlocalizedName()));
        if (Inventory.selectedHotbar() == hotbarSlot) {
            Inventory.selectHotbar(previousSlot);
        }
        return false;
    }

    private void finishOrContinueUse(EntityPlayer player) {
        if (player == null) {
            pendingUse = null;
            return;
        }
        if (tick <= pendingUse.startedAt) {
            return;
        }
        boolean using = player.isUsingItem();
        int selected = Inventory.selectedHotbar();
        ItemStack current = hotbarStack(pendingUse.usedSlot);
        boolean itemDecreased = itemConsumed(pendingUse, current);
        boolean effectConfirmed = effectApplied(player, pendingUse);
        boolean consumed = itemDecreased || effectConfirmed;
        long age = tick - pendingUse.startedAt;
        if (diagnostics.getAsBoolean() && age % 5L == 0L) {
            log("uso andamento: tick=" + tick + ", age=" + age
                    + ", selected=" + selected + ", expectedSlot=" + pendingUse.usedSlot
                    + ", using=" + using + ", sawUsing=" + pendingUse.sawUsing
                    + ", useKeyPressed=" + mc().gameSettings.keyBindUseItem.pressed
                    + ", physicalUse=" + GameSettings.isKeyDown(mc().gameSettings.keyBindUseItem)
                    + ", consumedOrEffect=" + consumed + ", item=" + describe(current));
        }
        if (consumed) {
            // Alguns servidores reduzem a pilha antes de o cliente encerrar a
            // animação. Continuar segurando USE inicia uma segunda maçã/poção.
            if (using) {
                mc().playerController.onStoppedUsingItem(player);
            }
            finishUse(true, player, current, effectConfirmed
                    ? "efeito confirmado" : "pilha diminuiu; aguardando efeito do servidor");
            return;
        }
        if (selected != pendingUse.usedSlot) {
            // Uma troca manual de slot tem prioridade sobre a automação.
            log("uso interrompido: slot mudou de " + pendingUse.usedSlot + " para " + selected);
            cancelPendingUse(false);
            return;
        }
        if (player.isUsingItem()) {
            pendingUse.sawUsing = true;
            if (age < MAX_USE_TICKS) {
                return;
            }
            mc().playerController.onStoppedUsingItem(player);
            finishUse(false, player, current, "animação excedeu o limite de 48 ticks");
            return;
        }
        if (age < MIN_USE_TICKS) {
            restartUse(player, "animação terminou cedo sem consumir");
        } else {
            finishUse(false, player, current, "32 ticks passaram sem item/efeito confirmado");
        }
    }

    private void restartUse(EntityPlayer player, String reason) {
        if (pendingUse.restarts >= MAX_USE_RESTARTS) {
            finishUse(false, player, hotbarStack(pendingUse.usedSlot), reason + "; limite de reinícios");
            return;
        }
        ItemStack held = player.getHeldItem();
        if (held == null || held.getItem() != pendingUse.item || held.getMetadata() != pendingUse.metadata) {
            finishUse(false, player, held, reason + "; item mudou");
            return;
        }
        boolean changed = mc().playerController.sendUseItem(player, mc().theWorld, held);
        pendingUse.restarts++;
        pendingUse.startedAt = tick;
        pendingUse.sawUsing = player.isUsingItem();
        log("uso reiniciado: motivo=" + reason + ", tentativa=" + pendingUse.restarts
                + ", localUsing=" + pendingUse.sawUsing + ", controllerChanged=" + changed);
    }

    private void finishUse(boolean success, EntityPlayer player, ItemStack current, String reason) {
        PendingUse finished = pendingUse;
        boolean effectConfirmed = success && effectApplied(player, finished);
        log("uso " + (effectConfirmed ? "confirmado" : success ? "observado" : "falhou") + ": motivo=" + reason
                + ", item=" + finished.itemName + ", metadata=" + finished.metadata
                + ", initialCount=" + finished.startingCount + ", current=" + describe(current)
                + ", elapsed=" + (tick - finished.firstStartedAt)
                + ", lastAttemptElapsed=" + (tick - finished.startedAt)
                + ", sawUsing=" + finished.sawUsing);
        if (success) {
            if (effectConfirmed) {
                confirmUse(finished);
            } else {
                pendingConfirmation = new PendingConfirmation(finished, tick + SERVER_EFFECT_CONFIRM_TICKS);
                log("aguardando confirmação do servidor: item=" + finished.itemName
                        + ", slot=" + finished.usedSlot + ", prazo=" + SERVER_EFFECT_CONFIRM_TICKS + " ticks");
            }
        } else {
            nextFailedUseRetryTick[finished.kind] = tick + FAILED_USE_RETRY_TICKS;
            if (finished.item == Items.golden_apple && player.getHealth() < player.getMaxHealth()) {
                healthLossPending = true;
            }
        }
        restoreUseKey();
        if (Inventory.selectedHotbar() == finished.usedSlot) {
            Inventory.selectHotbar(finished.previousSlot);
        }
        pendingUse = null;
    }

    private static ItemStack hotbarStack(int slot) {
        Object value = Inventory.stack(Inventory.HOTBAR_START + slot);
        return value instanceof ItemStack ? (ItemStack) value : null;
    }

    private static boolean itemConsumed(PendingUse use, ItemStack current) {
        return current == null || current.getItem() != use.item || current.getMetadata() != use.metadata
                || current.stackSize < use.startingCount;
    }

    private static boolean effectApplied(EntityPlayer player, PendingUse use) {
        if (use.item == Items.golden_apple) {
            return effectDuration(player, Potion.regeneration) > use.regenerationDurationBeforeUse + 5
                    || effectDuration(player, Potion.absorption) > use.absorptionDurationBeforeUse + 5;
        }
        Potion wanted = use.potionEffectId == Potion.damageBoost.getId() ? Potion.damageBoost
                : use.potionEffectId == Potion.moveSpeed.getId() ? Potion.moveSpeed : null;
        PotionEffect active = wanted == null ? null : player.getActivePotionEffect(wanted);
        return active != null && active.getDuration() > use.effectDurationBeforeUse + 5;
    }

    private static int effectDuration(EntityPlayer player, Potion effect) {
        PotionEffect active = player.getActivePotionEffect(effect);
        return active == null ? 0 : active.getDuration();
    }

    private void checkServerConfirmation(EntityPlayer player) {
        PendingConfirmation confirmation = pendingConfirmation;
        if (player == null || !player.isEntityAlive() || mc().theWorld == null) {
            pendingConfirmation = null;
            return;
        }
        if (effectApplied(player, confirmation.use)) {
            log("efeito confirmado pelo servidor: item=" + confirmation.use.itemName
                    + ", espera=" + (tick - confirmation.use.firstStartedAt) + " ticks");
            confirmUse(confirmation.use);
            pendingConfirmation = null;
        } else if (tick >= confirmation.deadline) {
            ItemStack current = hotbarStack(confirmation.use.usedSlot);
            log("sem confirmação de efeito do servidor: item=" + confirmation.use.itemName
                    + ", pilha=" + describe(current) + "; nova tentativa após cooldown");
            nextFailedUseRetryTick[confirmation.use.kind] = tick + FAILED_USE_RETRY_TICKS;
            pendingConfirmation = null;
        }
    }

    private void confirmUse(PendingUse use) {
        registerPotionConfirmation(use);
        if (use.item == Items.golden_apple) {
            nextAppleTick = tick + seconds(appleCooldownSeconds.getAsInt());
            healthLossPending = false;
        }
    }

    private static String describe(ItemStack stack) {
        return stack == null ? "vazio" : stack.getItem().getUnlocalizedName() + "[meta="
                + stack.getMetadata() + ",count=" + stack.stackSize + "]";
    }

    private void cancelPendingUse(boolean restoreSlot) {
        if (pendingUse == null) {
            return;
        }
        log("uso cancelado: slot=" + pendingUse.usedSlot
                + ", restoreSlot=" + restoreSlot);
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        if (player != null && player.isUsingItem()
                && Inventory.selectedHotbar() == pendingUse.usedSlot && mc.theWorld != null
                && mc.playerController != null) {
            mc.playerController.onStoppedUsingItem(player);
        }
        restoreUseKey();
        if (restoreSlot && Inventory.selectedHotbar() == pendingUse.usedSlot) {
            Inventory.selectHotbar(pendingUse.previousSlot);
        }
        nextFailedUseRetryTick[pendingUse.kind] = tick + FAILED_USE_RETRY_TICKS;
        pendingUse = null;
    }

    private static void restoreUseKey() {
        Minecraft mc = Minecraft.getMinecraft();
        mc.gameSettings.keyBindUseItem.pressed = GameSettings.isKeyDown(mc.gameSettings.keyBindUseItem);
    }

    private int findGoldenApple() {
        int preference = clamp(applePreference.getAsInt(), 0, 2);
        int enchanted = -1;
        int regular = -1;
        for (int slot = 0; slot < 9; slot++) {
            Object value = Inventory.stack(Inventory.HOTBAR_START + slot);
            ItemStack stack = value instanceof ItemStack ? (ItemStack) value : null;
            if (stack == null || stack.getItem() != Items.golden_apple) {
                continue;
            }
            if (stack.getMetadata() == 1) {
                if (enchanted < 0) enchanted = slot;
            } else if (regular < 0) {
                regular = slot;
            }
        }
        if (preference == 1) return enchanted;
        if (preference == 2) return regular;
        return enchanted >= 0 ? enchanted : regular;
    }

    private static boolean isConsumable(ItemStack stack) {
        return stack.getItem() == Items.golden_apple || stack.getItem() instanceof ItemPotion;
    }

    private boolean isEnchantedApple(int hotbarSlot) {
        Object value = Inventory.stack(Inventory.HOTBAR_START + hotbarSlot);
        return value instanceof ItemStack && ((ItemStack) value).getItem() == Items.golden_apple
                && ((ItemStack) value).getMetadata() == 1;
    }

    private static boolean hasCapiraEffect(EntityPlayer player) {
        PotionEffect regeneration = player.getActivePotionEffect(Potion.regeneration);
        return regeneration != null && regeneration.getAmplifier() >= CAPIRA_REGEN_AMPLIFIER
                && regeneration.getDuration() > CAPIRA_REGEN_MIN_TICKS;
    }

    private int findPotion(EntityPlayer player, Potion wanted, boolean allowed, boolean hardTrigger,
            int refreshTicks) {
        int kind = wanted == Potion.damageBoost ? 1 : 2;
        if (!allowed) {
            trace(kind, "effect-disabled", "efeito=" + wanted.getName());
            return -1;
        }
        PotionEffect active = player.getActivePotionEffect(wanted);
        int remaining = active == null ? 0 : active.getDuration();
        if (awaitingPotionEffect(wanted, remaining)) {
            trace(kind, "awaiting-confirmation", "remaining=" + remaining);
            return -1;
        }
        // Economic mode waits for expiration. Hard mode can refresh near expiration during PvP,
        // but neither mode replaces an effect with meaningful duration remaining.
        if (remaining > 0 && (!hardTrigger || remaining > refreshTicks)) {
            trace(kind, "effect-active", "remaining=" + remaining + "; hard=" + hardTrigger
                    + "; refreshAt=" + refreshTicks);
            return -1;
        }
        int drinkable = 0;
        int splash = 0;
        int noEffects = 0;
        for (int slot = 0; slot < 9; slot++) {
            Object value = Inventory.stack(Inventory.HOTBAR_START + slot);
            ItemStack stack = value instanceof ItemStack ? (ItemStack) value : null;
            if (stack == null || !(stack.getItem() instanceof ItemPotion)) {
                continue;
            }
            if (ItemPotion.isSplash(stack.getMetadata())) {
                splash++;
                continue;
            }
            drinkable++;
            List effects = ((ItemPotion) stack.getItem()).getEffects(stack);
            if (effects == null) {
                noEffects++;
                continue;
            }
            for (Object effectValue : effects) {
                if (effectValue instanceof PotionEffect
                        && ((PotionEffect) effectValue).getPotionID() == wanted.getId()) {
                    trace(kind, "ready-slot", "slot=" + slot + "; remaining=" + remaining
                            + "; hotbar=" + hotbarSummary());
                    return slot;
                }
            }
        }
        trace(kind, "no-matching-hotbar-potion", "remaining=" + remaining + "; drinkable="
                + drinkable + "; splashIgnored=" + splash + "; noEffects=" + noEffects
                + "; hotbar=" + hotbarSummary());
        return -1;
    }

    private boolean awaitingPotionEffect(Potion wanted, int remaining) {
        int id = wanted.getId();
        long until = id == Potion.damageBoost.getId() ? strengthConfirmUntil : speedConfirmUntil;
        int before = id == Potion.damageBoost.getId() ? strengthDurationBeforeUse : speedDurationBeforeUse;
        if (until <= tick) {
            if (id == Potion.damageBoost.getId()) strengthConfirmUntil = 0L;
            else speedConfirmUntil = 0L;
            return false;
        }
        // A refreshed effect should jump above the duration observed when we started.
        // Keep a short grace period for server-side effect packets and avoid consuming
        // another potion while the first one is still being acknowledged.
        if (remaining > before + 5) {
            if (id == Potion.damageBoost.getId()) strengthConfirmUntil = 0L;
            else speedConfirmUntil = 0L;
            return false;
        }
        return true;
    }

    private void registerPotionConfirmation(PendingUse use) {
        if (use.potionEffectId == Potion.damageBoost.getId()) {
            strengthConfirmUntil = tick + POTION_CONFIRM_TICKS;
            strengthDurationBeforeUse = use.effectDurationBeforeUse;
        } else if (use.potionEffectId == Potion.moveSpeed.getId()) {
            speedConfirmUntil = tick + POTION_CONFIRM_TICKS;
            speedDurationBeforeUse = use.effectDurationBeforeUse;
        }
    }

    private static long seconds(int value) {
        return clamp(value, 1, 30) * TICKS_PER_SECOND;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Minecraft mc() {
        return Minecraft.getMinecraft();
    }

    private static void log(String message) {
        DebugLog.write("AutoConsumables", message);
    }

    private static final class PendingUse {
        final int previousSlot;
        final int usedSlot;
        final long firstStartedAt;
        long startedAt;
        final String itemName;
        final Item item;
        final int metadata;
        final int startingCount;
        final int potionEffectId;
        final int kind;
        final int effectDurationBeforeUse;
        final int regenerationDurationBeforeUse;
        final int absorptionDurationBeforeUse;
        boolean sawUsing;
        int restarts;
        PendingUse(int previousSlot, int usedSlot, long startedAt, boolean alreadyUsing, Item item, int metadata,
                int startingCount, int potionEffectId, int effectDurationBeforeUse,
                int regenerationDurationBeforeUse, int absorptionDurationBeforeUse) {
            this.previousSlot = previousSlot;
            this.usedSlot = usedSlot;
            this.firstStartedAt = startedAt;
            this.startedAt = startedAt;
            this.sawUsing = alreadyUsing;
            this.itemName = item.getUnlocalizedName();
            this.item = item;
            this.metadata = metadata;
            this.startingCount = startingCount;
            this.potionEffectId = potionEffectId;
            this.kind = potionEffectId == Potion.damageBoost.getId() ? 1
                    : potionEffectId == Potion.moveSpeed.getId() ? 2 : 0;
            this.effectDurationBeforeUse = effectDurationBeforeUse;
            this.regenerationDurationBeforeUse = regenerationDurationBeforeUse;
            this.absorptionDurationBeforeUse = absorptionDurationBeforeUse;
        }
    }

    private static final class PendingConfirmation {
        final PendingUse use;
        final long deadline;

        PendingConfirmation(PendingUse use, long deadline) {
            this.use = use;
            this.deadline = deadline;
        }
    }
}
