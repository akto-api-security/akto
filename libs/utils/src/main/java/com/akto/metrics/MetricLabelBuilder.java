package com.akto.metrics;

import java.util.regex.Pattern;

/**
 * Builds bounded, low-cardinality metric labels from request paths.
 *
 * Dynamic path segments (ids) are collapsed to their Akto {@code SingleTypeInfo.SuperType}
 * name so that every distinct id does not create a new Prometheus time series, e.g.
 * {@code /api/inventory/123 -> /api/inventory/INTEGER}. Application URLs/routing are
 * never changed; this only normalizes the value used as a metric tag.
 */
public class MetricLabelBuilder {

    private static final Pattern INTEGER = Pattern.compile("^\\d+$");
    private static final Pattern FLOAT = Pattern.compile("^\\d+\\.\\d+$");
    private static final Pattern OBJECT_ID = Pattern.compile("^[0-9a-fA-F]{24}$");
    private static final Pattern UUID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private MetricLabelBuilder() {}

    public static String templatize(String path) {
        if (path == null || path.isEmpty()) return path;

        // defensively strip any query string
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);

        boolean leadingSlash = path.startsWith("/");
        boolean trailingSlash = path.length() > 1 && path.endsWith("/");

        String trimmed = path;
        if (leadingSlash) trimmed = trimmed.substring(1);
        if (trailingSlash) trimmed = trimmed.substring(0, trimmed.length() - 1);
        if (trimmed.isEmpty()) return path;

        String[] segments = trimmed.split("/");
        StringBuilder sb = new StringBuilder();
        if (leadingSlash) sb.append("/");
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) sb.append("/");
            sb.append(classify(segments[i]));
        }
        if (trailingSlash) sb.append("/");
        return sb.toString();
    }

    private static String classify(String segment) {
        if (segment.isEmpty()) return segment;
        // order matters: a purely numeric segment stays INTEGER even at 24 chars
        if (INTEGER.matcher(segment).matches()) return "INTEGER";
        if (OBJECT_ID.matcher(segment).matches()) return "OBJECT_ID";
        if (UUID.matcher(segment).matches()) return "STRING";
        if (FLOAT.matcher(segment).matches()) return "FLOAT";
        return segment;
    }
}
