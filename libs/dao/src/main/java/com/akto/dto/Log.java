package com.akto.dto;

import com.mongodb.BasicDBObject;
import lombok.Getter;
import lombok.Setter;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

public class Log {

    public enum ActivityType {
        TESTING_RUN_RESULT_SUMMARY_ACTIVITY
    }

    private ObjectId id;
    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }
    @BsonIgnore
    private String hexId;
    public String getHexId() {
        return this.id.toHexString();
    }

    public void setHexId(String hexId) {
        this.hexId = hexId;
    }
    private String log;
    private String key;

    public static final String TIMESTAMP = "timestamp";
    private int timestamp;

    public static final String ACTIVITY_ID = "activityId";
    public static final String ACTIVITY_TYPE = "activityType";

    @Getter
    @Setter
    private String activityId; // activity id for the log eg test run result summary id

    @Getter
    @Setter
    private ActivityType activityType; // activity type for the log eg TESTING_RUN_RESULT_SUMMARY_ACTIVITY

    public Log() {
    }

    public Log(String log, String key, int timestamp) {
        this.log = log;
        this.key = key;
        this.timestamp = timestamp;
    }

    public String getLog() {
        return this.log;
    }

    public void setLog(String log) {
        this.log = log;
    }

    public String getKey() {
        return this.key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public int getTimestamp() {
        return this.timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "{" +
            " log='" + getLog() + "'" +
            ", key='" + getKey() + "'" +
            ", timestamp='" + getTimestamp() + "'" +
            ", activityId='" + getActivityId() + "'" +
            ", activityType='" + getActivityType() + "'" +
            "}";
    }

    public BasicDBObject toBasicDBObject(){
        return new BasicDBObject("_id", getId())
                .append("log", getLog())
                .append("key", getKey())
                .append("timestamp", getTimestamp())
                .append(ACTIVITY_ID, getActivityId())
                .append(ACTIVITY_TYPE, getActivityType());
    }

}
