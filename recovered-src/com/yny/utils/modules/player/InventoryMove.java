package com.yny.utils.modules.player;

import ave;
import avb;
import avh;
import ayl;
import aym;
import zx;
import aay;
import com.yny.utils.modules.ModuleDiagnostics;
import dev.xavier.stein.loader.api.Keys;
import dev.xavier.stein.loader.api.Keys.GameKey;
import java.util.function.BooleanSupplier;

public final class InventoryMove {
    private final BooleanSupplier enabled;
    private final BooleanSupplier suspendUseWhileMoving;
    private final BooleanSupplier useKeyReserved;
    private boolean controllingKeys;

    public InventoryMove(BooleanSupplier enabled, BooleanSupplier suspendUseWhileMoving, BooleanSupplier useKeyReserved) {
        this.enabled = enabled;
        this.suspendUseWhileMoving = suspendUseWhileMoving;
        this.useKeyReserved = useKeyReserved;
    }

    public void onTickEnd() {
        ave mc = ave.A();
        if (this.enabled.getAsBoolean() && mc.h != null && isSupportedScreen(mc)) {
            boolean forward = sync(mc.t.Y);
            boolean back = sync(mc.t.aa);
            boolean left = sync(mc.t.Z);
            boolean right = sync(mc.t.ab);
            boolean jump = sync(mc.t.ac);
            boolean sprint = sync(mc.t.ae);
            boolean sneak = sync(mc.t.ad);
            boolean moving = forward || back || left || right || jump || sprint;

            // Direct KeyBinding holds via SteinLoader Keys API
            try {
                Keys.game(GameKey.FORWARD).hold(forward);
                Keys.game(GameKey.BACK).hold(back);
                Keys.game(GameKey.LEFT).hold(left);
                Keys.game(GameKey.RIGHT).hold(right);
                Keys.game(GameKey.JUMP).hold(jump);
                Keys.game(GameKey.SNEAK).hold(sneak);
                Keys.game(GameKey.SPRINT).hold(sprint);
            } catch (Throwable ignored) {}

            // Directly drive movementInput fields (mc.h.b)
            if (mc.h.b != null) {
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
                mc.h.b.b = moveForward;
                mc.h.b.a = moveStrafe;
                mc.h.b.c = jump;
                mc.h.b.d = sneak;
            }

            if (moving) {
                stopSwordBlocking(mc);
            }

            if (!(this.useKeyReserved.getAsBoolean() || AutoProtect.isControllingUseKey())) {
                boolean suspendUse = this.suspendUseWhileMoving.getAsBoolean() && shouldSuspendUse(moving);
                mc.t.ag.h = !suspendUse && avh.a(mc.t.ag);
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

    private static void stopSwordBlocking(ave mc) {
        zx item = mc.h.bA();
        boolean blocking = mc.h.bS() && item != null && item.b() instanceof aay;
        if (shouldStopSwordDefense(true, blocking) && mc.c != null) {
            mc.c.c(mc.h);
        }
    }

    private static boolean isSupportedScreen(ave mc) {
        return mc.m instanceof ayl && !(mc.m instanceof aym);
    }

    private static boolean sync(avb key) {
        key.h = avh.a(key);
        return key.h;
    }

    private void releaseToPhysicalState(ave mc) {
        if (this.controllingKeys) {
            try {
                Keys.game(GameKey.FORWARD).hold(false);
                Keys.game(GameKey.BACK).hold(false);
                Keys.game(GameKey.LEFT).hold(false);
                Keys.game(GameKey.RIGHT).hold(false);
                Keys.game(GameKey.JUMP).hold(false);
                Keys.game(GameKey.SNEAK).hold(false);
                Keys.game(GameKey.SPRINT).hold(false);
            } catch (Throwable ignored) {}

            if (mc != null && mc.t != null) {
                sync(mc.t.Y);
                sync(mc.t.aa);
                sync(mc.t.Z);
                sync(mc.t.ab);
                sync(mc.t.ac);
                sync(mc.t.ae);
                sync(mc.t.ad);
                sync(mc.t.ag);
            }
            this.controllingKeys = false;
        }
    }
}
