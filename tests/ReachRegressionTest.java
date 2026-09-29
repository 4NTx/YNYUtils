package com.yny.utils.modules.pvp;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Predicate;
import com.yny.utils.core.ReachGeometry;
import com.yny.utils.core.ReachRange;
import com.yny.utils.ui.AttackDiagnosticsHud;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import sun.misc.Unsafe;

/** Harness headless: Unsafe só cria dublês de teste, nunca é empacotado no mod. */
public final class ReachRegressionTest {
    private static final Unsafe UNSAFE;
    private static int checks;
    static {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            UNSAFE = (Unsafe) field.get(null);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static void main(String[] args) throws Exception {
        Minecraft mc = allocate(Minecraft.class);
        Minecraft.theMinecraft = mc;
        TestWorld world = allocate(TestWorld.class);
        world.entities = new ArrayList<>();
        mc.theWorld = world;
        TestPlayer camera = player(0);
        mc.thePlayer = camera;
        mc.renderViewEntity = camera;
        TestController controller = new TestController(mc);
        mc.playerController = controller;
        MovingObjectPosition vanilla = miss();
        mc.objectMouseOver = vanilla;
        boolean[] enabled = {false};
        double[] distance = {3.9};
        CustomReach reach = new CustomReach(() -> enabled[0], () -> distance[0]);
        TestPlayer target = player(3.8); // contato ~3.4, centro 3.8
        world.entities.add(target);

        reach.onInput();
        check(mc.objectMouseOver == vanilla && world.queries == 0, "OFF não altera nem consulta");
        enabled[0] = true;
        distance[0] = 3.0;
        reach.onFrame(0.4F);
        check(mc.objectMouseOver == vanilla && world.queries == 0, "ON/3.0 exatamente vanilla");
        distance[0] = 3.9;
        reach.onInput();
        check(mc.objectMouseOver.entityHit == target, "alvo estendido selecionado no input");
        check(camera.lastPartialTicks == 1, "input usa partialTicks=1");
        check(Math.abs(mc.objectMouseOver.hitVec.zCoord - 3.4) < 0.00001, "distância até contato");
        int queries = world.queries;
        MovingObjectPosition selected = mc.objectMouseOver;
        reach.onInput();
        check(world.queries == queries && mc.objectMouseOver == selected, "eventos repetidos reutilizam seleção");
        enabled[0] = false;
        reach.configurationChanged();
        check(mc.objectMouseOver == vanilla && mc.pointedEntity == null, "OFF restaura imediatamente");
        enabled[0] = true;
        reach.onInput();
        distance[0] = 3.0;
        reach.configurationChanged();
        check(mc.objectMouseOver == vanilla, "3.0 restaura imediatamente");
        distance[0] = 3.9;
        reach.onInput();
        MovingObjectPosition replacement = miss();
        mc.objectMouseOver = replacement;
        enabled[0] = false;
        reach.configurationChanged();
        check(mc.objectMouseOver == replacement, "não sobrescreve resultado externo");

        enabled[0] = true;
        controller.creative = true;
        queries = world.queries;
        reach.onInput();
        check(mc.objectMouseOver == replacement && world.queries == queries, "creative mantém alcance vanilla 6");
        controller.creative = false;
        controller.spectator = true;
        reach.onInput();
        check(world.queries == queries, "spectator não é alterado");
        controller.spectator = false;
        target.spectator = true;
        reach.onFrame(0.6F);
        check(mc.objectMouseOver == replacement, "alvo spectator ignorado");
        target.spectator = false;
        target.collidable = false;
        reach.onFrame(0.6F);
        check(mc.objectMouseOver == replacement, "alvo não colidível ignorado");
        target.collidable = true;
        camera.ridingEntity = target;
        reach.onFrame(0.6F);
        check(mc.objectMouseOver == replacement, "montaria fora dos olhos ignorada");
        camera.ridingEntity = null;
        camera.block = block(3.2);
        reach.onFrame(0.6F);
        check(mc.objectMouseOver == replacement, "MISS não permite alvo atrás de bloco");
        mc.objectMouseOver = camera.block;
        int traces = camera.blockTraces;
        reach.onFrame(0.6F);
        check(mc.objectMouseOver == camera.block && camera.blockTraces == traces, "BLOCK vanilla reaproveitado");
        camera.block = null;
        mc.objectMouseOver = miss();
        world.entities.clear();
        TestPlayer farther = player(4.0);
        world.entities.add(farther);
        world.entities.add(target);
        reach.onFrame(0.25F);
        check(mc.objectMouseOver.entityHit == target && camera.lastPartialTicks == 0.25F, "mais próximo e partialTicks do quadro");
        world.entities.clear();
        reach.onTickEnd();
        mc.objectMouseOver = replacement; // recalculado pelo vanilla no início do tick
        reach.onInput();
        check(mc.objectMouseOver == replacement, "não reutiliza alvo de tick anterior");
        world.entities.add(player(4.4));
        reach.onFrame(1);
        check(mc.objectMouseOver == replacement, "limite 3.9 exclui contato além dele");
        MovingObjectPosition nativeEntity = new MovingObjectPosition(target, new Vec3(0, 0, 2));
        mc.objectMouseOver = nativeEntity;
        queries = world.queries;
        reach.onFrame(1);
        check(mc.objectMouseOver == nativeEntity && world.queries == queries, "seleção ENTITY vanilla reaproveitada");
        world.entities.clear();
        world.entities.add(target);
        mc.objectMouseOver = miss();
        reach.onFrame(1);
        check(mc.objectMouseOver.entityHit == target, "seleção antes de mudar mundo");
        TestWorld newWorld = allocate(TestWorld.class);
        newWorld.entities = new ArrayList<>();
        mc.theWorld = newWorld;
        reach.configurationChanged();
        check(mc.objectMouseOver == null && mc.pointedEntity == null, "não restaura seleção de outro mundo");
        mc.theWorld = world;

        Vec3 eyes = new Vec3(0, 0, 0);
        Vec3 end = new Vec3(0, 0, 3.9);
        check(ReachGeometry.contact(eyes, end, new AxisAlignedBB(-1, -1, -1, 1, 1, 1)) == eyes, "olhos dentro da caixa");
        check(ReachGeometry.contact(eyes, end, new AxisAlignedBB(2, -1, 2, 3, 1, 3)) == null, "fora da linha da mira");
        check(!ReachGeometry.beforeBlock(9, 9), "empate favorece bloco");
        check(ReachRange.clamp(Double.NaN) == 3, "NaN volta ao vanilla");
        check(ReachRange.clamp(2.9) == 3 && ReachRange.clamp(4) == 3.9, "limites de configuração");
        diagnostics(mc, target);
        System.out.println("PASS: " + checks + " verificações de regressão Reach");
    }

    private static void diagnostics(Minecraft mc, Entity target) throws Exception {
        mc.fontRendererObj = allocate(TestFont.class);
        NetworkManager manager = new NetworkManager(EnumPacketDirection.CLIENTBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        channel.pipeline().addLast("packet_handler", new ChannelInboundHandlerAdapter());
        manager.channel = channel;
        NetHandlerPlayClient handler = allocate(NetHandlerPlayClient.class);
        handler.netManager = manager;
        mc.thePlayer.sendQueue = handler;
        target.setEntityId(42);
        mc.objectMouseOver = new MovingObjectPosition(target, new Vec3(0, 0, 3.4));
        boolean[] enabled = {false};
        AttackDiagnosticsHud hud = new AttackDiagnosticsHud(() -> enabled[0]);
        AttackDiagnostics diagnostic = new AttackDiagnostics(() -> enabled[0], () -> 3.9, hud);
        diagnostic.onTickEnd();
        check(channel.pipeline().get("ynyutils_attack_diagnostics") == null, "diagnóstico OFF sem observer");
        enabled[0] = true;
        diagnostic.onTickEnd();
        check(channel.pipeline().get("ynyutils_attack_diagnostics") != null, "observer instalado");
        diagnostic.onAttack(target);
        diagnostic.onTickEnd();
        check(!hud.layout(false, 1), "callback sem packet não conta como ataque enviado");
        C02PacketUseEntity interact = new C02PacketUseEntity(target, C02PacketUseEntity.Action.INTERACT);
        channel.writeOutbound(interact);
        check(channel.readOutbound() == interact, "INTERACT passa sem modificação");
        diagnostic.onTickEnd();
        check(!hud.layout(false, 1), "INTERACT não conta como ATTACK");
        C02PacketUseEntity attack = new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK);
        channel.writeOutbound(attack);
        check(channel.readOutbound() == attack && attack.entityId == 42
                && attack.getAction() == C02PacketUseEntity.Action.ATTACK, "ATTACK passa com identidade/conteúdo intactos");
        diagnostic.onTickEnd();
        check(hud.layout(false, 1), "escrita com sucesso mostra diagnóstico");
        diagnostic.observeDamage(target);
        Field damage = AttackDiagnosticsHud.class.getDeclaredField("damage");
        damage.setAccessible(true);
        check(((String) damage.get(hud)).contains("autoria não confirmada"), "dano não confirma autoria");
        hud.clear();
        diagnostic.onAttack(target);
        channel.writeAndFlush(attack, channel.voidPromise());
        channel.runPendingTasks();
        check(channel.readOutbound() == attack, "voidPromise não modifica packet");
        diagnostic.onTickEnd();
        check(hud.layout(false, 1), "voidPromise Netty 4.0 observada corretamente");
        hud.clear();
        diagnostic.onAttack(target);
        channel.pipeline().addFirst("test_failure", new ChannelOutboundHandlerAdapter() {
            @Override public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) {
                promise.setFailure(new IllegalStateException("test: write failed"));
            }
        });
        ChannelPromise failure = channel.newPromise();
        channel.write(attack, failure);
        channel.runPendingTasks();
        diagnostic.onTickEnd();
        check(failure.isDone() && !failure.isSuccess(), "falha de escrita é preservada");
        check(!hud.layout(false, 1), "falha de escrita não vira ataque enviado");
        channel.pipeline().remove("test_failure");
        diagnostic.reset();
        channel.writeOutbound(attack);
        channel.readOutbound();
        diagnostic.onTickEnd();
        check(!hud.layout(false, 1), "reset descarta tentativa anterior");
        enabled[0] = false;
        diagnostic.onTickEnd();
        check(channel.pipeline().get("ynyutils_attack_diagnostics") == null
                && channel.pipeline().get("packet_handler") != null, "OFF remove apenas seu observer");
        channel.finish();
    }

    private static void check(boolean condition, String name) {
        if (!condition) {
            throw new AssertionError(name);
        }
        checks++;
    }

    private static <T> T allocate(Class<T> type) throws InstantiationException {
        return type.cast(UNSAFE.allocateInstance(type));
    }

    private static TestPlayer player(double z) throws Exception {
        TestPlayer player = allocate(TestPlayer.class);
        player.collidable = true;
        player.box = new AxisAlignedBB(-0.3, -1, z - 0.3, 0.3, 1, z + 0.3);
        return player;
    }

    private static MovingObjectPosition miss() {
        return new MovingObjectPosition(MovingObjectPosition.MovingObjectType.MISS,
                new Vec3(0, 0, 4.5), EnumFacing.NORTH, BlockPos.ORIGIN);
    }

    private static MovingObjectPosition block(double z) {
        return new MovingObjectPosition(new Vec3(0, 0, z), EnumFacing.NORTH, BlockPos.ORIGIN);
    }

    public static final class TestController extends PlayerControllerMP {
        boolean creative, spectator;
        TestController(Minecraft mc) { super(mc, null); }
        @Override public boolean extendedReach() { return creative; }
        @Override public boolean isSpectator() { return spectator; }
    }

    public static final class TestFont extends FontRenderer {
        TestFont() { super(null, null, null, false); }
        @Override public String trimStringToWidth(String text, int width) { return text; }
    }

    public static final class TestPlayer extends EntityPlayerSP {
        AxisAlignedBB box;
        boolean collidable, spectator;
        float lastPartialTicks;
        int blockTraces;
        MovingObjectPosition block;
        TestPlayer() { super(null, null, null, null); }
        @Override public Vec3 getPositionEyes(float ticks) { lastPartialTicks = ticks; return new Vec3(0, 0, 0); }
        @Override public Vec3 getLook(float ticks) { return new Vec3(0, 0, 1); }
        @Override public AxisAlignedBB getEntityBoundingBox() { return box; }
        @Override public boolean canBeCollidedWith() { return collidable; }
        @Override public boolean isSpectator() { return spectator; }
        @Override public String getName() { return "TestPlayer"; }
        @Override public MovingObjectPosition rayTrace(double range, float ticks) { blockTraces++; return block; }
    }

    public static final class TestWorld extends WorldClient {
        List<Entity> entities;
        int queries;
        TestWorld() { super(null, null, 0, null, null); }
        @Override public List<Entity> getEntitiesInAABBexcluding(Entity camera, AxisAlignedBB box,
                Predicate<? super Entity> predicate) {
            queries++;
            List<Entity> result = new ArrayList<>();
            for (Entity entity : entities) {
                if (entity != camera && box.intersectsWith(entity.getEntityBoundingBox()) && predicate.apply(entity)) {
                    result.add(entity);
                }
            }
            return result;
        }
    }
}
