package com.akto.threat.detection;

import com.akto.DaoInit;
import com.akto.RuntimeMode;
import com.akto.dao.context.Context;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.kafka.KafkaConfig;
import com.akto.kafka.KafkaConsumerConfig;
import com.akto.kafka.KafkaProducerConfig;
import com.akto.kafka.Serializer;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.metrics.ModuleInfoWorker;
import com.akto.threat.detection.constants.KafkaTopic;
import com.akto.threat.detection.crons.ApiCountInfoRelayCron;
import com.akto.threat.detection.ip_api_counter.CmsCounterLayer;
import com.akto.threat.detection.ip_api_counter.DistributionCalculator;
import com.akto.threat.detection.ip_api_counter.DistributionDataForwardLayer;
import com.akto.threat.detection.tasks.MaliciousTrafficDetectorTask;
import com.akto.threat.detection.tasks.SendMaliciousEventsToBackend;
import com.akto.threat.detection.utils.Utils;
import com.mongodb.ConnectionString;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;

public class Main {

  private static final String CONSUMER_GROUP_ID = "akto.threat_detection";
  private static final LoggerMaker logger = new LoggerMaker(Main.class, LogDb.THREAT_DETECTION);
  private static boolean aggregationRulesEnabled = System.getenv().getOrDefault("AGGREGATION_RULES_ENABLED", "true").equals("true");

  private static final DataActor dataActor = DataActorFactory.fetchInstance();

  public static void main(String[] args) throws Exception {
    
    RedisClient localRedis = null;

    logger.warnAndAddToDb("aggregation rules enabled " + aggregationRulesEnabled);
    ModuleInfoWorker.init(ModuleInfo.ModuleType.THREAT_DETECTION, dataActor);

    boolean isHybridDeployment = RuntimeMode.isHybridDeployment();
    if (!isHybridDeployment) {
        DaoInit.init(new ConnectionString(System.getenv("AKTO_MONGO_CONN")));
    }

    if (aggregationRulesEnabled) {
        localRedis = createLocalRedisClient();
        if (localRedis != null) {
            triggerApiInfoRelayCron(localRedis);
        }
    }

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


    CmsCounterLayer.initialize(localRedis);
    DistributionCalculator distributionCalculator = new DistributionCalculator();
    DistributionDataForwardLayer distributionDataForwardLayer = new DistributionDataForwardLayer(localRedis, distributionCalculator);

    boolean apiDistributionEnabled = Utils.apiDistributionEnabled(localRedis != null, System.getenv().getOrDefault("API_DISTRIBUTION_ENABLED", "true").equals("true"));

    triggerDistributionDataForwardCron(apiDistributionEnabled, distributionDataForwardLayer);

    new MaliciousTrafficDetectorTask(trafficKafka, internalKafka, localRedis, distributionCalculator, apiDistributionEnabled).run();

    new SendMaliciousEventsToBackend(internalKafka, KafkaTopic.ThreatDetection.ALERTS).run();

  }

  public static void triggerApiInfoRelayCron(RedisClient localRedis) {
    if (localRedis == null) {
        return;
    }
    ApiCountInfoRelayCron apiCountInfoRelayCron = new ApiCountInfoRelayCron(localRedis);
    try {
        logger.info("Scheduling relayApiCountInfoCron at " + Context.now());
        apiCountInfoRelayCron.relayApiCountInfo();
    } catch (Exception e) {
        logger.error("Error scheduling relayApiCountInfoCron : {} ", e);
    }
  }

  public static void triggerDistributionDataForwardCron(boolean apiDistributionEnabled, DistributionDataForwardLayer distributionDataForwardLayer) {
    if (!apiDistributionEnabled) {
      return;
    }
    try {
        distributionDataForwardLayer.sendLastFiveMinuteDistributionData();
    } catch (Exception e) {
        logger.error("Error scheduling relayApiCountInfoCron : {} ", e);
    }
  }

  public static RedisClient createLocalRedisClient() {
    RedisClient redisClient = RedisClient.create(System.getenv("AKTO_THREAT_DETECTION_LOCAL_REDIS_URI"));
    try {
      logger.infoAndAddToDb("Connecting to local redis");
      StatefulRedisConnection<String, String> connection = redisClient.connect();
      connection.sync().set("test", "test");
      connection.sync().get("test");
      connection.close();
    } catch (Exception e) {
      logger.errorAndAddToDb("Error connecting to local redis: " + e.getMessage());
      return null;
    }
    logger.infoAndAddToDb("Connected to local redis");
    return redisClient;
  }

}
