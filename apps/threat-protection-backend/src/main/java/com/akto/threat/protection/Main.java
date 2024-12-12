package com.akto.threat.protection;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

import com.akto.DaoInit;
import com.akto.kafka.KafkaConfig;
import com.akto.kafka.KafkaConsumerConfig;
import com.akto.kafka.KafkaProducerConfig;
import com.akto.threat.protection.tasks.FlushMessagesToDB;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;

public class Main {
  public static void main(String[] args) throws Exception {
    String mongoURI = System.getenv("AKTO_MONGO_CONN");

    DaoInit.init(new ConnectionString(mongoURI));

    ConnectionString connectionString =
        new ConnectionString(System.getenv("AKTO_THREAT_PROTECTION_MONGO_CONN"));
    CodecRegistry pojoCodecRegistry =
        fromProviders(PojoCodecProvider.builder().automatic(true).build());
    CodecRegistry codecRegistry =
        fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), pojoCodecRegistry);
    MongoClientSettings clientSettings =
        MongoClientSettings.builder()
            .readPreference(ReadPreference.secondary())
            .writeConcern(WriteConcern.ACKNOWLEDGED)
            .applyConnectionString(connectionString)
            .codecRegistry(codecRegistry)
            .build();

    try (MongoClient threatProtectionMongo = MongoClients.create(clientSettings)) {
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
}
