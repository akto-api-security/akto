package com.akto.kafka;

import java.util.Properties;

public class KafkaConfig {
  // Kafka authentication configuration constants
  public static final String SECURITY_PROTOCOL = "security.protocol";
  public static final String SASL_MECHANISM = "sasl.mechanism";
  public static final String SASL_JAAS_CONFIG = "sasl.jaas.config";
  public static final String SECURITY_PROTOCOL_SASL_PLAINTEXT = "SASL_PLAINTEXT";
  public static final String SASL_MECHANISM_PLAIN = "PLAIN";

  private final String bootstrapServers;
  private final String groupId;
  private final KafkaConsumerConfig consumerConfig;
  private final KafkaProducerConfig producerConfig;
  private final Serializer keySerializer;
  private final Serializer valueSerializer;

  public static class Builder {
    private String bootstrapServers;
    private String groupId;
    private KafkaConsumerConfig consumerConfig;
    private KafkaProducerConfig producerConfig;
    private Serializer keySerializer;
    private Serializer valueSerializer;

    private Builder() {}

    public Builder setBootstrapServers(String bootstrapServers) {
      this.bootstrapServers = bootstrapServers;
      return this;
    }

    public Builder setGroupId(String groupId) {
      this.groupId = groupId;
      return this;
    }

    public Builder setConsumerConfig(KafkaConsumerConfig consumerConfig) {
      this.consumerConfig = consumerConfig;
      return this;
    }

    public Builder setProducerConfig(KafkaProducerConfig producerConfig) {
      this.producerConfig = producerConfig;
      return this;
    }

    public Builder setKeySerializer(Serializer keySerializer) {
      this.keySerializer = keySerializer;
      return this;
    }

    public Builder setValueSerializer(Serializer valueSerializer) {
      this.valueSerializer = valueSerializer;
      return this;
    }

    public KafkaConfig build() {
      return new KafkaConfig(this);
    }
  }

  private KafkaConfig(Builder builder) {
    this.bootstrapServers = builder.bootstrapServers;
    this.groupId = builder.groupId;
    this.consumerConfig = builder.consumerConfig;
    this.producerConfig = builder.producerConfig;

    this.keySerializer = builder.keySerializer == null ? Serializer.STRING : builder.keySerializer;
    this.valueSerializer =
        builder.valueSerializer == null ? Serializer.STRING : builder.valueSerializer;
  }

  public String getBootstrapServers() {
    return bootstrapServers;
  }

  public String getGroupId() {
    return groupId;
  }

  public KafkaConsumerConfig getConsumerConfig() {
    return consumerConfig;
  }

  public KafkaProducerConfig getProducerConfig() {
    return producerConfig;
  }

  public Serializer getKeySerializer() {
    return keySerializer;
  }

  public Serializer getValueSerializer() {
    return valueSerializer;
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  /**
   * Adds Kafka SASL authentication properties to the given Properties object.
   *
   * @param properties The Properties object to add authentication to
   * @param username   Kafka username
   * @param password   Kafka password
   */
  public static void addAuthenticationProperties(Properties properties, String username, String password) {
    properties.put(SECURITY_PROTOCOL, SECURITY_PROTOCOL_SASL_PLAINTEXT);
    properties.put(SASL_MECHANISM, SASL_MECHANISM_PLAIN);

    String jaasConfig = String.format(
        "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";",
        username, password);
    properties.put(SASL_JAAS_CONFIG, jaasConfig);
  }
}
