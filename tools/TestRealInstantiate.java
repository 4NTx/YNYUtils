import java.lang.reflect.*;
import sun.misc.Unsafe;
import java.io.File;

public class TestRealInstantiate {
    static int chatFormattedCalls = 0;
    static int chatTextCalls = 0;
    static String currentEntityName = "Steve";
    static boolean isPlayerFlag = true;

    public static void main(String[] args) throws Exception {
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        Unsafe unsafe = (Unsafe) f.get(null);

        Class<?> hostClass = Class.forName("dev.xavier.stein.loader.api.spi.Host", false, TestRealInstantiate.class.getClassLoader());
        Object mockHost = Proxy.newProxyInstance(hostClass.getClassLoader(), new Class<?>[] { hostClass }, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] margs) {
                String name = method.getName();
                if (name.equals("modIdOf")) {
                    return "ynyutils";
                }
                if (name.equals("gameDir")) {
                    return new File("data/.minecraft");
                }
                if (name.equals("readConfig")) {
                    // margs[2] is defaults
                    return margs[2];
                }
                if (name.equals("isPlayer")) {
                    return isPlayerFlag;
                }
                if (name.equals("entityName")) {
                    return currentEntityName;
                }
                if (name.equals("self") || name.equals("selfName")) {
                    return "Hero";
                }
                if (name.equals("chatFormatted")) {
                    chatFormattedCalls++;
                    Object arg = margs[0];
                    if (arg instanceof eu) {
                        return ((eu) arg).d();
                    }
                    return arg == null ? "" : arg.toString();
                }
                if (name.equals("chatText")) {
                    chatTextCalls++;
                    return new fa((String) margs[0]);
                }
                System.out.println("Host invoked: " + name + " with args: " + java.util.Arrays.toString(margs));
                if (method.getReturnType() == boolean.class) return false;
                if (method.getReturnType() == int.class) return 0;
                if (method.getReturnType() == float.class) return 0.0f;
                if (method.getReturnType() == double.class) return 0.0;
                return null;
            }
        });

        Field instField = hostClass.getDeclaredField("INSTANCE");
        Object base = unsafe.staticFieldBase(instField);
        long offset = unsafe.staticFieldOffset(instField);
        unsafe.putObject(base, offset, mockHost);
        System.out.println("Set Host.INSTANCE successfully via Unsafe!");

        test("com.yny.utils.YNYUtils");
        Object combatInst = test("com.yny.utils.team.SteinCombat");

        testNametagZeroAllocation(combatInst);

        testKnockbackAndAutoArmor();
    }

    private static void testNametagZeroAllocation(Object combat) throws Exception {
        System.out.println("\nTesting Nametag Zero-Allocation and Component Caching...");
        Method onNametag = combat.getClass().getMethod("onNametag", Object.class, Object.class);
        Object dummyEntity = new Object();

        chatFormattedCalls = 0;
        chatTextCalls = 0;

        // Frame 1: first render of Steve's nametag
        fa nameFrame1 = new fa("Steve");
        Object result1 = onNametag.invoke(combat, dummyEntity, nameFrame1);
        if (result1 == null) throw new AssertionError("result1 should not be null");
        if (chatFormattedCalls != 1) throw new AssertionError("chatFormatted should be called exactly once on cold cache, was: " + chatFormattedCalls);
        if (chatTextCalls != 1) throw new AssertionError("chatText should be called exactly once on cold cache, was: " + chatTextCalls);

        // Frame 2: Minecraft creates a NEW instance of fa("Steve")
        fa nameFrame2 = new fa("Steve");
        if (nameFrame1 == nameFrame2) throw new AssertionError("nameFrame1 and nameFrame2 should be different instances");
        Object result2 = onNametag.invoke(combat, dummyEntity, nameFrame2);

        // Verify zero allocations on hot path:
        if (chatFormattedCalls != 1) throw new AssertionError("chatFormatted was called on hot path! Expected 1, got: " + chatFormattedCalls);
        if (chatTextCalls != 1) throw new AssertionError("chatText was called on hot path! Expected 1, got: " + chatTextCalls);
        if (result1 != result2) throw new AssertionError("result2 should be identical cached component reference to result1");

        // Frames 3 to 100: Simulate 100 frames at 120 FPS
        for (int i = 0; i < 100; i++) {
            fa frameName = new fa("Steve");
            Object res = onNametag.invoke(combat, dummyEntity, frameName);
            if (res != result1) throw new AssertionError("frame " + i + " failed to return cached component");
        }
        if (chatFormattedCalls != 1 || chatTextCalls != 1) {
            throw new AssertionError("Calls occurred during 100 frames! formatted=" + chatFormattedCalls + ", text=" + chatTextCalls);
        }

        // Nametag change: e.g. health indicator updated by scoreboard: "Steve [18/20]"
        fa nameDamaged = new fa("Steve [18/20]");
        Object resultDamaged = onNametag.invoke(combat, dummyEntity, nameDamaged);
        if (resultDamaged == result1) throw new AssertionError("Damaged nametag should produce new component");
        if (chatFormattedCalls != 2) throw new AssertionError("chatFormatted should be called on nametag change");
        if (chatTextCalls != 2) throw new AssertionError("chatText should be called on nametag change");

        // Frame after change: hot path should cache the new damaged component
        fa nameDamagedNext = new fa("Steve [18/20]");
        Object resultDamagedNext = onNametag.invoke(combat, dummyEntity, nameDamagedNext);
        if (resultDamagedNext != resultDamaged) throw new AssertionError("Damaged nametag should be cached on subsequent frames");
        if (chatFormattedCalls != 2 || chatTextCalls != 2) throw new AssertionError("Calls occurred on cached damaged nametag");

        // Edge Case 1: Non-player entity (should bypass and return raw name)
        isPlayerFlag = false;
        fa mobName = new fa("Zombie");
        Object mobResult = onNametag.invoke(combat, dummyEntity, mobName);
        if (mobResult != mobName) throw new AssertionError("Non-player entity should return raw name object directly");
        if (chatFormattedCalls != 2 || chatTextCalls != 2) throw new AssertionError("Non-player entity should not invoke chat format/text");
        isPlayerFlag = true;

        // Edge Case 2: Null name or null entity
        Object nullResult = onNametag.invoke(combat, dummyEntity, null);
        if (nullResult != null) throw new AssertionError("Null name should return null");

        // Edge Case 3: colorNames disabled in config
        Field configField = combat.getClass().getDeclaredField("config");
        configField.setAccessible(true);
        Object configObj = configField.get(combat);
        Field colorNamesField = configObj.getClass().getDeclaredField("colorNames");
        colorNamesField.setAccessible(true);
        colorNamesField.set(configObj, false);
        fa rawWhenDisabled = new fa("Steve");
        Object disabledResult = onNametag.invoke(combat, dummyEntity, rawWhenDisabled);
        if (disabledResult != rawWhenDisabled) throw new AssertionError("Disabled colorNames should return raw name directly");
        colorNamesField.set(configObj, true);

        // Edge Case 4: Multiple distinct players on screen simultaneously
        currentEntityName = "Alex";
        int fmtBeforeAlex = chatFormattedCalls;
        int txtBeforeAlex = chatTextCalls;
        fa alexFrame1 = new fa("Alex");
        Object alexRes1 = onNametag.invoke(combat, dummyEntity, alexFrame1);
        if (chatFormattedCalls != fmtBeforeAlex + 1 || chatTextCalls != txtBeforeAlex + 1) {
            throw new AssertionError("Cold cache for Alex should increment formatted/text calls by 1");
        }
        fa alexFrame2 = new fa("Alex");
        Object alexRes2 = onNametag.invoke(combat, dummyEntity, alexFrame2);
        if (alexRes1 != alexRes2) throw new AssertionError("Alex frame 2 should return cached component");
        if (chatFormattedCalls != fmtBeforeAlex + 1 || chatTextCalls != txtBeforeAlex + 1) {
            throw new AssertionError("Hot path for Alex should have 0 allocations");
        }

        // Return to Steve: verify Steve's cache remained valid and separate from Alex
        currentEntityName = "Steve";
        fa steveFrameBack = new fa("Steve [18/20]");
        Object steveResBack = onNametag.invoke(combat, dummyEntity, steveFrameBack);
        if (steveResBack != resultDamagedNext) throw new AssertionError("Steve cached component should be preserved independently of Alex");
        if (chatFormattedCalls != fmtBeforeAlex + 1 || chatTextCalls != txtBeforeAlex + 1) {
            throw new AssertionError("Switching between players on hot path should have 0 allocations");
        }

        // Edge Case 5: Compound chat components with siblings (Scoreboard clan + health prefix/suffix)
        int fmtBeforeCompound = chatFormattedCalls;
        int txtBeforeCompound = chatTextCalls;
        currentEntityName = "Steve";
        fa compound1 = new fa("");
        compound1.a(new fa("§a[VIP] "));
        compound1.a(new fa("Steve"));
        compound1.a(new fa(" §c[20/20]"));
        Object compoundRes1 = onNametag.invoke(combat, dummyEntity, compound1);
        if (chatFormattedCalls != fmtBeforeCompound + 1 || chatTextCalls != txtBeforeCompound + 1) {
            throw new AssertionError("Cold cache for compound component should invoke format/text once");
        }
        String compoundFormatted = ((eu) compoundRes1).d();
        if (!compoundFormatted.contains("§a[VIP] ") || !compoundFormatted.contains(" §c[20/20]")) {
            throw new AssertionError("Compound component prefix/suffix not preserved: " + compoundFormatted);
        }

        // Compound Frame 2: verify 0 allocations and identical component reference
        fa compound2 = new fa("");
        compound2.a(new fa("§a[VIP] "));
        compound2.a(new fa("Steve"));
        compound2.a(new fa(" §c[20/20]"));
        Object compoundRes2 = onNametag.invoke(combat, dummyEntity, compound2);
        if (compoundRes1 != compoundRes2) throw new AssertionError("Compound component frame 2 should return cached instance");
        if (chatFormattedCalls != fmtBeforeCompound + 1 || chatTextCalls != txtBeforeCompound + 1) {
            throw new AssertionError("Compound component on hot path should have ZERO allocations");
        }

        // Edge Case 6: Empty nick
        currentEntityName = "";
        fa rawEmpty = new fa("Mystery");
        Object emptyRes = onNametag.invoke(combat, dummyEntity, rawEmpty);
        if (emptyRes != rawEmpty) throw new AssertionError("Empty nick should return raw name directly");

        // Edge Case 7: Nick not present in nametag text (unpainted nametag)
        int fmtBeforeUnpainted = chatFormattedCalls;
        int txtBeforeUnpainted = chatTextCalls;
        currentEntityName = "Ghost";
        fa unpaintedRaw1 = new fa("§c[Admin] Hidden");
        Object unpaintedRes1 = onNametag.invoke(combat, dummyEntity, unpaintedRaw1);
        if (chatFormattedCalls != fmtBeforeUnpainted + 1) throw new AssertionError("Unpainted nametag cold cache should format text");
        if (chatTextCalls != txtBeforeUnpainted) throw new AssertionError("Unpainted nametag should NOT allocate new Chat.text");
        if (unpaintedRes1 != unpaintedRaw1) throw new AssertionError("Unpainted nametag should return raw component directly");

        fa unpaintedRaw2 = new fa("§c[Admin] Hidden");
        Object unpaintedRes2 = onNametag.invoke(combat, dummyEntity, unpaintedRaw2);
        if (chatFormattedCalls != fmtBeforeUnpainted + 1 || chatTextCalls != txtBeforeUnpainted) {
            throw new AssertionError("Unpainted nametag hot path should have ZERO allocations");
        }
        if (unpaintedRes2 != unpaintedRes1) throw new AssertionError("Unpainted nametag should return cached component on frame 2");

        System.out.println("  PASS: 100 frames completed with ZERO allocations on the hot path!");
        System.out.println("  PASS: Nametag text changes (health/clan) invalidate cache dynamically!");
        System.out.println("  PASS: Component reference equality verified across frames!");
        System.out.println("  PASS: Non-player entities, null inputs, and disabled colors verified!");
        System.out.println("  PASS: Multiple distinct players cached independently without collisions!");
        System.out.println("  PASS: Compound components with siblings maintain 100% prefix/suffix integrity and zero allocations!");
        System.out.println("  PASS: Empty nicks and unpainted nametags verified with zero allocations!");
    }

    private static void testKnockbackAndAutoArmor() {
        System.out.println("\nTesting KnockbackControl and VelocityInterceptor cross-class access...");
        try {
            Class<?> kbClass = Class.forName("com.yny.utils.modules.pvp.KnockbackControl");
            Constructor<?> kbCtor = kbClass.getDeclaredConstructors()[0];
            kbCtor.setAccessible(true);
            Object[] args = new Object[kbCtor.getParameterCount()];
            for (int i = 0; i < args.length; i++) {
                Class<?> pt = kbCtor.getParameterTypes()[i];
                if (pt == java.util.function.BooleanSupplier.class) args[i] = (java.util.function.BooleanSupplier) () -> true;
                else if (pt == java.util.function.IntSupplier.class) args[i] = (java.util.function.IntSupplier) () -> 100;
                else args[i] = null;
            }
            Object kb = kbCtor.newInstance(args);

            Class<?> viClass = Class.forName("com.yny.utils.modules.pvp.KnockbackControl$VelocityInterceptor");
            Constructor<?> viCtor = viClass.getDeclaredConstructors()[0];
            viCtor.setAccessible(true);
            Object vi = viCtor.newInstance(kb);
            System.out.println("  SUCCESS instantiating VelocityInterceptor: " + vi);

            // Test field access that caused the IllegalAccessError:
            Field diagField = kbClass.getDeclaredField("diagnostics");
            Field scField = kbClass.getDeclaredField("serverCorrections");
            System.out.println("  diagnostics field access modifiers: " + Modifier.toString(diagField.getModifiers()));
            System.out.println("  serverCorrections field access modifiers: " + Modifier.toString(scField.getModifiers()));

            if (Modifier.isPrivate(diagField.getModifiers()) || Modifier.isPrivate(scField.getModifiers())) {
                throw new IllegalStateException("FAIL: diagnostics or serverCorrections is still private!");
            }
            System.out.println("  SUCCESS: diagnostics and serverCorrections are NOT private (Java 8 legal)!");

            Class<?> aaClass = Class.forName("com.yny.utils.modules.player.AutoArmor");
            for (Method m : aaClass.getDeclaredMethods()) {
                if (m.getName().equals("copy") || m.getName().equals("isBroken")) {
                    System.out.println("  AutoArmor." + m.getName() + " modifiers: " + Modifier.toString(m.getModifiers()));
                    if (Modifier.isPrivate(m.getModifiers())) {
                        throw new IllegalStateException("FAIL: AutoArmor." + m.getName() + " is still private!");
                    }
                }
            }
            System.out.println("  SUCCESS: AutoArmor.copy and isBroken are NOT private (Java 8 legal)!");
        } catch (Throwable t) {
            System.out.println("  TEST FAILED:");
            t.printStackTrace(System.out);
        }
    }

    private static Object test(String name) {
        System.out.println("\nTesting instantiation of: " + name);
        try {
            Class<?> c = Class.forName(name);
            Constructor<?> ctor = c.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object inst = ctor.newInstance();
            System.out.println("  SUCCESS instantiating " + name + ": " + inst);
            return inst;
        } catch (InvocationTargetException ite) {
            System.out.println("  InvocationTargetException cause:");
            ite.getCause().printStackTrace(System.out);
            return null;
        } catch (Throwable t) {
            System.out.println("  Caught throwable:");
            t.printStackTrace(System.out);
            return null;
        }
    }
}
