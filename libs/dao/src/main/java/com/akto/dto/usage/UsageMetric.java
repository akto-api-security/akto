package com.akto.dto.usage;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.types.ObjectId;

import com.akto.dao.context.Context;

public class UsageMetric {
    
    @BsonId
    private ObjectId id;
    public static final String ID = "_id";
    private String organizationId;
    public static final String ORGANIZATION_ID = "organizationId";
    private int accountId;
    public static final String ACCOUNT_ID = "accountId";
    private MetricTypes metricType;
    public static final String METRIC_TYPE = "metricType";
    private int recordedAt;
    public static final String RECORDED_AT = "recordedAt";
    public static final String _USAGE = "usage";
    private int usage;
    private String dashboardMode;
    private String dashboardVersion;
    private int minutesSinceLastSync;
    private String metadata;
    private boolean syncedWithAkto;
    public static final String SYNCED_WITH_AKTO = "syncedWithAkto";
    private String ipAddress;
    public static final String SYNC_EPOCH = "syncEpoch";
    private int syncEpoch;
    public static final String MEASURE_EPOCH = "measureEpoch";
    private int measureEpoch;
    private int aktoSaveEpoch;
    public static final String AKTO_SAVE_EPOCH = "aktoSaveEpoch";

    // Constructors
    public UsageMetric() { }

    public UsageMetric(String organizationId, int accountId, MetricTypes metricType, int syncEpoch, int measureEpoch, String dashboardMode, String dashboardVersion) {
        this.organizationId = organizationId;
        this.accountId = accountId;
        this.metricType = metricType;
        this.dashboardMode = dashboardMode;
        this.dashboardVersion = dashboardVersion;
        this.syncedWithAkto = false;
        this.syncEpoch = syncEpoch;
        this.measureEpoch = measureEpoch;

        if (syncEpoch == -1) {
            this.minutesSinceLastSync = -1;
        } else {
            this.minutesSinceLastSync = (Context.now() - syncEpoch) / 60;
        }
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
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

    public int getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(int recordedAt) {
        this.recordedAt = recordedAt;
    }

    public int getUsage() {
        return usage;
    }

    public void setUsage(int usage) {
        this.usage = usage;
    }

    public String getDashboardMode() {
        return dashboardMode;
    }

    public void setDashboardMode(String dashboardMode) {
        this.dashboardMode = dashboardMode;
    }

    public String getDashboardVersion() {
        return dashboardVersion;
    }

    public void setDashboardVersion(String dashboardVersion) {
        this.dashboardVersion = dashboardVersion;
    }

    public int getMinutesSinceLastSync() {
        return minutesSinceLastSync;
    }

    public void setMinutesSinceLastSync(int minutesSinceLastSync) {
        this.minutesSinceLastSync = minutesSinceLastSync;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public boolean isSyncedWithAkto() {
        return syncedWithAkto;
    }

    public void setSyncedWithAkto(boolean syncedWithAkto) {
        this.syncedWithAkto = syncedWithAkto;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
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

    public int getAktoSaveEpoch() {
        return aktoSaveEpoch;
    }

    public void setAktoSaveEpoch(int aktoSaveEpoch) {
        this.aktoSaveEpoch = aktoSaveEpoch;
    }
}
