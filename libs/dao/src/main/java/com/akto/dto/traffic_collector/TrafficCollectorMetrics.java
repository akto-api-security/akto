package com.akto.dto.traffic_collector;

import java.util.Map;

public class TrafficCollectorMetrics {
    private String id;
    public static final String RUNTIME_ID = "runtimeId";
    private String runtimeId;
    public static final String REQUESTS_COUNT_MAP_PER_MINUTE = "requestsCountMapPerMinute";
    private Map<String, Integer> requestsCountMapPerMinute;
    public static final String BUCKET_START_EPOCH = "bucketStartEpoch";
    private int bucketStartEpoch;
    public static final String BUCKET_END_EPOCH = "bucketEndEpoch";
    private int bucketEndEpoch;


    public TrafficCollectorMetrics(String id, String runtimeId,  Map<String, Integer> requestsCountMapPerMinute, int bucketStartEpoch, int bucketEndEpoch) {
        this.id = id;
        this.runtimeId = runtimeId;
        this.requestsCountMapPerMinute = requestsCountMapPerMinute;
        this.bucketStartEpoch = bucketStartEpoch;
        this.bucketEndEpoch = bucketEndEpoch;
    }

    public TrafficCollectorMetrics() {
    }

    @Override
    public String toString() {
        return "TrafficCollectorMetrics{" +
                "id='" + id + '\'' +
                ", runtimeId='" + runtimeId + '\'' +
                ", requestsCountMapPerMinute=" + requestsCountMapPerMinute +
                ", bucketStartEpoch=" + bucketStartEpoch +
                ", bucketEndEpoch=" + bucketEndEpoch +
                '}';
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Map<String, Integer> getRequestsCountMapPerMinute() {
        return requestsCountMapPerMinute;
    }

    public void setRequestsCountMapPerMinute(Map<String, Integer> requestsCountMapPerMinute) {
        this.requestsCountMapPerMinute = requestsCountMapPerMinute;
    }

    public int getBucketStartEpoch() {
        return bucketStartEpoch;
    }

    public void setBucketStartEpoch(int bucketStartEpoch) {
        this.bucketStartEpoch = bucketStartEpoch;
    }

    public int getBucketEndEpoch() {
        return bucketEndEpoch;
    }

    public void setBucketEndEpoch(int bucketEndEpoch) {
        this.bucketEndEpoch = bucketEndEpoch;
    }

    public String getRuntimeId() {
        return runtimeId;
    }

    public void setRuntimeId(String runtimeId) {
        this.runtimeId = runtimeId;
    }
}
