package com.akto.behaviour_modelling.core;

import com.akto.behaviour_modelling.model.TransitionKey;
import com.akto.dto.ApiInfo.ApiInfoKey;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Implements the Variable Order Markov Chain (VOMC) collapsibility algorithm.
 *
 * Given multi-order transition counts from a window, prunes redundant higher-order
 * contexts where credible intervals overlap with the parent context. This produces
 * a compact model that only retains contexts carrying statistically distinct information.
 *
 * Reference: Cloudflare blog — "Protecting APIs from abuse using sequence learning
 * and variable order Markov chains"
 */
public class MarkovModelBuilder {

    private static final double ALPHA = 0.05;

    /**
     * Prunes redundant higher-order contexts from the snapshot's transition counts.
     * Works top-down: for each context of order N, checks if it's collapsible into
     * its parent context of order N-1. If all credible intervals overlap, the
     * higher-order context is removed.
     *
     * @return a new map of transition counts containing only statistically significant contexts
     */
    public static Map<TransitionKey, Long> prune(Map<TransitionKey, Long> transitionCounts) {
        if (transitionCounts.isEmpty()) return transitionCounts;

        // Group transitions by their context (all but last element) to build
        // probability distributions: context → { nextApi → count }
        // Also track total count per context for computing probabilities.
        Map<String, Map<ApiInfoKey, Long>> contextToNextCounts = new HashMap<>();
        Map<String, Long> contextTotals = new HashMap<>();

        for (Map.Entry<TransitionKey, Long> entry : transitionCounts.entrySet()) {
            TransitionKey key = entry.getKey();
            long count = entry.getValue();
            ApiInfoKey[] seq = key.getSequence();
            if (seq.length < 2) continue;

            // Context = all elements except the last; next = last element
            String contextKey = buildContextKey(seq, 0, seq.length - 1);
            ApiInfoKey nextApi = seq[seq.length - 1];

            contextToNextCounts.computeIfAbsent(contextKey, k -> new HashMap<>())
                    .merge(nextApi, count, Long::sum);
            contextTotals.merge(contextKey, count, Long::sum);
        }

        // Collect all unique "next" APIs across all contexts for interval comparison
        Set<ApiInfoKey> allNextApis = new HashSet<>();
        for (Map<ApiInfoKey, Long> nextCounts : contextToNextCounts.values()) {
            allNextApis.addAll(nextCounts.keySet());
        }

        // Find the maximum order present
        int maxOrder = 0;
        for (TransitionKey key : transitionCounts.keySet()) {
            int order = key.getSequence().length - 1;
            if (order > maxOrder) maxOrder = order;
        }

        // Work top-down: highest order first
        Set<String> collapsedContexts = new HashSet<>();
        for (int order = maxOrder; order >= 2; order--) {
            for (Map.Entry<String, Map<ApiInfoKey, Long>> entry : contextToNextCounts.entrySet()) {
                String childContext = entry.getKey();
                int contextOrder = childContext.split("\\|\\|").length;
                if (contextOrder != order) continue;
                if (collapsedContexts.contains(childContext)) continue;

                // Parent context = drop the first element
                String parentContext = dropFirstFromContextKey(childContext);
                if (parentContext == null) continue;

                Map<ApiInfoKey, Long> childNext = entry.getValue();
                Map<ApiInfoKey, Long> parentNext = contextToNextCounts.get(parentContext);
                if (parentNext == null) continue;

                long childTotal = contextTotals.getOrDefault(childContext, 0L);
                long parentTotal = contextTotals.getOrDefault(parentContext, 0L);

                if (isCollapsible(childNext, childTotal, parentNext, parentTotal, allNextApis)) {
                    collapsedContexts.add(childContext);
                }
            }
        }

        // Build result: only keep transitions whose context was not collapsed
        Map<TransitionKey, Long> pruned = new HashMap<>();
        for (Map.Entry<TransitionKey, Long> entry : transitionCounts.entrySet()) {
            TransitionKey key = entry.getKey();
            ApiInfoKey[] seq = key.getSequence();
            String contextKey = buildContextKey(seq, 0, seq.length - 1);
            if (!collapsedContexts.contains(contextKey)) {
                pruned.put(key, entry.getValue());
            }
        }

        return pruned;
    }

    /**
     * A child context is collapsible into its parent if ALL credible intervals
     * for each possible next API overlap between child and parent.
     */
    private static boolean isCollapsible(
            Map<ApiInfoKey, Long> childNext, long childTotal,
            Map<ApiInfoKey, Long> parentNext, long parentTotal,
            Set<ApiInfoKey> allNextApis) {

        for (ApiInfoKey nextApi : allNextApis) {
            long childCount = childNext.getOrDefault(nextApi, 0L);
            long parentCount = parentNext.getOrDefault(nextApi, 0L);

            double[] childInterval = credibleInterval(childCount, childTotal);
            double[] parentInterval = credibleInterval(parentCount, parentTotal);

            // Check if intervals overlap
            if (childInterval[0] > parentInterval[1] || parentInterval[0] > childInterval[1]) {
                return false; // No overlap → not collapsible
            }
        }
        return true;
    }

    /**
     * Computes a Bayesian credible interval using the Beta distribution.
     * Uses Beta(successes + 1, failures + 1) with Jeffreys-like prior.
     *
     * @return [lower, upper] bounds at ALPHA significance level
     */
    private static double[] credibleInterval(long successes, long total) {
        long failures = total - successes;
        double alpha = successes + 1.0;
        double beta = failures + 1.0;
        double lower = betaInverseCdf(ALPHA / 2.0, alpha, beta);
        double upper = betaInverseCdf(1.0 - ALPHA / 2.0, alpha, beta);
        return new double[]{lower, upper};
    }

    /**
     * Approximation of the inverse CDF of the Beta distribution using
     * the normal approximation for Beta distributions.
     * Accurate enough for the collapsibility test with typical count sizes.
     */
    private static double betaInverseCdf(double p, double a, double b) {
        // For very small or extreme parameters, clamp
        if (p <= 0) return 0.0;
        if (p >= 1) return 1.0;

        // Use the normal approximation to the Beta distribution
        double mean = a / (a + b);
        double variance = (a * b) / ((a + b) * (a + b) * (a + b + 1));
        double stddev = Math.sqrt(variance);

        // Inverse normal CDF (probit function) using rational approximation
        double z = inverseNormalCdf(p);
        double result = mean + z * stddev;

        // Clamp to [0, 1]
        return Math.max(0.0, Math.min(1.0, result));
    }

    /**
     * Rational approximation of the inverse standard normal CDF.
     * Abramowitz and Stegun formula 26.2.23, accurate to ~4.5e-4.
     */
    private static double inverseNormalCdf(double p) {
        if (p <= 0) return Double.NEGATIVE_INFINITY;
        if (p >= 1) return Double.POSITIVE_INFINITY;
        if (p == 0.5) return 0.0;

        boolean lower = p < 0.5;
        double pp = lower ? p : 1.0 - p;
        double t = Math.sqrt(-2.0 * Math.log(pp));

        // Coefficients for rational approximation
        double c0 = 2.515517;
        double c1 = 0.802853;
        double c2 = 0.010328;
        double d1 = 1.432788;
        double d2 = 0.189269;
        double d3 = 0.001308;

        double z = t - (c0 + c1 * t + c2 * t * t) / (1.0 + d1 * t + d2 * t * t + d3 * t * t * t);
        return lower ? -z : z;
    }

    // ── Context key helpers ──────────────────────────────────────────────────

    private static String buildContextKey(ApiInfoKey[] seq, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            if (i > from) sb.append("||");
            sb.append(seq[i].toString());
        }
        return sb.toString();
    }

    private static String dropFirstFromContextKey(String contextKey) {
        int idx = contextKey.indexOf("||");
        if (idx < 0) return null; // Single-element context, no parent
        return contextKey.substring(idx + 2);
    }
}
