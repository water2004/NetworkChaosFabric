package com.moranpcy.networkchaos.api;

import com.moranpcy.networkchaos.internal.NetworkChaosRuntime;

/** Public control surface intended for integration tests and other test mods. */
public final class NetworkChaos {
    private NetworkChaos() {
    }

    public static void enable(ChaosConfig config) {
        NetworkChaosRuntime.configure(config);
        NetworkChaosRuntime.enable();
    }

    public static void enable() {
        NetworkChaosRuntime.enable();
    }

    public static void configure(ChaosConfig config) {
        NetworkChaosRuntime.configure(config);
    }

    public static void disable() {
        NetworkChaosRuntime.disable();
    }

    public static boolean isEnabled() {
        return NetworkChaosRuntime.enabled();
    }

    public static ChaosConfig config() {
        return NetworkChaosRuntime.config();
    }

    public static ChaosStats stats() {
        return NetworkChaosRuntime.stats();
    }

    public static void resetStats() {
        NetworkChaosRuntime.resetStats();
    }

    /** Disables injection, discards queued packets, clears config and counters. */
    public static void reset() {
        NetworkChaosRuntime.reset();
    }
}
