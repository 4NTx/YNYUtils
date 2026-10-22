package com.yny.utils.modules.player;

import com.yny.utils.modules.ModuleDiagnostics;
import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Keys;
import dev.xavier.stein.loader.api.Keys.GameKey;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.BooleanSupplier;

public final class InventoryMove {
    private final BooleanSupplier enabled;
    private final BooleanSupplier suspendUseWhileMoving;
    private final BooleanSupplier useKeyReserved;
    private boolean controllingKeys;

    private static Field movementInputField;
    private static Field moveForwardField;
    private static Field moveStrafeField;
    private static Field jumpField;
    private static Field sneakField;
    private static boolean fieldsResolved;

    public InventoryMove(BooleanSupplier enabled, BooleanSupplier suspendUseWhileMoving, BooleanSupplier useKeyReserved) {
        this.enabled = enabled;
        this.suspendUseWhileMoving = suspendUseWhileMoving;
        this.useKeyReserved = useKeyReserved;
    }

    public void onTickEnd() {
        Object mc = getMinecraft();
        if (this.enabled.getAsBoolean() && mc != null && hasPlayer(mc) && isSupportedScreen(mc)) {
            boolean forward = isKeyDown(GameKey.FORWARD);
            boolean back = isKeyDown(GameKey.BACK);
            boolean left = isKeyDown(GameKey.LEFT);
            boolean right = isKeyDown(GameKey.RIGHT);
            boolean jump = isKeyDown(GameKey.JUMP);
            boolean sprint = isKeyDown(GameKey.SPRINT);
            boolean sneak = isKeyDown(GameKey.SNEAK);
            boolean moving = forward || back || left || right || jump || sprint;

            // Direct KeyBinding holds via SteinLoader Keys API
            setBindingPressed(Keys.game(GameKey.FORWARD), forward);
            setBindingPressed(Keys.game(GameKey.BACK), back);
            setBindingPressed(Keys.game(GameKey.LEFT), left);
            setBindingPressed(Keys.game(GameKey.RIGHT), right);
            setBindingPressed(Keys.game(GameKey.JUMP), jump);
            setBindingPressed(Keys.game(GameKey.SNEAK), sneak);
            setBindingPressed(Keys.game(GameKey.SPRINT), sprint);

            // Directly drive movementInput fields
            Object self = Entities.self();
            if (self != null) {
                driveMovementInput(self, forward, back, left, right, jump, sneak);
            }

            if (moving) {
                stopSwordBlocking(mc);
            }

            if (!(this.useKeyReserved.getAsBoolean() || AutoProtect.isControllingUseKey())) {
                boolean suspendUse = this.suspendUseWhileMoving.getAsBoolean() && shouldSuspendUse(moving);
                boolean physicalUse = isKeyDown(GameKey.USE);
                setBindingPressed(Keys.game(GameKey.USE), !suspendUse && physicalUse);
            }

            this.controllingKeys = true;
            ModuleDiagnostics.inventoryState(this.controllingKeys);
        } else {
            this.releaseToPhysicalState(mc);
            ModuleDiagnostics.inventoryState(this.controllingKeys);
        }
    }

    static boolean shouldSuspendUse(boolean moving) {
        return moving;
    }

    static boolean shouldStopSwordDefense(boolean moving, boolean blocking) {
        return moving && blocking;
    }

    private static void stopSwordBlocking(Object mc) {
        if (mc == null) return;
        try {
            Field playerField = findField(mc.getClass(), "thePlayer", "h");
            Field controllerField = findField(mc.getClass(), "playerController", "c");
            if (playerField != null && controllerField != null) {
                Object player = playerField.get(mc);
                Object controller = controllerField.get(mc);
                if (player != null && controller != null) {
                    Method stopMethod = findMethod(controller.getClass(), new String[]{"onStoppedUsingItem", "c"}, player.getClass());
                    if (stopMethod != null) {
                        stopMethod.invoke(controller, player);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static boolean isSupportedScreen(Object mc) {
        if (mc == null) return false;
        try {
            Field screenField = findField(mc.getClass(), "currentScreen", "m");
            if (screenField == null) return false;
            Object screen = screenField.get(mc);
            if (screen == null) return false;
            String name = screen.getClass().getSimpleName();
            if ("GuiRepair".equals(name) || "aym".equals(name)) return false;
            if (isTextFieldFocused(screen)) return false;
            Class<?> sc = screen.getClass();
            while (sc != null && sc != Object.class) {
                String sname = sc.getSimpleName();
                if ("GuiContainer".equals(sname) || "ayl".equals(sname)) {
                    return true;
                }
                sc = sc.getSuperclass();
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean isTextFieldFocused(Object screen) {
        if (screen == null) return false;
        try {
            Class<?> c = screen.getClass();
            while (c != null && c != Object.class) {
                for (Field f : c.getDeclaredFields()) {
                    f.setAccessible(true);
                    Object val = f.get(screen);
                    if (val != null) {
                        String vName = val.getClass().getName();
                        if (vName.contains("GuiTextField") || vName.equals("avw") || vName.endsWith(".avw")) {
                            Method focusedM = findMethod(val.getClass(), new String[]{"isFocused", "m"});
                            if (focusedM != null) {
                                Object res = focusedM.invoke(val);
                                if (Boolean.TRUE.equals(res)) return true;
                            }
                        }
                    }
                }
                c = c.getSuperclass();
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean isKeyDown(GameKey gameKey) {
        try {
            Keys.Binding b = Keys.game(gameKey);
            if (b == null) return false;
            int code = b.key();
            if (code < 0) {
                return Keys.isMouseDown(code + 100);
            }
            return Keys.isDown(code);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void setBindingPressed(Keys.Binding binding, boolean pressed) {
        if (binding == null) return;
        try {
            binding.hold(pressed);
            Object handle = binding.handle();
            if (handle != null) {
                Class<?> c = handle.getClass();
                while (c != null && c != Object.class) {
                    for (Field f : c.getDeclaredFields()) {
                        if (f.getType() == boolean.class && !Modifier.isStatic(f.getModifiers())) {
                            if ("pressed".equals(f.getName()) || "h".equals(f.getName()) || "field_74513_e".equals(f.getName())) {
                                f.setAccessible(true);
                                f.setBoolean(handle, pressed);
                                return;
                            }
                        }
                    }
                    c = c.getSuperclass();
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void driveMovementInput(Object self, boolean forward, boolean back, boolean left, boolean right, boolean jump, boolean sneak) {
        try {
            resolveFields(self);
            if (movementInputField != null) {
                Object input = movementInputField.get(self);
                if (input != null) {
                    float moveForward = 0.0F;
                    float moveStrafe = 0.0F;
                    if (forward) moveForward += 1.0F;
                    if (back) moveForward -= 1.0F;
                    if (left) moveStrafe += 1.0F;
                    if (right) moveStrafe -= 1.0F;
                    if (sneak) {
                        moveStrafe = (float) ((double) moveStrafe * 0.3D);
                        moveForward = (float) ((double) moveForward * 0.3D);
                    }
                    if (moveForwardField != null) moveForwardField.setFloat(input, moveForward);
                    if (moveStrafeField != null) moveStrafeField.setFloat(input, moveStrafe);
                    if (jumpField != null) jumpField.setBoolean(input, jump);
                    if (sneakField != null) sneakField.setBoolean(input, sneak);
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void resolveFields(Object self) {
        if (fieldsResolved) return;
        fieldsResolved = true;
        try {
            Class<?> c = self.getClass();
            while (c != null && c != Object.class) {
                for (Field f : c.getDeclaredFields()) {
                    if ("movementInput".equals(f.getName()) || "b".equals(f.getName()) || "field_71158_b".equals(f.getName())) {
                        movementInputField = f;
                        f.setAccessible(true);
                        break;
                    }
                }
                if (movementInputField != null) break;
                c = c.getSuperclass();
            }

            if (movementInputField != null) {
                Object input = movementInputField.get(self);
                if (input != null) {
                    Class<?> ic = input.getClass();
                    while (ic != null && ic != Object.class) {
                        for (Field f : ic.getDeclaredFields()) {
                            if ("moveForward".equals(f.getName()) || "b".equals(f.getName()) || "field_78900_b".equals(f.getName())) {
                                moveForwardField = f;
                                f.setAccessible(true);
                            } else if ("moveStrafe".equals(f.getName()) || "a".equals(f.getName()) || "field_78902_a".equals(f.getName())) {
                                moveStrafeField = f;
                                f.setAccessible(true);
                            } else if ("sneak".equals(f.getName()) || "d".equals(f.getName()) || "field_78899_d".equals(f.getName())) {
                                sneakField = f;
                                f.setAccessible(true);
                            } else if ("jump".equals(f.getName()) || "c".equals(f.getName()) || "field_78901_c".equals(f.getName())) {
                                jumpField = f;
                                f.setAccessible(true);
                            }
                        }
                        ic = ic.getSuperclass();
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private void releaseToPhysicalState(Object mc) {
        if (this.controllingKeys) {
            setBindingPressed(Keys.game(GameKey.FORWARD), isKeyDown(GameKey.FORWARD));
            setBindingPressed(Keys.game(GameKey.BACK), isKeyDown(GameKey.BACK));
            setBindingPressed(Keys.game(GameKey.LEFT), isKeyDown(GameKey.LEFT));
            setBindingPressed(Keys.game(GameKey.RIGHT), isKeyDown(GameKey.RIGHT));
            setBindingPressed(Keys.game(GameKey.JUMP), isKeyDown(GameKey.JUMP));
            setBindingPressed(Keys.game(GameKey.SNEAK), isKeyDown(GameKey.SNEAK));
            setBindingPressed(Keys.game(GameKey.SPRINT), isKeyDown(GameKey.SPRINT));
            setBindingPressed(Keys.game(GameKey.USE), isKeyDown(GameKey.USE));
            this.controllingKeys = false;
        }
    }

    private static Object getMinecraft() {
        try {
            return Class.forName("ave").getMethod("A").invoke(null);
        } catch (Throwable t1) {
            try {
                return Class.forName("net.minecraft.client.Minecraft").getMethod("getMinecraft").invoke(null);
            } catch (Throwable t2) {
                return null;
            }
        }
    }

    private static boolean hasPlayer(Object mc) {
        try {
            Field f = findField(mc.getClass(), "thePlayer", "h");
            return f != null && f.get(mc) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Field findField(Class<?> clazz, String... names) {
        Class<?> cur = clazz;
        while (cur != null && cur != Object.class) {
            for (String name : names) {
                try {
                    Field f = cur.getDeclaredField(name);
                    f.setAccessible(true);
                    return f;
                } catch (NoSuchFieldException ignored) {}
            }
            cur = cur.getSuperclass();
        }
        return null;
    }

    private static Method findMethod(Class<?> clazz, String[] names, Class<?>... paramTypes) {
        Class<?> cur = clazz;
        while (cur != null && cur != Object.class) {
            for (String name : names) {
                for (Method m : cur.getDeclaredMethods()) {
                    if (m.getName().equals(name)) {
                        m.setAccessible(true);
                        return m;
                    }
                }
            }
            cur = cur.getSuperclass();
        }
        return null;
    }
}
