package com.akto.dto.settings;

import org.bson.codecs.pojo.annotations.BsonId;

public class DataControlSettings {

    @BsonId
    int id;

    public static final String KAFKA_CONSUMER_RECORDS_PER_MIN = "kafkaConsumerRecordsPerMin";
    public static final int DEFAULT_KAFKA_CONSUMER_RECORDS_PER_MIN = -1;
    int kafkaConsumerRecordsPerMin;

    public static final String DISCARD_NEW_API = "discardNewApi";
    boolean discardNewApi;

    public static final String DISCARD_OLD_API = "discardOldApi";
    boolean discardOldApi;

    public static final String POSTGRES_COMMAND = "postgresCommand";
    String postgresCommand;

    String postgresResult;
    String oldPostgresCommand;
    public DataControlSettings() {}

    public DataControlSettings(int id, int kafkaConsumerRecordsPerMin, boolean discardNewApi, boolean discardOldApi, String postgresCommand) {
        this.id = id;
        this.kafkaConsumerRecordsPerMin = kafkaConsumerRecordsPerMin;
        this.discardNewApi = discardNewApi;
        this.discardOldApi = discardOldApi;
        this.postgresCommand = postgresCommand;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getKafkaConsumerRecordsPerMin() {
        return kafkaConsumerRecordsPerMin;
    }

    public void setKafkaConsumerRecordsPerMin(int kafkaConsumerRecordsPerMin) {
        this.kafkaConsumerRecordsPerMin = kafkaConsumerRecordsPerMin;
    }

    public boolean isDiscardNewApi() {
        return discardNewApi;
    }

    public void setDiscardNewApi(boolean discardNewApi) {
        this.discardNewApi = discardNewApi;
    }

    public boolean isDiscardOldApi() {
        return discardOldApi;
    }

    public void setDiscardOldApi(boolean discardOldApi) {
        this.discardOldApi = discardOldApi;
    }

    public String getPostgresCommand() {
        return postgresCommand;
    }

    public void setPostgresCommand(String postgresCommand) {
        this.postgresCommand = postgresCommand;
    }

    public String getPostgresResult() {
        return postgresResult;
    }

    public void setPostgresResult(String postgresResult) {
        this.postgresResult = postgresResult;
    }

    public String getOldPostgresCommand() {
        return oldPostgresCommand;
    }

    public void setOldPostgresCommand(String oldPostgresCommand) {
        this.oldPostgresCommand = oldPostgresCommand;
    }
}
