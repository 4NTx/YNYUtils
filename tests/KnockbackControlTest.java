package com.yny.utils.modules.pvp;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.network.play.server.S12PacketEntityVelocity;

/** Verificações da escala preservada para o modo original de velocity. */
public final class KnockbackControlTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        check(KnockbackControl.scale(8000, 100) == 8000, "100% mantém X/Y/Z vanilla");
        check(KnockbackControl.scale(-8000, 100) == -8000, "100% mantém sinal negativo");
        check(KnockbackControl.scale(8000, 93) == 7440, "93% reduz impulso positivo");
        check(KnockbackControl.scale(-8000, 93) == -7440, "93% reduz impulso negativo");
        check(KnockbackControl.scale(0, 93) == 0, "impulso nulo permanece nulo");

        Field localId = KnockbackControl.class.getDeclaredField("localPlayerId");
        localId.setAccessible(true);
        Method adjust = KnockbackControl.class.getDeclaredMethod("adjust", S12PacketEntityVelocity.class);
        adjust.setAccessible(true);
        KnockbackControl jumpOnly = new KnockbackControl(() -> false, () -> 93,
                () -> false, () -> true, () -> false);
        localId.setInt(jumpOnly, 42);
        S12PacketEntityVelocity vanilla = new S12PacketEntityVelocity(42, 0.4, 0.42, -0.2);
        int x = vanilla.motionX, y = vanilla.motionY, z = vanilla.motionZ;
        adjust.invoke(jumpOnly, vanilla);
        check(vanilla.motionX == x && vanilla.motionY == y && vanilla.motionZ == z,
                "Jump Reset independente não modifica o S12");
        Field jumpField = KnockbackControl.class.getDeclaredField("jumpReset");
        jumpField.setAccessible(true);
        Field pendingField = KnockbackJumpReset.class.getDeclaredField("pendingImpulseAt");
        pendingField.setAccessible(true);
        AtomicLong pending = (AtomicLong) pendingField.get(jumpField.get(jumpOnly));
        check(pending.get() != 0L, "Jump Reset recebe hit com redução desligada");

        S12PacketEntityVelocity otherPlayer = new S12PacketEntityVelocity(43, 0.4, 0.42, -0.2);
        int otherX = otherPlayer.motionX, otherY = otherPlayer.motionY, otherZ = otherPlayer.motionZ;
        check(adjust.invoke(jumpOnly, otherPlayer) == null,
                "S12 de outra entidade não cria amostra local");
        check(otherPlayer.motionX == otherX && otherPlayer.motionY == otherY
                && otherPlayer.motionZ == otherZ, "outras entidades ficam vanilla");

        KnockbackControl horizontalOnly = new KnockbackControl(() -> true, () -> 93,
                () -> true, () -> false, () -> false);
        localId.setInt(horizontalOnly, 42);
        S12PacketEntityVelocity horizontal = new S12PacketEntityVelocity(42, 0.4, 0.42, -0.2);
        int originalY = horizontal.motionY;
        adjust.invoke(horizontalOnly, horizontal);
        check(horizontal.motionX == KnockbackControl.scale(x, 93)
                && horizontal.motionZ == KnockbackControl.scale(z, 93)
                && horizontal.motionY == originalY,
                "preservar vertical afeta apenas X/Z");
        System.out.println("PASS: " + checks + " verificações Knockback");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
