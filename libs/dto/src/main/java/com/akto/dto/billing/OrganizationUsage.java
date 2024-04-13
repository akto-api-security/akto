package com.akto.dto.billing;

import java.util.Map;

public class OrganizationUsage {

    public static enum DataSink {
        MIXPANEL, SLACK, STIGG;
    }

    public static final String ORG_ID = "orgId";
    private String orgId;

    public static final String DATE = "date";
    private int date;

    public static final String HOURLY_USAGE = "hourlyUsage";
    private Map<String, Map<String, Integer>> hourlyUsage;
    public static final String CREATION_EPOCH = "creationEpoch";
    private int creationEpoch;
    public static final String ORG_METRIC_MAP = "orgMetricMap";
    private Map<String, Integer> orgMetricMap;

    public static final String SINKS = "sinks";

    private Map<String, Integer> sinks;

    public OrganizationUsage() { }

    public OrganizationUsage(String orgId, int date, int creationEpoch, Map<String, Integer> orgMetricMap, Map<String, Integer> sinks, Map<String, Map<String, Integer>> hourlyUsage) {
        this.orgId = orgId;
        this.date = date;
        this.creationEpoch = creationEpoch;
        this.orgMetricMap = orgMetricMap;
        this.sinks = sinks;
        this.hourlyUsage = hourlyUsage;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public int getDate() {
        return date;
    }

    public void setDate(int date) {
        this.date = date;
    }

    public int getCreationEpoch() {
        return creationEpoch;
    }

    public void setCreationEpoch(int creationEpoch) {
        this.creationEpoch = creationEpoch;
    }

    public Map<String, Integer> getOrgMetricMap() {
        return orgMetricMap;
    }

    public void setOrgMetricMap(Map<String, Integer> orgMetricMap) {
        this.orgMetricMap = orgMetricMap;
    }

    public Map<String, Integer> getSinks() {
        return sinks;
    }

    public void setSinks(Map<String, Integer> sinks) {
        this.sinks = sinks;
    }

    public Map<String, Map<String, Integer>> getHourlyUsage() {
        return hourlyUsage;
    }

    public void setHourlyUsage(Map<String, Map<String, Integer>> hourlyUsage) {
        this.hourlyUsage = hourlyUsage;
    }

    @Override
    public String toString() {
        return "OrganizationUsage{" +
                "orgId='" + orgId + '\'' +
                ", date=" + date +
                ", creationEpoch=" + creationEpoch +
                ", metricMap=" + orgMetricMap +
                ", sinks=" + sinks +
                '}';
    }
}
