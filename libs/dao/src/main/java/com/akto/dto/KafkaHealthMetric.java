package com.akto.dto;

import org.bson.types.ObjectId;

import java.util.Objects;

public class KafkaHealthMetric {
    private ObjectId id;
    private String topicName;
    public static final String TOPIC_NAME = "topicName";
    private int partition;
    public static final String PARTITION = "partition";
    private long currentOffset;
    private long endOffset;
    private long lastUpdated;

    public KafkaHealthMetric(String topicName, int partition, long currentOffset, long endOffset, long lastUpdated) {
        this.topicName = topicName;
        this.partition = partition;
        this.currentOffset = currentOffset;
        this.endOffset = endOffset;
        this.lastUpdated = lastUpdated;
    }

    public KafkaHealthMetric() {
    }

    @Override
    public int hashCode() {
        return Objects.hash(topicName, partition);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof KafkaHealthMetric)) {
            return false;
        }
        KafkaHealthMetric kafkaHealthMetric = (KafkaHealthMetric) o;
        return topicName.equals(kafkaHealthMetric.topicName) && partition == kafkaHealthMetric.partition;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getTopicName() {
        return topicName;
    }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }

    public int getPartition() {
        return partition;
    }

    public void setPartition(int partition) {
        this.partition = partition;
    }

    public long getCurrentOffset() {
        return currentOffset;
    }

    public void setCurrentOffset(long currentOffset) {
        this.currentOffset = currentOffset;
    }

    public long getEndOffset() {
        return endOffset;
    }

    public void setEndOffset(long endOffset) {
        this.endOffset = endOffset;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
