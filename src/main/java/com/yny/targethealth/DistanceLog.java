package com.yny.targethealth;

/** Keeps the exact HUD distance while grouping diagnostic entries by metre. */
public final class DistanceLog {
    private DistanceLog() {}

    public static String bucket(String shown) {
        if (shown == null || !shown.endsWith("m")) return shown;
        try {
            float metres = Float.parseFloat(shown.substring(0, shown.length() - 1));
            return Math.round(metres) + "m";
        } catch (NumberFormatException ignored) {
            return shown;
        }
    }
}
