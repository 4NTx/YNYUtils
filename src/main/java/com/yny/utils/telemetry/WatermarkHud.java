package com.yny.utils.telemetry;

import dev.xavier.stein.loader.api.HudElement;
import dev.xavier.stein.loader.api.HudPlacement;
import dev.xavier.stein.loader.api.Render;

/**
 * Elemento discreto de marca d'água na HUD.
 * Exibe a versão e a assinatura curta de compilação da instalação (ex: YNY v1.0.0 #8F3A92C1),
 * permitindo auditoria visual em capturas de tela sem atrapalhar a jogabilidade
 * e sem expor dados confidenciais do usuário.
 */
public final class WatermarkHud implements HudElement {

    private final TelemetryIdentity identity;
    private final TelemetryConfig config;

    public WatermarkHud(TelemetryIdentity identity, TelemetryConfig config) {
        this.identity = identity;
        this.config = config;
    }

    private String getDisplayText() {
        String prefix = config.watermarkPrefix;
        if (prefix == null || prefix.trim().isEmpty()) {
            return "v1.0.0 #" + identity.getShortId();
        }
        return prefix.trim() + " v1.0.0 #" + identity.getShortId();
    }

    @Override
    public String id() {
        return "ynyutils.watermark";
    }

    @Override
    public String name() {
        return "Identificador da Modpack";
    }

    @Override
    public boolean layout(boolean preview, float partialTicks) {
        // Bloqueado para edição no editor de HUD (não permite mover ou esconder)
        return !preview;
    }

    @Override
    public int width() {
        try {
            int w = Render.textWidth(getDisplayText());
            return w > 0 ? w : 70;
        } catch (Throwable t) {
            return 70;
        }
    }

    @Override
    public int height() {
        try {
            int h = Render.fontHeight();
            return h > 0 ? h : 10;
        } catch (Throwable t) {
            return 10;
        }
    }

    @Override
    public void draw(boolean alignRight, float partialTicks) {
        try {
            int w = width();
            int x = alignRight ? -w : 0;

            // Opacidade fixa de 60% conforme requisitos de auditoria
            int alpha = 153; // 255 * 0.60f
            int color = (alpha << 24) | 0x00CCCCCC;

            Render.text(getDisplayText(), x, 0, color, true);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public HudPlacement defaultPlacement() {
        // Canto inferior esquerdo (anchorY = 2), escala 0.75x, visível inclusive no chat
        return new HudPlacement(0, 2, 0, 0, 0.75F, true, true);
    }

    @Override
    public HudPlacement savedPlacement() {
        // Sempre retorna a localização definitiva bloqueada da modpack
        return new HudPlacement(0, 2, 0, 0, 0.75F, true, true);
    }

    @Override
    public void options(java.util.List<dev.xavier.stein.loader.api.Option> list) {
        // Bloqueado para modificação
    }
}
