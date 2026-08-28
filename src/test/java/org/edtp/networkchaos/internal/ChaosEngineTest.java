package org.edtp.networkchaos.internal;

import org.edtp.networkchaos.api.ChaosConfig;
import org.edtp.networkchaos.api.ExactDropRule;
import org.edtp.networkchaos.api.LinkProfile;
import org.edtp.networkchaos.api.TrafficDirection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChaosEngineTest {
    @Test
    void oneHundredPercentLossAlwaysDrops() {
        ChaosEngine engine = new ChaosEngine(config(
                new LinkProfile(1, 0, 0, 0, 0, 0), 1));
        for (int index = 0; index < 100; index++) {
            assertTrue(engine.decide(TrafficDirection.CLIENT_TO_SERVER).drop());
        }
    }

    @Test
    void fixedLatencyDoesNotInventOtherFaults() {
        ChaosEngine engine = new ChaosEngine(config(
                new LinkProfile(0, 250, 0, 0, 0, 0), 2));
        PacketDecision decision = engine.decide(TrafficDirection.SERVER_TO_CLIENT);
        assertFalse(decision.drop());
        assertEquals(250, decision.delayMillis());
        assertFalse(decision.duplicate());
    }

    @Test
    void sameSeedAndTrafficOrderReproduceTheSameDecisions() {
        LinkProfile profile = new LinkProfile(0.2, 100, 50, 0.1, 0.3, 400);
        ChaosConfig config = config(profile, 99173);
        ChaosEngine engine = new ChaosEngine(config);
        List<PacketDecision> first = decisions(engine, 200);
        engine.configure(config);
        List<PacketDecision> second = decisions(engine, 200);
        assertEquals(first, second);
    }

    @Test
    void trafficInOneDirectionDoesNotPerturbTheOtherDirection() {
        LinkProfile profile = new LinkProfile(0.2, 100, 50, 0.1, 0.3, 400);
        ChaosConfig config = config(profile, 8181);
        ChaosEngine interleaved = new ChaosEngine(config);
        ChaosEngine serverOnly = new ChaosEngine(config);

        for (int index = 0; index < 100; index++) {
            interleaved.decide(TrafficDirection.CLIENT_TO_SERVER);
            assertEquals(
                    serverOnly.decide(TrafficDirection.SERVER_TO_CLIENT),
                    interleaved.decide(TrafficDirection.SERVER_TO_CLIENT));
        }
    }

    @Test
    void packetClassFiltersAreFullNameRegexes() {
        ChaosConfig config = config(LinkProfile.CLEAR, 3)
                .withPacketFilter(".*BlockUpdatePacket", ".*Ignored.*");
        ChaosEngine engine = new ChaosEngine(config);
        assertTrue(engine.includes(
                "net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket"));
        assertFalse(engine.includes(
                "net.minecraft.network.protocol.game.ClientboundSetTimePacket"));
        assertFalse(engine.includes("example.IgnoredBlockUpdatePacket"));
    }

    @Test
    void exactRuleDropsOnlyTheConfiguredCountAndPacketClass() {
        ExactDropRule rule = new ExactDropRule(
                TrafficDirection.CLIENT_TO_SERVER,
                ".*ServerboundSetCarriedItemPacket",
                2);
        ChaosEngine engine = new ChaosEngine(
                config(LinkProfile.CLEAR, 4).withExactDropRules(rule));

        assertFalse(engine.decide(
                TrafficDirection.CLIENT_TO_SERVER,
                "example.ServerboundUseItemOnPacket").drop());
        assertTrue(engine.decide(
                TrafficDirection.CLIENT_TO_SERVER,
                "example.ServerboundSetCarriedItemPacket").drop());
        assertTrue(engine.decide(
                TrafficDirection.CLIENT_TO_SERVER,
                "example.ServerboundSetCarriedItemPacket").drop());
        assertFalse(engine.decide(
                TrafficDirection.CLIENT_TO_SERVER,
                "example.ServerboundSetCarriedItemPacket").drop());
        assertFalse(engine.decide(
                TrafficDirection.SERVER_TO_CLIENT,
                "example.ServerboundSetCarriedItemPacket").drop());

        var stats = engine.exactDropStats().getFirst();
        assertEquals(3, stats.matched());
        assertEquals(2, stats.dropped());
        assertEquals(0, stats.remainingDrops());
    }

    @Test
    void resettingStatsDoesNotRearmAnExactRule() {
        ExactDropRule rule = new ExactDropRule(
                TrafficDirection.SERVER_TO_CLIENT, ".*BlockUpdatePacket", 1);
        ChaosEngine engine = new ChaosEngine(
                config(LinkProfile.CLEAR, 5).withExactDropRules(rule));

        assertTrue(engine.decide(TrafficDirection.SERVER_TO_CLIENT,
                "example.BlockUpdatePacket").drop());
        engine.resetStats();
        assertFalse(engine.decide(TrafficDirection.SERVER_TO_CLIENT,
                "example.BlockUpdatePacket").drop());

        var stats = engine.exactDropStats().getFirst();
        assertEquals(1, stats.matched());
        assertEquals(0, stats.dropped());
        assertEquals(0, stats.remainingDrops());
    }

    private static List<PacketDecision> decisions(ChaosEngine engine, int count) {
        List<PacketDecision> decisions = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            decisions.add(engine.decide(index % 2 == 0
                    ? TrafficDirection.CLIENT_TO_SERVER
                    : TrafficDirection.SERVER_TO_CLIENT));
        }
        return decisions;
    }

    private static ChaosConfig config(LinkProfile profile, long seed) {
        return new ChaosConfig(profile, profile, seed, true,
                ChaosConfig.ALL_PACKETS, ChaosConfig.NO_PACKETS);
    }
}
