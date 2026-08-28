package org.edtp.networkchaos.api;

import java.util.Objects;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Immutable configuration for both directions of an integrated-server link. */
public record ChaosConfig(
        LinkProfile clientToServer,
        LinkProfile serverToClient,
        long seed,
        boolean protectControlPackets,
        String includePacketRegex,
        String excludePacketRegex,
        List<ExactDropRule> exactDropRules) {

    public static final String ALL_PACKETS = ".*";
    public static final String NO_PACKETS = "(?!)";

    public ChaosConfig {
        Objects.requireNonNull(clientToServer, "clientToServer");
        Objects.requireNonNull(serverToClient, "serverToClient");
        Objects.requireNonNull(includePacketRegex, "includePacketRegex");
        Objects.requireNonNull(excludePacketRegex, "excludePacketRegex");
        exactDropRules = List.copyOf(
                Objects.requireNonNull(exactDropRules, "exactDropRules"));
        compile("includePacketRegex", includePacketRegex);
        compile("excludePacketRegex", excludePacketRegex);
    }

    public ChaosConfig(
            LinkProfile clientToServer,
            LinkProfile serverToClient,
            long seed,
            boolean protectControlPackets,
            String includePacketRegex,
            String excludePacketRegex) {
        this(clientToServer, serverToClient, seed, protectControlPackets,
                includePacketRegex, excludePacketRegex, List.of());
    }

    public static ChaosConfig clear() {
        return new ChaosConfig(
                LinkProfile.CLEAR,
                LinkProfile.CLEAR,
                0L,
                true,
                ALL_PACKETS,
                NO_PACKETS,
                List.of());
    }

    public ChaosConfig withClientToServer(LinkProfile profile) {
        return new ChaosConfig(profile, serverToClient, seed,
                protectControlPackets, includePacketRegex, excludePacketRegex,
                exactDropRules);
    }

    public ChaosConfig withServerToClient(LinkProfile profile) {
        return new ChaosConfig(clientToServer, profile, seed,
                protectControlPackets, includePacketRegex, excludePacketRegex,
                exactDropRules);
    }

    public ChaosConfig withSeed(long newSeed) {
        return new ChaosConfig(clientToServer, serverToClient, newSeed,
                protectControlPackets, includePacketRegex, excludePacketRegex,
                exactDropRules);
    }

    public ChaosConfig withPacketFilter(String includeRegex, String excludeRegex) {
        return new ChaosConfig(clientToServer, serverToClient, seed,
                protectControlPackets, includeRegex, excludeRegex,
                exactDropRules);
    }

    public ChaosConfig withExactDropRules(List<ExactDropRule> rules) {
        return new ChaosConfig(clientToServer, serverToClient, seed,
                protectControlPackets, includePacketRegex, excludePacketRegex,
                rules);
    }

    public ChaosConfig withExactDropRules(ExactDropRule... rules) {
        return withExactDropRules(List.of(rules));
    }

    private static Pattern compile(String name, String expression) {
        try {
            return Pattern.compile(expression);
        } catch (PatternSyntaxException exception) {
            throw new IllegalArgumentException(name + " is invalid", exception);
        }
    }
}
