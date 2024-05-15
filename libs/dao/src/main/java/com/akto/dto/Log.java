package com.akto.dto;

import com.mongodb.BasicDBObject;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

public class Log {

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
            "}";
    }

    public BasicDBObject toBasicDBObject(){
        return new BasicDBObject("_id", getId())
                .append("log", getLog())
                .append("key", getKey())
                .append("timestamp", getTimestamp());
    }

}
