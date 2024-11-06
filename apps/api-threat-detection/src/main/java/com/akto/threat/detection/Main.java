package com.akto.threat.detection;

import java.util.*;

import org.apache.commons.lang3.function.FailableFunction;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.dao.context.Context;
import com.akto.dto.HttpResponseParams;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.metrics.AllMetrics;
import com.akto.runtime.utils.Utils;
import com.akto.traffic.KafkaRunner;
import com.akto.filters.HttpCallFilter;
import com.akto.parsers.HttpCallParser;

public class Main {
    private static final LogDb module = LogDb.THREAT_DETECTION;
    private static final LoggerMaker loggerMaker = new LoggerMaker(Main.class, module);
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final int sync_threshold_time = 120;
    private static final int sync_threshold_count = 1000;
    private static long lastSyncOffset = 0;

    public static void main(String[] args) {

        Map<String, HttpCallFilter> httpCallFilterMap = new HashMap<>();
        String topicName = System.getenv("AKTO_KAFKA_TOPIC_NAME");
        if (topicName == null) {
            String defaultTopic = "akto.api.protection";
            loggerMaker.infoAndAddToDb(
                    String.format(
                            "Kafka topic is not defined, using default topic : %s", defaultTopic));
            topicName = defaultTopic;
        }

        Properties kafkaProperties = new Properties();
        kafkaProperties.put(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, System.getenv("AKTO_KAFKA_BROKER_IP"));

        FailableFunction<ConsumerRecords<String, String>, Void, Exception> func =
                records -> {
                    long start = System.currentTimeMillis();

                    // TODO: what happens if exception
                    Map<String, List<HttpResponseParams>> responseParamsToAccountMap =
                            new HashMap<>();
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
                            HttpCallFilter filter =
                                    new HttpCallFilter(sync_threshold_count, sync_threshold_time);
                            httpCallFilterMap.put(accountId, filter);
                            loggerMaker.infoAndAddToDb(
                                    "New filter created for account: " + accountId);
                        }

                        HttpCallFilter filter = httpCallFilterMap.get(accountId);
                        List<HttpResponseParams> accWiseResponse =
                                responseParamsToAccountMap.get(accountId);
                        filter.filterFunction(accWiseResponse);
                    }

                    AllMetrics.instance.setRuntimeProcessLatency(
                            System.currentTimeMillis() - start);

                    return null;
                };
        KafkaRunner.processKafkaRecords(module, Arrays.asList(topicName), func);
    }
}
