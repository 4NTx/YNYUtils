package com.yny.utils.modules.pvp;
import dev.xavier.stein.loader.api.Targeting.Hit;
import dev.xavier.stein.loader.api.Targeting.Ray;
import java.util.List;
public class AllyChainTest {
public static void main(String[] args) {
Object a=new Object(), b=new Object(), c=new Object(), enemy=new Object();
Ray ray=new Ray(List.of(new Hit(a,1,0,0,0),new Hit(b,1.4,0,0,0),new Hit(c,1.8,0,0,0),new Hit(enemy,2.5,0,0,0)),3,3);
check(HitThroughAllies.firstNonAllyBeforeBlock(ray,b,x->x==a||x==b||x==c)==enemy,"three allies");
Ray wall=new Ray(ray.hits,2,3);
check(HitThroughAllies.firstNonAllyBeforeBlock(wall,b,x->x==a||x==b||x==c)==null,"wall");
Ray allies=new Ray(ray.hits.subList(0,3),3,3);
check(HitThroughAllies.firstNonAllyBeforeBlock(allies,b,x->true)==null,"all allies");
System.out.println("ally chain, wall, no enemy: PASS");
}
static void check(boolean b,String msg){if(!b)throw new AssertionError(msg);}
}