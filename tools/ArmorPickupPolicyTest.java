import com.yny.utils.config.YNYConfig;
import com.yny.utils.modules.player.ArmorPickupPolicy;
public final class ArmorPickupPolicyTest {
 static void check(boolean expected,YNYConfig c,int piece,int remaining,int max,Boolean enchanted,Integer protection){
  boolean actual=ArmorPickupPolicy.shouldDiscard(c,piece,remaining,max,enchanted,protection);
  if(actual!=expected)throw new AssertionError("expected "+expected+" got "+actual);
 }
 public static void main(String[] args){
  YNYConfig c=new YNYConfig();
  check(false,c,2,5,100,false,0);
  c.autoArmorDiscardReceived=true;
  check(true,c,2,8,100,true,4);
  check(false,c,2,9,100,true,4);
  check(true,c,2,90,100,false,0);
  check(true,c,2,90,100,true,3);
  check(false,c,2,90,100,true,4);
  check(false,c,-1,0,100,false,0);
  check(false,c,2,90,100,null,null);
  c.autoArmorDiscardRed8=false;c.autoArmorDiscardUnenchanted=false;c.autoArmorDiscardBelowP4=false;
  check(false,c,2,1,100,false,0);
  System.out.println("PASS: armor pickup policy boundaries and opt-in");
 }
}