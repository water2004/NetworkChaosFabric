package org.edtp.networkchaos.api;

import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Drops the first {@code dropCount} packets matching one direction and class. */
public record ExactDropRule(
        TrafficDirection direction,
        String packetClassRegex,
        long dropCount) {

    public ExactDropRule {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(packetClassRegex, "packetClassRegex");
        if (dropCount <= 0) {
            throw new IllegalArgumentException("dropCount must be positive");
        }
        try {
            Pattern.compile(packetClassRegex);
        } catch (PatternSyntaxException exception) {
            throw new IllegalArgumentException(
                    "packetClassRegex is invalid", exception);
        }
    }

    /** Creates an exact full-class-name rule without exposing regex quoting to callers. */
    public static ExactDropRule forPacketClass(
            TrafficDirection direction,
            Class<?> packetClass,
            long dropCount) {
        Objects.requireNonNull(packetClass, "packetClass");
        return new ExactDropRule(
                direction, Pattern.quote(packetClass.getName()), dropCount);
    }
}
