package org.edtp.networkchaos.api;

import java.util.List;

public record ChaosStats(
        DirectionStats clientToServer,
        DirectionStats serverToClient,
        long ignoredNonLocal,
        long ignoredNonPlay,
        long ignoredByFilter,
        long protectedControlPackets,
        List<ExactDropRuleStats> exactDropRules) {

    public ChaosStats {
        exactDropRules = List.copyOf(exactDropRules);
    }
}
