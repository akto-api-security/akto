package com.akto.dto.metrics;

import com.akto.dao.context.Context;
import com.akto.dto.data_types.Conditions;
import org.bson.types.ObjectId;

public class MetricData {
    private ObjectId id;
    private String metricId;
    private float value;
    private String orgId;
    private String instanceId;
    private int timestamp;

    public MetricData() {
    }

    public MetricData(String metricId, float value, String orgId, String instanceId) {
        this.metricId = metricId;
        this.value = value;
        this.orgId = orgId;
        this.instanceId = instanceId;
        this.timestamp = Context.now();
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getMetricId() {
        return metricId;
    }

    public void setMetricId(String metricId) {
        this.metricId = metricId;
    }

    public float getValue() {
        return value;
    }

    public void setValue(float value) {
        this.value = value;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }
} 