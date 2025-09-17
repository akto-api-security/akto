package com.akto.utils;

import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class ThreatApiDistributionUtils {

    public static class Range {
        public final int min;
        public final int max;
        public final String label;

        public Range(int min, int max, String label) {
            this.min = min;
            this.max = max;
            this.label = label;
        }
    }

    private static final List<Range> BUCKET_RANGES = Arrays.asList(
            new Range(1, 10, "b1"), new Range(11, 50, "b2"), new Range(51, 100, "b3"),
            new Range(101, 250, "b4"), new Range(251, 500, "b5"), new Range(501, 1000, "b6"),
            new Range(1001, 2500, "b7"), new Range(2501, 5000, "b8"), new Range(5001, 10000, "b9"),
            new Range(10001, 20000, "b10"), new Range(20001, 35000, "b11"), new Range(35001, 50000, "b12"),
            new Range(50001, 100000, "b13"), new Range(100001, Integer.MAX_VALUE, "b14")
    );

    private static final Map<String, Range> LABEL_TO_RANGE_MAP = new HashMap<>();
    static {
        for (Range range : BUCKET_RANGES) {
            LABEL_TO_RANGE_MAP.put(range.label, range);
        }
    }

    public static List<Range> getBucketRanges() {
        return BUCKET_RANGES;
    }

    public static int getBucketUpperBound(String bucketLabel){
        return LABEL_TO_RANGE_MAP.get(bucketLabel).max;
    }

    public static int getBucketLowerBound(String bucketLabel){
        return LABEL_TO_RANGE_MAP.get(bucketLabel).min;
    }

}