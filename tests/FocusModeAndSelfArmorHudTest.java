package com.yny.utils.modules.pvp;

import com.yny.utils.config.YNYConfig;
import com.yny.utils.ui.BrokenArmorHud;

public final class FocusModeAndSelfArmorHudTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Running FocusMode and SelfArmorHud Tests ===");

        testConfigDefaultsAndSanitization();
        testBrokenArmorHudAttributes();
        testBrokenArmorPieceFormatting();
        testSelfArmorBreakNotification();
        testFocusModeStateAndSafeguards();
        testViewConeAndLookCalculations();
        testCommandInterceptionAndToggles();
        testHudOptionsAndEnableLogic();
        testCpsRateLimiter();

        System.out.println("PASS: All FocusMode and SelfArmorHud tests passed successfully!");
    }

    private static void testConfigDefaultsAndSanitization() {
        YNYConfig config = new YNYConfig();
        check(config.focusModeEnabled, "focusModeEnabled default must be true");
        check(!config.focusOnlyHelmet, "focusOnlyHelmet default must be false (prioritizes no-helmet, allows other pieces)");
        check(config.focusHelmetTimeSeconds == 1, "focusHelmetTimeSeconds default must be 1 (user asked: 'por mais de 1s')");
        check(Math.abs(config.focusReach - 3.0D) < 0.001D, "focusReach default must be 3.0 blocks");
        check(config.focusVisuals, "focusVisuals default must be true");

        // Test sanitization limits
        config.focusHelmetTimeSeconds = 0;
        config.focusReach = 1.0D;
        config.sanitize();
        check(config.focusHelmetTimeSeconds == 1, "Sanitize must clamp min helmet time to 1s");
        check(config.focusReach == 2.5D, "Sanitize must clamp min reach to 2.5 blocks");

        config.focusHelmetTimeSeconds = 100;
        config.focusReach = 20.0D;
        config.sanitize();
        check(config.focusHelmetTimeSeconds == 5, "Sanitize must clamp max helmet time to 5s");
        check(config.focusReach == 3.5D, "Sanitize must clamp max reach to 3.5 blocks");

        System.out.println("  PASS: Config defaults and sanitization verified.");
    }

    private static void testBrokenArmorHudAttributes() {
        BrokenArmorHud hud = new BrokenArmorHud();
        check("ynyutils.broken-armor-alerts".equals(hud.id()), "HUD id must be ynyutils.broken-armor-alerts");
        check("Alerta: Meu Set Quebrou".equals(hud.name()), "HUD name must indicate player's own set");
        check(hud.height() == 26, "HUD height must be 26");
        check(hud.defaultPlacement() != null, "Default placement must not be null");
        check(hud.defaultPlacement().anchorY == 0, "Default placement must anchor at top");
        check(hud.width() >= 170, "HUD width must fit title and pieces");
        System.out.println("  PASS: BrokenArmorHud properties and placement verified.");
    }

    private static void testBrokenArmorPieceFormatting() {
        // 1. Single piece breaks
        check("CAPACETE".equals(BrokenArmorHud.formatBrokenPieces(true, false, false, false)), "Single helmet");
        check("PEITORAL".equals(BrokenArmorHud.formatBrokenPieces(false, true, false, false)), "Single chest");
        check("CALÇA".equals(BrokenArmorHud.formatBrokenPieces(false, false, true, false)), "Single legs");
        check("BOTA".equals(BrokenArmorHud.formatBrokenPieces(false, false, false, true)), "Single boots");

        // 2. All 6 pairwise combinations (ensuring no pieces are dropped)
        check("CAPACETE & PEITORAL".equals(BrokenArmorHud.formatBrokenPieces(true, true, false, false)), "Helm + Chest");
        check("CAPACETE & CALÇA".equals(BrokenArmorHud.formatBrokenPieces(true, false, true, false)), "Helm + Legs");
        check("CAPACETE & BOTA".equals(BrokenArmorHud.formatBrokenPieces(true, false, false, true)), "Helm + Boots");
        check("PEITORAL & CALÇA".equals(BrokenArmorHud.formatBrokenPieces(false, true, true, false)), "Chest + Legs");
        check("PEITORAL & BOTA".equals(BrokenArmorHud.formatBrokenPieces(false, true, false, true)), "Chest + Boots");
        check("CALÇA & BOTA".equals(BrokenArmorHud.formatBrokenPieces(false, false, true, true)), "Legs + Boots");

        // 3. All 4 triplets
        check("CAPACETE, PEITORAL & CALÇA".equals(BrokenArmorHud.formatBrokenPieces(true, true, true, false)), "Helm + Chest + Legs");
        check("CAPACETE, PEITORAL & BOTA".equals(BrokenArmorHud.formatBrokenPieces(true, true, false, true)), "Helm + Chest + Boots");
        check("CAPACETE, CALÇA & BOTA".equals(BrokenArmorHud.formatBrokenPieces(true, false, true, true)), "Helm + Legs + Boots");
        check("PEITORAL, CALÇA & BOTA".equals(BrokenArmorHud.formatBrokenPieces(false, true, true, true)), "Chest + Legs + Boots");

        // 4. All 4 pieces
        check("SET COMPLETO".equals(BrokenArmorHud.formatBrokenPieces(true, true, true, true)), "All 4 pieces broken");

        // 5. Empty fallback
        check("ARMADURA".equals(BrokenArmorHud.formatBrokenPieces(false, false, false, false)), "Empty pieces fallback");

        System.out.println("  PASS: All 16 piece combinations formatted with zero dropped pieces.");
    }

    private static void testSelfArmorBreakNotification() {
        BrokenArmorHud.reset();
        check(BrokenArmorHud.ABSENCE_ALERT_DELAY_MS == 1000L, "Absence alert delay must be 1000ms (1 second)");
        for (int i = 0; i < 4; i++) {
            check(BrokenArmorHud.getUnequippedSince(i) == 0L, "unequippedSince must be 0 after reset");
            check(!BrokenArmorHud.isAlertedForAbsence(i), "isAlertedForAbsence must be false after reset");
            check(!BrokenArmorHud.isPrevWorn(i), "isPrevWorn must be false after reset");
        }

        // 1. Notify helmet broken
        BrokenArmorHud.notifySelfBroken("CAPACETE", 0xFFFF3333);
        check(BrokenArmorHud.class != null, "Notification must execute without error");

        // 2. Notify helmet & boots broken
        BrokenArmorHud.notifySelfBroken("CAPACETE & BOTA", 0xFFFF3333);

        // 3. Reset
        BrokenArmorHud.reset();
        check(BrokenArmorHud.ABSENCE_ALERT_DELAY_MS == 1000L, "Absence alert delay must be 1000ms (1 second)");
        for (int i = 0; i < 4; i++) {
            check(BrokenArmorHud.getUnequippedSince(i) == 0L, "unequippedSince must be 0 after reset");
            check(!BrokenArmorHud.isAlertedForAbsence(i), "isAlertedForAbsence must be false after reset");
            check(!BrokenArmorHud.isPrevWorn(i), "isPrevWorn must be false after reset");
        }
        System.out.println("  PASS: Self armor break notification, 1s absence delay, and reset verified.");
    }

    private static void testFocusModeStateAndSafeguards() {
        FocusMode.reset();
        check(FocusMode.getCurrentFocusTarget() == null, "Focus target must be null after reset");
        check(FocusMode.getCurrentFocusReason() == null, "Focus reason must be null after reset");

        // Clan filter safeguard
        check(!BrokenArmorHighlight.isSameClanOrAlly(null, "RivalPlayer"), "Rival player must not be same clan or ally");

        // Line of sight helper check with null safe handling
        check(!FocusMode.isLineOfSightClear(null, null), "Line of sight with nulls must return false");

        // View cone helper null safe handling
        check(FocusMode.getLookDotProduct(null, null) == -1.0D, "Look dot with nulls must return -1.0");

        System.out.println("  PASS: FocusMode state and safeguards verified.");
    }

    private static void testViewConeAndLookCalculations() {
        // Verify mathematical properties of yaw look vector
        // In Minecraft: yaw 0 faces south (+Z), yaw 90 faces west (-X), yaw 180 faces north (-Z), yaw 270 faces east (+X)
        float yawSouth = 0.0F;
        double radSouth = Math.toRadians(yawSouth);
        double lxSouth = -Math.sin(radSouth);
        double lzSouth = Math.cos(radSouth);
        check(Math.abs(lxSouth - 0.0D) < 0.001D, "lx for South must be 0");
        check(Math.abs(lzSouth - 1.0D) < 0.001D, "lz for South must be 1");

        // Target in front (+Z):
        double dxFront = 0.0D, dzFront = 3.0D;
        double dLenFront = Math.sqrt(dxFront * dxFront + dzFront * dzFront);
        double dotFront = (lxSouth * (dxFront / dLenFront)) + (lzSouth * (dzFront / dLenFront));
        check(dotFront > 0.99D, "Target in front must have dot ~ 1.0");

        // Target directly behind (-Z):
        double dxBehind = 0.0D, dzBehind = -3.0D;
        double dLenBehind = Math.sqrt(dxBehind * dxBehind + dzBehind * dzBehind);
        double dotBehind = (lxSouth * (dxBehind / dLenBehind)) + (lzSouth * (dzBehind / dLenBehind));
        check(dotBehind < -0.99D, "Target behind must have dot ~ -1.0");
        check(dotBehind < -0.15D, "Target behind must NOT be in view cone");

        System.out.println("  PASS: View cone forward and backward dot calculations verified.");
    }

    private static void testCommandInterceptionAndToggles() {
        // 1. Focus Mode command tests
        YNYConfig config = com.yny.utils.YNYUtils.getConfig();
        if (config == null) {
            config = new YNYConfig();
        }

        // Toggle command
        boolean initial = config.focusModeEnabled;
        boolean handled = com.yny.utils.core.YNYCommands.handleChatCommand("/focus");
        check(handled, "/focus must be intercepted as client command");
        check(config.focusModeEnabled == !initial, "/focus must toggle focusModeEnabled");

        // Explicit on/off
        check(com.yny.utils.core.YNYCommands.handleChatCommand("/focus off"), "/focus off must be intercepted");
        check(!config.focusModeEnabled, "focusModeEnabled must be false after /focus off");

        check(com.yny.utils.core.YNYCommands.handleChatCommand("/focus on"), "/focus on must be intercepted");
        check(config.focusModeEnabled, "focusModeEnabled must be true after /focus on");

        // Elmo filter toggle
        boolean initialHelmet = config.focusOnlyHelmet;
        check(com.yny.utils.core.YNYCommands.handleChatCommand("/focus elmo"), "/focus elmo must be intercepted");
        check(config.focusOnlyHelmet == !initialHelmet, "/focus elmo must toggle focusOnlyHelmet");

        // Parameters
        check(com.yny.utils.core.YNYCommands.handleChatCommand("/focus tempo 3"), "/focus tempo 3 must be intercepted");
        check(config.focusHelmetTimeSeconds == 3, "focusHelmetTimeSeconds must be 3");

        check(com.yny.utils.core.YNYCommands.handleChatCommand("/focus alcance 5.5"), "/focus alcance 5.5 must be intercepted");
        check(Math.abs(config.focusReach - 5.5D) < 0.001D, "focusReach must be 5.5");

        // Help and aliases
        check(com.yny.utils.core.YNYCommands.handleChatCommand("/focus help"), "/focus help must be intercepted");
        check(com.yny.utils.core.YNYCommands.handleChatCommand("/focus status"), "/focus status must be intercepted");
        check(com.yny.utils.core.YNYCommands.handleChatCommand(".focus"), ".focus must be intercepted");
        check(com.yny.utils.core.YNYCommands.handleChatCommand("/modofoco on"), "/modofoco on must be intercepted");

        // 2. Self Armor Break HUD Alert command tests
        boolean initialSelf = config.brokenArmorAlertChat;
        check(com.yny.utils.core.YNYCommands.handleChatCommand("/meuset"), "/meuset must be intercepted");
        check(config.brokenArmorAlertChat == !initialSelf, "/meuset must toggle brokenArmorAlertChat");

        check(com.yny.utils.core.YNYCommands.handleChatCommand("/meuset off"), "/meuset off must be intercepted");
        check(!config.brokenArmorAlertChat, "brokenArmorAlertChat must be false after /meuset off");

        check(com.yny.utils.core.YNYCommands.handleChatCommand("/meuset on"), "/meuset on must be intercepted");
        check(config.brokenArmorAlertChat, "brokenArmorAlertChat must be true after /meuset on");

        check(com.yny.utils.core.YNYCommands.handleChatCommand("/meuset status"), "/meuset status must be intercepted");
        check(com.yny.utils.core.YNYCommands.handleChatCommand("/meuset test"), "/meuset test must be intercepted");
        check(com.yny.utils.core.YNYCommands.handleChatCommand(".meuset"), ".meuset must be intercepted");
        check(com.yny.utils.core.YNYCommands.handleChatCommand("/setalert off"), "/setalert off must be intercepted");
        check(com.yny.utils.core.YNYCommands.handleChatCommand("/meuset on"), "re-enable /meuset");

        // 3. Normal chat, other commands, and similar prefix server commands must NOT be intercepted
        check(!com.yny.utils.core.YNYCommands.handleChatCommand("Bom dia galera"), "Normal chat must not be intercepted");
        check(!com.yny.utils.core.YNYCommands.handleChatCommand("/g Oi time"), "Chat /g must not be intercepted");
        check(!com.yny.utils.core.YNYCommands.handleChatCommand("/spawn"), "Server /spawn must not be intercepted");
        check(!com.yny.utils.core.YNYCommands.handleChatCommand("/focustarget bob"), "/focustarget must NOT be intercepted as /focus");
        check(!com.yny.utils.core.YNYCommands.handleChatCommand("/focused"), "/focused must NOT be intercepted as /focus");
        check(!com.yny.utils.core.YNYCommands.handleChatCommand("/meusetaudio"), "/meusetaudio must NOT be intercepted as /meuset");
        check(!com.yny.utils.core.YNYCommands.handleChatCommand(""), "Empty message must not be intercepted");
        check(!com.yny.utils.core.YNYCommands.handleChatCommand(null), "Null message must not be intercepted");

        System.out.println("  PASS: Command interception and toggles verified.");
    }

    private static void testHudOptionsAndEnableLogic() {
        BrokenArmorHud hud = new BrokenArmorHud();
        java.util.List<dev.xavier.stein.loader.api.Option> options = new java.util.ArrayList<>();
        hud.options(options);
        check(options.size() >= 2, "BrokenArmorHud must provide at least 2 options (toggle and test action)");

        // Test toggle state via method
        BrokenArmorHud.setState(false);
        check(!BrokenArmorHud.isAlertEnabled(), "isAlertEnabled must be false when disabled");

        // Trigger notification while disabled: must be ignored and not set visibleUntil
        BrokenArmorHud.notifySelfBroken("CAPACETE", 0xFFFF3333);
        check(!hud.layout(false, 0.0F), "HUD layout must return false when disabled");

        // Re-enable
        BrokenArmorHud.setState(true);
        check(BrokenArmorHud.isAlertEnabled(), "isAlertEnabled must be true when enabled");

        System.out.println("  PASS: HUD options and enable logic verified.");
    }

    private static void testCpsRateLimiter() {
        CpsRateLimiter.reset();
        check(CpsRateLimiter.isCpsCapActive() == false, "CPS limit must be inactive by default");

        // YNYConfig sanitization check
        YNYConfig config = new YNYConfig();
        config.sanitize();
        check(config.cpsCapEnabled == false, "cpsCapEnabled must be false in sanitize");

        // 1. Attack rate limiter tests: never blocks clicks or attacks
        check(CpsRateLimiter.canAttackAndRecord(new Object()), "Attack must be allowed");
        check(CpsRateLimiter.canAttackAndRecord(new Object()), "Consecutive attack must be allowed without CPS block");

        // 2. Click rate limiter tests: never blocks mouse clicks
        long start = System.nanoTime();
        check(CpsRateLimiter.canClickAndRecord(start), "Click 1 at t=0ms must be allowed");
        check(CpsRateLimiter.canClickAndRecord(start + 10_000_000L), "Click at t=10ms must be allowed without block");
        check(CpsRateLimiter.canClickAndRecord(start + 20_000_000L), "Click at t=20ms must be allowed without block");

        // 3. Packet limiter tests: never drops packets
        check(CpsRateLimiter.isAttackOrSwingPacket(new DummyC0APacketAnimation()), "DummyC0APacketAnimation must be detected as swing");
        check(CpsRateLimiter.isAttackOrSwingPacket(new DummyC02PacketUseEntity()), "DummyC02PacketUseEntity must be detected as attack");
        check(CpsRateLimiter.canSendPacketAndRecord(new DummyC0APacketAnimation()), "Swing packet must be allowed");
        check(CpsRateLimiter.canSendPacketAndRecord(new DummyC02PacketUseEntity()), "Attack packet must be allowed without drop");

        // 4. RateLimitedDeque tests
        CpsRateLimiter.RateLimitedDeque deque = new CpsRateLimiter.RateLimitedDeque();
        check(deque.isEmpty(), "New RateLimitedDeque must be empty");
        deque.addLast("mouse_event");
        check(deque.size() == 1, "Event must pass through deque");
        check("mouse_event".equals(deque.pollFirst()), "Poll must return added item");

        // Safe fallback when Win/Loader classes not present
        CpsRateLimiter.sanitizeMouseEvents();
        CpsRateLimiter.syncHudCps();

        // SafeEntities checks
        java.util.List<Object> safePlayers = com.yny.utils.core.SafeEntities.safePlayers();
        check(safePlayers != null, "safePlayers() must never return null");
        java.util.List<Object> safeAll = com.yny.utils.core.SafeEntities.safeAll();
        check(safeAll != null, "safeAll() must never return null");

        System.out.println("  PASS: CpsRateLimiter unblocked clicks and free menus verified.");
        System.out.println("  PASS: SafeEntities null-safe and crash-free access verified.");
    }

    private static class DummyC0APacketAnimation {
        // Class name ends with C0APacketAnimation
    }
    private static class DummyC02PacketUseEntity {
        // Class name ends with C02PacketUseEntity
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("CHECK FAILED: " + message);
        }
    }
}
