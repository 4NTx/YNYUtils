package com.yny.utils.modules.player;

/** Pure decisions for the server-driven part of an armor exchange. */
final class ArmorSwapSafety {
    enum Outcome {
        WAIT,
        CONFIRMED,
        LOCAL_STABLE,
        DESTINATION_UNKNOWN,
        ARMOR_CHANGED
    }

    private ArmorSwapSafety() {
    }

    static boolean mayReturnCursor(boolean alive, boolean screenOpen, boolean containerOpen, int windowId) {
        return alive && !screenOpen && !containerOpen && windowId == 0;
    }

    /** A peça pode gastar durabilidade durante a troca; empate não prova identidade. */
    static boolean closerDamage(int actual, int expected, int other) {
        return Math.abs((long) actual - expected) < Math.abs((long) actual - other);
    }

    /** Descarte só após evidência do servidor, sem intervenção manual e com nova peça equipada. */
    static boolean mayDropStuckOld(boolean enabled, boolean manualIntervened,
            boolean serverOwnedCursor, boolean swapConfirmed, boolean replacementEquipped,
            boolean retriesExhausted, int noSlotTicks, int noSlotGraceTicks) {
        return enabled && !manualIntervened && serverOwnedCursor && swapConfirmed
                && replacementEquipped && (retriesExhausted || noSlotTicks >= noSlotGraceTicks);
    }

    static Outcome returned(boolean cursorEmpty, boolean expectedArmorEquipped,
            boolean oldArmorInDestination, boolean cursorClearedByServer,
            boolean destinationSeenByServer, int waitedTicks, int timeoutTicks) {
        if (!cursorEmpty) {
            return Outcome.WAIT;
        }
        if (!expectedArmorEquipped) {
            return Outcome.ARMOR_CHANGED;
        }
        if (oldArmorInDestination && cursorClearedByServer && destinationSeenByServer) {
            return Outcome.CONFIRMED;
        }
        if (waitedTicks < timeoutTicks) {
            return Outcome.WAIT;
        }
        return oldArmorInDestination ? Outcome.LOCAL_STABLE : Outcome.DESTINATION_UNKNOWN;
    }
}
