package com.akto.threat.backend;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

import com.akto.DaoInit;
import com.akto.kafka.KafkaConfig;
import com.akto.kafka.KafkaConsumerConfig;
import com.akto.kafka.KafkaProducerConfig;
import com.akto.kafka.Serializer;
import com.akto.log.LoggerMaker;
import com.akto.threat.backend.service.ApiDistributionDataService;
import com.akto.threat.backend.service.MaliciousEventService;
import com.akto.threat.backend.service.ThreatActorService;
import com.akto.threat.backend.service.ThreatApiService;
import com.akto.threat.backend.tasks.FlushMessagesToDB;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;

public class Main {

  private static final LoggerMaker logger = new LoggerMaker(Main.class);

  public static void main(String[] args) throws Exception {

    DaoInit.init(new ConnectionString(System.getenv("AKTO_MONGO_CONN")));

    ConnectionString connectionString =
        new ConnectionString(System.getenv("AKTO_THREAT_PROTECTION_MONGO_CONN"));
    System.out.println("connectionString: " + connectionString);
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

    MongoClient threatProtectionMongo = MongoClients.create(clientSettings);
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
            .setKeySerializer(Serializer.STRING)
            .setValueSerializer(Serializer.STRING)
            .build();


    new FlushMessagesToDB(internalKafkaConfig, threatProtectionMongo).run();

    MaliciousEventService maliciousEventService =
        new MaliciousEventService(internalKafkaConfig, threatProtectionMongo);

    ThreatActorService threatActorService = new ThreatActorService(threatProtectionMongo);
    ThreatApiService threatApiService = new ThreatApiService(threatProtectionMongo);
    ApiDistributionDataService apiDistributionDataService = new ApiDistributionDataService(threatProtectionMongo);

    new BackendVerticle(maliciousEventService, threatActorService, threatApiService, apiDistributionDataService).start();
  }

}
