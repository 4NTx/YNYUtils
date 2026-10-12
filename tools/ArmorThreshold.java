package com.yny.utils.modules.player;
public final class ArmorThreshold {
 private ArmorThreshold(){}
 public static int normalize(int percent){
  if(percent<=0)return 0;
  return Math.max(15,Math.min(50,Math.round(percent/5.0f)*5));
 }
 public static double toSlider(int percent){
  int p=normalize(percent);
  return p==0?0:(p-15)/5+1;
 }
 public static int fromSlider(int index){
  int i=Math.max(0,Math.min(8,index));
  return i==0?0:10+5*i;
 }
}