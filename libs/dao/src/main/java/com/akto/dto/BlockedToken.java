package com.akto.dto;

public class BlockedToken {
    private String token;
    private int timestamp;
    public static final String TOKEN = "token";
    private String reason;
    public static final String REASON = "reason";

    public BlockedToken() {}

    public BlockedToken(String token, int timestamp, String reason) {
        this.token = token;
        this.timestamp = timestamp;
        this.reason = reason;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
