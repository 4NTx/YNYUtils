public class PingDirectionTest {
  public static void main(String[] args)throws Exception {
    Class<?> c=Class.forName("com.yny.utils.modules.clanping.ClanPing",false,PingDirectionTest.class.getClassLoader());
    var m=c.getDeclaredMethod("direction",double.class,double.class,float.class);
    m.setAccessible(true);
    check(m,0,1,0,"Frente");
    check(m,-1,0,0,"Direita");
    check(m,0,-1,0,"Atrás");
    check(m,1,0,0,"Esquerda");
    check(m,-1,0,90,"Frente");
    System.out.println("Five orientation cases: PASS");
  }
  static void check(java.lang.reflect.Method m,double x,double z,float yaw,String expected)throws Exception {
    String value=(String)m.invoke(null,x,z,yaw);
    if(!value.contains(expected))throw new AssertionError(x+","+z+","+yaw+": "+value);
  }
}