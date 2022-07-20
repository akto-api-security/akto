package com.akto.analyser;

import com.akto.DaoInit;
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
        String topicName = System.getenv("AKTO_CENTRAL_KAFKA_TOPIC_NAME");
        String kafkaBrokerUrl = System.getenv("AKTO_CENTRAL_KAFKA_BROKER_URL");
        String groupIdConfig =  System.getenv("AKTO_KAFKA_GROUP_ID_CONFIG");
        String mongoURI = System.getenv("AKTO_MONGO_CONN");;
        String currentInstanceIp = System.getenv("AKTO_CURRENT_INSTANCE_IP");
        int maxPollRecordsConfig = Integer.parseInt(System.getenv("AKTO_KAFKA_MAX_POLL_RECORDS_CONFIG"));

        if (topicName == null) topicName = "akto.central";

        DaoInit.init(new ConnectionString(mongoURI));
        Context.accountId.set(1_000_000);

        ParamTypeInfoDao.instance.createIndicesIfAbsent();

        // register central kafka url in mongo
        if (currentInstanceIp != null) {
            AccountSettingsDao.instance.updateCentralKafkaDetails(currentInstanceIp + ":9092", topicName);
        }

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