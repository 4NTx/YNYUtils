package com.yny.targethealth;

import java.util.Map;
import java.util.WeakHashMap;

/** Prevents a stale entity read from immediately undoing a health callback. */
public final class HealthEventLatch {
    private static final long HOLD_MS = 400L;
    private static final Map<Object, Sample> RECENT = new WeakHashMap<Object, Sample>();

    private HealthEventLatch() { }

    public static synchronized void record(Object entity, float health, float absorption) {
        if (entity != null) RECENT.put(entity, new Sample(health, absorption, System.currentTimeMillis()));
    }

    public static synchronized float health(float entityValue, Object entity) {
        Sample sample = current(entity, System.currentTimeMillis());
        return sample == null ? entityValue : sample.health;
    }

    public static synchronized float absorption(float entityValue, Object entity) {
        Sample sample = current(entity, System.currentTimeMillis());
        return sample == null ? entityValue : sample.absorption;
    }

    private static Sample current(Object entity, long now) {
        Sample sample = RECENT.get(entity);
        if (sample == null) return null;
        if (now - sample.at <= HOLD_MS) return sample;
        RECENT.remove(entity);
        return null;
    }

    private static final class Sample {
        final float health, absorption;
        final long at;
        Sample(float health, float absorption, long at) {
            this.health = health;
            this.absorption = absorption;
            this.at = at;
        }
    }
}
