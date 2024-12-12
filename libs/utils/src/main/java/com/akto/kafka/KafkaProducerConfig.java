package com.akto.kafka;

public class KafkaProducerConfig {
  private final int lingerMs;
  private final int batchSize;

  public static class Builder {
    private int lingerMs;
    private int batchSize;

    public Builder() {}

    public Builder setLingerMs(int lingerMs) {
      this.lingerMs = lingerMs;
      return this;
    }

    public Builder setBatchSize(int batchSize) {
      this.batchSize = batchSize;
      return this;
    }

    public KafkaProducerConfig build() {
      return new KafkaProducerConfig(this);
    }
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public int getLingerMs() {
    return lingerMs;
  }

  public int getBatchSize() {
    return batchSize;
  }

  public KafkaProducerConfig(Builder builder) {
    this.lingerMs = builder.lingerMs;
    this.batchSize = builder.batchSize;
  }
}
