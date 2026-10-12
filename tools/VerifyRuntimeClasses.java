public final class VerifyRuntimeClasses {
    public static void main(String[] args) throws Exception {
        ClassLoader loader = ClassLoader.getSystemClassLoader();
        Class.forName("com.yny.utils.config.YNYConfig", false, loader);
        Class.forName("com.yny.utils.modules.player.AutoArmor", false, loader);
        Class.forName("com.yny.utils.modules.player.AutoProtect", false, loader);
        Class.forName("com.yny.utils.modules.player.AutoProtectOptions", false, loader);
        Class.forName("com.yny.utils.YNYUtils", false, loader);
        Class.forName("com.yny.utils.modules.player.ArmorLog", false, loader);
        Class.forName("com.yny.utils.modules.StartupAudit", false, loader);
        Class.forName("com.yny.utils.modules.clanping.ClanPingHud", false, loader);
        Class.forName("com.yny.utils.modules.clanping.GfxWorldMarkers", false, loader);
        Class.forName("com.yny.utils.modules.clanping.PingClanName", false, loader);
        Class.forName("com.yny.targethealth.TargetHealthElement", false, loader);
        Class.forName("com.yny.targethealth.TargetClanName", false, loader);
        Class.forName("com.yny.targethealth.DistanceLog", false, loader);
        Class.forName("com.yny.targethealth.HealthEventLatch", false, loader);
        Class.forName("com.yny.targethealth.HealthEventLatch$Sample", false, loader);
        Class.forName("com.yny.targethealth.TargetGrace", false, loader);
        Class.forName("com.yny.targethealth.TargetHudRenderer", false, loader);
        Object config = Class.forName("com.yny.utils.config.YNYConfig", true, loader).getConstructor().newInstance();
        if (!config.getClass().getField("autoProtectEnabled").getBoolean(config)
                || !config.getClass().getField("autoProtectLowHealthEnabled").getBoolean(config)
                || config.getClass().getField("autoProtectToggleKey").getInt(config) != 0
                || !config.getClass().getField("pvpDiagnosticsEnabled").getBoolean(config)
                || !config.getClass().getField("clanPingHudEnabled").getBoolean(config)) {
            throw new AssertionError("log, ping HUD, and auto-protect defaults must match expected configuration");
        }
        Class.forName("com.yny.utils.modules.player.AutoProtect", false, loader)
                .getMethod("checkToggleKey", Class.forName("com.yny.utils.config.YNYConfig", false, loader),
                        Class.forName("dev.xavier.stein.loader.api.ModConfig", false, loader));
        java.lang.reflect.Method group = Class.forName("com.yny.utils.core.DebugLog", false, loader)
                .getDeclaredMethod("fileGroup", String.class);
        group.setAccessible(true);
        if (!"auto-protect".equals(group.invoke(null, "AutoProtect"))) {
            throw new AssertionError("AutoProtect log must have its own file");
        }
        Class<?> latch = Class.forName("com.yny.targethealth.HealthEventLatch", true, loader);
        Object target = new Object();
        latch.getMethod("record", Object.class, float.class, float.class).invoke(null, target, 7.0f, 2.0f);
        float health = (Float) latch.getMethod("health", float.class, Object.class).invoke(null, 20.0f, target);
        if (health != 7.0f) throw new AssertionError("health callback latch did not retain event");
        System.out.println("PASS: patched runtime classes load and verify");
    }
}
