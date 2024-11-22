package com.akto.suspect_data;

import com.akto.proto.threat_protection.consumer_service.v1.MaliciousEvent;

// Kafka Message Wrapper for suspect data
public class Message {
    private String accountId;
    private MaliciousEvent data;

    public Message() {
    }

    public Message(String accountId, MaliciousEvent data) {
        this.accountId = accountId;
        this.data = data;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public MaliciousEvent getData() {
        return data;
    }

    public void setData(MaliciousEvent data) {
        this.data = data;
    }
}
