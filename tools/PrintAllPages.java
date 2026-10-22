package dev.xavier.stein.loader;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import dev.xavier.stein.loader.api.Page;

public class PrintAllPages {
    public static void main(String[] args) throws Exception {
        File dir = new File("data/.minecraft/mods");
        List<ModInfo> mods = ModScanner.scan(dir, "0.1.26");
        ModLoading.instantiate(mods, mods);
        
        for (ModInfo mod : mods) {
            System.out.println("=== MOD: " + mod.id + " (" + mod.name + ") ===");
            for (Object inst : mod.instances) {
                System.out.println("  Instance: " + inst.getClass().getName());
                Page page = new Page(mod.name);
                try {
                    ((dev.xavier.stein.loader.api.SteinMod) inst).onPage(page);
                } catch (Exception e) {
                    System.out.println("    onPage threw: " + e);
                }
                System.out.println("    Page isEmpty: " + page.isEmpty());
                
                try {
                    Field elementsField = Page.class.getDeclaredField("elements");
                    elementsField.setAccessible(true);
                    List<?> elements = (List<?>) elementsField.get(page);
                    for (Object elem : elements) {
                        System.out.println("      Elem: " + elem.getClass().getSimpleName() + " -> " + elem);
                    }
                } catch (Exception ex) {
                    System.out.println("      Could not read elements: " + ex);
                }
            }
        }
    }
}
