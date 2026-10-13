package com.yny.targethealth;

import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Render;
import dev.xavier.stein.loader.api.Stacks;
import java.util.ArrayList;
import java.util.List;

/**
 * Modern, compact and responsive HUD renderer for Damage Indicator.
 * Dynamically adjusts vertical size and only displays equipment when present.
 */
public final class TargetHudRenderer {

    public static final int WIDTH = 220;
    public static final int BASE_HEIGHT = 32;
    public static final int FULL_HEIGHT = 58;

    private static final int[] OPACITIES = {90, 130, 176, 220};

    private static Object[] previewArmor;
    private static Object previewSword;
    private static boolean previewInitialized;

    private TargetHudRenderer() {
    }

    public static int getWidth() {
        return WIDTH;
    }

    public static int getHeight(Object target, Object[] armor, Object heldItem) {
        return hasAnyEquipment(target, armor, heldItem) ? FULL_HEIGHT : BASE_HEIGHT;
    }

    public static boolean hasAnyEquipment(Object target, Object[] armor, Object heldItem) {
        if (target == null) {
            // Preview in HUD editor: show equipment if either toggle is active
            return TargetHealthMod.settings.showArmorDurability || TargetHealthMod.settings.showHeldItem;
        }
        if (TargetHealthMod.settings.showArmorDurability && armor != null) {
            for (int i = 0; i < armor.length; i++) {
                if (armor[i] != null) return true;
            }
        }
        if (TargetHealthMod.settings.showHeldItem && heldItem != null) {
            return true;
        }
        return false;
    }

    public static void draw(
            boolean alignRight,
            float partialTicks,
            Object target,
            String name,
            String distanceText,
            String healthText,
            float health,
            float maximum,
            float absorption,
            float animatedHealth,
            float damageTrailHealth,
            Object[] armor,
            Object heldItem
    ) {
        if (target != null && TargetHealthElement.isIgnoredTarget(target)) {
            return;
        }
        int width = WIDTH;
        int height = getHeight(target, armor, heldItem);
        int x = alignRight ? -width : 0;

        // Card background
        int bg = backgroundColor();
        int accent = 0xFF000000 | Option.rgb(TargetHealthMod.settings.accentColor);
        Render.roundRect(x, 0, width, height, 4.0F, bg);
        Render.rect(x, 0, width, 1.5F, accent);

        // Player skin avatar head
        float headX = x + 5;
        float headY = 5;
        float headSize = 22;

        Render.roundRect(headX - 1, headY - 1, headSize + 2, headSize + 2, 2.0F, 0x4D000000);
        String skinName = resolveSkinName(target);
        try {
            Render.playerHead(skinName, headX, headY, headSize, true);
        } catch (Throwable ignored) {
            // Fallback in case of rendering issue
            try {
                Render.playerHead("Steve", headX, headY, headSize, false);
            } catch (Throwable ignored2) { }
        }

        // Header: Name and Distance
        float contentX = headX + headSize + 5;
        String dist = (TargetHealthMod.settings.showDistance && distanceText != null) ? distanceText : "";
        int distW = dist.isEmpty() ? 0 : Render.textWidth(dist);
        if (!dist.isEmpty()) {
            Render.text(dist, x + width - distW - 5, 6, 0xFFFFDA80, true);
        }

        if (TargetHealthMod.settings.showName && name != null && !name.isEmpty()) {
            int rightGap = distW > 0 ? distW + 8 : 4;
            int availWidth = (int)((width - 5) - (contentX - x) - rightGap);
            int maxTrim = (int)(availWidth / 1.15F);
            if (maxTrim > 6) {
                String trimmed = Render.trim(name, maxTrim);
                Render.push();
                Render.translate(contentX, 5);
                Render.scale(1.15F);
                Render.text(trimmed, 0, 0, 0xFFFFFFFF, true);
                Render.pop();
            }
        }

        // Health text (scaled) & animated health bar
        String hStr = (TargetHealthMod.settings.showNumbers && healthText != null) ? healthText : "";
        int hTextW = 0;
        int hColor = barColor(health, maximum);
        if (!hStr.isEmpty()) {
            hTextW = (int)Math.ceil(Render.textWidth(hStr) * 1.15F);
            float hTextX = x + width - hTextW - 5;
            Render.push();
            Render.translate(hTextX, 17);
            Render.scale(1.15F);
            Render.text(hStr, 0, 0, hColor, true);
            Render.pop();
        }

        float barX = contentX;
        float barY = 19;
        float barH = 6;
        float barW = (x + width - 5) - (hTextW > 0 ? hTextW + 5 : 0) - barX;
        if (barW > 10) {
            drawBar(barX, barY, barW, barH, health, maximum, animatedHealth, damageTrailHealth, absorption, hColor);
        }

        // Equipment row: only drawn if target actually has equipment
        if (hasAnyEquipment(target, armor, heldItem)) {
            List<Object> items = getItemsToDraw(target, armor, heldItem);
            int count = items.size();
            if (count > 0) {
                int cellW = 36;
                int cellH = 23;
                int gap = 3;
                int totalW = count * cellW + (count - 1) * gap;
                float startX = x + (width - totalW) / 2.0F;
                float equipY = 31;
                for (int i = 0; i < count; i++) {
                    float cX = startX + i * (cellW + gap);
                    drawItemCell(items.get(i), cX, equipY, cellW, cellH);
                }
            }
        }
    }

    private static void drawBar(
            float x,
            float y,
            float width,
            float height,
            float health,
            float maximum,
            float animatedHealth,
            float damageTrailHealth,
            float absorption,
            int barColor
    ) {
        float actualRatio = ratio(animatedHealth, maximum);
        float trailRatio = ratio(damageTrailHealth, maximum);

        int actual = Math.round(width * actualRatio);
        int trail = Math.round(width * trailRatio);

        // Bar background
        Render.rect(x, y, width, height, 0xFF2B2228);

        // Damage drop trail (red)
        if (trail > actual) {
            Render.rect(x + actual, y, trail - actual, height, 0xFFD54C55);
        }

        // Active health fill
        if (actual > 0) {
            Render.rect(x, y, actual, height, barColor);
        }

        // Absorption fill (gold)
        if (TargetHealthMod.settings.showAbsorption && absorption > 0.0F) {
            int extra = Math.min((int)(width - actual), Math.round(width * ratio(absorption, maximum)));
            if (extra > 0) {
                Render.rect(x + actual, y, extra, height, 0xFFE7BE43);
            }
        }
    }

    private static void drawItemCell(Object stack, float cellX, float cellY, int cellW, int cellH) {
        if (stack == null) return;

        // Container cell background
        Render.roundRect(cellX, cellY, cellW, cellH, 2.0F, 0x4D101822);

        // Item icon centered
        try {
            Render.item(stack, (int)(cellX + (cellW - 16) / 2.0F), (int)(cellY + 1), true);
        } catch (Throwable ignored) { }

        // Durability / Stack count
        try {
            if (Stacks.isDamageable(stack)) {
                int max = Math.max(0, Stacks.maxDamage(stack));
                int dam = Math.max(0, Math.min(max, Stacks.damage(stack)));
                int rem = max - dam;
                float pct = max > 0 ? (float)rem / (float)max : 1.0F;
                int duraColor = pct > 0.50F ? 0xFFB7C5D4 : (pct > 0.20F ? 0xFFFFE066 : 0xFFFF5555);

                String full = rem + "/" + max;
                String str = Render.textWidth(full) <= cellW - 2 ? full : String.valueOf(rem);
                int strW = Render.textWidth(str);
                Render.text(str, cellX + (cellW - strW) / 2.0F, cellY + 14, duraColor, false);
            } else {
                int count = Stacks.count(stack);
                if (count > 1) {
                    String str = "x" + count;
                    int strW = Render.textWidth(str);
                    Render.text(str, cellX + (cellW - strW) / 2.0F, cellY + 14, 0xFFE0E0E0, false);
                }
            }
        } catch (Throwable ignored) { }
    }

    private static List<Object> getItemsToDraw(Object target, Object[] armor, Object heldItem) {
        List<Object> items = new ArrayList<>();
        if (target == null) {
            // Preview mode: display sample armor & weapon
            if (TargetHealthMod.settings.showArmorDurability) {
                initPreviewStacks();
                if (previewArmor != null) {
                    for (Object a : previewArmor) {
                        if (a != null) items.add(a);
                    }
                }
            }
            if (TargetHealthMod.settings.showHeldItem) {
                initPreviewStacks();
                if (previewSword != null) items.add(previewSword);
            }
            return items;
        }

        // Real target mode: ONLY add non-null items
        if (TargetHealthMod.settings.showArmorDurability && armor != null) {
            for (int i = 0; i < armor.length; i++) {
                if (armor[i] != null) items.add(armor[i]);
            }
        }
        if (TargetHealthMod.settings.showHeldItem && heldItem != null) {
            items.add(heldItem);
        }
        return items;
    }

    private static void initPreviewStacks() {
        if (previewInitialized) return;
        previewInitialized = true;
        try {
            previewArmor = new Object[4];
            previewArmor[0] = Stacks.create("diamond_helmet", 1, 0);
            previewArmor[1] = Stacks.create("diamond_chestplate", 1, 0);
            previewArmor[2] = Stacks.create("diamond_leggings", 1, 0);
            previewArmor[3] = Stacks.create("diamond_boots", 1, 0);
            previewSword = Stacks.create("diamond_sword", 1, 0);
        } catch (Throwable ignored) { }
    }

    private static String resolveSkinName(Object target) {
        String skinName = null;
        if (target != null && Entities.isPlayer(target)) {
            skinName = Entities.name(target);
        } else if (target == null) {
            Object self = Entities.self();
            if (self != null) {
                skinName = Entities.name(self);
            }
        } else if (target != null) {
            // Non-player living target (mob): try entity name
            skinName = Entities.name(target);
        }
        if (skinName == null || skinName.trim().isEmpty()) {
            skinName = "Steve";
        }
        return stripFormatting(skinName);
    }

    public static String stripFormatting(String input) {
        if (input == null) return "Steve";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '§' && i + 1 < input.length()) {
                i++;
                continue;
            }
            sb.append(c);
        }
        String clean = sb.toString().trim();
        return clean.isEmpty() ? "Steve" : clean;
    }

    private static float ratio(float value, float max) {
        return Math.max(0.0F, Math.min(value / Math.max(1.0F, max), 1.0F));
    }

    private static int barColor(float health, float maximum) {
        if (!TargetHealthMod.settings.dynamicHealthColor) {
            return 0xFF000000 | Option.rgb(TargetHealthMod.settings.healthColor);
        }
        float percent = maximum > 0.0F ? health / maximum : 1.0F;
        if (percent > 0.5F) return 0xFF45B85A;  // green
        if (percent > 0.25F) return 0xFFE0A93B; // yellow
        return 0xFFCF4848;                      // red
    }

    private static int backgroundColor() {
        int index = Math.max(0, Math.min(TargetHealthMod.settings.opacityIndex, OPACITIES.length - 1));
        return (OPACITIES[index] << 24) | 0x101721;
    }
}
