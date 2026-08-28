package org.edtp.networkchaos.api;

/** Runtime counters for one configured exact-drop rule. */
public record ExactDropRuleStats(
        ExactDropRule rule,
        long matched,
        long dropped,
        long remainingDrops) {
}
