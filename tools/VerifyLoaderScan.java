package dev.xavier.stein.loader;

import java.io.File;
import java.util.List;

public final class VerifyLoaderScan {
    public static void main(String[] args) {
        List<ModInfo> mods = ModScanner.scan(new File(args[0]), "0.1.26");
        boolean yny = false;
        boolean combat = false;
        for (ModInfo mod : mods) {
            if (!"ynyutils".equals(mod.id) && !"steincombat".equals(mod.id)) continue;
            System.out.println(mod.id + ": " + mod.state() + ", entries=" + mod.entries
                    + ", error=" + mod.error());
            if (mod.rejected()) throw new AssertionError(mod.id + " rejected");
            if ("ynyutils".equals(mod.id)) {
                if (mod.entries.size() != 2) throw new AssertionError("YNYUtils entry count");
                yny = true;
            } else {
                combat = true;
            }
        }
        if (!yny || !combat) throw new AssertionError("Missing mod");
        java.util.ArrayList<ModInfo> selected = new java.util.ArrayList<>();
        for (ModInfo mod : mods) {
            if ("ynyutils".equals(mod.id) || "steincombat".equals(mod.id)) selected.add(mod);
        }
        ModLoading.instantiate(selected, selected);
        for (ModInfo mod : selected) {
            System.out.println(mod.id + " loaded: " + mod.state() + ", instances=" + mod.instances.size() + ", error=" + mod.error());
            if (mod.instances.size() != ("ynyutils".equals(mod.id) ? 2 : 1))
                throw new AssertionError(mod.id + " did not instantiate all entries");
        }
    }
}