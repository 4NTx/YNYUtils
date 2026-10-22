package tests;

import com.yny.utils.modules.pvp.BowAimAssist;

public class BowAimAssistTest {

    public static void main(String[] args) {
        System.out.println("=== Testing BowAimAssist Logic ===");

        // 1. Test Bow Speed
        testBowSpeed();

        // 2. Test AABB Intersection
        testAABBIntersection();

        // 3. Test Angle Wrapping
        testAngleWrapping();

        // 4. Test Camera Near Clipping & Segment Normalization
        testCameraNearClipping();

        // 5. Test Trajectory Reset
        testTrajectoryReset();

        System.out.println("ALL BOW AIM ASSIST TESTS PASSED!");
    }

    private static void testBowSpeed() {
        float minSpeed = BowAimAssist.calculateBowSpeed(0.0F);
        if (minSpeed < 0.25F || minSpeed > 0.35F) {
            throw new AssertionError("Expected min speed ~0.3, got: " + minSpeed);
        }

        float halfSpeed = BowAimAssist.calculateBowSpeed(10.0F);
        if (halfSpeed < 1.0F || halfSpeed > 2.0F) {
            throw new AssertionError("Expected half speed ~1.25, got: " + halfSpeed);
        }

        float maxSpeed = BowAimAssist.calculateBowSpeed(20.0F);
        if (Math.abs(maxSpeed - 3.0F) > 0.01F) {
            throw new AssertionError("Expected max speed 3.0, got: " + maxSpeed);
        }

        float overchargeSpeed = BowAimAssist.calculateBowSpeed(40.0F);
        if (Math.abs(overchargeSpeed - 3.0F) > 0.01F) {
            throw new AssertionError("Expected overcharge speed capped at 3.0, got: " + overchargeSpeed);
        }
        System.out.println("  PASS: Bow speed curve tested");
    }

    private static void testAABBIntersection() {
        // Player standing at (10, 64, 10), AABB: [9.4, 63.9, 9.4] to [10.6, 66.1, 10.6]
        double minX = 9.4, maxX = 10.6;
        double minY = 63.9, maxY = 66.1;
        double minZ = 9.4, maxZ = 10.6;

        // Line passing straight through center: (10, 65, 0) to (10, 65, 20)
        boolean hitDirect = BowAimAssist.intersectsAABB(10.0, 65.0, 0.0, 10.0, 65.0, 20.0, minX, minY, minZ, maxX, maxY, maxZ);
        if (!hitDirect) {
            throw new AssertionError("Expected direct ray to intersect AABB");
        }

        // Line completely missing: (20, 65, 0) to (20, 65, 20)
        boolean hitMiss = BowAimAssist.intersectsAABB(20.0, 65.0, 0.0, 20.0, 65.0, 20.0, minX, minY, minZ, maxX, maxY, maxZ);
        if (hitMiss) {
            throw new AssertionError("Expected missing ray to NOT intersect AABB");
        }

        // Segment stopping short of target: (10, 65, 0) to (10, 65, 5)
        boolean hitShort = BowAimAssist.intersectsAABB(10.0, 65.0, 0.0, 10.0, 65.0, 5.0, minX, minY, minZ, maxX, maxY, maxZ);
        if (hitShort) {
            throw new AssertionError("Expected short ray to NOT intersect AABB");
        }
        System.out.println("  PASS: AABB ray-box intersection tested");
    }

    private static void testAngleWrapping() {
        if (Math.abs(BowAimAssist.wrapDegrees(190.0F) - (-170.0F)) > 0.01F) {
            throw new AssertionError("wrapDegrees(190) failed");
        }
        if (Math.abs(BowAimAssist.wrapDegrees(-190.0F) - (170.0F)) > 0.01F) {
            throw new AssertionError("wrapDegrees(-190) failed");
        }
        if (Math.abs(BowAimAssist.wrapDegrees(360.0F) - 0.0F) > 0.01F) {
            throw new AssertionError("wrapDegrees(360) failed");
        }
        System.out.println("  PASS: Angle wrapping tested");
    }

    private static void testCameraNearClipping() {
        // Camera at origin (0, 0, 0) looking along +Z: lx = 0, ly = 0, lz = 1
        double camX = 0, camY = 0, camZ = 0;
        double lx = 0, ly = 0, lz = 1;

        // Case A: Segment behind camera: from Z = -2.0 to Z = 0.1
        // d2 = 0.1 <= 0.35 -> must be skipped entirely
        double[] segA = new double[] { 0, 0, -2.0, 0, 0, 0.1 };
        boolean visibleA = BowAimAssist.clipSegment(segA, camX, camY, camZ, lx, ly, lz, 0.35);
        if (visibleA) {
            throw new AssertionError("Segment behind camera should NOT be visible");
        }

        // Case B: Segment starting behind camera and extending in front: from Z = -0.1 to Z = 2.0
        // d1 = -0.1 < 0.35, d2 = 2.0 > 0.35 -> start must be clipped to Z = 0.35
        double[] segB = new double[] { 0, 0, -0.1, 0, 0, 2.0 };
        boolean visibleB = BowAimAssist.clipSegment(segB, camX, camY, camZ, lx, ly, lz, 0.35);
        if (!visibleB) {
            throw new AssertionError("Segment crossing camera should be visible after clipping");
        }
        if (Math.abs(segB[2] - 0.35) > 1e-4) {
            throw new AssertionError("Expected clipped start Z to be 0.35, got: " + segB[2]);
        }
        if (Math.abs(segB[5] - 2.0) > 1e-4) {
            throw new AssertionError("Expected end Z to remain 2.0, got: " + segB[5]);
        }

        // Case C: Segment fully ahead of camera: from Z = 2.0 to Z = 5.0
        // d1 = 2.0 >= 0.35 -> unmodified
        double[] segC = new double[] { 1.0, 2.0, 2.0, 1.0, 2.0, 5.0 };
        boolean visibleC = BowAimAssist.clipSegment(segC, camX, camY, camZ, lx, ly, lz, 0.35);
        if (!visibleC) {
            throw new AssertionError("Segment ahead should be visible");
        }
        if (Math.abs(segC[2] - 2.0) > 1e-4 || Math.abs(segC[5] - 5.0) > 1e-4) {
            throw new AssertionError("Segment ahead should not be modified");
        }

        System.out.println("  PASS: Camera near clipping tested (prevents screen-covering distortion)");
    }

    private static void testTrajectoryReset() {
        BowAimAssist.reset();
        if (BowAimAssist.getTrajCount() != 0) {
            throw new AssertionError("trajCount should be 0 after reset");
        }
        if (BowAimAssist.isHitSolid()) {
            throw new AssertionError("hitSolid should be false after reset");
        }
        if (BowAimAssist.getHitEntity() != null) {
            throw new AssertionError("hitEntity should be null after reset");
        }
        if (BowAimAssist.getHitBlockX() != 0 || BowAimAssist.getHitBlockY() != 0 || BowAimAssist.getHitBlockZ() != 0) {
            throw new AssertionError("hitBlock coordinates should be 0 after reset");
        }
        System.out.println("  PASS: Trajectory state reset tested");
    }
}
