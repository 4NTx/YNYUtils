package com.yny.utils.modules.pvp;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Locale;

import com.yny.utils.core.DebugLog;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

/**
 * Ajusta exclusivamente o S12 destinado ao jogador local antes de o manipulador
 * vanilla aplicar os valores em Entity.motionX, Entity.motionY e Entity.motionZ.
 */
public final class KnockbackControl {

    private static final String HANDLER_NAME = "ynyutils_knockback_control";
    private static final int DISPLACEMENT_TICKS = 6;

    private final BooleanSupplier enabled;
    private final IntSupplier percent;
    private final BooleanSupplier preserveVertical;
    private final BooleanSupplier jumpResetEnabled;
    private final BooleanSupplier diagnostics;
    private final KnockbackJumpReset jumpReset;
    private final ArrayBlockingQueue<VelocitySample> samples = new ArrayBlockingQueue<>(128);
    private final AtomicInteger droppedSamples = new AtomicInteger();
    private final AtomicInteger serverCorrections = new AtomicInteger();
    private DisplacementTrace displacement;
    private volatile int localPlayerId = Integer.MIN_VALUE;
    private volatile Channel installedChannel;
    private volatile boolean installationPending;

    public KnockbackControl(BooleanSupplier enabled, IntSupplier percent,
            BooleanSupplier preserveVertical, BooleanSupplier jumpResetEnabled,
            BooleanSupplier diagnostics) {
        this.enabled = enabled;
        this.percent = percent;
        this.preserveVertical = preserveVertical;
        this.jumpResetEnabled = jumpResetEnabled;
        this.diagnostics = diagnostics;
        this.jumpReset = new KnockbackJumpReset(jumpResetEnabled, diagnostics);
    }

    /** Solicita uma instalação para a conexão criada ao entrar no mundo. */
    public void requestInstallation() {
        jumpReset.reset();
        samples.clear();
        droppedSamples.set(0);
        serverCorrections.set(0);
        displacement = null;
        removeHandler();
        localPlayerId = Integer.MIN_VALUE;
        installationPending = true;
    }

    public void onDisconnected() {
        jumpReset.reset();
        samples.clear();
        droppedSamples.set(0);
        serverCorrections.set(0);
        displacement = null;
        removeHandler();
        localPlayerId = Integer.MIN_VALUE;
        installationPending = false;
    }

    private void removeHandler() {
        Channel channel = installedChannel;
        installedChannel = null;
        if (channel != null && channel.pipeline().get(HANDLER_NAME) != null) {
            channel.pipeline().remove(HANDLER_NAME);
        }
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
        if (channel.pipeline().get(HANDLER_NAME) != null) {
            installedChannel = channel;
            installationPending = false;
            return;
        }
        if (channel.pipeline().get("packet_handler") == null) {
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

    public void onTickEnd() {
        jumpReset.onTickEnd();
        if (!diagnostics.getAsBoolean()) {
            samples.clear();
            droppedSamples.set(0);
            displacement = null;
            return;
        }
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        VelocitySample sample;
        int drained = 0;
        boolean receivedVelocity = false;
        while (drained++ < 64 && (sample = samples.poll()) != null) {
            DebugLog.write("Knockback", sample.describe());
            if (sample.originalX == 0 && sample.originalZ == 0) {
                continue;
            }
            if (displacement != null) {
                DebugLog.write("Knockback", displacement.describe("outro-S12", serverCorrections.get(),
                        jumpReset.jumpedSince(displacement.impulse.receivedAtNanos)));
            }
            displacement = player == null ? null : new DisplacementTrace(sample, player, serverCorrections.get());
            receivedVelocity = true;
        }
        if (!receivedVelocity && displacement != null) {
            if (player == null) {
                DebugLog.write("Knockback", displacement.describe("sem-jogador", serverCorrections.get(),
                        jumpReset.jumpedSince(displacement.impulse.receivedAtNanos)));
                displacement = null;
            } else {
                displacement.observe(player);
                if (displacement.ticks >= DISPLACEMENT_TICKS) {
                    DebugLog.write("Knockback", displacement.describe("janela-completa", serverCorrections.get(),
                            jumpReset.jumpedSince(displacement.impulse.receivedAtNanos)));
                    displacement = null;
                }
            }
        }
        int dropped = droppedSamples.getAndSet(0);
        if (dropped > 0) {
            DebugLog.write("Knockback", "S12 amostras descartadas por fila cheia=" + dropped);
        }
    }

    public void reset() {
        jumpReset.reset();
        displacement = null;
    }

    public void onLocalHurt() {
        jumpReset.onLocalHurt();
    }

    private VelocitySample adjust(S12PacketEntityVelocity packet) {
        if (packet.getEntityID() != localPlayerId) {
            return null;
        }
        int originalX = packet.motionX;
        int originalY = packet.motionY;
        int originalZ = packet.motionZ;
        jumpReset.onVelocity(packet.motionX, packet.motionY, packet.motionZ);
        boolean reduce = enabled.getAsBoolean();
        int value = reduce ? Math.max(93, Math.min(100, percent.getAsInt())) : 100;
        boolean verticalVanilla = preserveVertical.getAsBoolean();
        if (reduce && value != 100) {
            packet.motionX = scale(packet.motionX, value);
            if (!verticalVanilla) {
                packet.motionY = scale(packet.motionY, value);
            }
            packet.motionZ = scale(packet.motionZ, value);
        }
        VelocitySample sample = new VelocitySample(packet.getEntityID(), originalX, originalY, originalZ,
                packet.motionX, packet.motionY, packet.motionZ,
                reduce, value, verticalVanilla, jumpResetEnabled.getAsBoolean());
        if (diagnostics.getAsBoolean()) {
            if (!samples.offer(sample)) {
                droppedSamples.incrementAndGet();
            }
        }
        return sample;
    }

    /** Roda após o handler vanilla do S12, sem tocar movimento normal entre hits. */
    private void afterVelocityHandled(VelocitySample sample, Channel sourceChannel) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (sourceChannel != installedChannel || player == null || mc.theWorld == null
                || player.getEntityId() != sample.entityId) {
            return;
        }
        double expectedX = sample.appliedX / 8000.0D;
        double expectedY = sample.appliedY / 8000.0D;
        double expectedZ = sample.appliedZ / 8000.0D;
        boolean applied = near(player.motionX, expectedX) && near(player.motionZ, expectedZ)
                && near(player.motionY, expectedY);
        String result = applied ? "aplicado" : "divergente";
        if (!applied && sample.reduce && sample.percent != 100
                && near(player.motionX, sample.originalX / 8000.0D)
                && near(player.motionZ, sample.originalZ / 8000.0D)
                && near(player.motionY, sample.originalY / 8000.0D)) {
            // Só corrige quando a entidade ainda contém exatamente o impulso
            // vanilla deste S12. Nunca escala movimento já alterado por input.
            player.motionX = expectedX;
            player.motionY = expectedY;
            player.motionZ = expectedZ;
            result = "corrigido-do-vanilla";
        }
        if (diagnostics.getAsBoolean()) {
            DebugLog.write("Knockback", String.format(Locale.ROOT,
                    "entidade pós-S12; resultado=%s; motion=(%.4f,%.4f,%.4f); esperado=(%.4f,%.4f,%.4f); "
                    + "ground=%s; sprint=%s; hurtTime=%d",
                    result, player.motionX, player.motionY, player.motionZ,
                    expectedX, expectedY, expectedZ, player.onGround, player.isSprinting(), player.hurtTime));
        }
        jumpReset.onVelocityApplied();
    }

    private static boolean near(double first, double second) {
        return Math.abs(first - second) < 0.0001D;
    }

    static int scale(int motion, int percent) {
        return Math.round(motion * (percent / 100.0F));
    }

    private static final class VelocitySample {
        final int entityId;
        final long receivedAtNanos;
        final int originalX, originalY, originalZ;
        final int appliedX, appliedY, appliedZ;
        final boolean reduce, verticalVanilla, jumpReset;
        final int percent;

        VelocitySample(int entityId, int originalX, int originalY, int originalZ,
                int appliedX, int appliedY, int appliedZ,
                boolean reduce, int percent, boolean verticalVanilla, boolean jumpReset) {
            this.entityId = entityId;
            this.receivedAtNanos = System.nanoTime();
            this.originalX = originalX;
            this.originalY = originalY;
            this.originalZ = originalZ;
            this.appliedX = appliedX;
            this.appliedY = appliedY;
            this.appliedZ = appliedZ;
            this.reduce = reduce;
            this.percent = percent;
            this.verticalVanilla = verticalVanilla;
            this.jumpReset = jumpReset;
        }

        String describe() {
            return String.format(Locale.ROOT,
                    "S12 local; reducao=%s; percentualXZ=%d; percentualY=%d; "
                    + "verticalVanilla=%s; jumpReset=%s; "
                    + "vanilla=(%.4f,%.4f,%.4f); aplicado=(%.4f,%.4f,%.4f); "
                    + "inteirosVanilla=(%d,%d,%d); inteirosAplicado=(%d,%d,%d)",
                    reduce, percent, reduce && !verticalVanilla ? percent : 100,
                    verticalVanilla, jumpReset,
                    originalX / 8000.0D, originalY / 8000.0D, originalZ / 8000.0D,
                    appliedX / 8000.0D, appliedY / 8000.0D, appliedZ / 8000.0D,
                    originalX, originalY, originalZ, appliedX, appliedY, appliedZ);
        }
    }

    /** Posições observadas após o S12; movimento do jogador também entra na medida. */
    private static final class DisplacementTrace {
        final VelocitySample impulse;
        final double startX, startY, startZ;
        final int correctionsAtStart;
        double lastX, lastY, lastZ;
        double pathXZ;
        int ticks;
        int inputTicks;

        DisplacementTrace(VelocitySample impulse, EntityPlayerSP player, int correctionsAtStart) {
            this.impulse = impulse;
            this.startX = this.lastX = player.posX;
            this.startY = this.lastY = player.posY;
            this.startZ = this.lastZ = player.posZ;
            this.correctionsAtStart = correctionsAtStart;
        }

        void observe(EntityPlayerSP player) {
            pathXZ += Math.hypot(player.posX - lastX, player.posZ - lastZ);
            lastX = player.posX;
            lastY = player.posY;
            lastZ = player.posZ;
            if (player.movementInput != null && (player.movementInput.moveForward != 0.0F
                    || player.movementInput.moveStrafe != 0.0F)) {
                inputTicks++;
            }
            ticks++;
        }

        String describe(String end, int correctionsNow, boolean jumpTriggered) {
            // O delta líquido não é uma medida isolada do KB quando há movimento,
            // outro impulso ou correção S08. Não o rotular como confirmação do servidor.
            return String.format(Locale.ROOT,
                    "deslocamento cliente pós-S12; fim=%s; ticks=%d/%d; deltaXZ=%.4f; caminhoXZ=%.4f; "
                    + "deltaY=%.4f; inputTicks=%d; S08=%d; percentualXZ=%d; impulsoXZ=%.4f; "
                    + "jumpDisparou=%s; comparavel=%s",
                    end, ticks, DISPLACEMENT_TICKS,
                    Math.hypot(lastX - startX, lastZ - startZ), pathXZ, lastY - startY,
                    inputTicks, correctionsNow - correctionsAtStart, impulse.percent,
                    Math.hypot(impulse.appliedX, impulse.appliedZ) / 8000.0D, jumpTriggered,
                    "janela-completa".equals(end) && inputTicks == 0
                            && correctionsNow == correctionsAtStart && !jumpTriggered);
        }
    }

    private static final class VelocityInterceptor extends ChannelInboundHandlerAdapter {

        private final KnockbackControl control;

        VelocityInterceptor(KnockbackControl control) {
            this.control = control;
        }

        @Override
        public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
            VelocitySample sample = null;
            if (message instanceof S12PacketEntityVelocity) {
                sample = control.adjust((S12PacketEntityVelocity) message);
            } else if (message instanceof S08PacketPlayerPosLook && control.diagnostics.getAsBoolean()) {
                control.serverCorrections.incrementAndGet();
            }
            try {
                context.fireChannelRead(message);
            } finally {
                // O handler vanilla agenda o S12 na thread principal e encerra a
                // leitura com ThreadQuickExitException. Esta tarefa deve entrar
                // depois da tarefa vanilla mesmo quando essa exceção é lançada.
                if (sample != null && (sample.originalX != 0 || sample.originalY != 0 || sample.originalZ != 0)
                        && ((sample.reduce && sample.percent != 100)
                        || control.jumpResetEnabled.getAsBoolean() || control.diagnostics.getAsBoolean())) {
                    final VelocitySample localSample = sample;
                    final Channel sourceChannel = context.channel();
                    Minecraft.getMinecraft().addScheduledTask(
                            () -> control.afterVelocityHandled(localSample, sourceChannel));
                }
            }
        }
    }
}
