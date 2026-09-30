package com.yny.utils.modules.pvp;

import java.util.function.BooleanSupplier;
import java.util.concurrent.atomic.AtomicLong;

import com.yny.utils.core.DebugLog;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;

/** Pulso opcional da tecla de pulo, sempre decidido na thread do jogo. */
final class KnockbackJumpReset {
    private static final long IMPULSE_WINDOW_NS = 350_000_000L;
    private static final long COOLDOWN_NS = 600_000_000L;

    private final BooleanSupplier enabled;
    private final BooleanSupplier diagnostics;
    private final AtomicLong pendingImpulseAt = new AtomicLong();
    private long lastJumpAt;
    private int pulseTicksRemaining;

    KnockbackJumpReset(BooleanSupplier enabled, BooleanSupplier diagnostics) {
        this.enabled = enabled;
        this.diagnostics = diagnostics;
    }

    /** Chamado pela thread de rede apenas para o S12 do jogador local. */
    void onVelocity(int x, int y, int z) {
        if (enabled.getAsBoolean() && y > 0 && (x != 0 || z != 0)) {
            pendingImpulseAt.set(System.nanoTime());
        }
    }

    /** Sinal de dano do SDK: pode chegar antes do tick que torna o jogador aéreo. */
    void onLocalHurt() {
        tryJump("hurt-event", true);
    }

    /** Executado na thread principal logo depois do handler vanilla do S12. */
    void onVelocityApplied() {
        // O próprio S12 positivo é o sinal de impulso; esperar hurtTime pode
        // perder o último tick no chão antes de o movimento ser atualizado.
        tryJump("post-S12", true);
    }

    void onTickEnd() {
        Minecraft mc = Minecraft.getMinecraft();
        if (pulseTicksRemaining > 0 && --pulseTicksRemaining == 0) {
            mc.gameSettings.keyBindJump.pressed = GameSettings.isKeyDown(mc.gameSettings.keyBindJump);
            if (mc.thePlayer != null) {
                log("JUMP_RESULT motionY=" + mc.thePlayer.motionY + "; ground="
                        + mc.thePlayer.onGround + "; hurtTime=" + mc.thePlayer.hurtTime);
            }
        }
        long pending = pendingImpulseAt.get();
        if (pending != 0L && System.nanoTime() - pending > IMPULSE_WINDOW_NS
                && pendingImpulseAt.compareAndSet(pending, 0L)) {
            log("JUMP_SKIPPED reason=expired-before-main-thread");
        }
    }

    private void tryJump(String source, boolean damageConfirmed) {
        Minecraft mc = Minecraft.getMinecraft();
        long pending = pendingImpulseAt.get();
        if (pending == 0L || !enabled.getAsBoolean() || mc.thePlayer == null
                || mc.theWorld == null || mc.currentScreen != null) {
            pendingImpulseAt.compareAndSet(pending, 0L);
            return;
        }
        long now = System.nanoTime();
        if (now - pending > IMPULSE_WINDOW_NS || now - lastJumpAt < COOLDOWN_NS) {
            pendingImpulseAt.compareAndSet(pending, 0L);
            log("JUMP_SKIPPED reason=" + (now - pending > IMPULSE_WINDOW_NS
                    ? "expired-or-no-hurt" : "cooldown")
                    + "; hurtTime=" + mc.thePlayer.hurtTime);
            return;
        }
        EntityPlayerSP player = mc.thePlayer;
        if (!player.onGround || !player.isEntityAlive()
                || player.isRiding() || player.isInWater() || player.isInLava()
                || player.isInWeb || player.isOnLadder() || player.isSneaking()
                || GameSettings.isKeyDown(mc.gameSettings.keyBindJump)) {
            // Não transformar um hit recebido já no ar em um salto adicional.
            pendingImpulseAt.compareAndSet(pending, 0L);
            log("JUMP_SKIPPED source=" + source + "; reason=state; ground=" + player.onGround
                    + "; sprint=" + player.isSprinting() + "; hurtTime=" + player.hurtTime
                    + "; manualJump=" + GameSettings.isKeyDown(mc.gameSettings.keyBindJump));
            return;
        }
        if (!damageConfirmed) {
            return;
        }
        if (!pendingImpulseAt.compareAndSet(pending, 0L)) {
            return;
        }
        lastJumpAt = now;
        mc.gameSettings.keyBindJump.pressed = true;
        pulseTicksRemaining = 2;
        log("JUMP_RESET source=" + source + "; inputPulse=1; ground=true; cooldownMs=600; hurtTime="
                + player.hurtTime);
    }

    void reset() {
        pendingImpulseAt.set(0L);
        lastJumpAt = 0L;
        if (pulseTicksRemaining > 0) {
            Minecraft mc = Minecraft.getMinecraft();
            mc.gameSettings.keyBindJump.pressed = GameSettings.isKeyDown(mc.gameSettings.keyBindJump);
            pulseTicksRemaining = 0;
        }
    }

    boolean jumpedSince(long sinceNanos) {
        return lastJumpAt >= sinceNanos;
    }

    private void log(String message) {
        if (diagnostics.getAsBoolean()) {
            DebugLog.write("Knockback", message);
        }
    }
}
