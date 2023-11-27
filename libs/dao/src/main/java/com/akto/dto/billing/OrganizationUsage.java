package com.akto.dto.billing;

import java.util.Map;

public class OrganizationUsage {

    public static final String ORG_ID = "orgId";
    private String orgId;

    public static final String DATE = "date";
    private int date;
    private int creationEpoch;
    private Map<String, Integer> metricMap;

    public OrganizationUsage(String orgId, int date, int creationEpoch, Map<String, Integer> metricMap) {
        this.orgId = orgId;
        this.date = date;
        this.creationEpoch = creationEpoch;
        this.metricMap = metricMap;
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

    public Map<String, Integer> getMetricMap() {
        return metricMap;
    }

    public void setMetricMap(Map<String, Integer> metricMap) {
        this.metricMap = metricMap;
    }

    @Override
    public String toString() {
        return "OrganizationUsage{" +
                "orgId=" + orgId +
                ", date=" + date +
                ", creationEpoch=" + creationEpoch +
                ", metricMap=" + metricMap +
                '}';
    }
}
