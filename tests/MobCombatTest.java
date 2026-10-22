package com.yny.utils.modules.pvp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.yny.utils.config.YNYConfig;
import com.yny.utils.modules.player.PriorityBus;
import com.yny.utils.modules.player.AutoArmorHelper;
import com.yny.utils.modules.player.ConsumableHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import dev.xavier.stein.loader.api.spi.Host;

public final class MobCombatTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Running Mob Combat & Guardian Protection Tests ===");

        installMockHost();
        testConfigDefaultsAndSanitization();
        testPriorityBus();
        testMobKnockbackFilterBasics();
        testMobAuraPrioritization();
        testMobAuraRealSelectTargets();
        testMobAuraEdgeCases();

        System.out.println("PASS: All Mob Combat & Guardian Protection tests passed successfully!");
    }

    private static void installMockHost() {
        try {
            Field instanceField = Host.class.getDeclaredField("INSTANCE");
            instanceField.setAccessible(true);
            Field modifiers = Field.class.getDeclaredField("modifiers");
            modifiers.setAccessible(true);
            modifiers.setInt(instanceField, instanceField.getModifiers() & ~java.lang.reflect.Modifier.FINAL);

            Object mock = Proxy.newProxyInstance(
                Host.class.getClassLoader(),
                new Class<?>[] { Host.class },
                (proxy, method, mArgs) -> {
                    String name = method.getName();
                    if ("entityType".equals(name) && mArgs != null && mArgs.length > 0) {
                        Object e = mArgs[0];
                        if (e instanceof DummyEntity) return ((DummyEntity) e).type;
                        return "";
                    }
                    if ("entityKind".equals(name) && mArgs != null && mArgs.length > 0) {
                        Object e = mArgs[0];
                        if (e instanceof DummyEntity && ((DummyEntity) e).guardian) return 2; // HOSTILE
                        return 0;
                    }
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == float.class) return 0.0f;
                    if (method.getReturnType() == double.class) return 0.0d;
                    return null;
                }
            );
            instanceField.set(null, mock);
        } catch (Throwable t) {
            System.out.println("  Notice: Could not inject mock Host: " + t.getMessage());
        }
    }

    private static void testConfigDefaultsAndSanitization() {
        YNYConfig config = new YNYConfig();
        check(!config.mobKnockbackEnabled, "mobKnockbackEnabled default must be false (only guardian KB is dropped)");
        check(config.guardianBeamProtect, "guardianBeamProtect default must be true");
        check(config.guardianOnlyKnockback, "guardianOnlyKnockback default must be true");
        check(config.witherKnockbackProtect, "witherKnockbackProtect default must be true");
        check(config.mobAuraEnabled, "mobAuraEnabled default must be true");
        check(config.mobAuraTargets == 2, "mobAuraTargets default must be 2");
        check(config.mobAuraGuardianPriority, "mobAuraGuardianPriority default must be true");
        check(config.mobAuraWitherPriority, "mobAuraWitherPriority default must be true");
        check(config.mobAuraReach == 3.0D, "mobAuraReach default must be 3.0D (survival legitimate reach)");
        check(config.mobAuraCps == 12, "mobAuraCps default must be 12");
        check(!config.mobAuraAttackPassives, "mobAuraAttackPassives default must be false");
        check(!config.mobAuraVisuals, "mobAuraVisuals default must be false");
        check(config.mobAuraPvpSafety, "mobAuraPvpSafety default must be true");
        check(config.mobAuraPvpThreatRadius == 6.0D, "mobAuraPvpThreatRadius default must be 6.0");
        check(config.autoArmorAlertEnabled, "autoArmorAlertEnabled default must be true");

        // Test sanitization boundaries
        config.mobAuraTargets = 99;
        config.mobAuraReach = 100.0D;
        config.mobAuraCps = 50;
        config.mobAuraPvpThreatRadius = 100.0D;
        config.sanitize();
        check(config.mobAuraTargets == 2, "Sanitize must clamp max targets to 2");
        check(config.mobAuraReach == 3.5D, "Sanitize must clamp max reach to 3.5");
        check(config.mobAuraCps == 20, "Sanitize must clamp max CPS to 20");
        check(config.mobAuraPvpThreatRadius == 12.0D, "Sanitize must clamp max pvp threat radius to 12.0");

        config.mobAuraTargets = -5;
        config.mobAuraReach = 1.0D;
        config.mobAuraCps = -1;
        config.mobAuraPvpThreatRadius = 0.5D;
        config.sanitize();
        check(config.mobAuraTargets == 1, "Sanitize must clamp min targets to 1");
        check(config.mobAuraReach == 2.5D, "Sanitize must clamp min reach to 2.5");
        check(config.mobAuraCps == 1, "Sanitize must clamp min CPS to 1");
        check(config.mobAuraPvpThreatRadius == 3.0D, "Sanitize must clamp min pvp threat radius to 3.0");

        // Test autoclick defaults & sanitization
        check(!config.autoclickEnabled, "autoclickEnabled default must be false");
        check(config.autoclickMode == 0, "autoclickMode default must be 0 (Hold)");
        check(config.autoclickCps == 12, "autoclickCps default must be 12");
        check(config.autoclickStatusHudEnabled, "autoclickStatusHudEnabled default must be true");

        config.autoclickMode = 99;
        config.autoclickCps = 999;
        config.sanitize();
        check(config.autoclickMode == 1, "Sanitize must clamp autoclickMode to 1");
        check(config.autoclickCps == 30, "Sanitize must clamp autoclickCps to 30");

        config.autoclickMode = -10;
        config.autoclickCps = -5;
        config.sanitize();
        check(config.autoclickMode == 0, "Sanitize must clamp autoclickMode to 0");
        check(config.autoclickCps == 1, "Sanitize must clamp autoclickCps to 1");

        System.out.println("  PASS: Config defaults and sanitization validated.");
    }

    private static void testPriorityBus() {
        PriorityBus.reset();
        check(!PriorityBus.isCursorBusy(), "Cursor should not be busy initially");
        check(!PriorityBus.isConsuming(), "Should not be consuming initially");
        check(PriorityBus.canDefend(), "Should be able to defend initially");
        check(PriorityBus.canUseConsumable(), "Should be able to use consumable initially");
        check(PriorityBus.canSwapArmor(), "Should be able to swap armor initially");

        // Test in-flight swap and grace window
        PriorityBus.setArmorSwapping(true);
        check(PriorityBus.isArmorSwapping(), "Armor swap should be in flight");
        check(!PriorityBus.canDefend(), "Cannot defend while armor swap in flight");
        check(!PriorityBus.canUseConsumable(), "Cannot consume while armor swap in flight");

        PriorityBus.setArmorSwapping(false);
        // Post-swap grace window of 150ms ensures packets settle before other actions proceed
        check(PriorityBus.isArmorSwapping(), "Armor swap must stay active during post-swap grace window");
        check(!PriorityBus.canDefend(), "Cannot defend during post-swap grace window");
        check(!PriorityBus.canUseConsumable(), "Cannot consume during post-swap grace window");

        // PriorityBus.reset immediately clears all state and grace window
        PriorityBus.reset();
        check(!PriorityBus.isArmorSwapping(), "Reset must clear grace window");
        check(PriorityBus.canUseConsumable(), "Can use consumable after reset");
        check(PriorityBus.canDefend(), "Can defend after reset");

        // Test ConsumableHelper integration with PriorityBus
        ConsumableHelper.resolveRestoreSlot(0, 3);
        check(PriorityBus.isConsuming(), "ConsumableHelper.resolveRestoreSlot must set PriorityBus active consumable");
        check(PriorityBus.getActiveConsumableSlot() == 3, "Active consumable slot must be 3");
        check(!PriorityBus.canDefend(), "Cannot defend while consuming via ConsumableHelper");
        check(!PriorityBus.canSwapArmor(), "Cannot swap armor while consuming via ConsumableHelper");

        ConsumableHelper.holdUseKey(false);
        check(!PriorityBus.isConsuming(), "ConsumableHelper.holdUseKey(false) must release active consumable in PriorityBus");
        check(PriorityBus.getActiveConsumableSlot() == -1, "Active consumable slot must reset to -1");
        check(PriorityBus.canSwapArmor(), "Can swap armor after ConsumableHelper releases");

        PriorityBus.setSwordSlot(0);
        check(PriorityBus.isSwordDefending(), "Sword slot >= 0 means defending");
        check(PriorityBus.getSwordSlot() == 0, "Sword slot should be 0");

        PriorityBus.reset();
        check(!PriorityBus.isArmorSwapping(), "Reset clears armor swapping");
        check(!PriorityBus.isConsuming(), "Reset clears consuming");
        check(!PriorityBus.isSwordDefending(), "Reset clears sword defending");
        check(PriorityBus.canDefend(), "Reset restores canDefend");
        check(PriorityBus.canSwapArmor(), "Reset restores canSwapArmor");

        // Cursor failsafe headless invocation safety check
        check(!AutoArmorHelper.recoverOrphanedCursor(), "recoverOrphanedCursor should return false safely in mock environment");

        // Preventive armor alert test and debounce reset
        AutoArmorHelper.reset();
        AutoArmorHelper.checkPreventiveArmorAlert(); // Must run without throwing in headless
        AutoArmorHelper.reset();

        // Zero-allocation cached instances check for all 16 bitmask states (0..15)
        for (int mask = 0; mask < 16; mask++) {
            Object b1 = (mask & 1) != 0 ? null : "boots1";
            Object l1 = (mask & 2) != 0 ? null : "legs1";
            Object c1 = (mask & 4) != 0 ? null : "chest1";
            Object h1 = (mask & 8) != 0 ? null : "helm1";

            Object b2 = (mask & 1) != 0 ? null : "boots2";
            Object l2 = (mask & 2) != 0 ? null : "legs2";
            Object c2 = (mask & 4) != 0 ? null : "chest2";
            Object h2 = (mask & 8) != 0 ? null : "helm2";

            BrokenArmorHighlight.ArmorStatus st1 = BrokenArmorHighlight.evaluateArmor(b1, l1, c1, h1);
            BrokenArmorHighlight.ArmorStatus st2 = BrokenArmorHighlight.evaluateArmor(b2, l2, c2, h2);
            check(st1 == st2, "Mask " + mask + " must return cached singleton instance");
            check(st1.hasMissingPiece == (mask != 0), "Mask " + mask + " missing piece mismatch");
        }

        // Test BrokenArmorHighlight.reset() and MobAura.reset()
        BrokenArmorHighlight.reset();
        MobAura.reset();
        check(MobAura.getCurrentTargets().isEmpty(), "MobAura.getCurrentTargets must be empty after reset");

        System.out.println("  PASS: PriorityBus coordinated bus and cursor failsafe verified.");
    }

    private static void testMobKnockbackFilterBasics() {
        check(!MobKnockbackFilter.shouldDropVelocity(null), "Null message must return false");
        check(!MobKnockbackFilter.shouldDropVelocity("NotAPacket"), "Non-velocity packet must return false");

        // Test monster categorization
        EntityZombie zombie = new EntityZombie(10);
        EntitySkeleton skeleton = new EntitySkeleton(11);
        EntityGuardian guardian = new EntityGuardian(12);
        EntityArmorStand armorStand = new EntityArmorStand(13);
        EntityWither wither = new EntityWither(14);
        EntityWitherSkull witherSkull = new EntityWitherSkull(15);

        check(MobKnockbackFilter.isMonster(zombie), "Zombie must be recognized as monster");
        check(MobKnockbackFilter.isMonster(skeleton), "Skeleton must be recognized as monster");
        check(MobKnockbackFilter.isMonster(guardian), "Guardian must be recognized as monster");
        check(MobKnockbackFilter.isMonster(wither), "Wither must be recognized as monster");
        check(MobKnockbackFilter.isGuardian(guardian), "Guardian must be recognized as guardian");
        check(!MobKnockbackFilter.isGuardian(zombie), "Zombie must NOT be recognized as guardian");
        check(MobKnockbackFilter.isWither(wither), "Wither must be recognized as wither");
        check(!MobKnockbackFilter.isWither(guardian), "Guardian must NOT be recognized as wither");
        check(!MobKnockbackFilter.isWither(zombie), "Zombie must NOT be recognized as wither");
        check(MobKnockbackFilter.isWitherSkull(witherSkull), "WitherSkull must be recognized as wither skull");
        check(!MobKnockbackFilter.isWitherSkull(wither), "Wither must NOT be recognized as wither skull");
        check(MobKnockbackFilter.isRangedMob(skeleton), "Skeleton must be recognized as ranged mob");
        check(!MobKnockbackFilter.isRangedMob(zombie), "Zombie must NOT be recognized as ranged mob");
        check(MobKnockbackFilter.isArmorStand(armorStand), "ArmorStand must be recognized as armor stand");
        check(!MobKnockbackFilter.isArmorStand(zombie), "Zombie must NOT be recognized as armor stand");

        // Test mock explosion packet reflection & zeroing
        MockExplosionPacket explosionPacket = new MockExplosionPacket(10.0, 64.0, 10.0, 4.0f, 1.5f, 0.8f, -1.2f);
        MobKnockbackFilter.zeroExplosionVelocity(explosionPacket);
        check(explosionPacket.f == 0.0f, "Explosion player motionX must be zeroed");
        check(explosionPacket.g == 0.0f, "Explosion player motionY must be zeroed");
        check(explosionPacket.h == 0.0f, "Explosion player motionZ must be zeroed");

        // Test mock packet reflection
        MockVelocityPacket packetOtherEntity = new MockVelocityPacket(999, 1000, 1000, 1000);
        // Packet for entity 999 while player id is -1/not match
        check(!MobKnockbackFilter.shouldDropVelocity(packetOtherEntity), "Packet for other entity must return false");

        MockVelocityPacket packetZero = new MockVelocityPacket(1, 0, 0, 0);
        check(!MobKnockbackFilter.shouldDropVelocity(packetZero), "Zero velocity packet must return false");

        System.out.println("  PASS: MobKnockbackFilter packet and entity classification validated.");
    }

    private static void testMobAuraPrioritization() {
        // Scenario 1: 1 Guardian at 3.5m, 1 Zombie closer at 2.0m, 1 Skeleton at 3.8m
        // Target limit = 2. Guardian priority = true.
        // Expected: Guardian is target #1 (FIRST hit), Zombie is target #2.
        DummyEntity guardian = new DummyEntity("Guardian", 101, true, false, false, false);
        DummyEntity zombie = new DummyEntity("Zombie", 102, false, false, false, false);
        DummyEntity skeleton = new DummyEntity("Skeleton", 103, false, false, false, false);

        List<MockCandidate> candidates = new ArrayList<>();
        candidates.add(new MockCandidate(guardian, 3.5D, false, true));
        candidates.add(new MockCandidate(zombie, 2.0D, false, false));
        candidates.add(new MockCandidate(skeleton, 3.8D, false, false));

        List<Object> targets = selectTargets(candidates, 2, 4.2D, false, true);
        check(targets.size() == 2, "Must select exactly 2 targets");
        check(targets.get(0) == guardian, "GUARDIAN MUST BE TARGET #1 (PRIORIDADE ABSOLUTA), even though Zombie was closer!");
        check(targets.get(1) == zombie, "Target #2 must be the closer remaining mob (Zombie)");

        // Scenario 2: 2 Guardians (3.0m and 4.0m) and 1 Zombie at 1.5m. Max targets = 2.
        // Expected: Both slots taken by Guardians, hit in order of proximity (Guardian #1 at 3m, Guardian #2 at 4m).
        DummyEntity guardian2 = new DummyEntity("Guardian2", 104, true, false, false, false);
        List<MockCandidate> multiGuardian = new ArrayList<>();
        multiGuardian.add(new MockCandidate(guardian, 3.0D, false, true));
        multiGuardian.add(new MockCandidate(guardian2, 4.0D, false, true));
        multiGuardian.add(new MockCandidate(zombie, 1.5D, false, false));

        List<Object> gTargets = selectTargets(multiGuardian, 2, 4.2D, false, true);
        check(gTargets.size() == 2, "Must select 2 targets");
        check(gTargets.get(0) == guardian, "First target must be closer Guardian");
        check(gTargets.get(1) == guardian2, "Second target must be second Guardian (both slots to Guardians)");

        // Scenario 3: 1 Guardian, but user configured max targets = 1.
        // Expected: Only Guardian selected.
        List<Object> singleTarget = selectTargets(candidates, 1, 4.2D, false, true);
        check(singleTarget.size() == 1, "Must select exactly 1 target");
        check(singleTarget.get(0) == guardian, "Single target must be the Guardian");

        // Scenario 4: No Guardians, only normal mobs (Zombie at 2.0m, Skeleton at 3.0m, Creeper at 3.5m).
        // Max targets = 2.
        DummyEntity creeper = new DummyEntity("Creeper", 105, false, false, false, false);
        List<MockCandidate> normalMobs = new ArrayList<>();
        normalMobs.add(new MockCandidate(creeper, 3.5D, false, false));
        normalMobs.add(new MockCandidate(zombie, 2.0D, false, false));
        normalMobs.add(new MockCandidate(skeleton, 3.0D, false, false));

        List<Object> normalTargets = selectTargets(normalMobs, 2, 4.2D, false, false);
        check(normalTargets.size() == 2, "Must select 2 targets");
        check(normalTargets.get(0) == zombie, "Closest mob (Zombie) must be target #1");
        check(normalTargets.get(1) == skeleton, "Second closest mob (Skeleton) must be target #2");

        // Scenario 5: WITHER PRIORITIZATION (Novo requisito: focar Wither)
        // Cenário: 1 Wither a 3.5m, 1 Guardian a 3.0m, 1 Zumbi a 2.0m
        DummyEntity witherBoss = new DummyEntity("WitherBoss", 106, false, true, false, false);
        List<MockCandidate> bossCandidates = new ArrayList<>();
        bossCandidates.add(new MockCandidate(witherBoss, 3.5D, true, false));
        bossCandidates.add(new MockCandidate(guardian, 3.0D, false, true));
        bossCandidates.add(new MockCandidate(zombie, 2.0D, false, false));

        // 5a. Focar Wither = TRUE, Focar Guardian = TRUE, MaxTargets = 2
        // Alvo #1 = Wither (prioridade máxima), Alvo #2 = Guardian (segunda prioridade)
        List<Object> witherAndGuardian = selectTargets(bossCandidates, 2, 4.2D, true, true);
        check(witherAndGuardian.size() == 2, "Must select 2 targets");
        check(witherAndGuardian.get(0) == witherBoss, "WITHER MUST BE TARGET #1 with Wither priority enabled!");
        check(witherAndGuardian.get(1) == guardian, "GUARDIAN MUST BE TARGET #2 with Guardian priority enabled!");

        // 5b. Focar Wither = TRUE, Focar Guardian = FALSE, MaxTargets = 2
        // Alvo #1 = Wither, Alvo #2 = Zumbi (mais próximo dos restantes que o Guardian)
        List<Object> witherAndZombie = selectTargets(bossCandidates, 2, 4.2D, true, false);
        check(witherAndZombie.size() == 2, "Must select 2 targets");
        check(witherAndZombie.get(0) == witherBoss, "Target #1 must be Wither");
        check(witherAndZombie.get(1) == zombie, "Target #2 must be Zombie (closer than Guardian)");

        // 5c. Focar Wither = FALSE, Focar Guardian = TRUE, MaxTargets = 2
        // Alvo #1 = Guardian, Alvo #2 = Zumbi (mais próximo dos restantes que o Wither)
        List<Object> guardianAndZombie = selectTargets(bossCandidates, 2, 4.2D, false, true);
        check(guardianAndZombie.size() == 2, "Must select 2 targets");
        check(guardianAndZombie.get(0) == guardian, "Target #1 must be Guardian");
        check(guardianAndZombie.get(1) == zombie, "Target #2 must be Zombie (closer than Wither)");

        // 5d. Focar Wither = FALSE, Focar Guardian = FALSE (sem prioridade, puramente distância)
        // Alvo #1 = Zumbi (2.0m), Alvo #2 = Guardian (3.0m)
        List<Object> distanceOnly = selectTargets(bossCandidates, 2, 4.2D, false, false);
        check(distanceOnly.size() == 2, "Must select 2 targets");
        check(distanceOnly.get(0) == zombie, "Target #1 must be closest mob (Zombie)");
        check(distanceOnly.get(1) == guardian, "Target #2 must be second closest mob (Guardian)");

        System.out.println("  PASS: MobAura strict Wither and Guardian priority and multi-targeting verified.");
    }

    private static void testMobAuraEdgeCases() {
        // Edge Case 1: Guardian is out of reach (5.0m > 4.2m)
        DummyEntity farGuardian = new DummyEntity("Guardian", 201, true, false, false, false);
        DummyEntity closeZombie = new DummyEntity("Zombie", 202, false, false, false, false);
        List<MockCandidate> candidates = new ArrayList<>();
        candidates.add(new MockCandidate(farGuardian, 5.0D, false, true));
        candidates.add(new MockCandidate(closeZombie, 2.5D, false, false));

        List<Object> targets = selectTargets(candidates, 2, 4.2D, false, true);
        check(targets.size() == 1, "Only entities within reach must be selected");
        check(targets.get(0) == closeZombie, "Far Guardian must NOT be selected if out of reach");

        // Edge Case 2: Empty candidates list
        List<Object> emptyTargets = selectTargets(new ArrayList<>(), 2, 4.2D, false, true);
        check(emptyTargets.isEmpty(), "Empty candidates must result in empty targets");

        System.out.println("  PASS: MobAura edge cases (reach cutoff, empty candidates) verified.");
    }

    private static void testMobAuraRealSelectTargets() {
        DummyEntity witherBoss = new DummyEntity("WitherBoss", 301, false, true, false, false);
        DummyEntity guardian = new DummyEntity("Guardian", 302, true, false, false, false);
        DummyEntity zombie = new DummyEntity("Zombie", 303, false, false, false, false);

        List<MobAura.Candidate> candidates = new ArrayList<>();
        candidates.add(new MobAura.Candidate(witherBoss, 3.5D, true, false));
        candidates.add(new MobAura.Candidate(guardian, 3.0D, false, true));
        candidates.add(new MobAura.Candidate(zombie, 2.0D, false, false));

        List<Object> targets = MobAura.selectTargets(candidates, 2, 4.2D, true, true);
        check(targets.size() == 2, "Real selectTargets must return 2 targets");
        check(targets.get(0) == witherBoss, "Real selectTargets #1 must be Wither");
        check(targets.get(1) == guardian, "Real selectTargets #2 must be Guardian");

        // Edge case: null self check in isEnemyPlayerNear
        check(!MobAura.isEnemyPlayerNear(null, 6.0D), "isEnemyPlayerNear(null, ...) must be false");

        System.out.println("  PASS: MobAura.selectTargets (real implementation) and isEnemyPlayerNear verified.");
    }

    /**
     * Exact replica of MobAura's target selection algorithm to test in isolation.
     */
    private static List<Object> selectTargets(List<MockCandidate> allCandidates, int maxTargets, double reach, boolean prioritizeWither, boolean prioritizeGuardian) {
        List<MockCandidate> withers = new ArrayList<>();
        List<MockCandidate> guardians = new ArrayList<>();
        List<MockCandidate> otherMobs = new ArrayList<>();

        for (MockCandidate c : allCandidates) {
            if (c.distance > reach) continue;
            if (c.isWither) {
                withers.add(c);
            } else if (c.isGuardian) {
                guardians.add(c);
            } else {
                otherMobs.add(c);
            }
        }

        Collections.sort(withers, Comparator.comparingDouble(c -> c.distance));
        Collections.sort(guardians, Comparator.comparingDouble(c -> c.distance));
        Collections.sort(otherMobs, Comparator.comparingDouble(c -> c.distance));

        List<Object> targets = new ArrayList<>(maxTargets);

        // 1. Prioridade Wither
        if (prioritizeWither) {
            for (MockCandidate w : withers) {
                if (targets.size() < maxTargets) {
                    targets.add(w.entity);
                }
            }
        }

        // 2. Prioridade Guardian
        if (prioritizeGuardian) {
            for (MockCandidate g : guardians) {
                if (targets.size() < maxTargets) {
                    targets.add(g.entity);
                }
            }
        }

        // 3. Vagas restantes preenchidas pelos outros mobs (e não priorizados)
        if (targets.size() < maxTargets) {
            List<MockCandidate> remaining = new ArrayList<>();
            if (!prioritizeWither) {
                remaining.addAll(withers);
            }
            if (!prioritizeGuardian) {
                remaining.addAll(guardians);
            }
            remaining.addAll(otherMobs);
            Collections.sort(remaining, Comparator.comparingDouble(c -> c.distance));

            for (MockCandidate c : remaining) {
                if (targets.size() < maxTargets && !targets.contains(c.entity)) {
                    targets.add(c.entity);
                }
            }
        }

        return targets;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("CHECK FAILED: " + message);
        }
    }

    public static final class MockVelocityPacket {
        public int a; // entityId
        public int b; // motionX
        public int c; // motionY
        public int d; // motionZ

        public MockVelocityPacket(int a, int b, int c, int d) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
        }
    }

    public static final class MockExplosionPacket {
        public double a;
        public double b;
        public double c;
        public float d;
        public List<?> e;
        public float f;
        public float g;
        public float h;

        public MockExplosionPacket(double a, double b, double c, float d, float f, float g, float h) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
            this.f = f;
            this.g = g;
            this.h = h;
        }
    }

    public static class DummyEntity {
        public final String type;
        public final int id;
        public final boolean guardian;
        public final boolean wither;
        public final boolean witherSkull;
        public final boolean armorStand;

        public DummyEntity(String type, int id, boolean guardian, boolean wither, boolean witherSkull, boolean armorStand) {
            this.type = type;
            this.id = id;
            this.guardian = guardian;
            this.wither = wither;
            this.witherSkull = witherSkull;
            this.armorStand = armorStand;
        }

        public int F() { return id; }
    }

    public static final class EntityZombie extends DummyEntity {
        public EntityZombie(int id) { super("Zombie", id, false, false, false, false); }
    }

    public static final class EntitySkeleton extends DummyEntity {
        public EntitySkeleton(int id) { super("Skeleton", id, false, false, false, false); }
    }

    public static final class EntityGuardian extends DummyEntity {
        public EntityGuardian(int id) { super("Guardian", id, true, false, false, false); }
    }

    public static final class EntityWither extends DummyEntity {
        public EntityWither(int id) { super("WitherBoss", id, false, true, false, false); }
    }

    public static final class EntityWitherSkull extends DummyEntity {
        public EntityWitherSkull(int id) { super("WitherSkull", id, false, false, true, false); }
    }

    public static final class EntityArmorStand extends DummyEntity {
        public EntityArmorStand(int id) { super("ArmorStand", id, false, false, false, true); }
    }

    private static final class MockCandidate {
        final Object entity;
        final double distance;
        final boolean isWither;
        final boolean isGuardian;

        MockCandidate(Object entity, double distance, boolean isWither, boolean isGuardian) {
            this.entity = entity;
            this.distance = distance;
            this.isWither = isWither;
            this.isGuardian = isGuardian;
        }
    }
}
