package org.edtp.networkchaos.internal;

import org.edtp.networkchaos.api.ChaosConfig;
import org.edtp.networkchaos.api.ExactDropRule;
import org.edtp.networkchaos.api.ExactDropRuleStats;
import org.edtp.networkchaos.api.LinkProfile;
import org.edtp.networkchaos.api.TrafficDirection;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.regex.Pattern;

final class ChaosEngine {
    private ChaosConfig config;
    private Pattern include;
    private Pattern exclude;
    private SplittableRandom clientToServerRandom;
    private SplittableRandom serverToClientRandom;
    private List<ExactDropState> exactDropStates;

    ChaosEngine(ChaosConfig config) {
        configure(config);
    }

    synchronized void configure(ChaosConfig newConfig) {
        config = newConfig;
        include = Pattern.compile(newConfig.includePacketRegex());
        exclude = Pattern.compile(newConfig.excludePacketRegex());
        SplittableRandom root = new SplittableRandom(newConfig.seed());
        clientToServerRandom = root.split();
        serverToClientRandom = root.split();
        exactDropStates = newConfig.exactDropRules().stream()
                .map(ExactDropState::new)
                .toList();
    }

    synchronized ChaosConfig config() {
        return config;
    }

    synchronized boolean includes(String packetClassName) {
        return include.matcher(packetClassName).matches()
                && !exclude.matcher(packetClassName).matches();
    }

    synchronized PacketDecision decide(TrafficDirection direction) {
        return decide(direction, "");
    }

    synchronized PacketDecision decide(
            TrafficDirection direction,
            String packetClassName) {
        for (ExactDropState state : exactDropStates) {
            if (!state.matches(direction, packetClassName)) continue;
            if (state.shouldDrop()) {
                return new PacketDecision(true, 0, false, 0);
            }
            break;
        }

        LinkProfile profile = direction == TrafficDirection.CLIENT_TO_SERVER
                ? config.clientToServer()
                : config.serverToClient();
        SplittableRandom random = direction == TrafficDirection.CLIENT_TO_SERVER
                ? clientToServerRandom
                : serverToClientRandom;
        if (random.nextDouble() < profile.lossRate()) {
            return new PacketDecision(true, 0, false, 0);
        }

        long delay = profile.latencyMillis();
        if (profile.jitterMillis() > 0) {
            delay = Math.max(0, delay + random.nextLong(
                    -profile.jitterMillis(), profile.jitterMillis() + 1));
        }
        if (profile.reorderExtraMillis() > 0
                && random.nextDouble() < profile.reorderRate()) {
            delay = Math.addExact(delay,
                    random.nextLong(1, profile.reorderExtraMillis() + 1));
        }

        boolean duplicate = random.nextDouble() < profile.duplicateRate();
        long duplicateDelay = duplicate
                ? Math.addExact(delay,
                        Math.max(1, profile.jitterMillis() == 0
                                ? 1
                                : random.nextLong(1, profile.jitterMillis() + 1)))
                : 0;
        return new PacketDecision(false, delay, duplicate, duplicateDelay);
    }

    synchronized List<ExactDropRuleStats> exactDropStats() {
        List<ExactDropRuleStats> snapshots =
                new ArrayList<>(exactDropStates.size());
        exactDropStates.forEach(state -> snapshots.add(state.snapshot()));
        return List.copyOf(snapshots);
    }

    synchronized void resetStats() {
        exactDropStates.forEach(ExactDropState::resetStats);
    }

    private static final class ExactDropState {
        private final ExactDropRule rule;
        private final Pattern packetClassPattern;
        private long consumedDrops;
        private long matchedSinceReset;
        private long droppedSinceReset;

        private ExactDropState(ExactDropRule rule) {
            this.rule = rule;
            packetClassPattern = Pattern.compile(rule.packetClassRegex());
        }

        private boolean matches(
                TrafficDirection direction,
                String packetClassName) {
            return rule.direction() == direction
                    && packetClassPattern.matcher(packetClassName).matches();
        }

        private boolean shouldDrop() {
            matchedSinceReset++;
            if (consumedDrops >= rule.dropCount()) return false;
            consumedDrops++;
            droppedSinceReset++;
            return true;
        }

        private ExactDropRuleStats snapshot() {
            return new ExactDropRuleStats(
                    rule,
                    matchedSinceReset,
                    droppedSinceReset,
                    rule.dropCount() - consumedDrops);
        }

        private void resetStats() {
            matchedSinceReset = 0;
            droppedSinceReset = 0;
        }
    }
}
