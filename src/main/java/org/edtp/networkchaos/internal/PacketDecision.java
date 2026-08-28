package org.edtp.networkchaos.internal;

record PacketDecision(
        boolean drop,
        long delayMillis,
        boolean duplicate,
        long duplicateDelayMillis) {
}
