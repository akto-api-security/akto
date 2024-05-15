package com.akto.dto.usage;

import com.akto.dao.context.Context;

public class UsageMetricInfo {
    
    private String organizationId;
    public static final String ORGANIZATION_ID = "organizationId";
    private int accountId;
    public static final String ACCOUNT_ID = "accountId";
    private MetricTypes metricType;
    public static final String METRIC_TYPE = "metricType";
    private int syncEpoch;
    public static final String SYNC_EPOCH = "syncEpoch";
    private int measureEpoch;
    public static final String MEASURE_EPOCH = "measureEpoch";

    public final static int MEASURE_PERIOD = 2629746; // a month

    public UsageMetricInfo() { }

    public UsageMetricInfo(String organizationId, int accountId, MetricTypes metricType) {
        this.organizationId = organizationId;
        this.accountId = accountId;
        this.metricType = metricType;
        this.syncEpoch = -1;
        // Initialize to start date of customers current billing cycle after billing is configured
        // For now initialize to 10 days ago to collect initial usage
        this.measureEpoch = Context.now() - 864000*2;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public int getAccountId() {
        return accountId;
    }

    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }

    public MetricTypes getMetricType() {
        return metricType;
    }

    public void setMetricType(MetricTypes metricType) {
        this.metricType = metricType;
    }

    public int getSyncEpoch() {
        return syncEpoch;
    }

    public void setSyncEpoch(int syncEpoch) {
        this.syncEpoch = syncEpoch;
    }

    public int getMeasureEpoch() {
        return measureEpoch;
    }

    public void setMeasureEpoch(int measureEpoch) {
        this.measureEpoch = measureEpoch;
    }
}
