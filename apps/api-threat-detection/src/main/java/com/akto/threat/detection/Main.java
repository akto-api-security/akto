package com.akto.threat.detection;

import java.util.*;

import com.akto.threat.detection.properties.KafkaProperties;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.dao.context.Context;
import com.akto.dto.HttpResponseParams;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.metrics.AllMetrics;
import com.akto.runtime.utils.Utils;
import com.akto.traffic.KafkaRunner;

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

    public static void main(String[] args) {

        KafkaProperties kafkaProperties = KafkaProperties.generate();

        Properties properties =
                Utils.configProperties(
                        kafkaProperties.getBrokerUrl(),
                        kafkaProperties.getGroupId(),
                        kafkaProperties.getMaxPollRecords());

        Consumer<String, String> kafkaConsumer = new KafkaConsumer<>(properties);

        KafkaRunner.init(
                kafkaConsumer,
                module,
                Collections.singletonList(kafkaProperties.getTopicName()),
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
                    logger.info("Committing offset at position: {}", lastSyncOffset);
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
                HttpCallFilter filter =
                        new HttpCallFilter(
                                createRedisClient(), sync_threshold_count, sync_threshold_time);
                httpCallFilterMap.put(accountId, filter);
                loggerMaker.infoAndAddToDb("New filter created for account: " + accountId);
            }

            HttpCallFilter filter = httpCallFilterMap.get(accountId);
            List<HttpResponseParams> accWiseResponse = responseParamsToAccountMap.get(accountId);
            filter.filterFunction(accWiseResponse);
        }

        AllMetrics.instance.setRuntimeProcessLatency(System.currentTimeMillis() - start);
    }

    private static RedisClient createRedisClient() {
        String redisHost = System.getenv("AKTO_REDIS_HOST");
        if (redisHost == null) {
            redisHost = "127.0.0.1";
        }
        return RedisClient.create("redis://" + redisHost + ":6379");
    }
}
