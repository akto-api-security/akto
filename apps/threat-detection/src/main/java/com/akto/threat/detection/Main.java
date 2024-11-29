package com.akto.threat.detection;

import com.akto.DaoInit;
import com.akto.threat.detection.config.kafka.KafkaConfig;
import com.akto.threat.detection.config.kafka.KafkaConsumerConfig;
import com.akto.threat.detection.config.kafka.KafkaProducerConfig;
import com.akto.threat.detection.constants.KafkaTopic;
import com.akto.threat.detection.tasks.CleanupTask;
import com.akto.threat.detection.tasks.FlushSampleDataTask;
import com.akto.threat.detection.tasks.MaliciousTrafficDetectorTask;
import com.akto.threat.detection.tasks.SendAlertsToBackend;
import com.mongodb.ConnectionString;
import io.lettuce.core.RedisClient;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.flywaydb.core.Flyway;

public class Main {

  private static final String CONSUMER_GROUP_ID = "akto.threat_detection";

  public static void main(String[] args) throws Exception {
    runMigrations();

    DaoInit.init(new ConnectionString(System.getenv("AKTO_MONGO_CONN")));
    KafkaConfig trafficKafka = KafkaConfig.newBuilder()
        .setGroupId(CONSUMER_GROUP_ID)
        .setBootstrapServers(System.getenv("AKTO_TRAFFIC_KAFKA_BOOTSTRAP_SERVER"))
        .setConsumerConfig(
            KafkaConsumerConfig.newBuilder()
                .setMaxPollRecords(100)
                .setPollDurationMilli(100)
                .build())
        .setProducerConfig(
            KafkaProducerConfig.newBuilder().setBatchSize(100).setLingerMs(100).build())
        .build();

    KafkaConfig internalKafka = KafkaConfig.newBuilder()
        .setGroupId(CONSUMER_GROUP_ID)
        .setBootstrapServers(System.getenv("AKTO_INTERNAL_KAFKA_BOOTSTRAP_SERVER"))
        .setConsumerConfig(
            KafkaConsumerConfig.newBuilder()
                .setMaxPollRecords(100)
                .setPollDurationMilli(100)
                .build())
        .setProducerConfig(
            KafkaProducerConfig.newBuilder().setBatchSize(100).setLingerMs(100).build())
        .build();

    Connection postgres = createPostgresConnection();

    new MaliciousTrafficDetectorTask(trafficKafka, internalKafka, createRedisClient()).run();
    new FlushSampleDataTask(postgres, internalKafka, KafkaTopic.ThreatDetection.MALICIOUS_EVENTS).run();
    new SendAlertsToBackend(postgres, internalKafka, KafkaTopic.ThreatDetection.ALERTS).run();
    new CleanupTask(postgres).run();
  }

  public static RedisClient createRedisClient() {
    return RedisClient.create(System.getenv("AKTO_THREAT_DETECTION_REDIS_URI"));
  }

  public static Connection createPostgresConnection() throws SQLException {
    String url = System.getenv("AKTO_THREAT_DETECTION_POSTGRES");
    String user = System.getenv("AKTO_THREAT_DETECTION_POSTGRES_USER");
    String password = System.getenv("AKTO_THREAT_DETECTION_POSTGRES_PASSWORD");
    return DriverManager.getConnection(url, user, password);
  }

  public static void runMigrations() {
    String url = System.getenv("AKTO_THREAT_DETECTION_POSTGRES");
    String user = System.getenv("AKTO_THREAT_DETECTION_POSTGRES_USER");
    String password = System.getenv("AKTO_THREAT_DETECTION_POSTGRES_PASSWORD");
    Flyway flyway = Flyway
        .configure()
        .dataSource(url, user, password)
        .locations("classpath:db/migration")
        .schemas("flyway")
        .load();

    flyway.migrate();
  }
}
