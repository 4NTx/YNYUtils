package com.yny.utils.modules.pvp;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

import com.yny.utils.ui.AttackDiagnosticsHud;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.util.MovingObjectPosition;

/** Observador passivo: nunca cria, cancela ou modifica packets. */
public final class AttackDiagnostics {
    private static final String HANDLER = "ynyutils_attack_diagnostics";
    private final BooleanSupplier enabled;
    private final DoubleSupplier limit;
    private final AttackDiagnosticsHud hud;
    private final AtomicReference<Attempt> pending = new AtomicReference<>();
    private final ArrayBlockingQueue<Attempt> written = new ArrayBlockingQueue<>(32);
    private Channel installedChannel;
    private volatile long session;
    private int lastEntityId = Integer.MIN_VALUE;
    private long lastWrittenAt;

    public AttackDiagnostics(BooleanSupplier enabled, DoubleSupplier limit, AttackDiagnosticsHud hud) {
        this.enabled = enabled;
        this.limit = limit;
        this.hud = hud;
    }

    public void reset() {
        session++;
        pending.set(null);
        written.clear();
        lastEntityId = Integer.MIN_VALUE;
        hud.clear();
    }

    /** Metadata capturada na thread do jogo, sem um segundo raycast. */
    public void onAttack(Entity entity) {
        if (!enabled.getAsBoolean()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        MovingObjectPosition hit = mc.objectMouseOver;
        double contact = hit != null && hit.entityHit == entity && hit.hitVec != null && mc.thePlayer != null
                ? mc.thePlayer.getPositionEyes(1).distanceTo(hit.hitVec) : Double.NaN;
        pending.set(new Attempt(session, entity.getEntityId(), entity.getName(), contact,
                limit.getAsDouble(), System.nanoTime()));
    }

    public void onTickEnd() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!enabled.getAsBoolean() || mc.theWorld == null || mc.getNetHandler() == null) {
            pending.set(null);
            written.clear();
            lastEntityId = Integer.MIN_VALUE;
            hud.clear();
            removeObserver();
            return;
        }
        Channel channel = mc.getNetHandler().getNetworkManager().channel;
        if (channel != installedChannel) {
            removeObserver();
            reset();
        }
        if (channel != null && channel.isOpen() && installedChannel == null) {
            if (channel.pipeline().get(HANDLER) == null && channel.pipeline().get("packet_handler") != null) {
                channel.pipeline().addBefore("packet_handler", HANDLER, new Observer());
            }
            if (channel.pipeline().get(HANDLER) != null) {
                installedChannel = channel;
            }
        }
        drainWritten();
    }

    private void drainWritten() {
        Attempt attempt;
        while ((attempt = written.poll()) != null) {
            if (attempt.session == session && System.nanoTime() - attempt.createdAt < 1_000_000_000L) {
                hud.show(attempt.name, attempt.contact, attempt.limit);
                lastEntityId = attempt.entityId;
                lastWrittenAt = System.nanoTime();
            }
        }
    }

    public void observeDamage(Entity entity) {
        if (!enabled.getAsBoolean()) {
            return;
        }
        drainWritten();
        if (enabled.getAsBoolean() && entity.getEntityId() == lastEntityId
                && System.nanoTime() - lastWrittenAt < 1_000_000_000L) {
            hud.observeDamage();
        }
    }

    private void removeObserver() {
        if (installedChannel != null) {
            if (installedChannel.pipeline().get(HANDLER) != null) {
                installedChannel.pipeline().remove(HANDLER);
            }
            installedChannel = null;
        }
    }

    private final class Observer extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) throws Exception {
            if (enabled.getAsBoolean() && message instanceof C02PacketUseEntity) {
                C02PacketUseEntity packet = (C02PacketUseEntity) message;
                Attempt attempt = pending.get();
                if (packet.getAction() == C02PacketUseEntity.Action.ATTACK && attempt != null
                        && attempt.session == session && packet.entityId == attempt.entityId
                        && System.nanoTime() - attempt.createdAt < 500_000_000L
                        && pending.compareAndSet(attempt, null)) {
                    // Sucesso local da escrita, não confirmação/aceitação do servidor.
                    // Netty 4.0 (1.8.9) não possui ChannelPromise.unvoid().
                    ChannelPromise observed = promise == ctx.voidPromise() ? ctx.newPromise() : promise;
                    observed.addListener(future -> {
                        if (future.isSuccess() && enabled.getAsBoolean() && attempt.session == session) {
                            written.offer(attempt); // fila limitada: não espera uma vaga
                        } else if (!future.isSuccess() && observed != promise) {
                            promise.tryFailure(future.cause());
                        }
                    });
                    ctx.write(message, observed);
                    return;
                }
            }
            ctx.write(message, promise);
        }
    }

    private static final class Attempt {
        final long session;
        final int entityId;
        final String name;
        final double contact, limit;
        final long createdAt;

        Attempt(long session, int entityId, String name, double contact, double limit, long createdAt) {
            this.session = session;
            this.entityId = entityId;
            this.name = name;
            this.contact = contact;
            this.limit = limit;
            this.createdAt = createdAt;
        }
    }
}
