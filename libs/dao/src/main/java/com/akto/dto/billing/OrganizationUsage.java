package com.akto.dto.billing;

import com.akto.dto.usage.MetricTypes;

import java.util.Map;

public class OrganizationUsage {

    public static enum DataSink {
        MIXPANEL, SLACK, STIGG;
    }

    public static final String ORG_ID = "orgId";
    private String orgId;

    public static final String DATE = "date";
    private int date;
    private int creationEpoch;
    private Map<MetricTypes, Integer> metricMap;

    public static final String SINKS = "sinks";

    private Map<DataSink, Integer> sinks;

    public OrganizationUsage(String orgId, int date, int creationEpoch, Map<MetricTypes, Integer> metricMap, Map<DataSink, Integer> sinks) {
        this.orgId = orgId;
        this.date = date;
        this.creationEpoch = creationEpoch;
        this.metricMap = metricMap;
        this.sinks = sinks;
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

    public Map<MetricTypes, Integer> getMetricMap() {
        return metricMap;
    }

    public void setMetricMap(Map<MetricTypes, Integer> metricMap) {
        this.metricMap = metricMap;
    }

    public Map<DataSink, Integer> getSinks() {
        return sinks;
    }

    public void setSinks(Map<DataSink, Integer> sinks) {
        this.sinks = sinks;
    }

    @Override
    public String toString() {
        return "OrganizationUsage{" +
                "orgId='" + orgId + '\'' +
                ", date=" + date +
                ", creationEpoch=" + creationEpoch +
                ", metricMap=" + metricMap +
                ", sinks=" + sinks +
                '}';
    }
}
