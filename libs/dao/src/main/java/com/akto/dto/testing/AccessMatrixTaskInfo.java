package com.akto.dto.testing;

import java.util.List;

import com.akto.dto.ApiInfo.ApiInfoKey;

public class AccessMatrixTaskInfo {
 
    private List<ApiInfoKey> apiInfoKeys;
    private int apiCollectionId;
    private int frequencyInSeconds;
    private int lastCompletedTimestamp;

    private int nextScheduledTimestamp;
    public final static String NEXT_SCHEDULED_TIMESTAMP = "nextScheduledTimestamp";

    public AccessMatrixTaskInfo() {
    }

    public AccessMatrixTaskInfo(List<ApiInfoKey> apiInfoKeys, int apiCollectionId, int frequencyInSeconds, int lastCompletedTimestamp, int nextScheduledTimestamp) {
        this.apiInfoKeys = apiInfoKeys;
        this.apiCollectionId = apiCollectionId;
        this.frequencyInSeconds = frequencyInSeconds;
        this.lastCompletedTimestamp = lastCompletedTimestamp;
        this.nextScheduledTimestamp = nextScheduledTimestamp;
    }

    public List<ApiInfoKey> getApiInfoKeys() {
        return this.apiInfoKeys;
    }

    public void setApiInfoKeys(List<ApiInfoKey> apiInfoKeys) {
        this.apiInfoKeys = apiInfoKeys;
    }

    public int getApiCollectionId() {
        return this.apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
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

    @Override
    public String toString() {
        return "{" +
            " apiInfoKeys='" + getApiInfoKeys() + "'" +
            ", apiCollectionId='" + getApiCollectionId() + "'" +
            "}";
    }

}
