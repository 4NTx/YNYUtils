package com.yny.targethealth;

/** Keeps a briefly lost target from resetting the health animation. */
public final class TargetGrace {
    private static final long LINGER_MS = 400L;
    private static Object lastTarget;
    private static long lastSeen;

    private TargetGrace() { }

    public static Object choose(Object candidate, Object previous) {
        return chooseAt(candidate, previous, System.currentTimeMillis());
    }

    static synchronized Object chooseAt(Object candidate, Object previous, long now) {
        if (candidate != null) {
            lastTarget = candidate;
            lastSeen = now;
            return candidate;
        }
        if (previous != null && previous == lastTarget && now - lastSeen < LINGER_MS) return previous;
        lastTarget = null;
        return null;
    }
}
