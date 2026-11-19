package com.akto.service.collections;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** What the home dashboard's MCP and guardrail widgets read. */
public final class McpPostureResponses {

    private McpPostureResponses() {
    }

    /** All the MCP counts, from one call. */
    @Getter
    @AllArgsConstructor
    public static class Counts {
        private final long totalApis;
        private final long newApis7Days;
        private final long thirdPartyApis;
        private final long criticalApis;
        private final long tools;
        private final long prompts;
        private final long resources;
        private final long servers;
        private final long policyGuardrailApis;
    }

    /** A page of one list widget. */
    @Getter
    @AllArgsConstructor
    public static class Data {
        private final List<Alert> alerts;
        private final List<TopApplication> topApplications;
        private final long total;
    }

    @Getter
    @AllArgsConstructor
    public static class Alert {
        private final String type;
        private final int lastDetected;
        private final String resourceName;
    }

    @Getter
    @AllArgsConstructor
    public static class TopApplication {
        private final String name;
        private final int collectionId;
        private final long hitCount;
    }

    /** Api calls of the tagged collections over a range, one point per time bucket. */
    @Getter
    @AllArgsConstructor
    public static class Series {
        private final List<Point> points;
    }

    /** ts: the bucket's start, in minutes since the epoch (the unit api_hit_count_info keeps). */
    @Getter
    @AllArgsConstructor
    public static class Point {
        private final long ts;
        private final long count;
    }
}
