package com.akto.service.insights;

import java.util.Locale;

/**
 * Java-side number formatting that a narrative prompt copies verbatim rather than recomputing.
 * Grows as providers need new shapes — keep every formatter here rather than inlining
 * String.format calls in provider code, so the numeric-guard allow-list has one place to scan.
 */
public final class MetricFormat {

    private MetricFormat() {}

    public static String count(long value, String unit) {
        return String.format(Locale.US, "%,d %s", value, value == 1 ? unit : pluralize(unit));
    }

    /** Just enough English pluralization to keep provider headlines grammatical — consonant+"y"
     *  words (policy, category, ...) take "-ies" rather than a blind "+s" ("policys"). Extend here,
     *  not by hand-writing a plural inline in a provider, if another irregular unit word comes up. */
    private static String pluralize(String unit) {
        if (unit.length() > 1 && unit.endsWith("y") && "aeiou".indexOf(unit.charAt(unit.length() - 2)) < 0) {
            return unit.substring(0, unit.length() - 1) + "ies";
        }
        return unit + "s";
    }

    public static String ofTotal(long value, long total) {
        return String.format(Locale.US, "%,d of %,d", value, total);
    }

    public static String percent(double fraction) {
        return String.format(Locale.US, "%.0f%%", fraction * 100);
    }
}
