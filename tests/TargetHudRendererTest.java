package com.yny.targethealth;

public final class TargetHudRendererTest {

    public static void main(String[] args) {
        testDimensionsAndEquipment();
        testFormatting();
        System.out.println("PASS: TargetHudRendererTest passed all checks");
    }

    private static void testDimensionsAndEquipment() {
        TargetHealthMod.settings.showArmorDurability = true;
        TargetHealthMod.settings.showHeldItem = true;

        if (TargetHudRenderer.getWidth() != 220) {
            throw new AssertionError("Width should be 220, got: " + TargetHudRenderer.getWidth());
        }

        Object target = new Object();
        Object[] emptyArmor = new Object[4];
        Object heldItem = null;

        // Naked target: no armor, no held item
        if (TargetHudRenderer.hasAnyEquipment(target, emptyArmor, heldItem)) {
            throw new AssertionError("Naked target should not have equipment");
        }
        if (TargetHudRenderer.getHeight(target, emptyArmor, heldItem) != TargetHudRenderer.BASE_HEIGHT) {
            throw new AssertionError("Naked target height should be BASE_HEIGHT (32), got: " +
                    TargetHudRenderer.getHeight(target, emptyArmor, heldItem));
        }

        // Target with only a sword
        Object sword = new Object();
        if (!TargetHudRenderer.hasAnyEquipment(target, emptyArmor, sword)) {
            throw new AssertionError("Target with sword should have equipment");
        }
        if (TargetHudRenderer.getHeight(target, emptyArmor, sword) != TargetHudRenderer.FULL_HEIGHT) {
            throw new AssertionError("Target with sword height should be FULL_HEIGHT (58), got: " +
                    TargetHudRenderer.getHeight(target, emptyArmor, sword));
        }

        // Target with only a chestplate
        Object[] armorWithChest = new Object[4];
        armorWithChest[1] = new Object();
        if (!TargetHudRenderer.hasAnyEquipment(target, armorWithChest, null)) {
            throw new AssertionError("Target with chestplate should have equipment");
        }
        if (TargetHudRenderer.getHeight(target, armorWithChest, null) != TargetHudRenderer.FULL_HEIGHT) {
            throw new AssertionError("Target with chestplate height should be FULL_HEIGHT (58), got: " +
                    TargetHudRenderer.getHeight(target, armorWithChest, null));
        }

        // Settings disabled
        TargetHealthMod.settings.showArmorDurability = false;
        TargetHealthMod.settings.showHeldItem = false;
        if (TargetHudRenderer.hasAnyEquipment(target, armorWithChest, sword)) {
            throw new AssertionError("When both settings disabled, should not report equipment");
        }
        if (TargetHudRenderer.getHeight(target, armorWithChest, sword) != TargetHudRenderer.BASE_HEIGHT) {
            throw new AssertionError("Height should be BASE_HEIGHT when toggles disabled");
        }

        // Reset settings
        TargetHealthMod.settings.showArmorDurability = true;
        TargetHealthMod.settings.showHeldItem = true;
    }

    private static void testFormatting() {
        if (!"Steve".equals(TargetHudRenderer.stripFormatting(null))) {
            throw new AssertionError("Null should format to Steve");
        }
        if (!"Player".equals(TargetHudRenderer.stripFormatting("§a§lPlayer§r"))) {
            throw new AssertionError("Formatting codes should be stripped");
        }
        if (!"[CLÃ] Player".equals(TargetHudRenderer.stripFormatting("§7[CLÃ] §fPlayer"))) {
            throw new AssertionError("Clan tag with colors should be stripped");
        }
    }
}
