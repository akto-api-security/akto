package com.akto.threat.detection;

import java.util.*;
import com.akto.suspect_data.FlushMessagesTask;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.DaoInit;
import com.akto.dao.context.Context;
import com.akto.dto.HttpResponseParams;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.metrics.AllMetrics;
import com.akto.runtime.utils.Utils;
import com.akto.traffic.KafkaRunner;
import com.mongodb.ConnectionString;

import io.lettuce.core.RedisClient;

import com.akto.filters.HttpCallFilter;
import com.akto.parsers.HttpCallParser;

public class Main {
    private static final LogDb module = LogDb.THREAT_DETECTION;
    private static final LoggerMaker loggerMaker = new LoggerMaker(Main.class, module);
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final int sync_threshold_time = 120;
    private static final int sync_threshold_count = 1000;
    private static long lastSyncOffset = 0;

    private static final Map<String, HttpCallFilter> httpCallFilterMap = new HashMap<>();

    private static final RedisClient redisClient = createRedisClient();

    public static void main(String[] args) {
        // We have a separate Mongo for storing threat detection data
        // Metadata is stored in the main Mongo, which we call using API
        // So we always need to enable hybrid mode for this module
        String mongoURI = System.getenv("AKTO_THREAT_DETECTION_MONGO_CONN");
        DaoInit.init(new ConnectionString(mongoURI));

        // Flush Messages task
        FlushMessagesTask.instance.init();

        String topicName = System.getenv("AKTO_KAFKA_TOPIC_NAME");
        if (topicName == null) {
            String defaultTopic = "akto.api.protection";
            loggerMaker.infoAndAddToDb(
                    String.format(
                            "Kafka topic is not defined, using default topic : %s", defaultTopic));
            topicName = defaultTopic;
        }

        KafkaRunner.consume(
                module,
                Collections.singletonList(topicName),
                records -> {
                    processRecords(records);
                    return null;
                });
    }

    public static void processRecords(ConsumerRecords<String, String> records) {
        long start = System.currentTimeMillis();

        // TODO: what happens if exception
        Map<String, List<HttpResponseParams>> responseParamsToAccountMap = new HashMap<>();
        for (ConsumerRecord<String, String> r : records) {
            HttpResponseParams httpResponseParams;
            try {
                Utils.printL(r.value());
                AllMetrics.instance.setRuntimeKafkaRecordCount(1);
                AllMetrics.instance.setRuntimeKafkaRecordSize(r.value().length());
                lastSyncOffset++;
                if (lastSyncOffset % 100 == 0) {
                    logger.info("Committing offset at position: " + lastSyncOffset);
                }
                httpResponseParams = HttpCallParser.parseKafkaMessage(r.value());
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(
                        e, "Error while parsing kafka message " + e, LogDb.RUNTIME);
                continue;
            }
            String accountId = httpResponseParams.getAccountId();
            if (!responseParamsToAccountMap.containsKey(accountId)) {
                responseParamsToAccountMap.put(accountId, new ArrayList<>());
            }
            responseParamsToAccountMap.get(accountId).add(httpResponseParams);
        }

        for (String accountId : responseParamsToAccountMap.keySet()) {
            int accountIdInt;
            try {
                accountIdInt = Integer.parseInt(accountId);
            } catch (Exception ignored) {
                loggerMaker.errorAndAddToDb("Account id not string", LogDb.RUNTIME);
                continue;
            }

            Context.accountId.set(accountIdInt);

            if (!httpCallFilterMap.containsKey(accountId)) {
                HttpCallFilter filter = new HttpCallFilter(redisClient, sync_threshold_count,
                        sync_threshold_time);
                httpCallFilterMap.put(accountId, filter);
                loggerMaker.infoAndAddToDb("New filter created for account: " + accountId);
            }

            HttpCallFilter filter = httpCallFilterMap.get(accountId);
            List<HttpResponseParams> accWiseResponse = responseParamsToAccountMap.get(accountId);
            filter.filterFunction(accWiseResponse);
        }

        AllMetrics.instance.setRuntimeProcessLatency(System.currentTimeMillis() - start);
    }

    public static RedisClient createRedisClient() {
        String host = System.getenv().getOrDefault("AKTO_THREAT_DETECTION_REDIS_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("AKTO_THREAT_DETECTION_REDIS_PORT", "6379"));
        int database = Integer.parseInt(System.getenv().getOrDefault("AKTO_THREAT_DETECTION_REDIS_DB", "0"));

        return RedisClient.create("redis://" + host + ":" + port + "/" + database);
    }
}
