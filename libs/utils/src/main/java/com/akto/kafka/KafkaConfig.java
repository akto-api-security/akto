package com.akto.kafka;

public class KafkaConfig {
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
}
