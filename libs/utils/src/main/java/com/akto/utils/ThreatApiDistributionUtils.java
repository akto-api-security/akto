package com.akto.utils;

import java.util.Arrays;
import java.util.List;

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

    public static List<Range> getBucketRanges() {
        return BUCKET_RANGES;
    }

    public static List<String> getBucketOrder() {
        return Arrays.asList("b1","b2","b3","b4","b5","b6","b7","b8","b9","b10","b11","b12","b13","b14");
    }

    public static List<Integer> getBucketUpperBounds() {
        return Arrays.asList(10, 50, 100, 250, 500, 1000, 2500, 5000, 10000, 20000, 35000, 50000, 100000, Integer.MAX_VALUE);
    }

    public static List<Integer> getBucketLowerBounds() {
        List<Integer> upperBounds = getBucketUpperBounds();
        List<Integer> lowerBounds = Arrays.asList(new Integer[upperBounds.size()]);
        for (int i = 0; i < upperBounds.size(); i++) {
            if (i == 0) {
                lowerBounds.set(i, 0);
            } else {
                lowerBounds.set(i, upperBounds.get(i - 1));
            }
        }
        return lowerBounds;
    }
}