package com.akto.threat.detection;

import com.akto.DaoInit;
import com.akto.kafka.KafkaConfig;
import com.akto.kafka.KafkaConsumerConfig;
import com.akto.kafka.KafkaProducerConfig;
import com.akto.kafka.Serializer;
import com.akto.threat.detection.client.IPLookupClient;
import com.akto.threat.detection.constants.KafkaTopic;
import com.akto.threat.detection.session_factory.SessionFactoryUtils;
import com.akto.threat.detection.tasks.CleanupTask;
import com.akto.threat.detection.tasks.FlushSampleDataTask;
import com.akto.threat.detection.tasks.MaliciousTrafficDetectorTask;
import com.akto.threat.detection.tasks.SendMaliciousEventsToBackend;
import com.mongodb.ConnectionString;
import io.lettuce.core.RedisClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import org.apache.commons.io.IOUtils;

import org.flywaydb.core.Flyway;
import org.hibernate.SessionFactory;

public class Main {

  private static final String CONSUMER_GROUP_ID = "akto.threat_detection";

  public static void main(String[] args) throws Exception {
    runMigrations();

    SessionFactory sessionFactory = SessionFactoryUtils.createFactory();

    // TODO: Remove this before merging. Will be using cyborg for fetching templates
    DaoInit.init(new ConnectionString(System.getenv("AKTO_MONGO_CONN")));
    KafkaConfig trafficKafka =
        KafkaConfig.newBuilder()
            .setGroupId(CONSUMER_GROUP_ID)
            .setBootstrapServers(System.getenv("AKTO_TRAFFIC_KAFKA_BOOTSTRAP_SERVER"))
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
    IPLookupClient ipLookupClient = new IPLookupClient(getMaxmindFile());

    new MaliciousTrafficDetectorTask(trafficKafka, internalKafka, localRedis, ipLookupClient).run();
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


  private static File getMaxmindFile() throws IOException {
    File maxmindTmpFile = File.createTempFile("tmp-geo-country", ".mmdb");
    maxmindTmpFile.deleteOnExit();

    try (FileOutputStream fos = new FileOutputStream(maxmindTmpFile)) {
      IOUtils.copy(
          Main.class.getClassLoader().getResourceAsStream("maxmind/Geo-Country.mmdb"), fos);
    }

    return maxmindTmpFile;
  }
}
