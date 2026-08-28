package org.edtp.networkchaos.api;

/**
 * Conditions applied independently to one direction of a local connection.
 * Rates are expressed in the inclusive range {@code 0.0..1.0}; times are in
 * milliseconds.
 */
public record LinkProfile(
        double lossRate,
        long latencyMillis,
        long jitterMillis,
        double duplicateRate,
        double reorderRate,
        long reorderExtraMillis) {

    private static final long MAX_TIME_MILLIS = 3_600_000;

    public static final LinkProfile CLEAR =
            new LinkProfile(0.0, 0, 0, 0.0, 0.0, 0);

    public LinkProfile {
        requireRate("lossRate", lossRate);
        requireRate("duplicateRate", duplicateRate);
        requireRate("reorderRate", reorderRate);
        requireNonNegative("latencyMillis", latencyMillis);
        requireNonNegative("jitterMillis", jitterMillis);
        requireNonNegative("reorderExtraMillis", reorderExtraMillis);
    }

    public boolean isClear() {
        return lossRate == 0.0
                && latencyMillis == 0
                && jitterMillis == 0
                && duplicateRate == 0.0
                && reorderRate == 0.0;
    }

    private static void requireRate(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
    }

    private static void requireNonNegative(String name, long value) {
        if (value < 0 || value > MAX_TIME_MILLIS) {
            throw new IllegalArgumentException(
                    name + " must be between 0 and " + MAX_TIME_MILLIS);
        }
    }
}
