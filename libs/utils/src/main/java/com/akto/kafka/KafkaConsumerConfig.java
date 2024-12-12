package com.akto.kafka;

public class KafkaConsumerConfig {
  private final int maxPollRecords;
  private final int pollDurationMilli;

  public static class Builder {
    private int maxPollRecords;
    private int pollDurationMilli;

    private Builder() {}

    public Builder setMaxPollRecords(int maxPollRecords) {
      this.maxPollRecords = maxPollRecords;
      return this;
    }

    public Builder setPollDurationMilli(int pollDurationMilli) {
      this.pollDurationMilli = pollDurationMilli;
      return this;
    }

    public KafkaConsumerConfig build() {
      return new KafkaConsumerConfig(this);
    }
  }

  public KafkaConsumerConfig(Builder builder) {
    this.maxPollRecords = builder.maxPollRecords;
    this.pollDurationMilli = builder.pollDurationMilli;
  }

  public int getMaxPollRecords() {
    return maxPollRecords;
  }

  public int getPollDurationMilli() {
    return pollDurationMilli;
  }

  public static Builder newBuilder() {
    return new Builder();
  }
}
