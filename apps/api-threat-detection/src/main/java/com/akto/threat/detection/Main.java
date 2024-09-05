package com.akto.threat.detection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.commons.lang3.function.FailableFunction;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import com.akto.dao.context.Context;
import com.akto.dto.HttpResponseParams;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.runtime.utils.Utils;
import com.akto.traffic.KafkaRunner;
import com.akto.filters.HttpCallFilter;
import com.akto.parsers.HttpCallParser;

public class Main {

    private static final LogDb module = LogDb.THREAT_DETECTION;
    private static final LoggerMaker loggerMaker = new LoggerMaker(Main.class, module);
    public static final int sync_threshold_time = 120;
    public static final int sync_threshold_count = 1000;
    public static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public static void main(String[] args) {

        Map<String, HttpCallFilter> httpCallFilterMap = new HashMap<>();
        String topicName = System.getenv("AKTO_KAFKA_TOPIC_NAME");
        if (topicName == null) {
            String defaultTopic = "akto.api.protection";
            loggerMaker.infoAndAddToDb(String.format("Kafka topic is not defined, using default topic : %s", defaultTopic));
            topicName = defaultTopic;
        }
        
        FailableFunction<ConsumerRecords<String, String>, Void, Exception> func = records -> {
            // TODO: what happens if exception
                Map<String, List<HttpResponseParams>> responseParamsToAccountMap = new HashMap<>();
                for (ConsumerRecord<String, String> r : records) {
                    HttpResponseParams httpResponseParams;
                    try {
                        Utils.printL(r.value());
                        httpResponseParams = HttpCallParser.parseKafkaMessage(r.value());
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(e, "Error while parsing kafka message " + e, LogDb.RUNTIME);
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
                        HttpCallFilter filter = new HttpCallFilter(sync_threshold_count, sync_threshold_time);
                        httpCallFilterMap.put(accountId, filter);
                        loggerMaker.infoAndAddToDb("New filter created for account: " + accountId);
                    }

                    HttpCallFilter filter = httpCallFilterMap.get(accountId);
                    List<HttpResponseParams> accWiseResponse = responseParamsToAccountMap.get(accountId);
                    filter.filterFunction(accWiseResponse);
                }
            return null;
        };
        KafkaRunner.processKafkaRecords(module, Arrays.asList(topicName), func);
    }
}