package com.yny.utils.modules.player;

import com.yny.utils.core.DebugLog;

/** Retains full reservation details while suppressing identical tick snapshots. */
public final class ArmorLog {
    private ArmorLog() { }

    public static void write(String category, String message) {
        if (message != null && message.startsWith("scan reservas ")) {
            int end = message.indexOf(';');
            String piece = end < 0 ? message.substring(14) : message.substring(14, end);
            DebugLog.state(category, "reservas_" + piece, message);
        } else {
            DebugLog.write(category, message);
        }
    }
}
