package com.moranpcy.networkchaos.api;

public record ChaosStats(
        DirectionStats clientToServer,
        DirectionStats serverToClient,
        long ignoredNonLocal,
        long ignoredNonPlay,
        long ignoredByFilter,
        long protectedControlPackets) {
}
