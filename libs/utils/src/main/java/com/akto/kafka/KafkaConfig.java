package com.akto.kafka;

public class KafkaConfig {
  private final String bootstrapServers;
  private final String groupId;
  private final KafkaConsumerConfig consumerConfig;
  private final KafkaProducerConfig producerConfig;

  public static class Builder {
    private String bootstrapServers;
    private String groupId;
    private KafkaConsumerConfig consumerConfig;
    private KafkaProducerConfig producerConfig;

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

    public KafkaConfig build() {
      return new KafkaConfig(this);
    }
  }

  private KafkaConfig(Builder builder) {
    this.bootstrapServers = builder.bootstrapServers;
    this.groupId = builder.groupId;
    this.consumerConfig = builder.consumerConfig;
    this.producerConfig = builder.producerConfig;
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

  public static Builder newBuilder() {
    return new Builder();
  }
}
