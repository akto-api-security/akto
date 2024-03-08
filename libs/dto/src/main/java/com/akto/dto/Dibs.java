package com.akto.dto;

import org.bson.codecs.pojo.annotations.BsonId;

public class Dibs {
    
    @BsonId
    private String id;
    
    private int startTs;
    private int expiryTs;
    private int freqInSeconds;
    private int lastPing;
    private String winner;


    public Dibs() {
    }

    public Dibs(String id, int startTs, int expiryTs, int freqInSeconds, int lastPing, String winner) {
        this.id = id;
        this.startTs = startTs;
        this.expiryTs = expiryTs;
        this.freqInSeconds = freqInSeconds;
        this.lastPing = lastPing;
        this.winner = winner;
    }

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getStartTs() {
        return this.startTs;
    }

    public void setStartTs(int startTs) {
        this.startTs = startTs;
    }

    public int getExpiryTs() {
        return this.expiryTs;
    }

    public void setExpiryTs(int expiryTs) {
        this.expiryTs = expiryTs;
    }

    public int getFreqInSeconds() {
        return this.freqInSeconds;
    }

    public void setFreqInSeconds(int freqInSeconds) {
        this.freqInSeconds = freqInSeconds;
    }

    public int getLastPing() {
        return this.lastPing;
    }

    public void setLastPing(int lastPing) {
        this.lastPing = lastPing;
    }

    public String getWinner() {
        return this.winner;
    }

    public void setWinner(String winner) {
        this.winner = winner;
    }

    @Override
    public String toString() {
        return "{" +
            " id='" + getId() + "'" +
            ", startTs='" + getStartTs() + "'" +
            ", expiryTs='" + getExpiryTs() + "'" +
            ", freqInSeconds='" + getFreqInSeconds() + "'" +
            ", lastPing='" + getLastPing() + "'" +
            ", winner='" + getWinner() + "'" +
            "}";
    }

}