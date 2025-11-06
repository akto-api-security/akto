package com.akto.dto;

import com.mongodb.BasicDBObject;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

public class LogsEndpointShield {

    private ObjectId id;
    private String agentId;
    private String deviceId;
    private String key;
    private String level;
    private String log;
    private int timestamp;

    public static final String AGENT_ID = "agentId";
    public static final String DEVICE_ID = "deviceId";
    public static final String KEY = "key";
    public static final String LEVEL = "level";
    public static final String LOG = "log";
    public static final String TIMESTAMP = "timestamp";

    public LogsEndpointShield() {
    }

    public LogsEndpointShield(String agentId, String deviceId, String key, String level, String log, int timestamp) {
        this.agentId = agentId;
        this.deviceId = deviceId;
        this.key = key;
        this.level = level;
        this.log = log;
        this.timestamp = timestamp;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    @BsonIgnore
    private String hexId;
    public String getHexId() {
        return this.id != null ? this.id.toHexString() : null;
    }

    public void setHexId(String hexId) {
        this.hexId = hexId;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getLog() {
        return log;
    }

    public void setLog(String log) {
        this.log = log;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "{" +
            " id='" + getId() + "'" +
            ", agentId='" + getAgentId() + "'" +
            ", deviceId='" + getDeviceId() + "'" +
            ", key='" + getKey() + "'" +
            ", level='" + getLevel() + "'" +
            ", log='" + getLog() + "'" +
            ", timestamp='" + getTimestamp() + "'" +
            "}";
    }

    public BasicDBObject toBasicDBObject(){
        return new BasicDBObject("_id", getId())
                .append("agentId", getAgentId())
                .append("deviceId", getDeviceId())
                .append("key", getKey())
                .append("level", getLevel())
                .append("log", getLog())
                .append("timestamp", getTimestamp());
    }
}