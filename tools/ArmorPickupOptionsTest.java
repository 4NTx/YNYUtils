import com.yny.utils.config.YNYConfig;
import com.yny.utils.modules.player.ArmorPickupOptions;
import dev.xavier.stein.loader.api.Page;
public final class ArmorPickupOptionsTest {
 public static void main(String[] args){
  YNYConfig config=new YNYConfig();
  if(config.autoArmorDiscardReceived)throw new AssertionError("filter must be opt-in");
  if(!config.autoArmorDiscardRed8||!config.autoArmorDiscardUnenchanted||!config.autoArmorDiscardBelowP4)throw new AssertionError("default rules");
  Page page=new Page("test");
  ArmorPickupOptions.add(page,config,null);
  if(page.entries().size()!=4)throw new AssertionError("expected four controls, got "+page.entries().size());
  System.out.println("PASS: four armor filter controls, safe opt-in default");
 }
}