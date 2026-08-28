package com.moranpcy.networkchaos.api;

public final class ChaosPresets {
    private ChaosPresets() {
    }

    public static ChaosConfig latency(long seed) {
        LinkProfile profile = new LinkProfile(0.0, 500, 100, 0.0, 0.0, 0);
        return symmetric(profile, seed);
    }

    public static ChaosConfig lossy(long seed) {
        LinkProfile profile = new LinkProfile(0.10, 100, 50, 0.01, 0.05, 250);
        return symmetric(profile, seed);
    }

    public static ChaosConfig bad(long seed) {
        LinkProfile profile = new LinkProfile(0.25, 250, 150, 0.03, 0.15, 500);
        return symmetric(profile, seed);
    }

    public static ChaosConfig symmetric(LinkProfile profile, long seed) {
        return new ChaosConfig(profile, profile, seed, true,
                ChaosConfig.ALL_PACKETS, ChaosConfig.NO_PACKETS);
    }
}
