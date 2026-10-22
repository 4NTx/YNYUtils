package tests;

import com.yny.targethealth.TargetHeartHealth;

public class TargetHeartHealthTest {

    public static void main(String[] args) {
        System.out.println("=== Testing TargetHeartHealth Logic ===");

        // 1. Test heart after value
        testValue("BolsonaroJair [14 ❤]", 14.0F);
        testValue("Nick 14.5 ❤", 14.5F);
        testValue("Nick (18❤)", 18.0F);
        testValue("Nick [14/20 ❤]", 14.0F);

        // 2. Test heart before value
        testValue("BolsonaroJair §c❤ 14", 14.0F);
        testValue("❤14", 14.0F);
        testValue("❤: 20", 20.0F);
        testValue("HP: 16.0", 16.0F);
        testValue("§7[§4♥ §c8§7]", 8.0F);

        // 3. Test text with no heart
        testNull("BolsonaroJair");
        testNull("[TdP] BolsonaroJair");

        System.out.println("ALL TARGET HEART HEALTH TESTS PASSED!");
    }

    private static void testValue(String text, float expected) {
        Float val = TargetHeartHealth.parseHeartValue(text);
        if (val == null) {
            throw new AssertionError("Expected " + expected + " for '" + text + "', but got null");
        }
        if (Math.abs(val.floatValue() - expected) > 0.01F) {
            throw new AssertionError("Expected " + expected + " for '" + text + "', but got " + val);
        }
        System.out.println("  PASS: '" + text + "' -> " + val);
    }

    private static void testNull(String text) {
        Float val = TargetHeartHealth.parseHeartValue(text);
        if (val != null) {
            throw new AssertionError("Expected null for '" + text + "', but got " + val);
        }
        System.out.println("  PASS: '" + text + "' -> null (no heart)");
    }
}
