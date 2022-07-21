package com.akto.analyser;

import com.akto.DaoInit;
import com.akto.InstanceDetails;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.ParamTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.HttpResponseParams;
import com.akto.parsers.HttpCallParser;
import com.mongodb.ConnectionString;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;

public class Main {
    private Consumer<String, String> consumer;
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        String kafkaBrokerUrl = System.getenv("AKTO_CENTRAL_KAFKA_BROKER_URL");
        String mongoURI = System.getenv("AKTO_MONGO_CONN");;
        int maxPollRecordsConfig = Integer.parseInt(System.getenv("AKTO_KAFKA_MAX_POLL_RECORDS_CONFIG"));

        String topicName = "akto.central";
        String groupIdConfig = "analyzer-group-config";

        DaoInit.init(new ConnectionString(mongoURI));
        Context.accountId.set(1_000_000);

        ParamTypeInfoDao.instance.createIndicesIfAbsent();

        final Main main = new Main();
        Properties properties = com.akto.runtime.Main.configProperties(kafkaBrokerUrl, groupIdConfig, maxPollRecordsConfig);
        main.consumer = new KafkaConsumer<>(properties);

        final Thread mainThread = Thread.currentThread();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                main.consumer.wakeup();
                try {
                    mainThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        Map<Integer, ResourceAnalyser> resourceAnalyserMap = new HashMap<>();

        try {
            main.consumer.subscribe(Collections.singleton(topicName));
            while (true) {
                ConsumerRecords<String, String> records = main.consumer.poll(Duration.ofMillis(10000));
                main.consumer.commitSync();
                for (ConsumerRecord<String,String> r: records) {
                    try {
                        HttpResponseParams httpResponseParams = HttpCallParser.parseKafkaMessage(r.value());
                        int accountId = Integer.parseInt(httpResponseParams.getAccountId());
                        ResourceAnalyser resourceAnalyser = resourceAnalyserMap.get(accountId);
                        if (resourceAnalyser == null) {
                            resourceAnalyser = new ResourceAnalyser(300_000_000, 0.01, 100_000_000, 0.01);
                            resourceAnalyserMap.put(accountId, resourceAnalyser);
                        }
                        resourceAnalyser.analyse(httpResponseParams);
                    } catch (Exception e) {
                        // todo: check cause
                        logger.error("Error parsing http response params : " + e.getMessage() + " " + e.getCause());
                    }
                }
            }
        } catch (WakeupException ignored) {
            // nothing to catch. This exception is called from the shutdown hook.
        } catch (Exception e) {
        } finally {
            main.consumer.close();
        }
    }
}