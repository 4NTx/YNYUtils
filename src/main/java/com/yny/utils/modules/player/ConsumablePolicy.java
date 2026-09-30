package com.yny.utils.modules.player;

/** Regra comum para maçãs e poções; não depende do cliente Minecraft. */
final class ConsumablePolicy {
    static final int OFF = 0;
    static final int ECONOMIC = 1;
    static final int COMBAT = 2;

    private ConsumablePolicy() { }

    static boolean shouldUse(int mode, boolean inCombat, long damageEpoch,
            long consumedDamageEpoch, int effectRemainingTicks, int refreshTicks) {
        if (mode == ECONOMIC) {
            return damageEpoch > consumedDamageEpoch && effectRemainingTicks <= 0;
        }
        if (mode == COMBAT) {
            return inCombat && effectRemainingTicks <= refreshTicks;
        }
        return false;
    }
}
