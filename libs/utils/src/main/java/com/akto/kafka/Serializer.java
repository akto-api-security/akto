package com.akto.kafka;

public enum Serializer {
  STRING(
      "org.apache.kafka.common.serialization.StringSerializer",
      "org.apache.kafka.common.serialization.StringDeserializer"),
  BYTE_ARRAY(
      "org.apache.kafka.common.serialization.ByteArraySerializer",
      "org.apache.kafka.common.serialization.ByteArrayDeserializer");

  private final String serializer;
  private final String deserializer;

  Serializer(String serializer, String deSerializer) {
    this.serializer = serializer;
    this.deserializer = deSerializer;
  }

  public String getDeserializer() {
    return deserializer;
  }

  public String getSerializer() {
    return serializer;
  }
}
