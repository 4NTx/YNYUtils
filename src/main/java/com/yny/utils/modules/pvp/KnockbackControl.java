package com.yny.utils.modules.pvp;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

/**
 * Ajusta exclusivamente o S12 destinado ao jogador local antes de o manipulador
 * vanilla aplicar os valores em Entity.motionX, Entity.motionY e Entity.motionZ.
 */
public final class KnockbackControl {

    private static final String HANDLER_NAME = "ynyutils_knockback_control";

    private final BooleanSupplier enabled;
    private final IntSupplier percent;
    private volatile int localPlayerId = Integer.MIN_VALUE;
    private volatile Channel installedChannel;
    private volatile boolean installationPending;

    public KnockbackControl(BooleanSupplier enabled, IntSupplier percent) {
        this.enabled = enabled;
        this.percent = percent;
    }

    /** Solicita uma instalação para a conexão criada ao entrar no mundo. */
    public void requestInstallation() {
        installedChannel = null;
        localPlayerId = Integer.MIN_VALUE;
        installationPending = true;
    }

    /**
     * Só consulta o Minecraft enquanto uma instalação está pendente. Depois de
     * instalado, retorna imediatamente até o próximo {@code onJoinGame}.
     */
    public void installIfPending() {
        if (!installationPending) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }
        localPlayerId = mc.thePlayer.getEntityId();

        NetHandlerPlayClient handler = mc.getNetHandler();
        if (handler == null) {
            return;
        }
        NetworkManager network = handler.getNetworkManager();
        Channel channel = network == null ? null : network.channel;
        if (channel == null) {
            return;
        }
        if (channel == installedChannel && channel.pipeline().get(HANDLER_NAME) != null) {
            installationPending = false;
            return;
        }
        try {
            channel.pipeline().addBefore("packet_handler", HANDLER_NAME, new VelocityInterceptor(this));
            installedChannel = channel;
            installationPending = false;
        } catch (Exception ignored) {
            // A pipeline ainda pode estar sendo montada; o próximo tick tenta de novo.
        }
    }

    private void adjust(S12PacketEntityVelocity packet) {
        if (!enabled.getAsBoolean() || packet.getEntityID() != localPlayerId) {
            return;
        }
        int value = Math.max(93, Math.min(100, percent.getAsInt()));
        if (value == 100) {
            return;
        }
        packet.motionX = scale(packet.motionX, value);
        packet.motionY = scale(packet.motionY, value);
        packet.motionZ = scale(packet.motionZ, value);
    }

    private static int scale(int motion, int percent) {
        return Math.round(motion * (percent / 100.0F));
    }

    private static final class VelocityInterceptor extends ChannelInboundHandlerAdapter {

        private final KnockbackControl control;

        VelocityInterceptor(KnockbackControl control) {
            this.control = control;
        }

        @Override
        public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
            if (message instanceof S12PacketEntityVelocity) {
                control.adjust((S12PacketEntityVelocity) message);
            }
            context.fireChannelRead(message);
        }
    }
}
