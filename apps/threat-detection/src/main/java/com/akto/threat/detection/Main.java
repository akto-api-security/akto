package com.akto.threat.detection;

import com.akto.DaoInit;
import com.akto.kafka.KafkaConfig;
import com.akto.kafka.KafkaConsumerConfig;
import com.akto.kafka.KafkaProducerConfig;
import com.akto.kafka.Serializer;
import com.akto.log.LoggerMaker;
import com.akto.threat.detection.constants.KafkaTopic;
import com.akto.threat.detection.session_factory.SessionFactoryUtils;
import com.akto.threat.detection.tasks.CleanupTask;
import com.akto.threat.detection.tasks.FlushSampleDataTask;
import com.akto.threat.detection.tasks.MaliciousTrafficDetectorTask;
import com.akto.threat.detection.tasks.SendMaliciousEventsToBackend;
import com.mongodb.ConnectionString;
import io.lettuce.core.RedisClient;
import org.flywaydb.core.Flyway;
import org.hibernate.SessionFactory;

public class Main {

  private static final String CONSUMER_GROUP_ID = "akto.threat_detection";
  private static final LoggerMaker logger = new LoggerMaker(Main.class);

  public static void main(String[] args) throws Exception {
    runMigrations();

    SessionFactory sessionFactory = SessionFactoryUtils.createFactory();

    KafkaConfig trafficKafka =
        KafkaConfig.newBuilder()
            .setGroupId(CONSUMER_GROUP_ID)
            .setBootstrapServers(System.getenv("AKTO_TRAFFIC_KAFKA_BOOTSTRAP_SERVER"))
            .setConsumerConfig(
                KafkaConsumerConfig.newBuilder()
                    .setMaxPollRecords(500)
                    .setPollDurationMilli(100)
                    .build())
            .setProducerConfig(
                KafkaProducerConfig.newBuilder().setBatchSize(100).setLingerMs(100).build())
            .setKeySerializer(Serializer.STRING)
            .setValueSerializer(Serializer.BYTE_ARRAY)
            .build();

    KafkaConfig internalKafka =
        KafkaConfig.newBuilder()
            .setGroupId(CONSUMER_GROUP_ID)
            .setBootstrapServers(System.getenv("AKTO_INTERNAL_KAFKA_BOOTSTRAP_SERVER"))
            .setConsumerConfig(
                KafkaConsumerConfig.newBuilder()
                    .setMaxPollRecords(100)
                    .setPollDurationMilli(100)
                    .build())
            .setProducerConfig(
                KafkaProducerConfig.newBuilder().setBatchSize(100).setLingerMs(100).build())
            .setKeySerializer(Serializer.STRING)
            .setValueSerializer(Serializer.BYTE_ARRAY)
            .build();

    RedisClient localRedis = createLocalRedisClient();

    new MaliciousTrafficDetectorTask(trafficKafka, internalKafka, localRedis).run();
    new FlushSampleDataTask(
            sessionFactory, internalKafka, KafkaTopic.ThreatDetection.MALICIOUS_EVENTS)
        .run();
    new SendMaliciousEventsToBackend(
            sessionFactory, internalKafka, KafkaTopic.ThreatDetection.ALERTS)
        .run();
    new CleanupTask(sessionFactory).run();
  }

  public static RedisClient createLocalRedisClient() {
    return RedisClient.create(System.getenv("AKTO_THREAT_DETECTION_LOCAL_REDIS_URI"));
  }

  public static void runMigrations() {
    String url = System.getenv("AKTO_THREAT_DETECTION_POSTGRES");
    String user = System.getenv("AKTO_THREAT_DETECTION_POSTGRES_USER");
    String password = System.getenv("AKTO_THREAT_DETECTION_POSTGRES_PASSWORD");
    Flyway flyway =
        Flyway.configure()
            .dataSource(url, user, password)
            .locations("classpath:db/migration")
            .schemas("flyway")
            .load();

    flyway.migrate();
  }

}
