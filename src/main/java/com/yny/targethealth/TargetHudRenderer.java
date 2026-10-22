package com.yny.targethealth;

import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Render;
import dev.xavier.stein.loader.api.Stacks;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Modern, compact and responsive HUD renderer for Damage Indicator.
 * Dynamically adjusts vertical size and only displays equipment when present with zero-allocation caching.
 */
public final class TargetHudRenderer {

    public static final int WIDTH = 220;
    public static final int BASE_HEIGHT = 32;
    public static final int FULL_HEIGHT = 68;

    private static final int[] OPACITIES = {90, 130, 176, 220};

    private static final Map<Integer, String> ENCH_ABBR = new HashMap<>();
    private static final Map<Integer, String> ENCH_COLOR = new HashMap<>();
    private static final Map<Integer, Integer> ENCH_PRIORITY = new HashMap<>();

    static {
        // Armor
        regEnch(0, "P", "§b", 100);       // Protection
        regEnch(1, "FP", "§c", 95);      // Fire Protection
        regEnch(2, "FF", "§a", 85);      // Feather Falling
        regEnch(3, "BP", "§6", 90);      // Blast Protection
        regEnch(4, "PP", "§e", 90);      // Projectile Protection
        regEnch(5, "Res", "§9", 70);     // Respiration
        regEnch(6, "AA", "§3", 70);      // Aqua Affinity
        regEnch(7, "Th", "§d", 80);      // Thorns
        regEnch(8, "DS", "§3", 75);      // Depth Strider

        // Weapons
        regEnch(16, "S", "§c", 100);     // Sharpness
        regEnch(17, "Sm", "§b", 95);     // Smite
        regEnch(18, "Boa", "§4", 70);    // Bane of Arthropods
        regEnch(19, "Kb", "§e", 80);     // Knockback
        regEnch(20, "F", "§6", 90);      // Fire Aspect
        regEnch(21, "Loot", "§a", 60);   // Looting

        // Bows
        regEnch(48, "P", "§c", 100);     // Power
        regEnch(49, "Pu", "§e", 80);     // Punch
        regEnch(50, "Fl", "§6", 90);     // Flame
        regEnch(51, "Inf", "§d", 90);    // Infinity

        // Tools
        regEnch(32, "Eff", "§b", 90);    // Efficiency
        regEnch(33, "Silk", "§a", 85);   // Silk Touch
        regEnch(35, "Fort", "§6", 85);   // Fortune

        // Durability
        regEnch(34, "U", "§7", 50);      // Unbreaking
    }

    private static void regEnch(int id, String abbr, String color, int prio) {
        ENCH_ABBR.put(id, abbr);
        ENCH_COLOR.put(id, color);
        ENCH_PRIORITY.put(id, prio);
    }

    private static Object[] previewArmor;
    private static Object previewSword;
    private static boolean previewInitialized;

    private static final Map<Integer, String> BADGE_CACHE = new ConcurrentHashMap<>();
    private static Method mTagListMojang, mTagListNotch;
    private static Method mCountMojang, mCountNotch;
    private static Method mGetTagMojang, mGetTagNotch;
    private static Method mGetShortMojang, mGetShortNotch;
    private static volatile boolean nbtMethodsInitialized = false;

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
                int cellH = 34;
                int gap = 3;
                int totalW = count * cellW + (count - 1) * gap;
                float startX = x + (width - totalW) / 2.0F;
                float equipY = 29;
                for (int i = 0; i < count; i++) {
                    float cX = startX + i * (cellW + gap);
                    drawItemCell(items.get(i), cX, equipY, cellW, cellH, target == null, i);
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

    private static void drawItemCell(Object stack, float cellX, float cellY, int cellW, int cellH, boolean isPreview, int itemIndex) {
        if (stack == null) return;

        // Container cell background
        Render.roundRect(cellX, cellY, cellW, cellH, 2.0F, 0x4D101822);

        // Enchantment badge
        String ench = TargetHealthMod.settings.showEnchantments ? getEnchantBadges(stack) : null;
        if (ench == null && isPreview && TargetHealthMod.settings.showEnchantments) {
            ench = getPreviewEnchant(itemIndex);
        }

        // Durability / Stack count
        String duraStr = "";
        int duraColor = 0xFFB7C5D4;
        try {
            if (Stacks.isDamageable(stack)) {
                int max = Math.max(0, Stacks.maxDamage(stack));
                int dam = Math.max(0, Math.min(max, Stacks.damage(stack)));
                int rem = max - dam;
                float pct = max > 0 ? (float)rem / (float)max : 1.0F;
                duraColor = pct > 0.50F ? 0xFFB7C5D4 : (pct > 0.20F ? 0xFFFFE066 : 0xFFFF5555);
                duraStr = String.valueOf(rem);
            } else {
                int count = Stacks.count(stack);
                if (count > 1) {
                    duraStr = "x" + count;
                    duraColor = 0xFFE0E0E0;
                }
            }
        } catch (Throwable ignored) { }

        boolean hasEnch = ench != null && !ench.isEmpty();

        // Icon position: slightly higher if item has enchantments below it
        float iconY = hasEnch ? (cellY + 1.5F) : (cellY + 4.0F);
        try {
            Render.item(stack, (int)(cellX + (cellW - 16) / 2.0F), (int)iconY, true);
        } catch (Throwable ignored) { }

        // Render Enchantment Badge (centered, 0.70x scale)
        if (hasEnch) {
            float enchScale = 0.70F;
            int enchW = (int) Math.ceil(Render.textWidth(ench) * enchScale);
            float enchX = cellX + (cellW - enchW) / 2.0F;
            Render.push();
            Render.translate(enchX, cellY + 17.5F);
            Render.scale(enchScale);
            Render.text(ench, 0, 0, 0xFFFFFFFF, true);
            Render.pop();
        }

        // Render Durability / Count (centered)
        if (!duraStr.isEmpty()) {
            float duraScale = hasEnch ? 0.70F : 0.75F;
            float duraY = hasEnch ? (cellY + 25.5F) : (cellY + 22.0F);
            int duraW = (int) Math.ceil(Render.textWidth(duraStr) * duraScale);
            float duraX = cellX + (cellW - duraW) / 2.0F;
            Render.push();
            Render.translate(duraX, duraY);
            Render.scale(duraScale);
            Render.text(duraStr, 0, 0, duraColor, true);
            Render.pop();
        }
    }

    public static String getPreviewEnchant(int itemIndex) {
        switch (itemIndex) {
            case 0: return "§bP4 §7U3";
            case 1: return "§bP4 §7U3";
            case 2: return "§bP4 §7U3";
            case 3: return "§bP4 §aFF4";
            case 4: return "§cS5 §6F2";
            default: return "§bP4";
        }
    }

    public static class EnchantmentData {
        public final int id;
        public final int level;
        public final int priority;

        public EnchantmentData(int id, int level, int priority) {
            this.id = id;
            this.level = level;
            this.priority = priority;
        }
    }

    private static synchronized void initNbtMethods(Class<?> stackCls) {
        if (nbtMethodsInitialized) return;
        nbtMethodsInitialized = true;
        try {
            try { mTagListNotch = stackCls.getMethod("p"); } catch (Throwable ignored) {}
            try { mTagListMojang = stackCls.getMethod("getEnchantmentTagList"); } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    public static List<EnchantmentData> getEnchantments(Object stack) {
        List<EnchantmentData> result = new ArrayList<>();
        if (stack == null) return result;
        try {
            if (!Stacks.isEnchanted(stack)) return result;
        } catch (Throwable ignored) {
            return result;
        }

        boolean readViaNbt = false;
        try {
            if (!nbtMethodsInitialized) initNbtMethods(stack.getClass());
            Method m = mTagListNotch != null ? mTagListNotch : mTagListMojang;
            if (m != null) {
                Object tagList = m.invoke(stack);
                if (tagList != null) {
                    if (mCountNotch == null && mCountMojang == null) {
                        try { mCountNotch = tagList.getClass().getMethod("c"); } catch (Throwable ignored) {}
                        try { mCountMojang = tagList.getClass().getMethod("tagCount"); } catch (Throwable ignored) {}
                    }
                    Method countM = mCountNotch != null ? mCountNotch : mCountMojang;
                    if (mGetTagNotch == null && mGetTagMojang == null) {
                        try { mGetTagNotch = tagList.getClass().getMethod("b", int.class); } catch (Throwable ignored) {}
                        try { mGetTagMojang = tagList.getClass().getMethod("getCompoundTagAt", int.class); } catch (Throwable ignored) {}
                    }
                    Method getTagM = mGetTagNotch != null ? mGetTagNotch : mGetTagMojang;
                    if (countM != null && getTagM != null) {
                        int count = ((Number) countM.invoke(tagList)).intValue();
                        for (int i = 0; i < count; i++) {
                            Object compound = getTagM.invoke(tagList, i);
                            if (compound != null) {
                                if (mGetShortNotch == null && mGetShortMojang == null) {
                                    try { mGetShortNotch = compound.getClass().getMethod("e", String.class); } catch (Throwable ignored) {}
                                    try { mGetShortMojang = compound.getClass().getMethod("getShort", String.class); } catch (Throwable ignored) {}
                                }
                                Method getShortM = mGetShortNotch != null ? mGetShortNotch : mGetShortMojang;
                                if (getShortM != null) {
                                    int id = ((Number) getShortM.invoke(compound, "id")).intValue();
                                    int lvl = ((Number) getShortM.invoke(compound, "lvl")).intValue();
                                    int prio = ENCH_PRIORITY.containsKey(id) ? ENCH_PRIORITY.get(id) : 10;
                                    result.add(new EnchantmentData(id, lvl, prio));
                                }
                            }
                        }
                        readViaNbt = true;
                    }
                }
            }
        } catch (Throwable ignored) {}

        if (!readViaNbt || result.isEmpty()) {
            for (Map.Entry<Integer, Integer> entry : ENCH_PRIORITY.entrySet()) {
                int id = entry.getKey();
                try {
                    int lvl = Stacks.enchantLevel(stack, id);
                    if (lvl > 0) {
                        result.add(new EnchantmentData(id, lvl, entry.getValue()));
                    }
                } catch (Throwable ignored) {}
            }
        }

        Collections.sort(result, (a, b) -> Integer.compare(b.priority, a.priority));
        return result;
    }

    public static String getEnchantBadges(Object stack) {
        if (stack == null) return null;
        int id = System.identityHashCode(stack);
        String cached = BADGE_CACHE.get(id);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }

        List<EnchantmentData> enchs = getEnchantments(stack);
        if (enchs.isEmpty()) {
            if (BADGE_CACHE.size() > 500) BADGE_CACHE.clear();
            BADGE_CACHE.put(id, "");
            return null;
        }

        StringBuilder sb = new StringBuilder();
        int count = Math.min(2, enchs.size());
        for (int i = 0; i < count; i++) {
            EnchantmentData ed = enchs.get(i);
            String abbr = ENCH_ABBR.get(ed.id);
            if (abbr == null) {
                abbr = "E" + ed.id;
            }
            String col = ENCH_COLOR.get(ed.id);
            if (col == null) {
                col = "§f";
            }
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(col).append(abbr);
            if (ed.id != 51 && ed.id != 33 && ed.id != 50) {
                sb.append(ed.level);
            }
        }
        String result = sb.toString();
        if (BADGE_CACHE.size() > 500) BADGE_CACHE.clear();
        BADGE_CACHE.put(id, result);
        return result;
    }

    private static List<Object> getItemsToDraw(Object target, Object[] armor, Object heldItem) {
        List<Object> items = new ArrayList<>(5);
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
        if (input.indexOf('§') == -1) {
            String trimmed = input.trim();
            return trimmed.isEmpty() ? "Steve" : trimmed;
        }
        StringBuilder sb = new StringBuilder(input.length());
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
