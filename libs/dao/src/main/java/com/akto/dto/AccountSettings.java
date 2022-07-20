package com.akto.dto;

import java.util.List;

public class AccountSettings {
    private int id;
    public static final String PRIVATE_CIDR_LIST = "privateCidrList";
    private List<String> privateCidrList;
    public static final String REDACT_PAYLOAD = "redactPayload";
    private boolean redactPayload;
    public static final String SAMPLE_DATA_COLLECTION_DROPPED = "sampleDataCollectionDropped";
    private boolean sampleDataCollectionDropped;
    public static final String SETUP_TYPE = "setupType";
    private SetupType setupType = SetupType.STAGING;

    public static final String CENTRAL_KAFKA_IP = "centralKafkaIp";
    private String centralKafkaIp;
    public static final String CENTRAL_KAFKA_TOPIC_NAME = "centralKafkaTopicName";
    private String centralKafkaTopicName;
    public static final String CENTRAL_KAFKA_BATCH_SIZE = "centralKafkaBatchSize";
    private int centralKafkaBatchSize;
    public static final String CENTRAL_KAFKA_LINGER_MS = "centralKafkaLingerMS";
    private int centralKafkaLingerMS;

    public AccountSettings() {
    }

    public AccountSettings(int id, List<String> privateCidrList, Boolean redactPayload, SetupType setupType) {
        this.id = id;
        this.privateCidrList = privateCidrList;
        this.redactPayload = redactPayload;
        this.setupType = setupType;
    }

    public enum SetupType {
        PROD, QA, STAGING, DEV
    }


    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public List<String> getPrivateCidrList() {
        return privateCidrList;
    }

    public void setPrivateCidrList(List<String> privateCidrList) {
        this.privateCidrList = privateCidrList;
    }

    public boolean isRedactPayload() {
        return redactPayload;
    }

    public boolean getRedactPayload() {
        return redactPayload;
    }

    public void setRedactPayload(boolean redactPayload) {
        this.redactPayload = redactPayload;
    }

    public boolean isSampleDataCollectionDropped() {
        return sampleDataCollectionDropped;
    }

    public void setSampleDataCollectionDropped(boolean sampleDataCollectionDropped) {
        this.sampleDataCollectionDropped = sampleDataCollectionDropped;
    }

    public SetupType getSetupType() {
        return setupType;
    }

    public void setSetupType(SetupType setupType) {
        this.setupType = setupType;
    }

    public String getCentralKafkaIp() {
        return centralKafkaIp;
    }

    public void setCentralKafkaIp(String centralKafkaIp) {
        this.centralKafkaIp = centralKafkaIp;
    }

    public String getCentralKafkaTopicName() {
        return centralKafkaTopicName;
    }

    public void setCentralKafkaTopicName(String centralKafkaTopicName) {
        this.centralKafkaTopicName = centralKafkaTopicName;
    }

    public int getCentralKafkaBatchSize() {
        return centralKafkaBatchSize;
    }

    public void setCentralKafkaBatchSize(int centralKafkaBatchSize) {
        this.centralKafkaBatchSize = centralKafkaBatchSize;
    }

    public int getCentralKafkaLingerMS() {
        return centralKafkaLingerMS;
    }

    public void setCentralKafkaLingerMS(int centralKafkaLingerMS) {
        this.centralKafkaLingerMS = centralKafkaLingerMS;
    }

    public static final int DEFAULT_CENTRAL_KAFKA_BATCH_SIZE = 999900;
    public static final int DEFAULT_CENTRAL_KAFKA_LINGER_MS = 60_000;
}
