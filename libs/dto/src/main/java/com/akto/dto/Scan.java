package com.akto.dto;

import org.bson.codecs.pojo.annotations.BsonId;

import java.util.List;

public class Scan {

    @BsonId
    private int id;

    private int testEvnSettingsId;

    private int startTimestamp;
    private int endTimestamp;

    private List<Attempt> attempts;

    public Scan() {
    }

    public Scan(int id, int testEvnSettingsId, int startTimestamp, int endTimestamp, List<Attempt> attempts) {
        this.id = id;
        this.testEvnSettingsId = testEvnSettingsId;
        this.startTimestamp = startTimestamp;
        this.endTimestamp = endTimestamp;
        this.attempts = attempts;
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getTestEvnSettingsId() {
        return this.testEvnSettingsId;
    }

    public void setTestEvnSettingsId(int testEvnSettingsId) {
        this.testEvnSettingsId = testEvnSettingsId;
    }

    public int getStartTimestamp() {
        return this.startTimestamp;
    }

    public void setStartTimestamp(int startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public int getEndTimestamp() {
        return this.endTimestamp;
    }

    public void setEndTimestamp(int endTimestamp) {
        this.endTimestamp = endTimestamp;
    }

    public List<Attempt> getAttempts() {
        return this.attempts;
    }

    public void setAttempts(List<Attempt> attempts) {
        this.attempts = attempts;
    }

    @Override
    public String toString() {
        return "{" +
            " id='" + getId() + "'" +
            ", testEvnSettingsId='" + getTestEvnSettingsId() + "'" +
            ", startTimestamp='" + getStartTimestamp() + "'" +
            ", endTimestamp='" + getEndTimestamp() + "'" +
            ", attempts='" + getAttempts() + "'" +
            "}";
    }
}
