package com.akto.threat.detection.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import java.util.Optional;

// Kafka Message Wrapper for suspect data
public class MessageEnvelope {
  private String accountId;
  private String data;
  private String actor;

  private static final ObjectMapper objectMapper = new ObjectMapper();

  public MessageEnvelope() {}

  public MessageEnvelope(String accountId, String actor, String data) {
    this.accountId = accountId;
    this.actor = actor;
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

  public static Optional<MessageEnvelope> unmarshal(String message) {
    try {
      return Optional.ofNullable(objectMapper.readValue(message, MessageEnvelope.class));
    } catch (Exception e) {
      e.printStackTrace();
    }

    return Optional.empty();
  }

  public static MessageEnvelope generateEnvelope(String accountId, String actor, Message msg)
      throws InvalidProtocolBufferException {
    String data = JsonFormat.printer().print(msg);
    return new MessageEnvelope(accountId, actor, data);
  }

  public String getActor() {
    return actor;
  }

  public void setActor(String actor) {
    this.actor = actor;
  }

  public static ObjectMapper getObjectmapper() {
    return objectMapper;
  }
}
