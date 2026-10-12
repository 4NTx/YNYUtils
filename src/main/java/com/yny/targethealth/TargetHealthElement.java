package com.yny.targethealth;

import com.yny.utils.core.DebugLog;
import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.HudElement;
import dev.xavier.stein.loader.api.HudPlacement;
import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Relations;
import dev.xavier.stein.loader.api.Render;
import dev.xavier.stein.loader.api.Stacks;
import dev.xavier.stein.loader.api.Targeting;
import dev.xavier.stein.loader.api.Targeting.Hit;
import dev.xavier.stein.loader.api.Targeting.Query;
import dev.xavier.stein.loader.api.Targeting.Ray;
import java.util.Locale;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.item.ItemStack;

public final class TargetHealthElement implements HudElement {
   private static final int MIN_WIDTH = 250;
   private static final int BAR_HEIGHT = 6;
   private static final double[] RANGES = new double[]{8.0D, 16.0D, 32.0D, 48.0D, 64.0D};
   private static final int[] OPACITIES = new int[]{90, 130, 176, 220};
   private static final String[] ARMOR_LABELS = new String[]{"CAP", "PEI", "CAL", "BOT"};
   private EntityLivingBase target;
   private String name = "Target";
   private String healthText = "20 / 20";
   private String heldName = "Mão vazia";
   private String heldDurability = "";
   private String distanceText = "";
   private float health = 20.0F;
   private float maximum = 20.0F;
   private float absorption;
   private int armorPoints;
   private float animatedHealth = 20.0F;
   private float damageTrailHealth = 20.0F;
   private ItemStack heldItem;
   private final ItemStack[] armor = new ItemStack[4];
   private EntityLivingBase distantTarget;
   private int scanCooldown;
   private long animationAt;

   public void updateDistantTarget() {
      Object self = Entities.self();
      if (self == null || !TargetHealthMod.settings.showDistantTargets) {
         distantTarget = null;
         return;
      }
      Object direct = directTarget();
      if (direct != null && !isIgnoredTarget(direct)) {
         distantTarget = null;
         scanCooldown = 0;
         return;
      }
      if (scanCooldown > 0) {
         scanCooldown--;
         return;
      }
      double reach = range();
      scanCooldown = reach >= 48.0D ? 3 : 1;
      Query query = Targeting.query().reach(reach).living().margin(0.3D).cutAtBlock().exclude(self);
      if (TargetHealthMod.settings.ignoreLeaves) query.passThrough(Targeting.FOLIAGE);
      distantTarget = firstDisplayableTarget(Targeting.raycast(query));
   }

   public void updateTarget() {
      Object candidate = TargetGrace.choose(findTarget(), target);
      if (!(candidate instanceof EntityLivingBase)) {
         if (target != null) DebugLog.state("DamageIndicator", "target", "none");
         target = null;
         heldItem = null;
         heldName = "Mão vazia";
         heldDurability = "";
         distanceText = "";
         armorPoints = 0;
         for (int i = 0; i < armor.length; i++) armor[i] = null;
         advanceAnimation(System.currentTimeMillis());
         return;
      }
      EntityLivingBase next = (EntityLivingBase)candidate;
      boolean changed = next != target;
      target = next;
      name = TargetClanName.display(next);
      if (changed) DebugLog.state("DamageIndicator", "target", name);
      float nextHealth = Math.max(0.0F, HealthEventLatch.health(Entities.health(next), next));
      float nextMaximum = Math.max(1.0F, Entities.maxHealth(next));
      float nextAbsorption = Math.max(0.0F, HealthEventLatch.absorption(Entities.absorption(next), next));
      armorPoints = Math.max(0, Entities.armor(next));
      acceptHealth(nextHealth, nextMaximum, nextAbsorption, changed, System.currentTimeMillis());
      Object self = Entities.self();
      distanceText = self == null ? "" : String.format(Locale.ROOT, "%.1fm", (double)Entities.distance(self, next));
      readEquipment(next);
      DebugLog.state("DamageIndicator", "distance", DistanceLog.bucket(distanceText));
      DebugLog.state("DamageIndicator", "held", heldName + "; durability=" + heldDurability);
      DebugLog.state("DamageIndicator", "armor", armorSummary());
   }

   public void onHealthChanged(Object entity, float oldHealth, float newHealth, float oldAbsorption,
                               float newAbsorption) {
      if (entity == null || entity != target) return;
      HealthEventLatch.record(entity, newHealth, newAbsorption);
      acceptHealth(Math.max(0.0F, newHealth), Math.max(1.0F, Entities.maxHealth(entity)),
              Math.max(0.0F, newAbsorption), false, System.currentTimeMillis());
   }

   private void acceptHealth(float nextHealth, float nextMaximum, float nextAbsorption,
                             boolean changed, long now) {
      float priorHealth = health;
      health = Math.min(nextHealth, nextMaximum);
      maximum = nextMaximum;
      absorption = nextAbsorption;
      healthText = format(health) + " / " + format(maximum);
      if (Math.abs(health - priorHealth) > 0.01F) DebugLog.write("DamageIndicator", "health=" + format(priorHealth) + "->" + format(health) + "; max=" + format(maximum) + "; absorption=" + format(absorption));
      if (changed || animationAt == 0L) {
         animatedHealth = health;
         damageTrailHealth = health;
         animationAt = now;
      } else {
         if (health < priorHealth) damageTrailHealth = Math.max(damageTrailHealth, animatedHealth);
         if (health > priorHealth) {
            animatedHealth = Math.max(animatedHealth, health);
            damageTrailHealth = Math.max(damageTrailHealth, health);
         }
         advanceAnimation(now);
      }
   }

   private void advanceAnimation(long now) {
      if (animationAt == 0L) {
         animationAt = now;
         animatedHealth = health;
         damageTrailHealth = health;
         return;
      }
      float elapsed = Math.max(0.0F, Math.min(0.20F, (now - animationAt) / 1000.0F));
      animationAt = now;
      if (animatedHealth > health) animatedHealth = Math.max(health, animatedHealth - elapsed * 42.0F);
      else if (animatedHealth < health) animatedHealth = Math.min(health, animatedHealth + elapsed * 70.0F);
      if (animatedHealth >= health) damageTrailHealth = Math.max(health, damageTrailHealth - elapsed * 10.0F);
   }

   private void readEquipment(EntityLivingBase living) {
      heldItem = living.getHeldItem();
      heldName = heldItem == null ? "Mão vazia / sem dados" : safeStackName(heldItem);
      heldDurability = heldItem == null ? "" : durability(heldItem);
      for (int i = 0; i < armor.length; i++) armor[i] = living.getCurrentArmor(3 - i);
   }

   private String armorSummary() {
      StringBuilder value = new StringBuilder();
      for (int i = 0; i < armor.length; i++) {
         if (i > 0) value.append(" | ");
         ItemStack stack = armor[i];
         value.append(ARMOR_LABELS[i]).append("=").append(stack == null ? "empty" : safeStackName(stack) + "; durability=" + durability(stack));
      }
      return value.toString();
   }

   private static String durability(ItemStack stack) {
      if (stack == null || !Stacks.isDamageable(stack)) return "Sem desgaste";
      int max = Math.max(0, Stacks.maxDamage(stack));
      if (max == 0) return "Sem desgaste";
      int damage = Math.max(0, Math.min(max, Stacks.damage(stack)));
      return (max - damage) + "/" + max;
   }

   private static String safeStackName(ItemStack stack) {
      try {
         String value = Stacks.name(stack);
         return value == null || value.trim().isEmpty() ? "Item" : value;
      } catch (Throwable ignored) { return "Item"; }
   }

   private static String safeDisplayName(EntityLivingBase entity) {
      try {
         String value = Entities.displayName(entity);
         if (value != null && !value.trim().isEmpty()) return value;
      } catch (Throwable ignored) { }
      String value = Entities.name(entity);
      return value == null || value.trim().isEmpty() ? "Alvo" : value;
   }

   private EntityLivingBase findTarget() {
      Object direct = directTarget();
      if (direct instanceof EntityLivingBase && !isIgnoredTarget(direct)) return (EntityLivingBase)direct;
      return distantTarget != null && !distantTarget.isDead && !isIgnoredTarget(distantTarget) ? distantTarget : null;
   }

   private static Object directTarget() {
      Object candidate = Targeting.pointedEntity();
      Object self = Entities.self();
      return candidate != null && candidate != self && Entities.isLiving(candidate) ? candidate : null;
   }

   private static EntityLivingBase firstDisplayableTarget(Ray ray) {
      if (ray == null) return null;
      for (Hit hit : ray.hits) {
         if (hit.distance >= ray.blockDistance) break;
         if (hit.entity instanceof EntityLivingBase && !isIgnoredTarget(hit.entity)) return (EntityLivingBase)hit.entity;
      }
      return null;
   }

   private static boolean isIgnoredTarget(Object entity) {
      if (TargetHealthMod.settings.ignoreArmorStands && entity instanceof EntityArmorStand) return true;
      return TargetHealthMod.settings.ignoreAllies && Relations.of(entity).friendly();
   }

   public String id() { return TargetHealthMod.ID + ".target"; }
   public String name() { return "Damage Indicator"; }

   public boolean layout(boolean preview, float partialTicks) {
      if (!TargetHealthMod.settings.enabled) return false;
      updateTarget();
      if (target == null && !preview) return false;
      if (target == null) {
         name = "Jogador [Clã]";
         health = 14.0F; maximum = 20.0F; absorption = 0.0F;
         armorPoints = 20;
         animatedHealth = 13.3F; damageTrailHealth = 19.0F;
         healthText = "14 / 20"; distanceText = "8.4m";
         heldName = "Espada de diamante"; heldDurability = "1042/1561";
      }
      return true;
   }

   public int width() {
      return MIN_WIDTH;
   }

   private int baseHeight() {
      int value = template();
      return value == 1 ? 35 : value == 2 || value == 3 ? 20 : 31;
   }

   public int height() {
      return 60;
   }

   public void draw(boolean alignRight, float partialTicks) {
      int width = width();
      int x = alignRight ? -width : 0;
      Render.roundRect(x, 0, width, height(), 4.0F, backgroundColor());
      Render.rect(x, 0, width, 1, 0xFF000000 | Option.rgb(TargetHealthMod.settings.accentColor));
      drawClassic(x, width);
      drawEquipment(x, width);
   }

   private void drawClassic(int x, int width) {
      if (TargetHealthMod.settings.showName) {
         Render.roundRect(x + 3, 2, 18, 18, 2.0F, 0xFF9C6848);
         Render.rect(x + 8, 8, 2, 2, 0xFF33251C);
         Render.rect(x + 14, 8, 2, 2, 0xFF33251C);
         Render.rect(x + 9, 14, 6, 1, 0xFF633C2B);
         int nameWidth = width - 48;
         if (TargetHealthMod.settings.showDistance && !distanceText.isEmpty()) {
            nameWidth = width - Render.textWidth(distanceText) - 45;
         }
         Render.text(Render.trim(name, nameWidth), x + 25, 5, 0xFFFFFFFF, true);
      }
      if (TargetHealthMod.settings.showDistance && !distanceText.isEmpty()) {
         Render.text(distanceText, x + width - Render.textWidth(distanceText) - 4, 5, 0xFFFFDA80, true);
      }
      if (TargetHealthMod.settings.showNumbers) {
         Render.text(healthText, x + width - Render.textWidth(healthText) - 4, 17,
                 barColor(health / maximum), true);
      }
      drawBar(x + 3, 20, width - 56, 5);
   }

   private void drawHearts(int x, int width) {
      if (TargetHealthMod.settings.showName) Render.text(Render.trim(name, width - 8), x + 3, 4, 0xFFFFFFFF, true);
      int trail = Math.min(10, (int)Math.ceil(10.0F * damageTrailHealth / maximum));
      int filled = Math.min(10, (int)Math.ceil(10.0F * animatedHealth / maximum));
      int color = barColor(health / maximum);
      for (int i = 0; i < 10; i++) Render.text("❤", x + 3 + i * 12, 17,
              i < filled ? color : i < trail ? 0xFFFF625D : 0xFF49343A, true);
      if (TargetHealthMod.settings.showNumbers) Render.text(healthText, x + 3, 27, 0xFFFFFFFF, true);
   }

   private void drawCompact(int x, int width) {
      if (TargetHealthMod.settings.showName) Render.text(Render.trim(name, width - 12), x + 3, 3, 0xFFFFFFFF, true);
      if (TargetHealthMod.settings.showNumbers) Render.text(healthText, x + width - Render.textWidth(healthText) - 4, 3, barColor(health / maximum), true);
      drawBar(x + 3, 14, width - 6, 3);
   }

   private void drawMinimal(int x, int width) {
      if (TargetHealthMod.settings.showName) Render.text(Render.trim(name, width - 12), x + 3, 2, 0xFFFFFFFF, true);
      if (TargetHealthMod.settings.showNumbers) Render.text(healthText, x + width - Render.textWidth(healthText) - 4, 2, 0xFFFFFFFF, true);
      drawBar(x + 3, 13, width - 6, 5);
   }

   private void drawBar(int x, int y, int width, int height) {
      int actual = Math.round(width * ratio(animatedHealth, maximum));
      int trail = Math.round(width * ratio(damageTrailHealth, maximum));
      Render.rect(x, y, width, height, 0xFF382B32);
      if (trail > actual) Render.rect(x + actual, y, trail - actual, height, 0xFFD54C55);
      if (actual > 0) Render.rect(x, y, actual, height, barColor(health / maximum));
      if (TargetHealthMod.settings.showAbsorption && absorption > 0.0F) {
         int extra = Math.min(width - actual, Math.round(width * ratio(absorption, maximum)));
         if (extra > 0) Render.rect(x + actual, y, extra, height, 0xFFE7BE43);
      }
   }

   private void drawEquipment(int x, int width) {
      int start = x + (width - 210) / 2;
      if (TargetHealthMod.settings.showArmorDurability) {
         for (int i = 0; i < armor.length; i++) {
            drawItemCell(armor[i], start + i * 42, 31);
         }
      }
      if (TargetHealthMod.settings.showHeldItem) {
         int handX = TargetHealthMod.settings.showArmorDurability ? start + 168 : x + (width - 42) / 2;
         drawItemCell(heldItem, handX, 31);
      }
   }

   private void drawItemCell(ItemStack stack, int x, int y) {
      if (stack == null) {
         Render.text("—", x + 15, y + 19, 0xFF728194, false);
         return;
      }
      Render.item(stack, x + 13, y, true);
      Render.text(Render.trim(durability(stack), 40), x + 1, y + 19, 0xFFB7C5D4, false);
   }

   private static float ratio(float value, float max) { return Math.max(0.0F, Math.min(value / Math.max(1.0F, max), 1.0F)); }
   private static String format(float value) { return value == Math.round(value) ? Integer.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value); }
   private static int template() { return Math.max(0, Math.min(TargetHealthMod.settings.templateIndex, 3)); }
   private static double range() { int i = TargetHealthMod.settings.rangeIndex; return RANGES[Math.max(0, Math.min(i, RANGES.length - 1))]; }
   private static int backgroundColor() { int i = Math.max(0, Math.min(TargetHealthMod.settings.opacityIndex, OPACITIES.length - 1)); return OPACITIES[i] << 24 | 0x101721; }
   private static int barColor(float percent) {
      if (!TargetHealthMod.settings.dynamicHealthColor) return 0xFF000000 | Option.rgb(TargetHealthMod.settings.healthColor);
      if (percent > 0.5F) return 0xFF45B85A;
      if (percent > 0.25F) return 0xFFE0A93B;
      return 0xFFCF4848;
   }
   public HudPlacement defaultPlacement() { return new HudPlacement(1, 1, 0, 28, 1.0F, true, false); }
}



