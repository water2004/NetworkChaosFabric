package org.edtp.networkchaos.internal;

import org.edtp.networkchaos.api.ChaosConfig;
import org.edtp.networkchaos.api.ChaosStats;
import org.edtp.networkchaos.api.ExactDropRule;
import org.edtp.networkchaos.api.LinkProfile;
import org.edtp.networkchaos.api.NetworkChaos;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

public final class NetworkChaosGameTests {
    private static final String BLOCK_UPDATE_ONLY =
            ".*ClientboundBlockUpdatePacket";

    @GameTest(maxTicks = 30)
    public void localPlayConnectionSupportsDropDelayAndCancellation(
            GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        NetworkChaosRuntime.enableTestMode();
        ChaosConfig dropConfig = new ChaosConfig(
                LinkProfile.CLEAR,
                new LinkProfile(1.0, 0, 0, 0, 0, 0),
                17L,
                true,
                BLOCK_UPDATE_ONLY,
                ChaosConfig.NO_PACKETS);
        NetworkChaos.enable(dropConfig);
        player.connection.send(packet());
        ChaosStats dropped = NetworkChaos.stats();
        helper.assertValueEqual(dropped.serverToClient().seen(), 1L,
                "The selected local PLAY packet must reach the injector; stats="
                        + dropped);
        helper.assertValueEqual(dropped.serverToClient().dropped(), 1L,
                "A 100% loss profile must cancel the selected packet");
        helper.assertValueEqual(dropped.clientToServer().seen(), 0L,
                "Server outbound traffic must be classified as S2C");

        NetworkChaos.reset();
        NetworkChaosRuntime.enableTestMode();
        ChaosConfig exactDropConfig = ChaosConfig.clear().withExactDropRules(
                ExactDropRule.forPacketClass(
                        org.edtp.networkchaos.api.TrafficDirection.SERVER_TO_CLIENT,
                        ClientboundBlockUpdatePacket.class,
                        2));
        NetworkChaos.enable(exactDropConfig);
        player.connection.send(packet());
        player.connection.send(packet());
        player.connection.send(packet());
        ChaosStats exact = NetworkChaos.stats();
        helper.assertValueEqual(exact.serverToClient().dropped(), 2L,
                "The exact rule must drop only its configured packet count");
        helper.assertValueEqual(exact.serverToClient().passedImmediately(), 1L,
                "The packet after the exact-drop budget must pass");
        helper.assertValueEqual(exact.exactDropRules().getFirst().matched(), 3L,
                "The exact rule must count all matching packets");
        helper.assertValueEqual(exact.exactDropRules().getFirst().remainingDrops(), 0L,
                "The exact rule budget must be exhausted");

        NetworkChaos.reset();
        NetworkChaosRuntime.enableTestMode();
        ChaosConfig delayConfig = new ChaosConfig(
                LinkProfile.CLEAR,
                new LinkProfile(0, 75, 0, 0, 0, 0),
                23L,
                true,
                BLOCK_UPDATE_ONLY,
                ChaosConfig.NO_PACKETS);
        NetworkChaos.enable(delayConfig);
        player.connection.send(packet());
        ChaosStats initial = NetworkChaos.stats();
        helper.assertValueEqual(initial.serverToClient().delayed(), 1L,
                "The packet must be queued instead of sent immediately");
        helper.assertValueEqual(initial.serverToClient().deliveredFromQueue(), 0L,
                "The queued packet must not be counted as delivered immediately");

        await(helper,
                () -> NetworkChaos.stats().serverToClient()
                        .deliveredFromQueue() == 1,
                "The delayed packet was not forwarded");
        ChaosStats delivered = NetworkChaos.stats();
        helper.assertValueEqual(
                delivered.serverToClient().deliveredFromQueue(), 1L,
                "The delayed packet must be forwarded once after its deadline");
        helper.assertValueEqual(delivered.serverToClient().dropped(), 0L,
                "Pure latency must not become packet loss");

        NetworkChaos.reset();
        NetworkChaosRuntime.enableTestMode();
        ChaosConfig cancelConfig = new ChaosConfig(
                LinkProfile.CLEAR,
                new LinkProfile(0, 75, 0, 0, 0, 0),
                29L,
                true,
                BLOCK_UPDATE_ONLY,
                ChaosConfig.NO_PACKETS);
        NetworkChaos.enable(cancelConfig);
        player.connection.send(packet());
        NetworkChaos.disable();
        helper.assertValueEqual(
                NetworkChaos.stats().serverToClient().delayed(), 1L,
                "The packet must enter the delay queue before shutdown");
        await(helper,
                () -> NetworkChaos.stats().serverToClient()
                        .cancelledFromQueue() == 1,
                "The disabled queue entry was not cancelled");
        try {
            helper.assertValueEqual(
                    NetworkChaos.stats().serverToClient().cancelledFromQueue(),
                    1L,
                    "Disabling must invalidate queued packets");
            helper.succeed();
        } finally {
            NetworkChaos.reset();
        }
    }

    private static ClientboundBlockUpdatePacket packet() {
        return new ClientboundBlockUpdatePacket(
                BlockPos.ZERO, Blocks.STONE.defaultBlockState());
    }

    private static void await(
            GameTestHelper helper,
            BooleanSupplier condition,
            String message) {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            LockSupport.parkNanos(1_000_000L);
        }
        helper.assertTrue(condition.getAsBoolean(),
                message + "; stats=" + NetworkChaos.stats());
    }
}
