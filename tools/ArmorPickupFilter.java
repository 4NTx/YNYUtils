package com.yny.utils.modules.player;
import com.yny.utils.config.YNYConfig;
import com.yny.utils.core.DebugLog;
import dev.xavier.stein.loader.api.Inventory;
import dev.xavier.stein.loader.api.Stacks;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
public final class ArmorPickupFilter {
 private static final Object[] seen=new Object[36],queued=new Object[36];
 private static final long[] attemptedAt=new long[36];
 private static final Field pendingField,actionField;
 private static boolean known, warned;
 private static long tick;
 static {
  Field field=null,action=null;
  try{field=AutoArmor.class.getDeclaredField("pending");field.setAccessible(true);action=AutoArmor.class.getDeclaredField("nextClickAt");action.setAccessible(true);}
  catch(Exception ignored){}
  pendingField=field;actionField=action;
 }
 private ArmorPickupFilter(){}
 public static void reset(){
  Arrays.fill(seen,null);Arrays.fill(queued,null);Arrays.fill(attemptedAt,0L);
  known=false;tick=0;warned=false;
 }
 public static void onWindowItems(int window){
  if(window==0){for(int i=0;i<36;i++)seen[i]=Inventory.stack(i);
   Arrays.fill(queued,null);known=true;}
 }
 public static void onSlotChanged(int window,int slot,Object stack,YNYConfig config){
  if(window!=0)return;
  int index=Inventory.inventoryIndexOf(slot);
  if(index<0||index>=36)return;
  Object current=Inventory.stack(index),before=seen[index];
  seen[index]=current;
  if(!known||!config.autoArmorDiscardReceived||before!=null||current==null||current!=stack)return;
  if(Inventory.isScreenOpen()||Inventory.isContainerOpen()||Inventory.cursor()!=null)return;
  if(Inventory.armorPiece(current)<0||!reject(current,config))return;
  queued[index]=current;
 }
 public static void runAutoArmor(AutoArmor armor,YNYConfig config){
  boolean ready=safe(armor);
  long clickAt=lastAction(armor);
  armor.onTickEnd();
  process(config,armor,!ready||clickAt!=lastAction(armor));
 }
 private static long lastAction(AutoArmor armor){
  try{return actionField==null?-1L:actionField.getLong(armor);}catch(Exception ignored){return -1L;}
 }
 private static boolean process(YNYConfig config,AutoArmor armor,boolean blocked){
  tick++;
  if(!known){onWindowItems(0);return false;}
  if(!config.autoArmorDiscardReceived){Arrays.fill(queued,null);snapshot();return false;}
  if(blocked||!safe(armor)){snapshot();return false;}
  for(int i=0;i<36;i++){
   Object stack=queued[i];
   if(stack==null)continue;
   queued[i]=null;
   if(Inventory.stack(i)!=stack||!reject(stack,config))continue;
   if(attemptedAt[i]!=0&&tick-attemptedAt[i]<20){queued[i]=stack;continue;}
   int slot=Inventory.slotOf(i);
   if(slot<0||Inventory.inventoryIndexOf(slot)!=i)continue;
   attemptedAt[i]=tick;
   if(Inventory.drop(slot,true)){
    DebugLog.write("AutoArmor","filtro descartou armadura recebida no slot "+i+": "+Stacks.name(stack));
    snapshot();return true;
   }
   queued[i]=stack;
   DebugLog.write("AutoArmor","filtro: descarte recusado no slot "+i+"; nova tentativa após intervalo seguro");
  }
  snapshot();return false;
 }
 private static void snapshot(){for(int i=0;i<36;i++)seen[i]=Inventory.stack(i);}
 private static boolean safe(AutoArmor armor){
  if(Inventory.windowId()!=0||Inventory.isScreenOpen()||Inventory.isContainerOpen()||Inventory.cursor()!=null||pendingField==null||actionField==null)return false;
  try{return pendingField.get(armor)==null;}catch(Exception ignored){return false;}
 }
 private static boolean reject(Object stack,YNYConfig config){
  int piece=Inventory.armorPiece(stack),max=Stacks.maxDamage(stack);
  int remaining=max-Stacks.damage(stack);
  if(ArmorPickupPolicy.shouldDiscard(config,piece,remaining,max,null,null))return true;
  if(piece<0||!config.autoArmorDiscardReceived)return false;
  if(config.autoArmorDiscardUnenchanted||config.autoArmorDiscardBelowP4){
   try{
    boolean enchanted=(boolean)stack.getClass().getMethod("w").invoke(stack);
    if(ArmorPickupPolicy.shouldDiscard(config,piece,remaining,max,enchanted,null))return true;
    if(config.autoArmorDiscardBelowP4)return ArmorPickupPolicy.shouldDiscard(config,piece,remaining,max,enchanted,protection(stack));
   }catch(Exception error){
    if(!warned){DebugLog.write("AutoArmor","filtro não conseguiu ler encantamentos; descarte por encantamento suspenso: "+error);warned=true;}
   }
  }
  return false;
 }
 private static int protection(Object stack)throws Exception {
  Class<?> enchant=Class.forName("aci"),helper=Class.forName("ack");
  Object protection=enchant.getField("c").get(null);
  int id=enchant.getField("B").getInt(protection);
  for(Method method:helper.getDeclaredMethods()){
   Class<?>[] params=method.getParameterTypes();
   if(method.getName().equals("a")&&params.length==2&&params[0]==int.class&&params[1].isInstance(stack)&&method.getReturnType()==int.class){
    method.setAccessible(true);return (int)method.invoke(null,id,stack);
   }
  }
  throw new NoSuchMethodException("Protection level");
 }
}
