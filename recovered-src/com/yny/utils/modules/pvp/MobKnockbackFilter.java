package com.yny.utils.modules.pvp;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import com.yny.utils.YNYUtils;
import com.yny.utils.config.YNYConfig;
import com.yny.utils.core.DebugLog;

import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Gameplay;

/**
 * Filtro inteligente de knockback para diferenciar ataques de Mobs vs Players
 * e anular completamente o knockback e desaceleração do raio do Guardian.
 */
public final class MobKnockbackFilter {

    private static Field fEntityId;
    private static Field fMotionX;
    private static Field fMotionY;
    private static Field fMotionZ;
    private static boolean packetFieldsResolved;

    private static Field fExplosionMotionX;
    private static Field fExplosionMotionY;
    private static Field fExplosionMotionZ;
    private static boolean explosionFieldsResolved;

    private static Method entityIdMethod;
    private static Method targetMethod;
    private static Method hasTargetMethod;
    private static Method dataWatcherMethod;
    private static Method getWatchableObjectIntMethod;
    private static boolean guardianMethodsResolved;

    private static Method witherTargetMethod;
    private static boolean witherMethodsResolved;

    private MobKnockbackFilter() {}

    /**
     * Chamado na leitura de pacotes da pipeline Netty.
     * Se retornar true, o pacote S12PacketEntityVelocity (hm) é descartado
     * imediatamente sem ser propagado para o Minecraft vanilla.
     * Se for S27PacketExplosion (gk), o knockback da explosão é zerado via reflexão.
     */
    public static boolean shouldDropVelocity(Object msg) {
        if (msg == null) return false;
        String clsName = msg.getClass().getName();

        YNYConfig config = YNYUtils.getConfig();
        if (config == null) return false;

        // Anulação de knockback de explosões (S27PacketExplosion / gk)
        if ("gk".equals(clsName) || clsName.endsWith("S27PacketExplosion")) {
            if (config.witherKnockbackProtect && hasNearbyWitherOrSkull()) {
                zeroExplosionVelocity(msg);
                DebugLog.write("MobKnockback", "EXPLOSION: Knockback da explosão do Wither anulado!");
            }
            return false; // Permite que a explosão seja renderizada com som/partículas sem aplicar KB
        }

        if (!"hm".equals(clsName) && !clsName.endsWith("S12PacketEntityVelocity")) {
            return false;
        }

        resolvePacketFields(msg.getClass());

        int entityId = getPacketInt(fEntityId, msg);
        Object self = Entities.self();
        if (self == null) return false;

        int localId = getEntityId(self);
        if (localId != -1 && entityId != localId) {
            return false; // Pacote destinado a outra entidade, não interferir
        }

        int motionX = getPacketInt(fMotionX, msg);
        int motionY = getPacketInt(fMotionY, msg);
        int motionZ = getPacketInt(fMotionZ, msg);

        if (motionX == 0 && motionY == 0 && motionZ == 0) {
            return false;
        }

        // 1. Proteção contra o Wither (ataques físicos, crânios e dano direto)
        if (config.witherKnockbackProtect && isWitherKnockback(motionX, motionY, motionZ)) {
            DebugLog.write("MobKnockback", "DROPPED: Knockback do Wither anulado! Motion=("
                    + motionX + "," + motionY + "," + motionZ + ")");
            return true;
        }

        // 2. Proteção contra o Guardian (raio/olho, espinhos ou ataque físico)
        if (config.guardianBeamProtect && isGuardianKnockback(motionX, motionY, motionZ)) {
            DebugLog.write("MobKnockback", "DROPPED: Knockback do Guardian anulado! Motion=("
                    + motionX + "," + motionY + "," + motionZ + ")");
            return true;
        }

        // 3. Não levar knockback de outros mobs (apenas se mobKnockbackEnabled for true e não for somente Guardian)
        if (config.mobKnockbackEnabled && !config.guardianOnlyKnockback && isMobKnockback(motionX, motionY, motionZ)) {
            DebugLog.write("MobKnockback", "DROPPED: Knockback de mob anulado! Motion=("
                    + motionX + "," + motionY + "," + motionZ + ")");
            return true;
        }

        return false;
    }

    public static void onTickEnd() {
        YNYConfig config = YNYUtils.getConfig();
        if (config != null && config.guardianBeamProtect && isPlayerTargetedByGuardian()) {
            Gameplay.setSprintOnHit(true);
            Gameplay.setNoHitSlowdown(true);
        }
    }

    public static boolean isWitherKnockback(int motionX, int motionY, int motionZ) {
        if (isPlayerTargetedByWither()) {
            return true;
        }

        Object self = Entities.self();
        if (self == null) return false;

        List<Object> nearbyWithers = new ArrayList<>();
        List<Object> nearbyPlayers = new ArrayList<>();

        try {
            for (Object e : Entities.all()) {
                if (e == self) continue;
                if (isWither(e) || isWitherSkull(e)) {
                    double dist = Entities.distance(self, e);
                    if (dist <= 40.0D) {
                        nearbyWithers.add(e);
                    }
                }
            }

            for (Object p : Entities.players()) {
                if (p == self) continue;
                if (!Entities.isAlive(p)) continue;
                double dist = Entities.distance(self, p);
                if (dist <= 6.5D) {
                    nearbyPlayers.add(p);
                }
            }
        } catch (Throwable ignored) {
            return false;
        }

        if (nearbyWithers.isEmpty()) {
            return false;
        }

        if (nearbyPlayers.isEmpty()) {
            return true;
        }

        double vX = motionX;
        double vZ = motionZ;
        double vLen = Math.hypot(vX, vZ);
        if (vLen < 1.0D) {
            double closestPlayerDist = minDistance(self, nearbyPlayers);
            double closestWitherDist = minDistance(self, nearbyWithers);
            return closestWitherDist < closestPlayerDist;
        }

        vX /= vLen;
        vZ /= vLen;

        double bestWitherScore = bestAlignment(self, nearbyWithers, vX, vZ);
        double bestPlayerScore = bestAlignment(self, nearbyPlayers, vX, vZ);

        return bestWitherScore > bestPlayerScore;
    }

    public static boolean isPlayerTargetedByWither() {
        Object self = Entities.self();
        if (self == null) return false;
        int myId = getEntityId(self);

        for (Object e : Entities.all()) {
            if (e == self) continue;
            if (!isWither(e)) continue;
            if (isWitherTargeting(e, self, myId)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isWither(Object e) {
        if (e == null) return false;
        String type = getTypeNameSafe(e);
        if (type != null) {
            String t = type.toLowerCase();
            if (t.contains("witherskeleton")) return false;
            if (t.contains("witherskull")) return false;
            if (t.contains("wither")) return true;
        }
        String name = e.getClass().getName();
        if ("uk".equals(name) || name.endsWith("EntityWither") || name.endsWith("EntityWitherBoss")) {
            return true;
        }
        String sName = e.getClass().getSimpleName().toLowerCase();
        return sName.contains("wither") && !sName.contains("skeleton") && !sName.contains("skull");
    }

    public static boolean isWitherSkull(Object e) {
        if (e == null) return false;
        String type = getTypeNameSafe(e);
        if (type != null && type.toLowerCase().contains("witherskull")) {
            return true;
        }
        String name = e.getClass().getName();
        if ("xd".equals(name) || name.endsWith("EntityWitherSkull")) {
            return true;
        }
        String sName = e.getClass().getSimpleName().toLowerCase();
        return sName.contains("witherskull");
    }

    public static boolean hasNearbyWitherOrSkull() {
        Object self = Entities.self();
        if (self == null) return false;
        try {
            for (Object e : Entities.all()) {
                if (e == self) continue;
                if (isWither(e) || isWitherSkull(e)) {
                    double dist = Entities.distance(self, e);
                    if (dist <= 48.0D) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static boolean isWitherTargeting(Object wither, Object self, int myId) {
        if (wither == null || self == null) return false;
        resolveWitherMethods(wither.getClass());
        try {
            if (witherTargetMethod != null) {
                for (int head = 0; head < 3; head++) {
                    Object targetId = witherTargetMethod.invoke(wither, head);
                    if (targetId instanceof Number && ((Number) targetId).intValue() == myId) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static void zeroExplosionVelocity(Object explosionPacket) {
        if (explosionPacket == null) return;
        resolveExplosionFields(explosionPacket.getClass());
        try {
            if (fExplosionMotionX != null) fExplosionMotionX.setFloat(explosionPacket, 0.0F);
            if (fExplosionMotionY != null) fExplosionMotionY.setFloat(explosionPacket, 0.0F);
            if (fExplosionMotionZ != null) fExplosionMotionZ.setFloat(explosionPacket, 0.0F);
        } catch (Throwable ignored) {}
    }

    public static boolean isGuardianKnockback(int motionX, int motionY, int motionZ) {
        if (isPlayerTargetedByGuardian()) {
            return true;
        }

        Object self = Entities.self();
        if (self == null) return false;

        List<Object> nearbyGuardians = new ArrayList<>();
        List<Object> nearbyPlayers = new ArrayList<>();

        try {
            for (Object e : Entities.all()) {
                if (e == self) continue;
                if (isGuardian(e) && Entities.isAlive(e)) {
                    double dist = Entities.distance(self, e);
                    if (dist <= 20.0D) {
                        nearbyGuardians.add(e);
                    }
                }
            }

            for (Object p : Entities.players()) {
                if (p == self) continue;
                if (!Entities.isAlive(p)) continue;
                double dist = Entities.distance(self, p);
                if (dist <= 6.5D) {
                    nearbyPlayers.add(p);
                }
            }
        } catch (Throwable ignored) {
            return false;
        }

        if (nearbyGuardians.isEmpty()) {
            return false;
        }

        if (nearbyPlayers.isEmpty()) {
            return true;
        }

        double vX = motionX;
        double vZ = motionZ;
        double vLen = Math.hypot(vX, vZ);
        if (vLen < 1.0D) {
            double closestPlayerDist = minDistance(self, nearbyPlayers);
            double closestGuardianDist = minDistance(self, nearbyGuardians);
            return closestGuardianDist < closestPlayerDist;
        }

        vX /= vLen;
        vZ /= vLen;

        double bestGuardianScore = bestAlignment(self, nearbyGuardians, vX, vZ);
        double bestPlayerScore = bestAlignment(self, nearbyPlayers, vX, vZ);

        return bestGuardianScore > bestPlayerScore;
    }

    public static boolean isPlayerTargetedByGuardian() {
        Object self = Entities.self();
        if (self == null) return false;
        int myId = getEntityId(self);

        for (Object e : Entities.all()) {
            if (e == self) continue;
            if (!isGuardian(e)) continue;
            if (isGuardianTargeting(e, self, myId)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isGuardian(Object e) {
        if (e == null) return false;
        String type = getTypeNameSafe(e);
        if (type != null && type.toLowerCase().contains("guardian")) return true;
        String name = e.getClass().getName();
        return "vt".equals(name) || name.endsWith("EntityGuardian");
    }

    public static boolean isGuardianTargeting(Object guardian, Object self, int myId) {
        if (guardian == null) return false;
        resolveGuardianMethods(guardian.getClass());

        // Teste direto por método getTargetedEntity (cq no Notch)
        try {
            if (targetMethod != null) {
                Object target = targetMethod.invoke(guardian);
                if (target != null) {
                    return target == self || getEntityId(target) == myId;
                }
            }
        } catch (Throwable ignored) {}

        // Teste por hasTargetedEntity (cp no Notch)
        try {
            if (hasTargetMethod != null) {
                Object hasTarget = hasTargetMethod.invoke(guardian);
                if (Boolean.TRUE.equals(hasTarget)) {
                    double dist = Entities.distance(self, guardian);
                    if (dist <= 20.0D) {
                        int watcherTarget = getGuardianTargetIdFromDataWatcher(guardian);
                        if (watcherTarget > 0) {
                            return watcherTarget == myId;
                        }
                        if (countNearbyPlayers(self, 20.0D) <= 1) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }

    private static int getGuardianTargetIdFromDataWatcher(Object guardian) {
        try {
            if (dataWatcherMethod == null) {
                for (Method m : guardian.getClass().getMethods()) {
                    if ("H".equals(m.getName()) || "getDataWatcher".equals(m.getName()) || "func_70096_w".equals(m.getName())) {
                        dataWatcherMethod = m;
                        m.setAccessible(true);
                        break;
                    }
                }
            }
            if (dataWatcherMethod != null) {
                Object watcher = dataWatcherMethod.invoke(guardian);
                if (watcher != null) {
                    if (getWatchableObjectIntMethod == null) {
                        for (Method m : watcher.getClass().getMethods()) {
                            if ("c".equals(m.getName()) || "getWatchableObjectInt".equals(m.getName()) || "func_75679_c".equals(m.getName())) {
                                getWatchableObjectIntMethod = m;
                                m.setAccessible(true);
                                break;
                            }
                        }
                    }
                    if (getWatchableObjectIntMethod != null) {
                        Object res = getWatchableObjectIntMethod.invoke(watcher, 17);
                        if (res instanceof Number) {
                            return ((Number) res).intValue();
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    public static boolean isMobKnockback(int motionX, int motionY, int motionZ) {
        Object self = Entities.self();
        if (self == null) return false;

        if (isPlayerTargetedByGuardian()) {
            return true;
        }

        List<Object> nearbyPlayers = new ArrayList<>();
        for (Object p : Entities.players()) {
            if (p == self) continue;
            if (!Entities.isAlive(p)) continue;
            double dist = Entities.distance(self, p);
            if (dist <= 6.5D) {
                nearbyPlayers.add(p);
            }
        }

        List<Object> nearbyMobs = new ArrayList<>();
        for (Object e : Entities.all()) {
            if (e == self) continue;
            if (Entities.isPlayer(e)) continue;
            if (!Entities.isLiving(e)) continue;
            if (isArmorStand(e)) continue;
            if (Entities.kind(e) == Entities.Kind.HOSTILE || isGuardian(e) || isMonster(e)) {
                double dist = Entities.distance(self, e);
                double maxReach = isGuardian(e) ? 20.0D : (isRangedMob(e) ? 16.0D : 6.5D);
                if (dist <= maxReach) {
                    nearbyMobs.add(e);
                }
            }
        }

        if (nearbyPlayers.isEmpty()) {
            if (!nearbyMobs.isEmpty()) {
                return true;
            }
            if (countNearbyPlayers(self, 20.0D) <= 1) {
                return true;
            }
            return false;
        }

        if (nearbyMobs.isEmpty()) {
            return false;
        }

        double vX = motionX;
        double vZ = motionZ;
        double vLen = Math.hypot(vX, vZ);
        if (vLen < 1.0D) {
            double closestPlayerDist = minDistance(self, nearbyPlayers);
            double closestMobDist = minDistance(self, nearbyMobs);
            return closestMobDist < closestPlayerDist;
        }

        vX /= vLen;
        vZ /= vLen;

        double bestMobScore = bestAlignment(self, nearbyMobs, vX, vZ);
        double bestPlayerScore = bestAlignment(self, nearbyPlayers, vX, vZ);

        return bestMobScore > bestPlayerScore;
    }

    private static double bestAlignment(Object self, List<Object> candidates, double vX, double vZ) {
        double myX = Entities.x(self, 1.0F);
        double myZ = Entities.z(self, 1.0F);
        double best = -100.0D;

        for (Object c : candidates) {
            double cx = Entities.x(c, 1.0F);
            double cz = Entities.z(c, 1.0F);
            double dx = myX - cx;
            double dz = myZ - cz;
            double dLen = Math.hypot(dx, dz);
            if (dLen < 0.001D) {
                best = Math.max(best, 1.0D);
                continue;
            }
            double dot = (vX * (dx / dLen)) + (vZ * (dz / dLen));
            double score = dot / Math.max(1.0D, dLen);
            if (score > best) {
                best = score;
            }
        }
        return best;
    }

    private static double minDistance(Object self, List<Object> candidates) {
        double min = Double.MAX_VALUE;
        for (Object c : candidates) {
            double d = Entities.distance(self, c);
            if (d < min) min = d;
        }
        return min;
    }

    private static int countNearbyPlayers(Object self, double maxDistance) {
        int count = 0;
        for (Object p : Entities.players()) {
            if (Entities.distance(self, p) <= maxDistance) {
                count++;
            }
        }
        return count;
    }

    public static boolean isMonster(Object e) {
        if (e == null) return false;
        try {
            if (Entities.kind(e) == Entities.Kind.HOSTILE) return true;
        } catch (Throwable ignored) {}
        if (isGuardian(e)) return true;
        if (isWither(e)) return true;
        String type = getTypeNameSafe(e);
        if (type == null || type.isEmpty()) return false;
        String t = type.toLowerCase();
        return t.contains("zombie") || t.contains("skeleton") || t.contains("spider")
                || t.contains("creeper") || t.contains("enderman") || t.contains("slime")
                || t.contains("witch") || t.contains("blaze") || t.contains("ghast")
                || t.contains("silverfish") || t.contains("golem") || t.contains("wither")
                || t.contains("dragon") || t.contains("guardian") || t.contains("magma");
    }

    public static boolean isArmorStand(Object e) {
        if (e == null) return false;
        String type = getTypeNameSafe(e);
        if ("ArmorStand".equalsIgnoreCase(type)) return true;
        String name = e.getClass().getName();
        return name.contains("ArmorStand");
    }

    public static boolean isRangedMob(Object e) {
        if (e == null) return false;
        String type = getTypeNameSafe(e);
        if (type == null) return false;
        String t = type.toLowerCase();
        return t.contains("skeleton") || t.contains("blaze") || t.contains("ghast") || t.contains("witch");
    }

    private static Method mIsAngry;
    private static Field fAngerLevel;
    private static boolean pigZombieReflectResolved;

    public static boolean isPigZombie(Object e) {
        if (e == null) return false;
        String type = getTypeNameSafe(e);
        if (type != null) {
            String t = type.toLowerCase();
            if (t.contains("pigzombie") || t.contains("pig_zombie") || t.contains("zombiepigman")
                    || t.contains("zombie_pigman") || t.contains("pigman")) {
                return true;
            }
        }
        Class<?> c = e.getClass();
        String name = c.getName();
        return name.contains("PigZombie") || name.equals("vw") || name.endsWith(".vw");
    }

    public static boolean isPigZombieAngry(Object e) {
        if (!isPigZombie(e)) return false;
        if (!pigZombieReflectResolved) {
            pigZombieReflectResolved = true;
            Class<?> c = e.getClass();
            while (c != null && c != Object.class) {
                for (Method m : c.getDeclaredMethods()) {
                    if (m.getParameterCount() == 0 && m.getReturnType() == boolean.class) {
                        String n = m.getName();
                        if ("func_175457_ck".equals(n) || "cm".equals(n) || "isAngry".equals(n)) {
                            mIsAngry = m;
                            m.setAccessible(true);
                            break;
                        }
                    }
                }
                for (Field f : c.getDeclaredFields()) {
                    if (f.getType() == int.class) {
                        String n = f.getName();
                        if ("field_70838_e".equals(n) || "bn".equals(n) || "angerLevel".equals(n)) {
                            fAngerLevel = f;
                            f.setAccessible(true);
                            break;
                        }
                    }
                }
                c = c.getSuperclass();
            }
        }
        try {
            if (mIsAngry != null) {
                return (boolean) mIsAngry.invoke(e);
            }
            if (fAngerLevel != null) {
                return fAngerLevel.getInt(e) > 0;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static String getTypeNameSafe(Object e) {
        if (e == null) return null;
        try {
            String type = Entities.typeName(e);
            if (type != null && !type.isEmpty()) return type;
        } catch (Throwable ignored) {}
        try {
            Field tf = e.getClass().getField("type");
            Object val = tf.get(e);
            if (val != null) return val.toString();
        } catch (Throwable ignored) {}
        String name = e.getClass().getSimpleName();
        if (name.startsWith("Entity")) {
            name = name.substring("Entity".length());
        }
        return name;
    }

    public static int getEntityId(Object entity) {
        if (entity == null) return -1;
        try {
            if (entityIdMethod != null) {
                return (Integer) entityIdMethod.invoke(entity);
            }
            for (Method m : entity.getClass().getMethods()) {
                String name = m.getName();
                if ("F".equals(name) || "getEntityId".equals(name) || "func_145782_y".equals(name)) {
                    entityIdMethod = m;
                    m.setAccessible(true);
                    return (Integer) m.invoke(entity);
                }
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    private static synchronized void resolvePacketFields(Class<?> clazz) {
        if (packetFieldsResolved) return;
        packetFieldsResolved = true;
        try {
            for (Field f : clazz.getDeclaredFields()) {
                f.setAccessible(true);
                String name = f.getName();
                if (f.getType() == int.class) {
                    if ("a".equals(name) || "entityID".equals(name)) fEntityId = f;
                    else if ("b".equals(name) || "motionX".equals(name)) fMotionX = f;
                    else if ("c".equals(name) || "motionY".equals(name)) fMotionY = f;
                    else if ("d".equals(name) || "motionZ".equals(name)) fMotionZ = f;
                }
            }
        } catch (Throwable ignored) {}
    }

    private static synchronized void resolveGuardianMethods(Class<?> clazz) {
        if (guardianMethodsResolved) return;
        guardianMethodsResolved = true;
        try {
            for (Method m : clazz.getMethods()) {
                String name = m.getName();
                if ("getTargetedEntity".equals(name) || "cq".equals(name) || "func_175466_co".equals(name)) {
                    targetMethod = m;
                    m.setAccessible(true);
                } else if ("hasTargetedEntity".equals(name) || "cp".equals(name) || "func_175474_cn".equals(name)) {
                    hasTargetMethod = m;
                    m.setAccessible(true);
                }
            }
        } catch (Throwable ignored) {}
    }

    private static synchronized void resolveWitherMethods(Class<?> clazz) {
        if (witherMethodsResolved) return;
        witherMethodsResolved = true;
        try {
            for (Method m : clazz.getMethods()) {
                if (("s".equals(m.getName()) || "getWatchedTargetId".equals(m.getName()) || "func_82208_v".equals(m.getName()))
                        && m.getParameterCount() == 1 && m.getParameterTypes()[0] == int.class) {
                    witherTargetMethod = m;
                    m.setAccessible(true);
                    break;
                }
            }
        } catch (Throwable ignored) {}
    }

    private static synchronized void resolveExplosionFields(Class<?> clazz) {
        if (explosionFieldsResolved) return;
        explosionFieldsResolved = true;
        try {
            List<Field> floatFields = new ArrayList<>();
            for (Field f : clazz.getDeclaredFields()) {
                f.setAccessible(true);
                if (f.getType() == float.class) {
                    String name = f.getName();
                    if ("f".equals(name) || "field_149152_f".equals(name)) {
                        fExplosionMotionX = f;
                    } else if ("g".equals(name) || "field_149153_g".equals(name)) {
                        fExplosionMotionY = f;
                    } else if ("h".equals(name) || "field_149159_h".equals(name)) {
                        fExplosionMotionZ = f;
                    }
                    floatFields.add(f);
                }
            }
            if ((fExplosionMotionX == null || fExplosionMotionY == null || fExplosionMotionZ == null) && floatFields.size() >= 4) {
                fExplosionMotionX = floatFields.get(floatFields.size() - 3);
                fExplosionMotionY = floatFields.get(floatFields.size() - 2);
                fExplosionMotionZ = floatFields.get(floatFields.size() - 1);
            }
        } catch (Throwable ignored) {}
    }

    private static int getPacketInt(Field f, Object obj) {
        if (f == null || obj == null) return 0;
        try {
            return f.getInt(obj);
        } catch (Throwable ignored) {
            return 0;
        }
    }
}
