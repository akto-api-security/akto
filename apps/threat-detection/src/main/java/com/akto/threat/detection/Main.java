package com.akto.threat.detection;

import com.akto.DaoInit;
import com.akto.threat.detection.config.kafka.KafkaConfig;
import com.akto.threat.detection.config.kafka.KafkaConsumerConfig;
import com.akto.threat.detection.config.kafka.KafkaProducerConfig;
import com.akto.threat.detection.tasks.FlushSampleDataTask;
import com.akto.threat.detection.tasks.MaliciousTrafficDetectorTask;
import com.mongodb.ConnectionString;
import io.lettuce.core.RedisClient;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Main {

  public static void main(String[] args) throws Exception {
    DaoInit.init(new ConnectionString(System.getenv("AKTO_MONGO_CONN")));
    KafkaConfig trafficKafka =
        KafkaConfig.newBuilder()
            .setGroupId("akto.threat.detection")
            .setBootstrapServers("localhost:29092")
            .setConsumerConfig(
                KafkaConsumerConfig.newBuilder()
                    .setMaxPollRecords(100)
                    .setPollDurationMilli(100)
                    .build())
            .setProducerConfig(
                KafkaProducerConfig.newBuilder().setBatchSize(100).setLingerMs(100).build())
            .build();

    Connection postgres = createPostgresConnection();

    new MaliciousTrafficDetectorTask(trafficKafka, createRedisClient()).run();
    new FlushSampleDataTask(postgres, trafficKafka, "akto,malicious").run();
  }

  public static RedisClient createRedisClient() {
    return RedisClient.create(System.getenv("AKTO_THREAT_DETECTION_REDIS_URI"));
  }

  public static Connection createPostgresConnection() throws SQLException {
    String url = System.getenv("AKTO_THREAT_DETECTION_POSTGRES");
    return DriverManager.getConnection(url);
  }
}
