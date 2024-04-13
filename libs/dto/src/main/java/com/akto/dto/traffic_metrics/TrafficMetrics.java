package com.akto.dto.traffic_metrics;

import java.util.Map;
import java.util.Objects;

public class TrafficMetrics {

    private Key id;
    private Map<String, Long> countMap;

    public TrafficMetrics() {
    }

    public TrafficMetrics(Key id, Map<String, Long> countMap) {
        this.id = id;
        this.countMap = countMap;
    }

    public enum Name {
        OUTGOING_PACKETS_MIRRORING("Bytes processed for HTTP data", "Bytes of data processed by traffic mirroring module"),
        OUTGOING_REQUESTS_MIRRORING("API calls extracted", "Count of API req-resp processed by traffic mirroring module"),
        INCOMING_PACKETS_MIRRORING("Bytes received", "Bytes of data received from traffic mirroring"),
        TOTAL_REQUESTS_RUNTIME("API calls received", "Count of API req-resp recevied by runtime module"),
        FILTERED_REQUESTS_RUNTIME("API calls processed", "Count of API req-resp processed successfully by runtime module");

        private final String description;
        private final String descriptionName;

        Name(String descriptionName, String description){
            this.descriptionName = descriptionName;
            this.description = description;
        }

        public String getDescription(){
            return this.description;
        }

        public String getDescriptionName(){
            return this.descriptionName;
        }
    }

    public static class Key {
        private String ip;
        public static final String IP = "ip";
        private String host;
        public static final String HOST = "host";
        private int vxlanID;
        public static final String VXLAN_ID = "vxlanID";
        private Name name;
        public static final String NAME = "name";
        private int bucketStartEpoch;
        public static final String BUCKET_START_EPOCH = "bucketStartEpoch";
        private int bucketEndEpoch;
        public static final String BUCKET_END_EPOCH = "bucketEndEpoch";

        public Key(String ip, String host, int vxlanID, Name name, int bucketStartEpoch, int bucketEndEpoch) {
            this.ip = ip;
            this.host = host;
            this.vxlanID = vxlanID;
            this.name = name;
            this.bucketStartEpoch = bucketStartEpoch;
            this.bucketEndEpoch = bucketEndEpoch;
        }

        public Key() {}

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getVxlanID() {
            return vxlanID;
        }

        public void setVxlanID(int vxlanID) {
            this.vxlanID = vxlanID;
        }

        public Name getName() {
            return name;
        }

        public void setName(Name name) {
            this.name = name;
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Key key = (Key) o;
            return vxlanID == key.vxlanID && bucketStartEpoch == key.bucketStartEpoch && bucketEndEpoch == key.bucketEndEpoch && ip.equals(key.ip) && Objects.equals(host, key.host) && name == key.name;
        }

        @Override
        public int hashCode() {
            return Objects.hash(ip, host, vxlanID, name, bucketStartEpoch, bucketEndEpoch);
        }
    }

    public void inc(int value) {
        long epochTimeMillis = System.currentTimeMillis();
        long epochHours = (long) Math.floor(epochTimeMillis / (60.0 * 60.0 * 1000.0));

        long val = countMap.getOrDefault(epochHours+"", 0L);
        val += value;
        countMap.put(epochHours+"",val);
    }

    public Key getId() {
        return id;
    }

    public void setId(Key id) {
        this.id = id;
    }

    public Map<String, Long> getCountMap() {
        return countMap;
    }

    public void setCountMap(Map<String, Long> countMap) {
        this.countMap = countMap;
    }
}
