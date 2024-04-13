package com.akto.dto.testing;

import java.util.List;

import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import com.akto.dto.ApiInfo.ApiInfoKey;

public class AccessMatrixTaskInfo {

    private ObjectId id;
    public static final String ENDPOINT_LOGICAL_GROUP_NAME = "endpointLogicalGroupName";
    private String endpointLogicalGroupName;
    public static final String FREQUENCY_IN_SECONDS = "frequencyInSeconds";
    private int frequencyInSeconds;
    public static final String LAST_COMPLETED_TIMESTAMP = "lastCompletedTimestamp";
    private int lastCompletedTimestamp;
    public static final String NEXT_SCHEDULED_TIMESTAMP = "nextScheduledTimestamp";
    private int nextScheduledTimestamp;

    @BsonIgnore
    private String hexId;

    public AccessMatrixTaskInfo() {
    }

    public AccessMatrixTaskInfo(String endpointLogicalGroupName, int frequencyInSeconds, int lastCompletedTimestamp, int nextScheduledTimestamp) {
        this.endpointLogicalGroupName = endpointLogicalGroupName;
        this.frequencyInSeconds = frequencyInSeconds;
        this.lastCompletedTimestamp = lastCompletedTimestamp;
        this.nextScheduledTimestamp = nextScheduledTimestamp;
    }


    public int getFrequencyInSeconds() {
        return this.frequencyInSeconds;
    }

    public void setFrequencyInSeconds(int frequencyInSeconds) {
        this.frequencyInSeconds = frequencyInSeconds;
    }

    public int getLastCompletedTimestamp() {
        return this.lastCompletedTimestamp;
    }

    public void setLastCompletedTimestamp(int lastCompletedTimestamp) {
        this.lastCompletedTimestamp = lastCompletedTimestamp;
    }

    public int getNextScheduledTimestamp() {
        return this.nextScheduledTimestamp;
    }

    public void setNextScheduledTimestamp(int nextScheduledTimestamp) {
        this.nextScheduledTimestamp = nextScheduledTimestamp;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getHexId() {
        return this.id.toHexString();
    }

    public String getEndpointLogicalGroupName() {
        return endpointLogicalGroupName;
    }

    public void setEndpointLogicalGroupName(String endpointLogicalGroupName) {
        this.endpointLogicalGroupName = endpointLogicalGroupName;
    }

    @Override
    public String toString() {
        return "AccessMatrixTaskInfo{" +
                "id=" + id +
                ", endpointLogicalGroupName='" + endpointLogicalGroupName + '\'' +
                ", frequencyInSeconds=" + frequencyInSeconds +
                ", lastCompletedTimestamp=" + lastCompletedTimestamp +
                ", nextScheduledTimestamp=" + nextScheduledTimestamp +
                ", hexId='" + hexId + '\'' +
                '}';
    }
}
