public final class ArmorPickupReflectionTest {
 public static void main(String[] args)throws Exception {
  ClassLoader loader=ArmorPickupReflectionTest.class.getClassLoader();
  Class<?> enchant=Class.forName("aci",false,loader),stack=Class.forName("zx",false,loader),helper=Class.forName("ack",false,loader);
  if(enchant.getField("c").getType()!=enchant||enchant.getField("B").getType()!=int.class)throw new AssertionError("Protection fields");
  if(helper.getMethod("a",int.class,stack).getReturnType()!=int.class)throw new AssertionError("Protection lookup");
  if(stack.getMethod("w").getReturnType()!=boolean.class)throw new AssertionError("enchantment lookup");
  System.out.println("PASS: Minecraft 1.8.9 enchantment API signature");
 }
}