package com.akto.kafka;

public class KafkaConsumerConfig {
  private final int maxPollRecords;
  private final int pollDurationMilli;
  private final int fetchMaxBytes;

  public static class Builder {
    private int maxPollRecords;
    private int pollDurationMilli;
    private int fetchMaxBytes = 0;

    private Builder() {}

    public Builder setMaxPollRecords(int maxPollRecords) {
      this.maxPollRecords = maxPollRecords;
      return this;
    }

    public Builder setPollDurationMilli(int pollDurationMilli) {
      this.pollDurationMilli = pollDurationMilli;
      return this;
    }

    public Builder setFetchMaxBytes(int fetchMaxBytes) {
      this.fetchMaxBytes = fetchMaxBytes;
      return this;
    }

    public KafkaConsumerConfig build() {
      return new KafkaConsumerConfig(this);
    }
  }

  public KafkaConsumerConfig(Builder builder) {
    this.maxPollRecords = builder.maxPollRecords;
    this.pollDurationMilli = builder.pollDurationMilli;
    this.fetchMaxBytes = builder.fetchMaxBytes;
  }

  public int getMaxPollRecords() {
    return maxPollRecords;
  }

  public int getPollDurationMilli() {
    return pollDurationMilli;
  }

  public int getFetchMaxBytes() {
    return fetchMaxBytes;
  }

  public static Builder newBuilder() {
    return new Builder();
  }
}
