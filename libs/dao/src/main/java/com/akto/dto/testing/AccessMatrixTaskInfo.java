package com.akto.dto.testing;

import java.util.List;

import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import com.akto.dto.ApiInfo.ApiInfoKey;

public class AccessMatrixTaskInfo {
 
    private ObjectId id;
    public static final String API_INFO_KEYS = "apiInfoKeys";
    private List<ApiInfoKey> apiInfoKeys;
    public static final String API_COLLECTION_ID = "apiCollectionId";
    private int apiCollectionId;
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

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getHexId() {
        return this.id.toHexString();
    }

    @Override
    public String toString() {
        return "{" +
            " apiInfoKeys='" + getApiInfoKeys() + "'" +
            ", apiCollectionId='" + getApiCollectionId() + "'" +
            "}";
    }

}
