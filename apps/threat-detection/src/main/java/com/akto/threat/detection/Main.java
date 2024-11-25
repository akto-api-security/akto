package com.akto.threat.detection;

import com.akto.threat.detection.config.kafka.KafkaConfig;
import com.akto.threat.detection.config.kafka.KafkaConsumerConfig;
import com.akto.threat.detection.config.kafka.KafkaProducerConfig;
import com.akto.threat.detection.tasks.MaliciousTrafficDetectorTask;
import io.lettuce.core.RedisClient;

public class Main {

  public static void main(String[] args) {
    new MaliciousTrafficDetectorTask(
            KafkaConfig.newBuilder()
                .setTopic("akto.api.logs")
                .setGroupId("akto.threat.detection")
                .setBootstrapServers("localhost:29092")
                .setConsumerConfig(
                    KafkaConsumerConfig.newBuilder()
                        .setMaxPollRecords(100)
                        .setPollDurationMilli(100)
                        .build())
                .setProducerConfig(
                    KafkaProducerConfig.newBuilder().setBatchSize(100).setLingerMs(100).build())
                .build(),
            createRedisClient())
        .run();
  }

  public static RedisClient createRedisClient() {
    return RedisClient.create(System.getenv("AKTO_THREAT_DETECTION_REDIS_URI"));
  }
}
