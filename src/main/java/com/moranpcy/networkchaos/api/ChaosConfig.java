package com.moranpcy.networkchaos.api;

import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Immutable configuration for both directions of an integrated-server link. */
public record ChaosConfig(
        LinkProfile clientToServer,
        LinkProfile serverToClient,
        long seed,
        boolean protectControlPackets,
        String includePacketRegex,
        String excludePacketRegex) {

    public static final String ALL_PACKETS = ".*";
    public static final String NO_PACKETS = "(?!)";

    public ChaosConfig {
        Objects.requireNonNull(clientToServer, "clientToServer");
        Objects.requireNonNull(serverToClient, "serverToClient");
        Objects.requireNonNull(includePacketRegex, "includePacketRegex");
        Objects.requireNonNull(excludePacketRegex, "excludePacketRegex");
        compile("includePacketRegex", includePacketRegex);
        compile("excludePacketRegex", excludePacketRegex);
    }

    public static ChaosConfig clear() {
        return new ChaosConfig(
                LinkProfile.CLEAR,
                LinkProfile.CLEAR,
                0L,
                true,
                ALL_PACKETS,
                NO_PACKETS);
    }

    public ChaosConfig withClientToServer(LinkProfile profile) {
        return new ChaosConfig(profile, serverToClient, seed,
                protectControlPackets, includePacketRegex, excludePacketRegex);
    }

    public ChaosConfig withServerToClient(LinkProfile profile) {
        return new ChaosConfig(clientToServer, profile, seed,
                protectControlPackets, includePacketRegex, excludePacketRegex);
    }

    public ChaosConfig withSeed(long newSeed) {
        return new ChaosConfig(clientToServer, serverToClient, newSeed,
                protectControlPackets, includePacketRegex, excludePacketRegex);
    }

    public ChaosConfig withPacketFilter(String includeRegex, String excludeRegex) {
        return new ChaosConfig(clientToServer, serverToClient, seed,
                protectControlPackets, includeRegex, excludeRegex);
    }

    private static Pattern compile(String name, String expression) {
        try {
            return Pattern.compile(expression);
        } catch (PatternSyntaxException exception) {
            throw new IllegalArgumentException(name + " is invalid", exception);
        }
    }
}
