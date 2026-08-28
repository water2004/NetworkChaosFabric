package com.moranpcy.networkchaos.internal;

import com.moranpcy.networkchaos.api.ChaosConfig;
import com.moranpcy.networkchaos.api.LinkProfile;
import com.moranpcy.networkchaos.api.TrafficDirection;

import java.util.SplittableRandom;
import java.util.regex.Pattern;

final class ChaosEngine {
    private ChaosConfig config;
    private Pattern include;
    private Pattern exclude;
    private SplittableRandom clientToServerRandom;
    private SplittableRandom serverToClientRandom;

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
    }

    synchronized ChaosConfig config() {
        return config;
    }

    synchronized boolean includes(String packetClassName) {
        return include.matcher(packetClassName).matches()
                && !exclude.matcher(packetClassName).matches();
    }

    synchronized PacketDecision decide(TrafficDirection direction) {
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
}
