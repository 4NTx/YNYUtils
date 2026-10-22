package com.yny.utils.modules.clanping;

final class PingHudModel {
    private static final String[] DIRECTIONS = {
        "FRENTE", "FRENTE DIREITA", "DIREITA", "TRÁS DIREITA",
        "ATRÁS", "TRÁS ESQUERDA", "ESQUERDA", "FRENTE ESQUERDA"
    };

    private PingHudModel() {}

    static int sector(double dx, double dz, float yaw) {
        double angle = Math.toDegrees(Math.atan2(dz, dx)) - yaw - 90.0;
        return ((int) Math.floor((angle + 22.5) / 45.0)) & 7;
    }

    static String direction(int sector) {
        return sector < 0 ? "DIREÇÃO INDISPONÍVEL" : DIRECTIONS[sector & 7];
    }

    static int meters(double dx, double dz) {
        return (int) Math.round(Math.hypot(dx, dz));
    }

    static int seconds(long expiresAt, long now) {
        return (int) Math.max(0L, (expiresAt - now + 999L) / 1000L);
    }
}
