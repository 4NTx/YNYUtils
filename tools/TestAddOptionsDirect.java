package dev.xavier.stein.loader;

import com.yny.utils.team.SteinCombat;
import dev.xavier.stein.loader.api.Page;

public class TestAddOptionsDirect {
    public static void main(String[] args) {
        try {
            SteinCombat sc = new SteinCombat();
            System.out.println("Instantiated SteinCombat. instance=" + SteinCombat.instance);
            Page page = new Page("YNYUtils");
            SteinCombat.addOptions(page);
            System.out.println("addOptions succeeded! isEmpty=" + page.isEmpty());
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
