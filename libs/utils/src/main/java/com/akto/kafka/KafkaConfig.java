package com.akto.kafka;

import java.util.Properties;

public class KafkaConfig {
  // Kafka authentication configuration constants
  public static final String SECURITY_PROTOCOL = "security.protocol";
  public static final String SASL_MECHANISM = "sasl.mechanism";
  public static final String SASL_JAAS_CONFIG = "sasl.jaas.config";
  public static final String SECURITY_PROTOCOL_SASL_PLAINTEXT = "SASL_PLAINTEXT";
  public static final String SASL_MECHANISM_PLAIN = "PLAIN";
  public static final String SASL_MECHANISM_SCRAM_SHA_256 = "SCRAM-SHA-256";
  public static final String SASL_MECHANISM_SCRAM_SHA_512 = "SCRAM-SHA-512";

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
    // Default to PLAIN for backward compatibility
    addAuthenticationProperties(properties, username, password, SASL_MECHANISM_PLAIN);
  }

  /**
   * Adds Kafka SASL authentication properties to the given Properties object with specified mechanism.
   *
   * @param properties   The Properties object to add authentication to
   * @param username     Kafka username
   * @param password     Kafka password
   * @param saslMechanism SASL mechanism (PLAIN, SCRAM-SHA-256, SCRAM-SHA-512)
   */
  public static void addAuthenticationProperties(Properties properties, String username, String password, String saslMechanism) {
    properties.put(SECURITY_PROTOCOL, SECURITY_PROTOCOL_SASL_PLAINTEXT);
    properties.put(SASL_MECHANISM, saslMechanism);

    // Select login module based on mechanism
    String loginModule;
    if (SASL_MECHANISM_PLAIN.equals(saslMechanism)) {
      loginModule = "org.apache.kafka.common.security.plain.PlainLoginModule";
    } else if (SASL_MECHANISM_SCRAM_SHA_256.equals(saslMechanism) ||
               SASL_MECHANISM_SCRAM_SHA_512.equals(saslMechanism)) {
      loginModule = "org.apache.kafka.common.security.scram.ScramLoginModule";
    } else {
      throw new IllegalArgumentException("Unsupported SASL mechanism: " + saslMechanism);
    }

    String jaasConfig = String.format(
        "%s required username=\"%s\" password=\"%s\";",
        loginModule, username, password);
    properties.put(SASL_JAAS_CONFIG, jaasConfig);
  }
}
