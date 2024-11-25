package com.akto.threat.detection.dto;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;

// Kafka Message Wrapper for suspect data
public class MaliciousMessageEnvelope {
  private String accountId;
  private String data;

  private static final ObjectMapper objectMapper = new ObjectMapper();

  public MaliciousMessageEnvelope() {}

  public MaliciousMessageEnvelope(String accountId, String data) {
    this.accountId = accountId;
    this.data = data;
  }

  public String getAccountId() {
    return accountId;
  }

  public void setAccountId(String accountId) {
    this.accountId = accountId;
  }

  public String getData() {
    return data;
  }

  public void setData(String data) {
    this.data = data;
  }

  public Optional<String> marshal() {
    try {
      return Optional.ofNullable(objectMapper.writeValueAsString(this));
    } catch (Exception e) {
      e.printStackTrace();
    }

    return Optional.empty();
  }

  public static Optional<MaliciousMessageEnvelope> unmarshal(String message) {
    try {
      return Optional.ofNullable(objectMapper.readValue(message, MaliciousMessageEnvelope.class));
    } catch (Exception e) {
      e.printStackTrace();
    }

    return Optional.empty();
  }
}