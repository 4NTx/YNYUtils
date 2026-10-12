package com.yny.utils.modules.clanping;
public final class PingHudModelTest {
 public static void main(String[] args){
  double[][] points={{0,10},{-10,10},{-10,0},{-10,-10},{0,-10},{10,-10},{10,0},{10,10}};
  for(int i=0;i<8;i++)if(PingHudModel.sector(points[i][0],points[i][1],0)!=i)throw new AssertionError("sector "+i);
  if(PingHudModel.sector(-10,0,90)!=0)throw new AssertionError("yaw");
  if(PingHudModel.meters(3,4)!=5)throw new AssertionError("distance");
  if(PingHudModel.seconds(1501,0)!=2||PingHudModel.seconds(-1,0)!=0)throw new AssertionError("timer");
  if(!PingHudModel.direction(7).equals("FRENTE ESQUERDA"))throw new AssertionError("label");
  System.out.println("PASS: 8 directions, yaw, distance and timer");
 }
}