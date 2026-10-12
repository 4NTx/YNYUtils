public class VerifyPingClasses {
  public static void main(String[] args)throws Exception {
    Class.forName("com.yny.utils.modules.clanping.ClanPingHud");
    Class.forName("com.yny.utils.modules.clanping.PingCompass");
    Class.forName("com.yny.utils.YNYUtils");
    Class.forName("com.yny.utils.modules.pvp.AllyGroundMarker");
    System.out.println("Ping HUD, compass, and mod classes: PASS");
  }
}