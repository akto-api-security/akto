package com.akto.threat.protection;

import com.akto.DaoInit;
import com.akto.kafka.KafkaConfig;
import com.akto.kafka.KafkaConsumerConfig;
import com.akto.kafka.KafkaProducerConfig;
import com.akto.threat.protection.tasks.FlushMessagesToDB;
import com.akto.threat.protection.utils.KafkaUtils;
import com.mongodb.ConnectionString;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;

public class Main {
  public static void main(String[] args) throws Exception {
    String mongoURI = System.getenv("AKTO_MONGO_CONN");

    DaoInit.init(new ConnectionString(mongoURI));

    MongoClient threatProtectionMongo =
        DaoInit.createMongoClient(
            new ConnectionString(System.getenv("AKTO_THREAT_PROTECTION_MONGO_CONN")),
            ReadPreference.secondary(),
            WriteConcern.ACKNOWLEDGED);

    KafkaConfig internalKafkaConfig =
        KafkaConfig.newBuilder()
            .setBootstrapServers(System.getenv("THREAT_EVENTS_KAFKA_BROKER_URL"))
            .setGroupId("akto.threat_protection.flush_db")
            .setConsumerConfig(
                KafkaConsumerConfig.newBuilder()
                    .setMaxPollRecords(100)
                    .setPollDurationMilli(100)
                    .build())
            .setProducerConfig(
                KafkaProducerConfig.newBuilder().setBatchSize(100).setLingerMs(1000).build())
            .build();

    new FlushMessagesToDB(internalKafkaConfig, threatProtectionMongo).run();

    int port =
        Integer.parseInt(
            System.getenv().getOrDefault("AKTO_THREAT_PROTECTION_BACKEND_PORT", "8980"));
    BackendServer server = new BackendServer(port, threatProtectionMongo, internalKafkaConfig);
    server.start();
    server.blockUntilShutdown();
  }
}
