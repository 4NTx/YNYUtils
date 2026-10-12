package com.yny.utils.modules.player;

import com.yny.utils.config.YNYConfig;

public final class ArmorPickupPolicy {
    private ArmorPickupPolicy() {}

    public static boolean shouldDiscard(YNYConfig config, int piece, int remaining, int max, Boolean enchanted, Integer protection) {
        if (piece < 0 || config == null || !config.autoArmorDiscardReceived) {
            return false;
        }
        if (config.autoArmorDiscardRed8 && max > 0 && 100L * (long) remaining <= 8L * (long) max) {
            return true;
        }
        if (config.autoArmorDiscardUnenchanted && Boolean.FALSE.equals(enchanted)) {
            return true;
        }
        return config.autoArmorDiscardBelowP4 && protection != null && protection < 4;
    }
}
