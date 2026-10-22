/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package com.yny.utils.team;

import dev.xavier.stein.loader.api.Keys;
import dev.xavier.stein.loader.api.Lang;

/** Only team controls are registered in the YNYUtils package. */
final class CombatKeys {
    private final SteinCombat mod;
    final Keys.Binding toggleSprint;
    final Keys.Binding ally = null;
    final Keys.Binding enemy = null;
    final Keys.Binding noHitDelay = null;
    final Keys.Binding keepSprint = null;
    final Keys.Binding omniSprint = null;
    final Keys.Binding autoArmor = null;
    final Keys.Binding smartEffect = null;
    final Keys.Binding autoProtect = null;
    final Keys.Binding autoRefill = null;
    final Keys.Binding autoclick = null;
    final Keys.Binding hide;
    final Keys.Binding clanProtect;

    CombatKeys(SteinCombat mod) {
        this.mod = mod;
        toggleSprint = Keys.bind("key.steincombat.toggleSprint", Keys.KEY_G, "key.categories.ynyutils");
        hide = Keys.bind("key.steincombat.hide", Keys.KEY_H, "key.categories.ynyutils");
        clanProtect =
                Keys.bind("key.steincombat.clanProtect", Keys.KEY_NONE, "key.categories.ynyutils");
    }

    void handle() {
        if (toggleSprint != null) {
            while (toggleSprint.consumeClick()) {
                mod.config.toggleSprint = !mod.config.toggleSprint;
                mod.sprint.toggled = mod.config.toggleSprint;
                mod.save();
                Say.actionBar(
                        Lang.tr(mod.config.toggleSprint ? "steincombat.sprintOn" : "steincombat.sprintOff"));
            }
        }
        while (hide.consumeClick()) {
            mod.guard.hiding = !mod.guard.hiding;
            Say.chat(Lang.tr(mod.guard.hiding ? "steincombat.hideOn" : "steincombat.hideOff"));
        }
        while (clanProtect.consumeClick()) {
            mod.config.protectAllies = !mod.config.protectAllies;
            mod.save();
            Say.actionBar(
                    Lang.tr(
                            mod.config.protectAllies
                                    ? "steincombat.featureOn"
                                    : "steincombat.featureOff",
                            Lang.tr("steincombat.protect")));
        }
    }
}
