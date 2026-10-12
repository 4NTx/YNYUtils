import com.yny.utils.config.YNYConfig;
import com.yny.utils.modules.player.ArmorThreshold;
import java.lang.reflect.*;
public final class ArmorThresholdTest {
 public static void main(String[] args)throws Exception {
  int[] values={0,15,20,25,30,35,40,45,50};
  for(int i=0;i<values.length;i++){
   if(ArmorThreshold.fromSlider(i)!=values[i])throw new AssertionError("from "+i);
   if(ArmorThreshold.toSlider(values[i])!=i)throw new AssertionError("to "+values[i]);
  }
  for(int value:new int[]{5,10,15,50,55}){
   YNYConfig c=new YNYConfig();c.autoArmorPreventiveThreshold=value;c.sanitize();
   int expected=Math.max(15,Math.min(50,Math.round(value/5f)*5));
   if(c.autoArmorPreventiveThreshold!=expected)throw new AssertionError("sanitize "+value);
  }
  Field field=Class.forName("com.yny.utils.YNYUtils").getDeclaredField("config");field.setAccessible(true);
  YNYConfig config=new YNYConfig();config.autoArmorPreventiveThreshold=5;field.set(null,config);
  Method runtime=Class.forName("com.yny.utils.YNYUtils").getDeclaredMethod("lambda$new$14");runtime.setAccessible(true);
  if((int)runtime.invoke(null)!=15)throw new AssertionError("runtime floor");
  System.out.println("PASS: break only, 15-50%, config and runtime floor");
 }
}