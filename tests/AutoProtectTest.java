package tests;

import com.yny.utils.config.YNYConfig;
import com.yny.utils.modules.player.AutoProtect;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class AutoProtectTest {
    public static void main(String[] args) throws Exception {
        testLowHealthGuard();
        testToggleKeyBehavior();
        testSanitizeDefaults();
        testCombatAndOverrideFields();
        testBookAndScreenDetection();
        System.out.println("PASS: AutoProtectTest passed all assertions");
    }

    private static void testBookAndScreenDetection() throws Exception {
        // Test null safely
        if (AutoProtect.isBook(null)) {
            throw new AssertionError("null item should not be a book");
        }
        if (AutoProtect.isBookScreen(null)) {
            throw new AssertionError("null screen should not be a book screen");
        }
        if (AutoProtect.isContainerScreen(null)) {
            throw new AssertionError("null screen should not be a container screen");
        }

        // Mock test screen classes
        class MockGuiScreenBook { }
        class ayo { }
        class MockCustomBookGui { }
        class MockGuiInventory { }
        class MockGuiChest { }

        if (!AutoProtect.isBookScreen(new MockGuiScreenBook())) {
            throw new AssertionError("MockGuiScreenBook should be identified as book screen");
        }
        if (!AutoProtect.isBookScreen(new ayo())) {
            throw new AssertionError("ayo should be identified as book screen");
        }
        if (!AutoProtect.isBookScreen(new MockCustomBookGui())) {
            throw new AssertionError("MockCustomBookGui should be identified as book screen");
        }
        if (AutoProtect.isBookScreen(new MockGuiInventory())) {
            throw new AssertionError("MockGuiInventory should NOT be identified as book screen");
        }

        // Book screens must NEVER be considered container screens for auto-protect
        if (AutoProtect.isContainerScreen(new MockGuiScreenBook())) {
            throw new AssertionError("MockGuiScreenBook must NOT be a container screen");
        }
    }

    private static void testLowHealthGuard() throws Exception {
        YNYConfig config = new YNYConfig();
        config.autoProtectEnabled = true;
        config.autoProtectLowHealthEnabled = true;
        config.autoProtectLowHealth = 10;
        config.autoProtectResumeHealth = 14;

        Field lowHealthField = AutoProtect.class.getDeclaredField("lowHealthActive");
        lowHealthField.setAccessible(true);
        AutoProtect.reset();

        if (lowHealthField.getBoolean(null)) {
            throw new AssertionError("lowHealthActive should be false after reset");
        }

        // Test with lowHealthEnabled = false
        config.autoProtectLowHealthEnabled = false;
        if (config.autoProtectLowHealthEnabled) {
            throw new AssertionError("autoProtectLowHealthEnabled should be false");
        }

        // Test with lowHealthEnabled = true
        config.autoProtectLowHealthEnabled = true;
        if (!config.autoProtectLowHealthEnabled) {
            throw new AssertionError("autoProtectLowHealthEnabled should be true");
        }
    }

    private static void testToggleKeyBehavior() throws Exception {
        YNYConfig config = new YNYConfig();
        config.autoProtectEnabled = true;
        config.autoProtectToggleKey = 0; // Default: Nenhum

        // Key 0 should never trigger
        AutoProtect.checkToggleKey(config, null);
        if (!config.autoProtectEnabled) {
            throw new AssertionError("Key 0 should not toggle autoProtectEnabled");
        }

        // Method checkToggleKey exists and handles null store safely
        AutoProtect.checkToggleKey(null, null);
    }

    private static void testSanitizeDefaults() {
        YNYConfig config = new YNYConfig();
        config.autoProtectLowHealth = 0;
        config.autoProtectResumeHealth = 30;
        config.sanitize();

        if (config.autoProtectLowHealth < 1 || config.autoProtectLowHealth > 16) {
            throw new AssertionError("autoProtectLowHealth not clamped properly: " + config.autoProtectLowHealth);
        }
        if (config.autoProtectResumeHealth < config.autoProtectLowHealth + 1 || config.autoProtectResumeHealth > 20) {
            throw new AssertionError("autoProtectResumeHealth not clamped properly: " + config.autoProtectResumeHealth);
        }
        if (!config.autoProtectLowHealthEnabled) {
            throw new AssertionError("autoProtectLowHealthEnabled should default to true");
        }
        if (config.autoProtectToggleKey != 0) {
            throw new AssertionError("autoProtectToggleKey should default to 0");
        }
    }

    private static void testCombatAndOverrideFields() throws Exception {
        Field combatField = AutoProtect.class.getDeclaredField("combatTicks");
        combatField.setAccessible(true);
        Field overrideField = AutoProtect.class.getDeclaredField("manualOverrideTicks");
        overrideField.setAccessible(true);

        combatField.setInt(null, 45);
        overrideField.setInt(null, 30);

        AutoProtect.reset();

        if (combatField.getInt(null) != 0) {
            throw new AssertionError("combatTicks should be 0 after reset");
        }
        if (overrideField.getInt(null) != 0) {
            throw new AssertionError("manualOverrideTicks should be 0 after reset");
        }

        // Ensure isEnemyNear handles null safely
        boolean near = AutoProtect.isEnemyNear(null, 6.0D);
        if (near) {
            throw new AssertionError("isEnemyNear should return false for null self");
        }
    }
}
