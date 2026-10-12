package com.yny.utils.modules;

import com.yny.utils.config.YNYConfig;
import com.yny.utils.core.DebugLog;
import java.io.File;

/** Prints the loaded build and effective feature switches once per world join. */
public final class StartupAudit {
    public static final String BUILD = "e84b36c+startup-audit";

    private StartupAudit() { }

    public static void log(YNYConfig config) {
        if (config == null) return;
        String gameDir = "unknown";
        try {
            Class<?> minecraft = Class.forName("ave");
            Object game = minecraft.getMethod("A").invoke(null);
            if (game != null) {
                File directory = (File) minecraft.getField("v").get(game);
                if (directory != null) gameDir = directory.getAbsolutePath();
            }
        } catch (Throwable ignored) { }
        DebugLog.write("System", "event=runtime_ready; build=" + BUILD
                + "; game_dir=" + gameDir
                + "; diagnostics=" + config.pvpDiagnosticsEnabled
                + "; damage_indicator=see_ynydamageindicator_config"
                + "; ally_chance=" + config.hitThroughAlliesEnabled
                + "; auto_armor=" + config.autoArmorEnabled
                + "; preventive_threshold=" + config.autoArmorPreventiveThreshold
                + "; discard_received=" + config.autoArmorDiscardReceived
                + "; invmove_release_defense=" + config.inventoryMoveSuspendUse
                + "; clan_ping=" + config.clanPingEnabled
                + "; clan_ping_hud=" + config.clanPingHudEnabled);
    }
}
