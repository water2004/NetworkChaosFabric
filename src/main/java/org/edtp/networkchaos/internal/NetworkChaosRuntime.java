package org.edtp.networkchaos.internal;

import org.edtp.networkchaos.api.ChaosConfig;
import org.edtp.networkchaos.api.ChaosStats;
import org.edtp.networkchaos.api.TrafficDirection;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class NetworkChaosRuntime {
    private static final Logger LOGGER =
            LoggerFactory.getLogger("NetworkChaosFabric");
    private static final ThreadLocal<Boolean> BYPASS =
            ThreadLocal.withInitial(() -> false);
    private static final ChaosEngine ENGINE =
            new ChaosEngine(ChaosConfig.clear());
    private static final AtomicBoolean ENABLED = new AtomicBoolean();
    private static final AtomicBoolean TEST_MODE =
            new AtomicBoolean();
    private static final AtomicLong EPOCH = new AtomicLong();
    private static final DirectionCounters C2S = new DirectionCounters();
    private static final DirectionCounters S2C = new DirectionCounters();
    private static final LongAdder IGNORED_NON_LOCAL = new LongAdder();
    private static final LongAdder IGNORED_NON_PLAY = new LongAdder();
    private static final LongAdder IGNORED_BY_FILTER = new LongAdder();
    private static final LongAdder PROTECTED_CONTROL = new LongAdder();
    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory());

    private NetworkChaosRuntime() {
    }

    public static boolean intercept(
            Connection connection,
            Packet<?> packet,
            ChannelFutureListener listener,
            Channel channel,
            boolean flush) {
        if (BYPASS.get() || !ENABLED.get()) return false;
        if (!isLocalConnection(connection, channel) && !TEST_MODE.get()) {
            IGNORED_NON_LOCAL.increment();
            return false;
        }
        PacketListener packetListener = connection.getPacketListener();
        if (!(packetListener instanceof ClientGamePacketListener)
                && !(packetListener instanceof ServerGamePacketListener)) {
            IGNORED_NON_PLAY.increment();
            return false;
        }

        String packetName = packet.getClass().getName();
        TrafficDirection direction = direction(connection.getSending());
        PacketDecision decision;
        synchronized (ENGINE) {
            ChaosConfig config = ENGINE.config();
            if (!ENGINE.includes(packetName)) {
                IGNORED_BY_FILTER.increment();
                return false;
            }
            if (config.protectControlPackets() && isControlPacket(packet)) {
                PROTECTED_CONTROL.increment();
                return false;
            }
            decision = ENGINE.decide(direction, packetName);
        }
        DirectionCounters counters = counters(direction);
        counters.seen.increment();
        if (decision.drop()) {
            counters.dropped.increment();
            completeDroppedSend(channel, listener);
            return true;
        }

        long epoch = EPOCH.get();
        if (decision.delayMillis() > 0) {
            counters.delayed.increment();
            schedule(connection, packet, listener, flush,
                    decision.delayMillis(), epoch, counters);
            if (decision.duplicate()) {
                counters.duplicated.increment();
                schedule(connection, packet, null, flush,
                        decision.duplicateDelayMillis(), epoch, counters);
            }
            return true;
        }

        counters.passedImmediately.increment();
        if (decision.duplicate()) {
            counters.duplicated.increment();
            schedule(connection, packet, null, flush,
                    decision.duplicateDelayMillis(), epoch, counters);
        }
        return false;
    }

    public static void configure(ChaosConfig config) {
        ENGINE.configure(config);
        EPOCH.incrementAndGet();
    }

    public static ChaosConfig config() {
        return ENGINE.config();
    }

    public static void enable() {
        ENABLED.set(true);
    }

    public static void disable() {
        ENABLED.set(false);
        EPOCH.incrementAndGet();
    }

    public static boolean enabled() {
        return ENABLED.get();
    }

    public static ChaosStats stats() {
        return new ChaosStats(
                C2S.snapshot(),
                S2C.snapshot(),
                IGNORED_NON_LOCAL.sum(),
                IGNORED_NON_PLAY.sum(),
                IGNORED_BY_FILTER.sum(),
                PROTECTED_CONTROL.sum(),
                ENGINE.exactDropStats());
    }

    public static void resetStats() {
        C2S.reset();
        S2C.reset();
        IGNORED_NON_LOCAL.reset();
        IGNORED_NON_PLAY.reset();
        IGNORED_BY_FILTER.reset();
        PROTECTED_CONTROL.reset();
        ENGINE.resetStats();
    }

    public static void reset() {
        disable();
        configure(ChaosConfig.clear());
        resetStats();
        TEST_MODE.set(false);
    }

    static void enableTestMode() {
        TEST_MODE.set(true);
    }

    private static void schedule(
            Connection connection,
            Packet<?> packet,
            ChannelFutureListener listener,
            boolean flush,
            long delayMillis,
            long expectedEpoch,
            DirectionCounters counters) {
        SCHEDULER.schedule(() -> {
            if (expectedEpoch != EPOCH.get()
                    || (!connection.isConnected() && !TEST_MODE.get())) {
                counters.cancelledFromQueue.increment();
                return;
            }
            BYPASS.set(true);
            try {
                connection.send(packet, listener, flush);
                counters.deliveredFromQueue.increment();
            } catch (RuntimeException exception) {
                counters.failedFromQueue.increment();
                LOGGER.error("Failed to forward delayed packet {}",
                        packet.getClass().getName(), exception);
            } finally {
                BYPASS.remove();
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    private static TrafficDirection direction(PacketFlow sending) {
        return sending == PacketFlow.SERVERBOUND
                ? TrafficDirection.CLIENT_TO_SERVER
                : TrafficDirection.SERVER_TO_CLIENT;
    }

    private static DirectionCounters counters(TrafficDirection direction) {
        return direction == TrafficDirection.CLIENT_TO_SERVER ? C2S : S2C;
    }

    private static boolean isControlPacket(Packet<?> packet) {
        String name = packet.getClass().getSimpleName();
        return name.contains("KeepAlive")
                || name.contains("Disconnect")
                || name.contains("Configuration")
                || name.contains("Login")
                || name.contains("Cookie")
                || name.contains("Ping")
                || name.contains("Pong")
                || name.contains("ResourcePack");
    }

    private static boolean isLocalConnection(
            Connection connection,
            Channel channel) {
        if (connection.isMemoryConnection()) return true;
        if (channel == null) return false;
        SocketAddress remote = channel.remoteAddress();
        if (!(remote instanceof InetSocketAddress inet)) return false;
        InetAddress address = inet.getAddress();
        return address != null && address.isLoopbackAddress();
    }

    private static void completeDroppedSend(
            Channel channel,
            ChannelFutureListener listener) {
        if (channel == null || listener == null) return;
        Runnable callback = () -> {
            try {
                listener.operationComplete(channel.newSucceededFuture());
            } catch (Exception exception) {
                LOGGER.error("Dropped-packet completion listener failed", exception);
            }
        };
        if (channel.eventLoop().inEventLoop()) {
            callback.run();
        } else {
            channel.eventLoop().execute(callback);
        }
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "network-chaos-scheduler");
            thread.setDaemon(true);
            return thread;
        }
    }
}
