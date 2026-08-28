package com.moranpcy.networkchaos.api;

public record DirectionStats(
        long seen,
        long passedImmediately,
        long dropped,
        long delayed,
        long duplicated,
        long deliveredFromQueue,
        long cancelledFromQueue,
        long failedFromQueue) {
}
