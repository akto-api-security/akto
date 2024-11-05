package com.akto.threat.detection.properties;

import com.akto.log.LoggerMaker;

public class KafkaProperties {

    private final String topicName;

    private final String brokerUrl;

    private final String groupId;

    private final int maxPollRecords;

    private static final LoggerMaker loggerMaker =
            new LoggerMaker(KafkaProperties.class, LoggerMaker.LogDb.THREAT_DETECTION);

    KafkaProperties(String topicName, String brokerUrl, String groupId, int maxPollRecords) {
        this.topicName = topicName;
        this.brokerUrl = brokerUrl;
        this.groupId = groupId;
        this.maxPollRecords = maxPollRecords;
    }

    public String getTopicName() {
        return topicName;
    }

    public String getBrokerUrl() {
        return brokerUrl;
    }

    public String getGroupId() {
        return groupId;
    }

    public int getMaxPollRecords() {
        return maxPollRecords;
    }

    public static KafkaProperties generate() {
        String topicName = System.getenv("AKTO_KAFKA_TOPIC_NAME");
        if (topicName == null) {
            String defaultTopic = "akto.api.protection";
            loggerMaker.infoAndAddToDb(
                    String.format(
                            "Kafka topic is not defined, using default topic : %s", defaultTopic));
            topicName = defaultTopic;
        }

        String kafkaBrokerUrl = "kafka1:19092";
        String isKubernetes = System.getenv("IS_KUBERNETES");
        if (isKubernetes != null && isKubernetes.equalsIgnoreCase("true")) {
            loggerMaker.infoAndAddToDb("is_kubernetes: true");
            kafkaBrokerUrl = "127.0.0.1:29092";
        }
        String groupId = System.getenv("AKTO_KAFKA_GROUP_ID_CONFIG");
        int maxPollRecords =
                Integer.parseInt(
                        System.getenv().getOrDefault("AKTO_KAFKA_MAX_POLL_RECORDS_CONFIG", "100"));

        return new KafkaProperties(topicName, kafkaBrokerUrl, groupId, maxPollRecords);
    }
}
