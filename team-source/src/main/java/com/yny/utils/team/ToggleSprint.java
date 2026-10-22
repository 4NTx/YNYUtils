/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package com.yny.utils.team;

import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Keys;
import dev.xavier.stein.loader.api.Players;
import dev.xavier.stein.loader.api.Screens;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Toggle sprint: sprinting without holding the game's sprint key — the mod keeps it pressed and
 * asserts sprinting immediately when moving, without needing double-tap W or holding Control.
 */
final class ToggleSprint {

    /** Toggle sprint on (the key, or the Panel row). Starts on by default. */
    boolean toggled = true;

    /** The mod is holding the game's sprint key. */
    private boolean holding;

    private Method setSprintingMethod;
    private boolean methodResolved;

    private Field sprintPressedField;
    private boolean sprintPressedResolved;

    private Field movementInputField;
    private boolean inputFieldResolved;
    private Field moveForwardField;
    private Field moveStrafeField;
    private Field sneakField;

    /**
     * Every tick: the game's sprint key stays pressed; when turned off, it goes back to the
     * keyboard's state. When moving forward or in all directions, forces sprinting immediately.
     */
    void tick(boolean allDirections) {
        boolean want = toggled && Players.self() != null && !Screens.isOpen();
        if (want) {
            setSprintPressed(true);
            Keys.game(Keys.GameKey.SPRINT).hold(true);
            holding = true;
            Object self = Entities.self();
            if (self != null && isMoving(self, allDirections)) {
                forceSprint(self);
            }
        } else if (holding) {
            holding = false;
            Keys.game(Keys.GameKey.SPRINT).hold(false);
            setSprintPressed(false);
        }
    }

    private void setSprintPressed(boolean pressed) {
        try {
            Object handle = Keys.game(Keys.GameKey.SPRINT).handle();
            if (handle != null) {
                if (sprintPressedField == null && !sprintPressedResolved) {
                    sprintPressedResolved = true;
                    Class<?> c = handle.getClass();
                    while (c != null && c != Object.class) {
                        for (Field f : c.getDeclaredFields()) {
                            if (f.getType() == boolean.class
                                    && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                                if ("pressed".equals(f.getName())
                                        || "h".equals(f.getName())
                                        || "field_74513_e".equals(f.getName())) {
                                    sprintPressedField = f;
                                    f.setAccessible(true);
                                    break;
                                }
                            }
                        }
                        if (sprintPressedField != null) break;
                        c = c.getSuperclass();
                    }
                }
                if (sprintPressedField != null) {
                    sprintPressedField.setBoolean(handle, pressed);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private boolean isMoving(Object self, boolean allDirections) {
        try {
            if (movementInputField == null && !inputFieldResolved) {
                inputFieldResolved = true;
                Class<?> c = self.getClass();
                while (c != null && c != Object.class) {
                    for (Field f : c.getDeclaredFields()) {
                        if ("movementInput".equals(f.getName())
                                || "b".equals(f.getName())
                                || "field_71158_b".equals(f.getName())) {
                            movementInputField = f;
                            f.setAccessible(true);
                            break;
                        }
                    }
                    if (movementInputField != null) break;
                    c = c.getSuperclass();
                }
            }
            if (movementInputField != null) {
                Object input = movementInputField.get(self);
                if (input != null) {
                    if (moveForwardField == null) {
                        Class<?> ic = input.getClass();
                        while (ic != null && ic != Object.class) {
                            for (Field f : ic.getDeclaredFields()) {
                                if ("moveForward".equals(f.getName())
                                        || "b".equals(f.getName())
                                        || "field_78900_b".equals(f.getName())) {
                                    moveForwardField = f;
                                    f.setAccessible(true);
                                } else if ("moveStrafe".equals(f.getName())
                                        || "a".equals(f.getName())
                                        || "field_78902_a".equals(f.getName())) {
                                    moveStrafeField = f;
                                    f.setAccessible(true);
                                } else if ("sneak".equals(f.getName())
                                        || "d".equals(f.getName())
                                        || "field_78899_d".equals(f.getName())) {
                                    sneakField = f;
                                    f.setAccessible(true);
                                }
                            }
                            ic = ic.getSuperclass();
                        }
                    }
                    if (sneakField != null && sneakField.getBoolean(input)) {
                        return false;
                    }
                    float fwd = moveForwardField != null ? moveForwardField.getFloat(input) : 0f;
                    float str = moveStrafeField != null ? moveStrafeField.getFloat(input) : 0f;
                    if (fwd > 0.01f) {
                        return true;
                    }
                    if (allDirections && (Math.abs(fwd) > 0.01f || Math.abs(str) > 0.01f)) {
                        return true;
                    }
                    return false;
                }
            }
        } catch (Throwable ignored) {
        }
        return Keys.game(Keys.GameKey.FORWARD).isDown();
    }

    private void forceSprint(Object self) {
        try {
            if (setSprintingMethod == null && !methodResolved) {
                methodResolved = true;
                Class<?> c = self.getClass();
                while (c != null && c != Object.class) {
                    for (Method m : c.getDeclaredMethods()) {
                        if (m.getParameterCount() == 1
                                && m.getParameterTypes()[0] == boolean.class) {
                            if ("setSprinting".equals(m.getName())
                                    || "d".equals(m.getName())
                                    || "func_70031_b".equals(m.getName())) {
                                setSprintingMethod = m;
                                m.setAccessible(true);
                                break;
                            }
                        }
                    }
                    if (setSprintingMethod != null) break;
                    c = c.getSuperclass();
                }
            }
            if (setSprintingMethod != null) {
                setSprintingMethod.invoke(self, true);
            }
        } catch (Throwable ignored) {
        }
    }
}
