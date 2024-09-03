package com.akto.monitoring;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.DaoInit;
import com.akto.dao.APIConfigsDao;
import com.akto.dao.context.Context;
import com.akto.dto.APIConfig;
import com.akto.dto.HttpResponseParams;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.filters.HttpCallFilter;
import com.akto.hybrid_parsers.HttpCallParser;
import com.akto.hybrid_runtime.Main.AccountInfo;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;

public class Main {
    private Consumer<String, String> consumer;
    private static final LoggerMaker loggerMaker = new LoggerMaker(Main.class, LogDb.THREAD_DETECTION);
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    public static final int sync_threshold_time = 120;

    public static void main(String[] args) {

        final Main main = new Main();

        String kafkaBrokerUrl = "kafka1:19092";
        String groupIdConfig = System.getenv("AKTO_KAFKA_GROUP_ID_CONFIG");
        int maxPollRecordsConfig = Integer.parseInt(System.getenv().getOrDefault("AKTO_KAFKA_MAX_POLL_RECORDS_CONFIG", "100"));
        String mongoURI = System.getenv("AKTO_MONGO_CONN");
        DaoInit.init(new ConnectionString(mongoURI));

        Properties properties = com.akto.hybrid_runtime.Main.configProperties(kafkaBrokerUrl, groupIdConfig,
                maxPollRecordsConfig);

        main.consumer = new KafkaConsumer<>(properties);

        final Thread mainThread = Thread.currentThread();
        final AtomicBoolean exceptionOnCommitSync = new AtomicBoolean(false);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                main.consumer.wakeup();
                try {
                    if (!exceptionOnCommitSync.get()) {
                        mainThread.join();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (Error e) {
                    loggerMaker.errorAndAddToDb("Error in main thread: " + e.getMessage(), LogDb.RUNTIME);
                }
            }
        });

        Map<String, HttpCallFilter> httpCallFilterMap = new HashMap<>();
        long lastSyncOffset = 0;
        Map<Integer, AccountInfo> accountInfoMap = new HashMap<>();

        String topicName = System.getenv("AKTO_KAFKA_TOPIC_NAME");

        APIConfig apiConfig = null;
        String configName = System.getenv("AKTO_CONFIG_NAME");

        try {
            apiConfig = APIConfigsDao.instance.findOne(Filters.eq("name", configName));
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error while fetching api config " + e.getMessage(), LogDb.RUNTIME);
        }
        if (apiConfig == null) {
            apiConfig = new APIConfig(configName, "access-token", 1, 10_000_000, sync_threshold_time);
        }

        try {
            main.consumer.subscribe(Arrays.asList(topicName));
            loggerMaker.infoAndAddToDb("Consumer subscribed", LogDb.RUNTIME);
            while (true) {
                ConsumerRecords<String, String> records = main.consumer.poll(Duration.ofMillis(10000));
                try {
                    main.consumer.commitSync();
                } catch (Exception e) {
                    throw e;
                }

                // TODO: what happens if exception
                Map<String, List<HttpResponseParams>> responseParamsToAccountMap = new HashMap<>();
                for (ConsumerRecord<String, String> r : records) {
                    HttpResponseParams httpResponseParams;
                    try {

                        com.akto.hybrid_runtime.Main.printL(r.value());
                        lastSyncOffset++;

                        if (lastSyncOffset % 100 == 0) {
                            logger.info("Committing offset at position: " + lastSyncOffset);
                        }

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

                    AccountInfo accountInfo = accountInfoMap.get(accountIdInt);
                    if (accountInfo == null) {
                        accountInfo = new AccountInfo();
                        accountInfoMap.put(accountIdInt, accountInfo);
                    }

                    if (!httpCallFilterMap.containsKey(accountId)) {
                        HttpCallFilter filter = new HttpCallFilter(1000, sync_threshold_time);
                        httpCallFilterMap.put(accountId, filter);
                        loggerMaker.infoAndAddToDb("New filter created for account: " + accountId);
                    }

                    HttpCallFilter filter = httpCallFilterMap.get(accountId);
                    List<HttpResponseParams> accWiseResponse = responseParamsToAccountMap.get(accountId);
                    filter.filterFunction(accWiseResponse);
                }
            }

        } catch (WakeupException ignored) {
            // nothing to catch. This exception is called from the shutdown hook.
        } catch (Exception e) {
            exceptionOnCommitSync.set(true);
            com.akto.hybrid_runtime.Main.printL(e);
            loggerMaker.errorAndAddToDb("Error in main runtime: " + e.getMessage(), LogDb.RUNTIME);
            e.printStackTrace();
            System.exit(0);
        } finally {
            main.consumer.close();
        }
    }

}