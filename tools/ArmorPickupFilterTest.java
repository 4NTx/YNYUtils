import com.yny.utils.config.YNYConfig;
import com.yny.utils.modules.player.ArmorPickupFilter;
import com.yny.utils.modules.player.ArmorPickupPolicy;

public final class ArmorPickupFilterTest {
    public static void main(String[] args) {
        YNYConfig config = new YNYConfig();
        config.autoArmorDiscardReceived = true;
        config.autoArmorDiscardRed8 = true;
        config.autoArmorDiscardUnenchanted = true;
        config.autoArmorDiscardBelowP4 = true;

        // 1. Unenchanted diamond helmet (piece=3, 100% dura, ench=false, prot=0)
        boolean discardUnench = ArmorPickupPolicy.shouldDiscard(config, 3, 363, 363, false, 0);
        if (!discardUnench) throw new AssertionError("Unenchanted helmet must be discarded!");

        // 2. Protection 3 diamond helmet (piece=3, 100% dura, ench=true, prot=3)
        boolean discardP3 = ArmorPickupPolicy.shouldDiscard(config, 3, 363, 363, true, 3);
        if (!discardP3) throw new AssertionError("Protection 3 helmet must be discarded!");

        // 3. Protection 4 diamond helmet (piece=3, 100% dura, ench=true, prot=4)
        boolean discardP4 = ArmorPickupPolicy.shouldDiscard(config, 3, 363, 363, true, 4);
        if (discardP4) throw new AssertionError("Protection 4 helmet must NOT be discarded!");

        // 4. Protection 4 damaged diamond helmet (piece=3, 8% dura, ench=true, prot=4)
        boolean discardRed8 = ArmorPickupPolicy.shouldDiscard(config, 3, 20, 363, true, 4);
        if (!discardRed8) throw new AssertionError("Red 8% helmet must be discarded even if P4!");

        // 5. When autoArmorDiscardReceived is false, nothing is discarded
        config.autoArmorDiscardReceived = false;
        if (ArmorPickupPolicy.shouldDiscard(config, 3, 363, 363, false, 0)) {
            throw new AssertionError("Should not discard when filter is disabled!");
        }

        // 6. Non-armor (piece = -1)
        config.autoArmorDiscardReceived = true;
        if (ArmorPickupPolicy.shouldDiscard(config, -1, 100, 100, false, 0)) {
            throw new AssertionError("Non-armor must never be discarded!");
        }

        System.out.println("PASS: ArmorPickupFilterTest - all discard policy scenarios verified");
    }
}
