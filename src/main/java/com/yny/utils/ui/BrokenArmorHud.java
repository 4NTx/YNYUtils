package com.yny.utils.ui;

import com.yny.utils.YNYUtils;
import com.yny.utils.config.YNYConfig;
import com.yny.utils.core.DebugLog;
import com.yny.utils.core.ToggleKey;
import com.yny.utils.modules.player.AutoArmorHelper;

import dev.xavier.stein.loader.api.Chat;
import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.HudElement;
import dev.xavier.stein.loader.api.HudPlacement;
import dev.xavier.stein.loader.api.Inventory;
import dev.xavier.stein.loader.api.Render;
import dev.xavier.stein.loader.api.Screens;
import dev.xavier.stein.loader.api.Stacks;

/**
 * HUD dedicada e independente para alertas visuais instantâneos quando uma peça
 * da armadura do PRÓPRIO JOGADOR quebra (capacete, peitoral, calça ou bota).
 *
 * Atende com precisão ao requisito:
 * "aviso na hud sobre o MEU SET, se quebrou x y z sabe? o MEU e não do adversário"
 *
 * Posicionável livremente pelo HUD Editor do Stein (Shift+R).
 */
public final class BrokenArmorHud implements HudElement {

    private static final long DISPLAY_NANOS = 4_500_000_000L; // 4.5 segundos visível na tela

    private static volatile String lastPieces = "CAPACETE";
    private static volatile int lastAccentColor = 0xFFFF3333;
    private static volatile long visibleUntil = 0L;

    // Rastreamento local da armadura equipada do jogador (0=Bota, 1=Calça, 2=Peitoral, 3=Capacete)
    private static final boolean[] prevWorn = new boolean[4];
    private static final long[] unequippedSince = new long[4];
    private static final boolean[] alertedForAbsence = new boolean[4];
    private static final long[] lastAlertPerPiece = new long[4];
    private static boolean initialized = false;

    // Tempo mínimo sem o item para alertar (1 segundo = 1000ms)
    // Permite que o AutoArmor reponha o item silenciosamente sem disparar falso positivo
    public static final long ABSENCE_ALERT_DELAY_MS = 1000L;

    public BrokenArmorHud() {}

    /**
     * Verifica se o alerta na HUD do próprio set está ativo tanto no Stein HUD quanto nas configs do modpack.
     */
    public static boolean isAlertEnabled() {
        try {
            if (!dev.xavier.stein.loader.api.Hud.isEnabled("ynyutils.broken-armor-alerts")) {
                return false;
            }
        } catch (Throwable ignored) {}
        YNYConfig config = YNYUtils.getConfig();
        return config == null || config.brokenArmorAlertChat;
    }

    /**
     * Alterna o estado do aviso do próprio set na HUD (liga/desliga com som, action bar e persistência).
     */
    public static boolean toggleState() {
        YNYConfig config = YNYUtils.getConfig();
        if (config == null) return false;
        return setState(!config.brokenArmorAlertChat);
    }

    /**
     * Define o estado do aviso do próprio set na HUD (com som, action bar e persistência).
     */
    public static boolean setState(boolean enabled) {
        YNYConfig config = YNYUtils.getConfig();
        if (config == null) return false;
        config.brokenArmorAlertChat = enabled;
        if (!enabled) {
            visibleUntil = 0L;
        }
        AutoArmorHelper.playToggleSound(enabled);
        try {
            Chat.actionBar(enabled ? "§a§lAlerta Meu Set (HUD): ATIVADO" : "§c§lAlerta Meu Set (HUD): DESATIVADO");
            Chat.print("§8[§c§lYNY§8] §7Aviso na HUD (Meu Set Quebrou): " + (enabled ? "§a§lATIVADO" : "§c§lDESATIVADO"));
        } catch (Throwable ignored) {}
        if (YNYUtils.getConfigStore() != null) {
            YNYUtils.getConfigStore().saveSoon();
        }
        return enabled;
    }

    private static final ToggleKey toggleKey = new ToggleKey();

    /**
     * Atualiza a verificação de tecla de atalho para alternar o aviso do meu set.
     */
    public static void updateToggleKey() {
        YNYConfig config = YNYUtils.getConfig();
        if (config == null || config.selfArmorAlertToggleKey == 0) return;
        if (toggleKey.wasPressed(config.selfArmorAlertToggleKey)) {
            toggleState();
        }
    }

    /**
     * Dispara um alerta na HUD informando que uma peça da armadura do próprio jogador quebrou.
     *
     * @param pieces Nome da peça ou peças que quebraram (ex: "CAPACETE", "PEITORAL", "BOTA", "CAPACETE & BOTA")
     * @param accentColor Cor de destaque da barra lateral
     */
    public static void notifySelfBroken(String pieces, int accentColor) {
        if (!isAlertEnabled()) return;

        lastPieces = pieces != null && !pieces.isEmpty() ? pieces : "ARMADURA";
        lastAccentColor = accentColor;
        visibleUntil = System.nanoTime() + DISPLAY_NANOS;

        // Som de aviso sonoro instantâneo
        AutoArmorHelper.playSubtleAlertSound();

        // Alerta na Action Bar e Chat para redundância visual no calor do combate
        try {
            Chat.actionBar("§c§l[!] SEU SET: §e" + lastPieces + " §c§lQUEBROU!");
            Chat.print("§8[§c§lYNY§8] §c§l[!] ALERTA: Seu §e" + lastPieces + " §c§lquebrou!");
        } catch (Throwable ignored) {}

        DebugLog.write("BrokenArmorHud", "MEU SET: " + lastPieces + " quebrou!");
    }

    /**
     * Método de compatibilidade legado (evita NoSuchMethodError caso algum patch externo invoque notify).
     */
    public static void notify(String player, String pieces, int accentColor) {
        // Redireciona para o alerta do próprio jogador se player for "Você" ou nulo
        if (player == null || player.isEmpty() || player.equalsIgnoreCase("Você") || player.equalsIgnoreCase("Voce")) {
            notifySelfBroken(pieces, accentColor);
        }
    }

    /**
     * Verificação chamada a cada tick no onTickEnd para detectar a perda/quebra de peças do MEU SET.
     */
    public static void checkSelfArmorBreak() {
        try {
            Object self = Entities.self();
            if (self == null || !Entities.isAlive(self)) {
                reset();
                return;
            }

            // Não alertar falso positivo quando o jogador estiver manuseando o inventário/baú manualmente,
            // ou quando o aviso estiver desativado pelo usuário.
            if (Screens.isOpen() || Inventory.isScreenOpen() || Inventory.isContainerOpen() || Inventory.cursor() != null || !isAlertEnabled()) {
                for (int piece = 0; piece < 4; piece++) {
                    Object worn = Inventory.armor(piece);
                    prevWorn[piece] = (worn != null && !isPieceBroken(worn));
                    unequippedSince[piece] = 0L;
                    alertedForAbsence[piece] = false;
                }
                initialized = true;
                return;
            }

            // Inicialização do estado na primeira execução da sessão
            if (!initialized) {
                for (int piece = 0; piece < 4; piece++) {
                    Object worn = Inventory.armor(piece);
                    prevWorn[piece] = (worn != null && !isPieceBroken(worn));
                    unequippedSince[piece] = 0L;
                    alertedForAbsence[piece] = false;
                }
                initialized = true;
                return;
            }

            long now = System.currentTimeMillis();
            boolean alertHelm = false;
            boolean alertChest = false;
            boolean alertLegs = false;
            boolean alertBoots = false;

            for (int piece = 0; piece < 4; piece++) {
                Object worn = Inventory.armor(piece);
                boolean isEquipped = (worn != null && !isPieceBroken(worn));

                if (isEquipped) {
                    // Peça equipada com sucesso: zera contagem de ausência e desarma alerta
                    prevWorn[piece] = true;
                    unequippedSince[piece] = 0L;
                    alertedForAbsence[piece] = false;
                } else {
                    // Peça ausente/quebrada: só monitora se a peça estava equipada anteriormente
                    if (prevWorn[piece]) {
                        if (unequippedSince[piece] == 0L) {
                            unequippedSince[piece] = now;
                        }

                        // Só avisar quando ficar mais de 1s sem o item (se o AutoArmor repuser antes de 1s, não avisa)
                        if (!alertedForAbsence[piece] && (now - unequippedSince[piece] >= ABSENCE_ALERT_DELAY_MS)) {
                            alertedForAbsence[piece] = true;
                            lastAlertPerPiece[piece] = now;
                            if (piece == 3) alertHelm = true;
                            else if (piece == 2) alertChest = true;
                            else if (piece == 1) alertLegs = true;
                            else if (piece == 0) alertBoots = true;
                        }
                    }
                }
            }

            if (alertHelm || alertChest || alertLegs || alertBoots) {
                String pieces = formatBrokenPieces(alertHelm, alertChest, alertLegs, alertBoots);
                notifySelfBroken(pieces, 0xFFFF3333);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Formata qualquer combinação de peças quebradas (1 a 4 peças) de maneira precisa e gramaticalmente correta.
     */
    public static String formatBrokenPieces(boolean lostHelm, boolean lostChest, boolean lostLegs, boolean lostBoots) {
        if (lostHelm && lostChest && lostLegs && lostBoots) {
            return "SET COMPLETO";
        }
        int count = (lostHelm ? 1 : 0) + (lostChest ? 1 : 0) + (lostLegs ? 1 : 0) + (lostBoots ? 1 : 0);
        if (count == 0) return "ARMADURA";

        StringBuilder sb = new StringBuilder();
        int added = 0;
        if (lostHelm) {
            sb.append("CAPACETE");
            added++;
        }
        if (lostChest) {
            if (added > 0) sb.append(added == count - 1 ? " & " : ", ");
            sb.append("PEITORAL");
            added++;
        }
        if (lostLegs) {
            if (added > 0) sb.append(added == count - 1 ? " & " : ", ");
            sb.append("CALÇA");
            added++;
        }
        if (lostBoots) {
            if (added > 0) sb.append(added == count - 1 ? " & " : ", ");
            sb.append("BOTA");
            added++;
        }
        return sb.toString();
    }

    private static boolean isPieceBroken(Object worn) {
        if (worn == null) return true;
        try {
            if (Stacks.isDamageable(worn)) {
                int max = Stacks.maxDamage(worn);
                int dmg = Stacks.damage(worn);
                return max > 0 && dmg >= max;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static void reset() {
        for (int i = 0; i < 4; i++) {
            prevWorn[i] = false;
            unequippedSince[i] = 0L;
            alertedForAbsence[i] = false;
            lastAlertPerPiece[i] = 0L;
        }
        initialized = false;
        visibleUntil = 0L;
    }

    public static long getUnequippedSince(int piece) {
        return (piece >= 0 && piece < 4) ? unequippedSince[piece] : 0L;
    }

    public static boolean isAlertedForAbsence(int piece) {
        return (piece >= 0 && piece < 4) && alertedForAbsence[piece];
    }

    public static boolean isPrevWorn(int piece) {
        return (piece >= 0 && piece < 4) && prevWorn[piece];
    }

    @Override
    public String id() {
        return "ynyutils.broken-armor-alerts";
    }

    @Override
    public String name() {
        return "Alerta: Meu Set Quebrou";
    }

    @Override
    public boolean layout(boolean preview, float partialTicks) {
        if (preview) return true;
        return isAlertEnabled() && System.nanoTime() < visibleUntil;
    }

    @Override
    public int width() {
        try {
            boolean isPreview = System.nanoTime() >= visibleUntil;
            String pieces = isPreview ? "CAPACETE & BOTA" : lastPieces;
            String detail = "Quebrou: " + pieces;
            int detailW = Render.textWidth(detail);
            int titleW = Render.textWidth("[!] ALERTA: SEU SET QUEBROU!");
            return Math.max(175, Math.max(detailW, titleW) + 20);
        } catch (Throwable t) {
            return 175;
        }
    }

    @Override
    public int height() {
        return 26;
    }

    @Override
    public void draw(boolean alignRight, float partialTicks) {
        try {
            int w = width();
            int h = height();
            float x = alignRight ? -w : 0.0F;

            boolean isPreview = System.nanoTime() >= visibleUntil;
            String pieces = isPreview ? "CAPACETE & BOTA" : lastPieces;
            int accent = isPreview ? 0xFFFF3333 : lastAccentColor;

            // Fundo escuro translúcido com cantos arredondados
            Render.roundRect(x, 0.0F, (float) w, (float) h, 4.0F, 0xDC111622);
            // Barra lateral colorida de destaque no lado esquerdo
            Render.rect(x, 0.0F, 3.0F, (float) h, accent);

            // Linha 1: Título do alerta sobre o MEU SET
            Render.text("§c§l[!] ALERTA: SEU SET QUEBROU!", x + 8.0F, 3.0F, 0xFFFF5555, true);
            // Linha 2: Peça quebrada do meu set (x, y, z)
            String line = "§eQuebrou: §c§l" + pieces;
            Render.text(line, x + 8.0F, 14.0F, 0xFFFFFFFF, true);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public HudPlacement defaultPlacement() {
        // Centralizado horizontalmente no topo (anchorX: 1, anchorY: 0), 48px abaixo do topo da tela
        return new HudPlacement(1, 0, 0, 48, 1.0F, true, false);
    }

    @Override
    public void options(java.util.List<dev.xavier.stein.loader.api.Option> options) {
        options.add(dev.xavier.stein.loader.api.Option.toggle("Alerta quando MEU SET quebrar", () -> {
            YNYConfig cfg = YNYUtils.getConfig();
            return cfg == null || cfg.brokenArmorAlertChat;
        }, value -> {
            setState(value);
        }));
        options.add(dev.xavier.stein.loader.api.Option.action("Testar Alerta na HUD", "Disparar Alerta Teste", () -> {
            notifySelfBroken("CAPACETE & BOTA", 0xFFFF3333);
        }));
    }
}
