package com.yny.utils.core;

/** Limites compartilhados pelas utilidades de alcance do YNYUtils. */
public final class ReachRange {

    public static final double VANILLA = 3.0D;
    public static final double MAXIMUM = 3.9D;

    private ReachRange() {
    }

    public static double clamp(double value) {
        if (Double.isNaN(value)) {
            return VANILLA;
        }
        return Math.max(VANILLA, Math.min(MAXIMUM, value));
    }
}
