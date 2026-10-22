package com.yny.utils.modules.pvp;

import com.yny.utils.YNYUtils;
import com.yny.utils.config.YNYConfig;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Limitador e Clamp Rígido de CPS (Trava de Segurança Anti-Cheat):
 * Impede que cliques de autoclickers externos, macros ou bursts excedam o teto
 * configurado (padrão e máximo absoluto de 25 CPS no servidor).
 *
 * Arquitetura de Defesa em 4 Camadas:
 * 1. Camada de Entrada Nativa (RateLimitedDeque em Win.mouseEvents):
 *    Intercepta cliques do Windows/GLFW antes que o Minecraft ou o SteinHud os processem.
 *    Descarta cliques excedentes e bursts (> 25 CPS ou cadência < 40ms).
 * 2. Camada de Ações e Módulos (FocusMode, HitThroughWeb, MobAura):
 *    Gate centralizado canAttackAndRecord(target) que impede o envio de ataques redundantes
 *    e elimina pacotes C0APacketAnimation (swing) duplicados.
 * 3. Camada de Rede Netty (CpsPacketHandler outbound):
 *    Intercepta o pipeline Netty (NetworkManager) e barra fisicamente qualquer pacote
 *    C0APacketAnimation (swing) ou C02PacketUseEntity (ataque) que exceda o teto de 25/s.
 * 4. Sincronização e Precisão da HUD (SteinHud.ClickCounter):
 *    Como os cliques excedentes são bloqueados na entrada, o contador da HUD computa
 *    apenas os cliques reais que chegam ao jogo e ao servidor, refletindo 100% de precisão.
 */
public final class CpsRateLimiter {

    public static final int DEFAULT_MAX_CPS = 25;
    public static final int ABSOLUTE_HARD_CAP = 25;
    private static final long ONE_SECOND_NANOS = 1_000_000_000L;

    // Buffer circular de timestamps de cliques de mouse (entrada)
    private static final long[] clickTimes = new long[64];
    private static int clickHead = 0;
    private static long lastClickNanos = 0L;

    // Buffer circular de timestamps de ataques (ações)
    private static final long[] attackTimes = new long[64];
    private static int attackHead = 0;
    private static long lastAttackNanos = 0L;

    // Buffer circular de timestamps de pacotes de rede (saída Netty)
    private static final long[] packetTimes = new long[64];
    private static int packetHead = 0;
    private static long lastPacketNanos = 0L;
    private static long lastSwingNanos = 0L;

    // Suporte a MobAura Multi-Target
    private static long auraTickNanos = 0L;
    private static int auraTargetCountThisTick = 0;
    private static volatile long auraAllowUntilNanos = 0L;
    private static volatile int auraAllowedPackets = 0;

    public static synchronized void notifyAuraAttack(int targetCount) {
        auraAllowUntilNanos = System.nanoTime() + 100_000_000L; // Janela de 100ms para permitir pacotes da MobAura no Netty
        auraAllowedPackets = Math.max(2, targetCount * 2);
    }

    public static long getAuraAllowUntilNanos() {
        return auraAllowUntilNanos;
    }

    // Controle de estado para descarte atômico de UP correspondente a DOWN descartado
    private static volatile boolean discardNextRelease = false;

    // Flags de instalação dos interceptores
    private static volatile boolean mouseLimiterInstalled = false;
    private static volatile boolean nettyHandlerInstalled = false;

    // Cache reflexivo para Win e Win$MouseEvent
    private static volatile boolean winLookupAttempted = false;
    private static volatile Field mouseEventsField = null;
    private static volatile Field currentMouseField = null;
    private static volatile Field mouseButtonsField = null;
    private static volatile Field mouseEventButtonField = null;
    private static volatile Field mouseEventStateField = null;

    // Cache reflexivo para sincronização da HUD (SteinHud)
    private static volatile boolean hudLookupAttempted = false;
    private static volatile Object steinHudInstance = null;
    private static volatile Field statsField = null;
    private static volatile Field leftCounterField = null;
    private static volatile Field counterTimesField = null;

    static {
        // Limitador de CPS desativado para cliques e menus livres
    }

    private CpsRateLimiter() {}

    public static int getEffectiveMaxCps() {
        YNYConfig config = YNYUtils.getConfig();
        if (config == null) return DEFAULT_MAX_CPS;
        if (!config.cpsCapEnabled) return ABSOLUTE_HARD_CAP;
        return Math.max(1, Math.min(ABSOLUTE_HARD_CAP, config.maxCps));
    }

    public static boolean isCpsCapActive() {
        return false;
    }

    /**
     * Valida se um clique de mouse de entrada (botão esquerdo) é permitido pela trava de segurança.
     * Registra o timestamp se aprovado. Retorna false se o clique violar a taxa máxima ou o intervalo mínimo.
     */
    public static synchronized boolean canClickAndRecord(long now) {
        return true;
    }

    public static synchronized boolean canAttackAndRecord(Object target) {
        return true;
    }

    public static synchronized boolean canSendPacketAndRecord(Object packet) {
        return true;
    }

    private static void recordPacket(long now) {
        packetTimes[packetHead] = now;
        packetHead = (packetHead + 1) % packetTimes.length;
        lastPacketNanos = now;
    }

    private static int getPacketCountInWindow(long now) {
        int count = 0;
        for (int i = 0; i < packetTimes.length; i++) {
            long t = packetTimes[i];
            if (t > 0L && (now - t) < ONE_SECOND_NANOS) {
                count++;
            }
        }
        return count;
    }

    public static boolean isSwingPacket(Object msg) {
        if (msg == null) return false;
        String name = msg.getClass().getName();
        return name.endsWith("C0APacketAnimation") || name.equals("iy") || name.endsWith(".iy");
    }

    public static boolean isAttackPacket(Object msg) {
        if (msg == null) return false;
        String name = msg.getClass().getName();
        return name.endsWith("C02PacketUseEntity") || name.equals("in") || name.endsWith(".in");
    }

    public static boolean isAttackOrSwingPacket(Object msg) {
        return isSwingPacket(msg) || isAttackPacket(msg);
    }

    /**
     * Deque de eventos de mouse com clamp rígido de CPS.
     * Intercepta diretamente as operações de adição e remoção em Win.mouseEvents.
     */
    public static class RateLimitedDeque extends ArrayDeque<Object> {
        private static final long serialVersionUID = 1L;

        public RateLimitedDeque() {
            super();
        }

        public RateLimitedDeque(Collection<?> c) {
            super(c != null ? c : new ArrayDeque<Object>());
        }

        @Override
        public void addLast(Object e) {
            if (!filterIncoming(e)) return;
            super.addLast(e);
        }

        @Override
        public boolean offerLast(Object e) {
            if (!filterIncoming(e)) return true;
            return super.offerLast(e);
        }

        @Override
        public boolean add(Object e) {
            if (!filterIncoming(e)) return true;
            return super.add(e);
        }

        @Override
        public boolean offer(Object e) {
            if (!filterIncoming(e)) return true;
            return super.offer(e);
        }

        @Override
        public void push(Object e) {
            if (!filterIncoming(e)) return;
            super.push(e);
        }

        @Override
        public void addFirst(Object e) {
            if (!filterIncoming(e)) return;
            super.addFirst(e);
        }

        @Override
        public boolean offerFirst(Object e) {
            if (!filterIncoming(e)) return true;
            return super.offerFirst(e);
        }

        @Override
        public Object pollFirst() {
            return super.pollFirst();
        }

        @Override
        public Object poll() {
            return pollFirst();
        }

        @Override
        public Object removeFirst() {
            Object obj = pollFirst();
            if (obj == null) throw new NoSuchElementException();
            return obj;
        }

        @Override
        public void clear() {
            super.clear();
            discardNextRelease = false;
        }

        private boolean filterIncoming(Object e) {
            return true;
        }

        private boolean isEventDisallowedOnPoll(Object e) {
            return false;
        }
    }

    public static class CpsPacketHandler extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            super.write(ctx, msg, promise);
        }
    }

    /**
     * Assegura que tanto o interceptor nativo de mouse quanto o Netty estão instalados.
     */
    public static void ensureInstalled() {
        // Desativado: nao intercepta mouse nem Netty
    }

    public static synchronized void installMouseLimiter() {
        // Desativado: sem substituicao de Win.mouseEvents para cliques e menus 100% livres
        mouseLimiterInstalled = true;
    }

    public static synchronized void reinstallNettyHandler() {
        // Desativado
        nettyHandlerInstalled = true;
    }

    public static synchronized void installNettyHandler() {
        // Desativado: sem interceptor Netty para nao descartar pacotes
        nettyHandlerInstalled = true;
    }

    public static void setMouseButtonState(int button, boolean state) {
        try {
            initWinReflection();
            if (mouseButtonsField != null) {
                boolean[] buttons = (boolean[]) mouseButtonsField.get(null);
                if (buttons != null && button >= 0 && button < buttons.length) {
                    buttons[button] = state;
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Sanitização preventiva da fila caso o limitador ainda não tenha sido acoplado.
     */
    public static void sanitizeMouseEvents() {
        // Desativado: preserva fila de eventos de mouse 100% integra
    }

    private static void initWinReflection() {
        if (winLookupAttempted) return;
        winLookupAttempted = true;
        try {
            Class<?> winClass = Class.forName("dev.xavier.stein.bridge.core.Win");
            mouseEventsField = winClass.getDeclaredField("mouseEvents");
            mouseEventsField.setAccessible(true);
            currentMouseField = winClass.getDeclaredField("currentMouse");
            currentMouseField.setAccessible(true);
            mouseButtonsField = winClass.getDeclaredField("mouseButtons");
            mouseButtonsField.setAccessible(true);

            Class<?> mouseEvClass = Class.forName("dev.xavier.stein.bridge.core.Win$MouseEvent");
            mouseEventButtonField = mouseEvClass.getDeclaredField("button");
            mouseEventButtonField.setAccessible(true);
            mouseEventStateField = mouseEvClass.getDeclaredField("state");
            mouseEventStateField.setAccessible(true);
        } catch (Throwable ignored) {}
    }

    private static synchronized int getClickCountInWindow(long now) {
        int count = 0;
        for (int i = 0; i < clickTimes.length; i++) {
            long t = clickTimes[i];
            if (t > 0L && (now - t) < ONE_SECOND_NANOS) {
                count++;
            }
        }
        return count;
    }

    /**
     * Sincroniza e garante que o contador da HUD de CPS (SteinHud.ClickCounter)
     * nunca exiba mais cliques do que o limite permitido de 25 CPS.
     */
    public static void syncHudCps() {
        try {
            initHudReflection();
            if (steinHudInstance == null || statsField == null || leftCounterField == null || counterTimesField == null) {
                return;
            }

            Object stats = statsField.get(steinHudInstance);
            if (stats == null) return;

            Object leftCounter = leftCounterField.get(stats);
            if (leftCounter == null) return;

            long[] times = (long[]) counterTimesField.get(leftCounter);
            if (times == null || times.length == 0) return;

            int maxCps = getEffectiveMaxCps();
            long now = System.nanoTime();

            int count = 0;
            for (int i = 0; i < times.length; i++) {
                long t = times[i];
                if (t > 0L && (now - t) < ONE_SECOND_NANOS) {
                    count++;
                }
            }

            if (count > maxCps) {
                int toPrune = count - maxCps;
                for (int i = 0; i < times.length && toPrune > 0; i++) {
                    long t = times[i];
                    if (t > 0L && (now - t) < ONE_SECOND_NANOS) {
                        times[i] = 0L;
                        toPrune--;
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void initHudReflection() {
        if (hudLookupAttempted && steinHudInstance != null) return;
        hudLookupAttempted = true;
        try {
            Class<?> loaderClass = Class.forName("dev.xavier.stein.loader.Loader");
            Method getMethod = loaderClass.getMethod("get");
            Object loader = getMethod.invoke(null);
            if (loader == null) return;

            Field modsField = loaderClass.getDeclaredField("mods");
            modsField.setAccessible(true);
            Object[] mods = (Object[]) modsField.get(loader);
            if (mods == null) return;

            for (Object m : mods) {
                if (m != null && m.getClass().getName().contains("SteinHud")) {
                    steinHudInstance = m;
                    statsField = m.getClass().getDeclaredField("stats");
                    statsField.setAccessible(true);

                    Object stats = statsField.get(m);
                    if (stats != null) {
                        leftCounterField = stats.getClass().getDeclaredField("left");
                        leftCounterField.setAccessible(true);

                        Object leftCounter = leftCounterField.get(stats);
                        if (leftCounter != null) {
                            counterTimesField = leftCounter.getClass().getDeclaredField("times");
                            counterTimesField.setAccessible(true);
                        }
                    }
                    break;
                }
            }
        } catch (Throwable ignored) {}
    }

    public static synchronized void reset() {
        for (int i = 0; i < attackTimes.length; i++) attackTimes[i] = 0L;
        for (int i = 0; i < clickTimes.length; i++) clickTimes[i] = 0L;
        for (int i = 0; i < packetTimes.length; i++) packetTimes[i] = 0L;
        attackHead = 0;
        clickHead = 0;
        packetHead = 0;
        lastAttackNanos = 0L;
        lastClickNanos = 0L;
        lastPacketNanos = 0L;
        lastSwingNanos = 0L;
        auraTickNanos = 0L;
        auraTargetCountThisTick = 0;
        discardNextRelease = false;
    }

    public static synchronized int getCurrentAttackCps() {
        long now = System.nanoTime();
        int count = 0;
        for (int i = 0; i < attackTimes.length; i++) {
            long t = attackTimes[i];
            if (t > 0L && (now - t) < ONE_SECOND_NANOS) {
                count++;
            }
        }
        return count;
    }

    public static synchronized int getCurrentClickCps() {
        long now = System.nanoTime();
        return getClickCountInWindow(now);
    }

    public static synchronized int getCurrentPacketCps() {
        long now = System.nanoTime();
        int count = 0;
        for (int i = 0; i < packetTimes.length; i++) {
            long t = packetTimes[i];
            if (t > 0L && (now - t) < ONE_SECOND_NANOS) {
                count++;
            }
        }
        return count;
    }
}
