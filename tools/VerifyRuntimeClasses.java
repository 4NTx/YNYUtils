public final class VerifyRuntimeClasses {
    public static void main(String[] args) throws Exception {
        ClassLoader loader = ClassLoader.getSystemClassLoader();
        Class.forName("com.yny.utils.config.YNYConfig", false, loader);
        Class.forName("com.yny.utils.modules.player.AutoArmor", false, loader);
        Class.forName("com.yny.utils.modules.player.ArmorLog", false, loader);
        Class.forName("com.yny.utils.modules.clanping.ClanPingHud", false, loader);
        Class.forName("com.yny.utils.modules.clanping.PingClanName", false, loader);
        Class.forName("com.yny.targethealth.TargetHealthElement", false, loader);
        Class.forName("com.yny.targethealth.DistanceLog", false, loader);
        Object config = Class.forName("com.yny.utils.config.YNYConfig", true, loader).getConstructor().newInstance();
        if (!config.getClass().getField("pvpDiagnosticsEnabled").getBoolean(config)
                || !config.getClass().getField("clanPingHudEnabled").getBoolean(config)) {
            throw new AssertionError("log and ping HUD defaults must be enabled");
        }
        System.out.println("PASS: patched runtime classes load and verify");
    }
}
