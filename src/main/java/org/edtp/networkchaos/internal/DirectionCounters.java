package org.edtp.networkchaos.internal;

import org.edtp.networkchaos.api.DirectionStats;

import java.util.concurrent.atomic.LongAdder;

final class DirectionCounters {
    final LongAdder seen = new LongAdder();
    final LongAdder passedImmediately = new LongAdder();
    final LongAdder dropped = new LongAdder();
    final LongAdder delayed = new LongAdder();
    final LongAdder duplicated = new LongAdder();
    final LongAdder deliveredFromQueue = new LongAdder();
    final LongAdder cancelledFromQueue = new LongAdder();
    final LongAdder failedFromQueue = new LongAdder();

    DirectionStats snapshot() {
        return new DirectionStats(
                seen.sum(),
                passedImmediately.sum(),
                dropped.sum(),
                delayed.sum(),
                duplicated.sum(),
                deliveredFromQueue.sum(),
                cancelledFromQueue.sum(),
                failedFromQueue.sum());
    }

    void reset() {
        seen.reset();
        passedImmediately.reset();
        dropped.reset();
        delayed.reset();
        duplicated.reset();
        deliveredFromQueue.reset();
        cancelledFromQueue.reset();
        failedFromQueue.reset();
    }
}
