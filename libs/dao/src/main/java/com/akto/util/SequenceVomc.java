package com.akto.util;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SequenceVomc {

    public static final String CONTEXT_SEPARATOR = "|";
    public static final double CI_LOW_QUANTILE = 0.05;
    public static final double CI_HIGH_QUANTILE = 0.95;

    private static final int CF_MAX_ITERATIONS = 100000;
    private static final double CF_EPSILON = 1e-15;
    private static final double FP_MIN = 1e-300;
    private static final int BISECTION_ITERATIONS = 64;

    private static final double[] LANCZOS = {
            0.99999999999980993, 676.5203681218851, -1259.1392167224028, 771.32342877765313,
            -176.61502916214059, 12.507343278686905, -0.13857109526572012,
            9.9843695780195716e-6, 1.5056327351493116e-7
    };

    private SequenceVomc() {}

    public static String contextKey(List<String> context) {
        if (context == null || context.isEmpty()) return "";
        return String.join(CONTEXT_SEPARATOR, context);
    }

    public static String parentContextKey(List<String> context) {
        if (context == null || context.isEmpty()) return null;
        return contextKey(context.subList(1, context.size()));
    }

    public static double[] credibleInterval(long k, long n) {
        double a = k + 1.0;
        double b = n - k + 1.0;
        return new double[] { betaQuantile(CI_LOW_QUANTILE, a, b), betaQuantile(CI_HIGH_QUANTILE, a, b) };
    }

    public static boolean intervalsOverlap(double[] x, double[] y) {
        return x[0] <= y[1] && y[0] <= x[1];
    }

    public static boolean isCollapsed(int order, Map<String, Long> contextCounts, Map<String, Long> parentCounts) {
        if (order < 1 || parentCounts == null || parentCounts.isEmpty()) return false;
        long n = sum(contextCounts);
        long parentN = sum(parentCounts);
        if (n <= 0 || parentN <= 0) return false;
        Set<String> nexts = new HashSet<>(contextCounts.keySet());
        nexts.addAll(parentCounts.keySet());
        for (String next : nexts) {
            double[] ci = credibleInterval(getOrZero(contextCounts, next), n);
            double[] parentCi = credibleInterval(getOrZero(parentCounts, next), parentN);
            if (!intervalsOverlap(ci, parentCi)) return false;
        }
        return true;
    }

    public static double precedence(long sequenceCount, long order0Count) {
        if (order0Count <= 0) return 0;
        return (double) sequenceCount / order0Count;
    }

    public static long sum(Map<String, Long> counts) {
        long total = 0;
        if (counts == null) return total;
        for (Long c : counts.values()) {
            if (c != null) total += c;
        }
        return total;
    }

    private static long getOrZero(Map<String, Long> counts, String key) {
        Long v = counts.get(key);
        return v == null ? 0 : v;
    }

    public static double betaQuantile(double p, double a, double b) {
        if (p <= 0) return 0;
        if (p >= 1) return 1;
        double lo = 0;
        double hi = 1;
        for (int i = 0; i < BISECTION_ITERATIONS; i++) {
            double mid = (lo + hi) / 2;
            if (regularizedIncompleteBeta(mid, a, b) < p) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return (lo + hi) / 2;
    }

    public static double regularizedIncompleteBeta(double x, double a, double b) {
        if (x <= 0) return 0;
        if (x >= 1) return 1;
        double logFront = logGamma(a + b) - logGamma(a) - logGamma(b) + a * Math.log(x) + b * Math.log1p(-x);
        double front = Math.exp(logFront);
        if (x < (a + 1) / (a + b + 2)) {
            return front * betaContinuedFraction(x, a, b) / a;
        }
        return 1 - front * betaContinuedFraction(1 - x, b, a) / b;
    }

    private static double betaContinuedFraction(double x, double a, double b) {
        double qab = a + b;
        double qap = a + 1;
        double qam = a - 1;
        double c = 1;
        double d = 1 - qab * x / qap;
        if (Math.abs(d) < FP_MIN) d = FP_MIN;
        d = 1 / d;
        double h = d;
        for (int m = 1; m <= CF_MAX_ITERATIONS; m++) {
            int m2 = 2 * m;
            double aa = m * (b - m) * x / ((qam + m2) * (a + m2));
            d = 1 + aa * d;
            if (Math.abs(d) < FP_MIN) d = FP_MIN;
            c = 1 + aa / c;
            if (Math.abs(c) < FP_MIN) c = FP_MIN;
            d = 1 / d;
            h *= d * c;
            aa = -(a + m) * (qab + m) * x / ((a + m2) * (qap + m2));
            d = 1 + aa * d;
            if (Math.abs(d) < FP_MIN) d = FP_MIN;
            c = 1 + aa / c;
            if (Math.abs(c) < FP_MIN) c = FP_MIN;
            d = 1 / d;
            double del = d * c;
            h *= del;
            if (Math.abs(del - 1) < CF_EPSILON) break;
        }
        return h;
    }

    public static double logGamma(double x) {
        if (x < 0.5) {
            return Math.log(Math.PI / Math.abs(Math.sin(Math.PI * x))) - logGamma(1 - x);
        }
        x -= 1;
        double sum = LANCZOS[0];
        double t = x + 7.5;
        for (int i = 1; i < LANCZOS.length; i++) {
            sum += LANCZOS[i] / (x + i);
        }
        return 0.5 * Math.log(2 * Math.PI) + (x + 0.5) * Math.log(t) - t + Math.log(sum);
    }
}
