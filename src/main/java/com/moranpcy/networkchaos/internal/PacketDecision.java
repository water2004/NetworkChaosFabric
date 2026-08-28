package com.moranpcy.networkchaos.internal;

record PacketDecision(
        boolean drop,
        long delayMillis,
        boolean duplicate,
        long duplicateDelayMillis) {
}
