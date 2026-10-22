package com.yny.utils.core;

import com.yny.utils.YNYUtils;
import com.yny.utils.config.YNYConfig;
import com.yny.utils.modules.pvp.FocusMode;
import com.yny.utils.ui.BrokenArmorHud;

import dev.xavier.stein.loader.api.Chat;

import java.util.Locale;

/**
 * Processador central de comandos de chat do cliente (/focus, /meuset, etc.).
 * Permite ativar, desativar, configurar e testar funcionalidades de combate e HUD via comandos locais,
 * interceptando-os para que nunca sejam enviados ao servidor.
 */
public final class YNYCommands {

    private YNYCommands() {}

    /**
     * Intercepta mensagens enviadas pelo jogador no chat.
     * Retorna true se for um comando do YNYUtils (cancelando o envio para o servidor),
     * ou false se for mensagem normal de chat ou comando do servidor.
     */
    public static boolean handleChatCommand(String message) {
        if (message == null) return false;
        String trimmed = message.trim();
        if (trimmed.isEmpty()) return false;

        String[] parts = trimmed.split("\\s+");
        if (parts.length == 0) return false;

        String cmd = parts[0].toLowerCase(Locale.ROOT);

        // 1. Comando: /focus ou .focus ou /modofoco ou /foco
        if (cmd.equals("/focus") || cmd.equals(".focus")
                || cmd.equals("/modofoco") || cmd.equals(".modofoco")
                || cmd.equals("/foco") || cmd.equals(".foco")
                || cmd.equals("/focusmode") || cmd.equals(".focusmode")) {
            handleFocusCommand(parts);
            return true;
        }

        // 2. Comando: /meuset ou .meuset ou /setalert ou /alertaarmadura
        if (cmd.equals("/meuset") || cmd.equals(".meuset")
                || cmd.equals("/setalert") || cmd.equals(".setalert")
                || cmd.equals("/alertaarmadura") || cmd.equals(".alertaarmadura")
                || cmd.equals("/meusetquebrou") || cmd.equals(".meusetquebrou")
                || cmd.equals("/armorhud") || cmd.equals(".armorhud")
                || cmd.equals("/brokenhud") || cmd.equals(".brokenhud")) {
            handleSelfArmorCommand(parts);
            return true;
        }

        return false;
    }

    private static void handleFocusCommand(String[] parts) {
        YNYConfig config = YNYUtils.getConfig();
        if (config == null) return;

        if (parts.length <= 1) {
            FocusMode.toggleState();
            return;
        }

        String sub = parts[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "on":
            case "ativar":
            case "ligar":
            case "true":
            case "1":
                FocusMode.setState(true);
                break;

            case "off":
            case "desativar":
            case "desligar":
            case "false":
            case "0":
                FocusMode.setState(false);
                break;

            case "toggle":
            case "alternar":
                FocusMode.toggleState();
                break;

            case "elmo":
            case "helmet":
            case "capacete":
                config.focusOnlyHelmet = !config.focusOnlyHelmet;
                try {
                    Chat.actionBar(config.focusOnlyHelmet ? "§eModo Foco: APENAS CAPACETE" : "§eModo Foco: QUALQUER PEÇA");
                    Chat.print("§8[§c§lYNY§8] §7Modo Foco agora foca: " + (config.focusOnlyHelmet ? "§eApenas adversários sem capacete" : "§eAdversários sem qualquer peça (elmo, peitoral, calça, bota)"));
                } catch (Throwable ignored) {}
                if (YNYUtils.getConfigStore() != null) YNYUtils.getConfigStore().saveSoon();
                break;

            case "tempo":
            case "time":
                if (parts.length >= 3) {
                    try {
                        int sec = Integer.parseInt(parts[2]);
                        config.focusHelmetTimeSeconds = Math.max(1, Math.min(5, sec));
                        try {
                            Chat.actionBar("§eTempo sem capacete: " + config.focusHelmetTimeSeconds + "s");
                            Chat.print("§8[§c§lYNY§8] §7Tempo sem capacete definido para: §e" + config.focusHelmetTimeSeconds + " segundos");
                        } catch (Throwable ignored) {}
                        if (YNYUtils.getConfigStore() != null) YNYUtils.getConfigStore().saveSoon();
                    } catch (NumberFormatException e) {
                        try { Chat.print("§c[!] Uso: /focus tempo <1 a 5 segundos>"); } catch (Throwable ignored) {}
                    }
                } else {
                    try { Chat.print("§e[!] Tempo atual sem capacete: " + config.focusHelmetTimeSeconds + "s (uso: /focus tempo <1-5>)"); } catch (Throwable ignored) {}
                }
                break;

            case "alcance":
            case "reach":
                if (parts.length >= 3) {
                    try {
                        double r = Double.parseDouble(parts[2].replace(',', '.'));
                        config.focusReach = Math.max(3.0D, Math.min(6.0D, r));
                        try {
                            Chat.actionBar("§eAlcance do Foco: " + String.format(Locale.ROOT, "%.1f", config.focusReach) + " blocos");
                            Chat.print("§8[§c§lYNY§8] §7Alcance do Modo Foco definido para: §e" + String.format(Locale.ROOT, "%.1f", config.focusReach) + " blocos");
                        } catch (Throwable ignored) {}
                        if (YNYUtils.getConfigStore() != null) YNYUtils.getConfigStore().saveSoon();
                    } catch (NumberFormatException e) {
                        try { Chat.print("§c[!] Uso: /focus alcance <3.0 a 6.0 blocos>"); } catch (Throwable ignored) {}
                    }
                } else {
                    try { Chat.print("§e[!] Alcance atual do Foco: " + String.format(Locale.ROOT, "%.1f", config.focusReach) + " blocos"); } catch (Throwable ignored) {}
                }
                break;

            case "status":
                try {
                    Chat.print("§8[§c§lYNY§8] §7Modo Foco: " + (config.focusModeEnabled ? "§a§lATIVADO" : "§c§lDESATIVADO")
                            + " §7| Alvo: " + (config.focusOnlyHelmet ? "§eApenas Capacete" : "§eQualquer Peça")
                            + " §7| Tempo: §e" + config.focusHelmetTimeSeconds + "s"
                            + " §7| Alcance: §e" + String.format(Locale.ROOT, "%.1f", config.focusReach) + " blocos");
                } catch (Throwable ignored) {}
                break;

            case "help":
            case "ajuda":
            case "?":
            default:
                try {
                    Chat.print("§8==== §c§lMODO FOCO (PvP Focus) §8====");
                    Chat.print("§f/focus §7- Alterna o Modo Foco (Ligar/Desligar)");
                    Chat.print("§f/focus on §7| §f/focus off §7- Ativa ou desativa diretamente");
                    Chat.print("§f/focus elmo §7- Alterna focar apenas sem capacete ou qualquer peça");
                    Chat.print("§f/focus tempo <1-5> §7- Define tempo sem capacete (padrão: 1s)");
                    Chat.print("§f/focus alcance <3-6> §7- Define alcance de ataque (padrão: 4.2)");
                    Chat.print("§f/focus status §7- Exibe o status e parâmetros atuais");
                    Chat.print("§7Estado atual: " + (config.focusModeEnabled ? "§a§lATIVADO" : "§c§lDESATIVADO")
                            + " §7| Alvo: " + (config.focusOnlyHelmet ? "§eApenas Capacete" : "§eQualquer Peça")
                            + " §7| Tempo: §e" + config.focusHelmetTimeSeconds + "s");
                } catch (Throwable ignored) {}
                break;
        }
    }

    private static void handleSelfArmorCommand(String[] parts) {
        YNYConfig config = YNYUtils.getConfig();
        if (config == null) return;

        if (parts.length <= 1) {
            BrokenArmorHud.toggleState();
            return;
        }

        String sub = parts[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status":
                try {
                    Chat.print("§8[§c§lYNY§8] §7Aviso Meu Set (HUD): " + (config.brokenArmorAlertChat ? "§a§lATIVADO" : "§c§lDESATIVADO"));
                } catch (Throwable ignored) {}
                break;
            case "on":
            case "ativar":
            case "ligar":
            case "true":
            case "1":
                BrokenArmorHud.setState(true);
                break;

            case "off":
            case "desativar":
            case "desligar":
            case "false":
            case "0":
                BrokenArmorHud.setState(false);
                break;

            case "toggle":
            case "alternar":
                BrokenArmorHud.toggleState();
                break;

            case "test":
            case "teste":
                BrokenArmorHud.notifySelfBroken("CAPACETE & BOTA", 0xFFFF3333);
                try {
                    Chat.print("§8[§c§lYNY§8] §aAlerta teste disparado na HUD! (posicione com Shift+R)");
                } catch (Throwable ignored) {}
                break;

            case "help":
            case "ajuda":
            case "?":
            default:
                try {
                    Chat.print("§8==== §c§lALERTA: MEU SET QUEBROU (HUD) §8====");
                    Chat.print("§f/meuset §7- Alterna o aviso de armadura própria na HUD");
                    Chat.print("§f/meuset on §7| §f/meuset off §7- Ativa ou desativa diretamente");
                    Chat.print("§f/meuset test §7- Dispara um alerta de teste na HUD");
                    Chat.print("§7Dica: Pressione §eShift+R §7para abrir o HUD Editor do Stein e mover o alerta.");
                    Chat.print("§7Estado atual: " + (config.brokenArmorAlertChat ? "§a§lATIVADO" : "§c§lDESATIVADO"));
                } catch (Throwable ignored) {}
                break;
        }
    }
}
