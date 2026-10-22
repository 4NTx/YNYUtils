package com.yny.utils.telemetry;

import com.yny.utils.core.DebugLog;

/** Records commands entered in the chat box before other modules alter or consume them. */
public final class OutgoingCommandLog {
    private OutgoingCommandLog() { }

    public static void record(String message) {
        if (message == null) return;
        int index = 0;
        while (index < message.length() && Character.isWhitespace(message.charAt(index))) index++;
        if (index >= message.length() || message.charAt(index) != '/') return;

        // Filtra comandos sensíveis contendo senhas ou credenciais de autenticação
        if (isSensitiveCommand(message.substring(index))) {
            return;
        }

        try { DebugLog.write("Command", "outgoing=" + message); } catch (Throwable ignored) { }
    }

    public static boolean isSensitiveCommand(String cmd) {
        if (cmd == null) return false;
        String clean = cmd.trim();
        if (clean.startsWith("/")) clean = clean.substring(1).trim();
        if (clean.isEmpty()) return false;
        String lower = clean.toLowerCase(java.util.Locale.ROOT);
        String root = lower.split("\\s+")[0];
        return root.equals("login") || root.equals("l") || root.equals("log")
                || root.equals("register") || root.equals("reg") || root.equals("registro")
                || root.equals("changepassword") || root.equals("trocarsenha") || root.equals("mudarsenha")
                || root.equals("auth") || root.equals("pin") || root.equals("2fa") || root.equals("senha");
    }
}
