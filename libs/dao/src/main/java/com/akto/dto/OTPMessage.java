package com.akto.dto;

public class OTPMessage {

    private int id;
    private String from;
    private String message;
    private int timestamp;

    public OTPMessage(int id, String from, String message, int timestamp) {
        this.id = id;
        this.from = from;
        this.message = message;
        this.timestamp = timestamp;
    }

    public OTPMessage() { }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }
}

