package com.akto.suspect_data;

import com.akto.dto.threat_detection.SampleRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;

// Kafka Message Wrapper for suspect data
public class Message {
    private String accountId;
    private SampleRequest data;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public Message() {
    }

    public Message(String accountId, SampleRequest data) {
        this.accountId = accountId;
        this.data = data;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public SampleRequest getData() {
        return data;
    }

    public void setData(SampleRequest data) {
        this.data = data;
    }

    public static Optional<String> marshall(Message m) {
        try {
            return Optional.of(objectMapper.writeValueAsString(m));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<String> marshall() {
        return marshall(this);
    }

    public static Optional<Message> unmarshall(String s) {
        try {
            return Optional.of(objectMapper.readValue(s, Message.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
