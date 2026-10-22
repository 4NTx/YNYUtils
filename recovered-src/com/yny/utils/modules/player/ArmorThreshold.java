package com.yny.utils.modules.player;
public final class ArmorThreshold {
 private ArmorThreshold(){}
 public static int normalize(int percent){
  if(percent<20)return 0;
  return Math.max(20,Math.min(50,Math.round(percent/5.0f)*5));
 }
 public static double toSlider(int percent){
  int p=normalize(percent);
  return p==0?0:(double)((p-20)/5+1);
 }
 public static int fromSlider(int index){
  int i=Math.max(0,Math.min(7,index));
  return i==0?0:15+5*i;
 }
}
