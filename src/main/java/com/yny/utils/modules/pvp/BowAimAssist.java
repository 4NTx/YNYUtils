package com.yny.utils.modules.pvp;

import com.yny.utils.YNYUtils;
import com.yny.utils.config.YNYConfig;
import com.yny.utils.core.SafeEntities;
import com.yny.utils.modules.clanping.PingClanName;

import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Inventory;
import dev.xavier.stein.loader.api.Relation;
import dev.xavier.stein.loader.api.Relations;
import dev.xavier.stein.loader.api.Stacks;
import dev.xavier.stein.loader.api.Terrain;
import dev.xavier.stein.loader.api.WorldRender;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Trajetória Visual 3D de Alta Precisão & ZERO LAG para Arco (Apenas Trajetória Visual):
 * 1. Predição balística física exata do Minecraft 1.8.9 (gravidade 0.05/tick, arrasto do ar 0.99).
 * 2. Marcador visual 3D fino do ponto de impacto em blocos e jogadores inimigos.
 * 3. Zero alteração da mira do jogador (AimAssist removido).
 * 4. Zero alocações de objetos por frame no hot loop (arrays primitivos pré-alocados).
 */
public final class BowAimAssist {

    private static final int MAX_TRAJ_POINTS = 64;
    private static final double[] TRAJ_X = new double[MAX_TRAJ_POINTS];
    private static final double[] TRAJ_Y = new double[MAX_TRAJ_POINTS];
    private static final double[] TRAJ_Z = new double[MAX_TRAJ_POINTS];
    private static int trajCount = 0;
    private static Object hitEntity = null;
    private static boolean hitSolid = false;
    private static int hitBlockX = 0;
    private static int hitBlockY = 0;
    private static int hitBlockZ = 0;

    // Rastreamento de carga do arco
    private static int chargeTicks = 0;

    // Reflexão em cache para campos do jogador
    private static boolean reflectionInit = false;
    private static Field pitchField;
    private static Field prevPitchField;
    private static Method itemUseDurationMethod;

    private BowAimAssist() {}

    private static void initReflection(Class<?> playerClass) {
        if (reflectionInit) return;
        reflectionInit = true;
        pitchField = findField(playerClass, "rotationPitch", "z");
        prevPitchField = findField(playerClass, "prevRotationPitch", "B");
        itemUseDurationMethod = findMethod(playerClass, "getItemInUseDuration", "bT");
    }

    private static Field findField(Class<?> clazz, String... names) {
        Class<?> cur = clazz;
        while (cur != null && cur != Object.class) {
            for (String name : names) {
                try {
                    Field f = cur.getDeclaredField(name);
                    f.setAccessible(true);
                    return f;
                } catch (Throwable ignored) {}
            }
            cur = cur.getSuperclass();
        }
        return null;
    }

    private static Method findMethod(Class<?> clazz, String... names) {
        Class<?> cur = clazz;
        while (cur != null && cur != Object.class) {
            for (String name : names) {
                try {
                    Method m = cur.getDeclaredMethod(name);
                    m.setAccessible(true);
                    return m;
                } catch (Throwable ignored) {}
            }
            cur = cur.getSuperclass();
        }
        return null;
    }

    public static boolean isHoldingBow() {
        try {
            Object held = Inventory.heldItem();
            return held != null && Stacks.is(held, "bow");
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static float getBowChargeDuration(Object player) {
        if (!Inventory.isUsingItem()) return 20.0F; // Estimativa de carga cheia para pré-visualização
        if (itemUseDurationMethod != null && player != null) {
            try {
                Object res = itemUseDurationMethod.invoke(player);
                if (res instanceof Number) {
                    return ((Number) res).floatValue();
                }
            } catch (Throwable ignored) {}
        }
        return (float) Math.max(1, chargeTicks);
    }

    public static float calculateBowSpeed(float duration) {
        float f = duration / 20.0F;
        f = (f * f + f * 2.0F) / 3.0F;
        if (f > 1.0F) f = 1.0F;
        if (f < 0.1F) f = 0.1F;
        return f * 3.0F; // Velocidade da flecha no Minecraft 1.8.9 (até 3.0 blocos/tick)
    }

    /**
     * Executado a cada tick (20 Hz).
     * Rastreia apenas a duração da puxada do arco para cálculo físico da velocidade.
     * NUNCA altera a rotação, yaw ou pitch da mira do jogador.
     */
    public static void onTickEnd() {
        if (!isHoldingBow()) {
            chargeTicks = 0;
            return;
        }

        boolean isUsing = Inventory.isUsingItem();
        if (isUsing) {
            chargeTicks++;
        } else {
            chargeTicks = 0;
        }
    }

    /**
     * Renderização no Mundo 3D da trajetória balística e marcador de impacto (Zero Lag).
     */
    public static void onRenderWorld(float partialTicks) {
        YNYConfig config = YNYUtils.getConfig();
        if (config == null || !config.bowTrajectoryEnabled) return;
        if (!isHoldingBow()) return;

        Object self = Entities.self();
        if (self == null) return;

        initReflection(self.getClass());

        float yaw = Entities.yaw(self, partialTicks);
        float pitch = 0.0F;
        if (pitchField != null) {
            try {
                float cur = pitchField.getFloat(self);
                if (prevPitchField != null) {
                    float prev = prevPitchField.getFloat(self);
                    pitch = prev + (cur - prev) * partialTicks;
                } else {
                    pitch = cur;
                }
            } catch (Throwable ignored) {}
        }

        float bowSpeed = calculateBowSpeed(getBowChargeDuration(self));

        // Calcula a trajetória física da flecha
        simulateTrajectory(self, yaw, pitch, bowSpeed, partialTicks);

        if (trajCount < 2) return;

        final float renderPitch = pitch;
        final float renderYaw = yaw;

        // Renderiza a linha e caixas através das paredes com shader nativo
        WorldRender.throughWalls(() -> {
            try {
                // Cores dinâmicas: Vermelho se for acertar inimigo, Verde se bater em bloco, Ciano no ar
                int lineColor = (hitEntity != null) ? 0xFFFF3333 : (hitSolid ? 0xFF00EE77 : 0xFF00DDFF);

                double camX = WorldRender.cameraX();
                double camY = WorldRender.cameraY();
                double camZ = WorldRender.cameraZ();

                double radYaw = Math.toRadians(renderYaw);
                double radPitch = Math.toRadians(renderPitch);
                double lx = -Math.sin(radYaw) * Math.cos(radPitch);
                double ly = -Math.sin(radPitch);
                double lz = Math.cos(radYaw) * Math.cos(radPitch);

                // Espessura fina e elegante de 2.8 cm em blocos/metros (WorldRender opera em metros, NÃO em pixels!)
                final double lineWidth = 0.028D;

                for (int i = 0; i < trajCount - 1; i++) {
                    double x1 = TRAJ_X[i];
                    double y1 = TRAJ_Y[i];
                    double z1 = TRAJ_Z[i];
                    double x2 = TRAJ_X[i + 1];
                    double y2 = TRAJ_Y[i + 1];
                    double z2 = TRAJ_Z[i + 1];

                    // Distância de cada vértice projetada ao longo do vetor de visão da câmera
                    double d1 = (x1 - camX) * lx + (y1 - camY) * ly + (z1 - camZ) * lz;
                    double d2 = (x2 - camX) * lx + (y2 - camY) * ly + (z2 - camZ) * lz;

                    // Se todo o segmento estiver atrás ou muito próximo do plano near (0.05m),
                    // ignorar para evitar explosão de vértices por divisão de W negativo/zero na GPU
                    if (d2 <= 0.35D) {
                        continue;
                    }

                    // Se o primeiro ponto estiver atrás ou muito perto da câmera,
                    // interpola exatamente até o plano seguro de 0.35m à frente dos olhos
                    if (d1 < 0.35D) {
                        double t = (0.35D - d1) / (d2 - d1);
                        x1 += (x2 - x1) * t;
                        y1 += (y2 - y1) * t;
                        z1 += (z2 - z1) * t;
                    }

                    WorldRender.line(x1, y1, z1, x2, y2, z2, lineWidth, lineColor);
                }

                double endX = TRAJ_X[trajCount - 1];
                double endY = TRAJ_Y[trajCount - 1];
                double endZ = TRAJ_Z[trajCount - 1];

                if (hitEntity != null) {
                    // Destaque limpo da entidade inimiga atingida
                    WorldRender.entityOutline(hitEntity, 0.035D, 0xFFFF3333);
                    WorldRender.circle(endX, endY + 0.05D, endZ, 0.40D, 0x66FF2222);
                    WorldRender.ring(endX, endY + 0.06D, endZ, 0.42D, 0.035D, 0xFFFF2222);
                    WorldRender.label("🎯 [ALVO]", endX, endY + 0.9D, endZ, 1.1F, 0xFFFF3333, true);
                } else if (hitSolid) {
                    // Marcador 3D preciso no bloco de impacto
                    WorldRender.blockOutline(hitBlockX, hitBlockY, hitBlockZ, 0.025D, 0x6600EE77);
                    WorldRender.boxOutline(endX - 0.08D, endY - 0.08D, endZ - 0.08D,
                                           endX + 0.08D, endY + 0.08D, endZ + 0.08D,
                                           0.020D, 0xFF00EE77);
                    WorldRender.circle(endX, endY + 0.015D, endZ, 0.28D, 0x5500EE77);
                    WorldRender.ring(endX, endY + 0.02D, endZ, 0.30D, 0.030D, 0xFF00EE77);
                }
            } catch (Throwable ignored) {}
        });
    }

    private static void simulateTrajectory(Object self, float yaw, float pitch, float speed, float partialTicks) {
        trajCount = 0;
        hitEntity = null;
        hitSolid = false;
        hitBlockX = 0;
        hitBlockY = 0;
        hitBlockZ = 0;

        double radYaw = Math.toRadians(yaw);
        double radPitch = Math.toRadians(pitch);

        double posX = Entities.x(self, partialTicks) - Math.cos(radYaw) * 0.16D;
        double posY = Entities.y(self, partialTicks) + 1.62D - 0.10D; // Altura do arco
        double posZ = Entities.z(self, partialTicks) - Math.sin(radYaw) * 0.16D;

        double motionX = -Math.sin(radYaw) * Math.cos(radPitch) * speed;
        double motionY = -Math.sin(radPitch) * speed;
        double motionZ = Math.cos(radYaw) * Math.cos(radPitch) * speed;

        TRAJ_X[0] = posX;
        TRAJ_Y[0] = posY;
        TRAJ_Z[0] = posZ;
        trajCount = 1;

        List<Object> players = SafeEntities.safePlayers();

        for (int step = 0; step < MAX_TRAJ_POINTS - 1; step++) {
            double nextX = posX + motionX;
            double nextY = posY + motionY;
            double nextZ = posZ + motionZ;

            // 1. Colisão com bloco (sub-passos a cada 0.5 blocos para não atravessar paredes finas)
            double dist = Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ);
            int checks = Math.max(1, (int) Math.ceil(dist / 0.5D));
            boolean hitBlockInStep = false;
            double hitX = nextX, hitY = nextY, hitZ = nextZ;

            for (int c = 1; c <= checks; c++) {
                double frac = (double) c / checks;
                double cx = posX + motionX * frac;
                double cy = posY + motionY * frac;
                double cz = posZ + motionZ * frac;
                int bx = (int) Math.floor(cx);
                int by = (int) Math.floor(cy);
                int bz = (int) Math.floor(cz);
                if (Terrain.isSolid(bx, by, bz)) {
                    hitSolid = true;
                    hitBlockX = bx;
                    hitBlockY = by;
                    hitBlockZ = bz;
                    hitX = cx;
                    hitY = cy;
                    hitZ = cz;
                    hitBlockInStep = true;
                    break;
                }
            }

            if (hitBlockInStep) {
                TRAJ_X[trajCount] = hitX;
                TRAJ_Y[trajCount] = hitY;
                TRAJ_Z[trajCount] = hitZ;
                trajCount++;
                break;
            }

            // 2. Colisão com entidade viva (jogadores inimigos)
            Object hit = checkEntityCollision(self, posX, posY, posZ, nextX, nextY, nextZ, players, partialTicks);
            if (hit != null) {
                TRAJ_X[trajCount] = nextX;
                TRAJ_Y[trajCount] = nextY;
                TRAJ_Z[trajCount] = nextZ;
                trajCount++;
                hitEntity = hit;
                break;
            }

            TRAJ_X[trajCount] = nextX;
            TRAJ_Y[trajCount] = nextY;
            TRAJ_Z[trajCount] = nextZ;
            trajCount++;

            // Arrasto e gravidade
            int curBx = (int) Math.floor(nextX);
            int curBy = (int) Math.floor(nextY);
            int curBz = (int) Math.floor(nextZ);
            boolean inWater = Terrain.isWater(curBx, curBy, curBz);
            double drag = inWater ? 0.6D : 0.99D;
            motionX *= drag;
            motionY = motionY * drag - 0.05D; // Gravidade da flecha = 0.05 blocos/tick²
            motionZ *= drag;

            posX = nextX;
            posY = nextY;
            posZ = nextZ;
        }
    }

    private static Object checkEntityCollision(Object self, double x1, double y1, double z1, double x2, double y2, double z2,
                                                List<Object> players, float partialTicks) {
        if (players == null || players.isEmpty()) return null;
        for (Object p : players) {
            if (p == self || !Entities.isAlive(p)) continue;
            String name = Entities.name(p);
            Relation rel = Relations.of(p);
            if (rel == Relation.GROUP || rel == Relation.ALLY || (rel != null && rel.friendly()) || PingClanName.isSameClan(p, name)) continue;

            double px = Entities.x(p, partialTicks);
            double py = Entities.y(p, partialTicks);
            double pz = Entities.z(p, partialTicks);

            // Bounding box expandida em 0.3D (igual à colisão real de EntityArrow no Minecraft 1.8.9)
            double minX = px - 0.6D;
            double maxX = px + 0.6D;
            double minY = py - 0.1D;
            double maxY = py + 2.1D;
            double minZ = pz - 0.6D;
            double maxZ = pz + 0.6D;

            if (intersectsAABB(x1, y1, z1, x2, y2, z2, minX, minY, minZ, maxX, maxY, maxZ)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Teste de interseção ultrarrápido entre segmento de reta e AABB (Slab Method).
     * Zero alocações, apenas matemática primitiva de ponto flutuante.
     */
    public static boolean intersectsAABB(double x1, double y1, double z1, double x2, double y2, double z2,
                                         double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;

        double tmin = 0.0D;
        double tmax = 1.0D;

        if (Math.abs(dx) > 1e-6) {
            double tx1 = (minX - x1) / dx;
            double tx2 = (maxX - x1) / dx;
            if (tx1 > tx2) { double tmp = tx1; tx1 = tx2; tx2 = tmp; }
            if (tx1 > tmin) tmin = tx1;
            if (tx2 < tmax) tmax = tx2;
            if (tmin > tmax) return false;
        } else if (x1 < minX || x1 > maxX) {
            return false;
        }

        if (Math.abs(dy) > 1e-6) {
            double ty1 = (minY - y1) / dy;
            double ty2 = (maxY - y1) / dy;
            if (ty1 > ty2) { double tmp = ty1; ty1 = ty2; ty2 = tmp; }
            if (ty1 > tmin) tmin = ty1;
            if (ty2 < tmax) tmax = ty2;
            if (tmin > tmax) return false;
        } else if (y1 < minY || y1 > maxY) {
            return false;
        }

        if (Math.abs(dz) > 1e-6) {
            double tz1 = (minZ - z1) / dz;
            double tz2 = (maxZ - z1) / dz;
            if (tz1 > tz2) { double tmp = tz1; tz1 = tz2; tz2 = tmp; }
            if (tz1 > tmin) tmin = tz1;
            if (tz2 < tmax) tmax = tz2;
            if (tmin > tmax) return false;
        } else if (z1 < minZ || z1 > maxZ) {
            return false;
        }

        return true;
    }

    /**
     * Auxiliar determinístico de corte contra o plano near da câmera.
     * Modifica o array inOut [x1, y1, z1, x2, y2, z2] mantendo o segmento em profundidade segura.
     * Retorna false se o segmento inteiro estiver atrás ou muito próximo da câmera.
     */
    public static boolean clipSegment(double[] inOut, double camX, double camY, double camZ,
                                      double lx, double ly, double lz, double minForwardDist) {
        if (inOut == null || inOut.length < 6) return false;
        double d1 = (inOut[0] - camX) * lx + (inOut[1] - camY) * ly + (inOut[2] - camZ) * lz;
        double d2 = (inOut[3] - camX) * lx + (inOut[4] - camY) * ly + (inOut[5] - camZ) * lz;
        if (d2 <= minForwardDist) return false;
        if (d1 < minForwardDist) {
            double t = (minForwardDist - d1) / (d2 - d1);
            inOut[0] += (inOut[3] - inOut[0]) * t;
            inOut[1] += (inOut[4] - inOut[1]) * t;
            inOut[2] += (inOut[5] - inOut[2]) * t;
        }
        return true;
    }

    public static float wrapDegrees(float value) {
        value = value % 360.0F;
        if (value >= 180.0F) {
            value -= 360.0F;
        }
        if (value < -180.0F) {
            value += 360.0F;
        }
        return value;
    }

    public static void reset() {
        chargeTicks = 0;
        trajCount = 0;
        hitEntity = null;
        hitSolid = false;
        hitBlockX = 0;
        hitBlockY = 0;
        hitBlockZ = 0;
    }

    // Getters para testes unitários
    public static int getTrajCount() { return trajCount; }
    public static double getTrajX(int idx) { return TRAJ_X[idx]; }
    public static double getTrajY(int idx) { return TRAJ_Y[idx]; }
    public static double getTrajZ(int idx) { return TRAJ_Z[idx]; }
    public static boolean isHitSolid() { return hitSolid; }
    public static Object getHitEntity() { return hitEntity; }
    public static int getHitBlockX() { return hitBlockX; }
    public static int getHitBlockY() { return hitBlockY; }
    public static int getHitBlockZ() { return hitBlockZ; }
}
