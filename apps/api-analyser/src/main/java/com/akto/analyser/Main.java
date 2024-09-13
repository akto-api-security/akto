package com.akto.analyser;

import com.akto.DaoInit;
import com.akto.InstanceDetails;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.context.Context;
import com.akto.dto.AccountSettings;
import com.akto.dto.HttpResponseParams;
import com.akto.log.LoggerMaker;
import com.akto.parsers.HttpCallParser;
import com.akto.runtime.utils.Utils;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Updates;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;

public class Main {
    private Consumer<String, String> consumer;
    private static final LoggerMaker loggerMaker = new LoggerMaker(Main.class);

    public static void main(String[] args) {
        String mongoURI = System.getenv("AKTO_MONGO_CONN");;
        String centralBrokerIp = "kafka1:19092";
        String currentInstanceIp = System.getenv("AKTO_CURRENT_INSTANCE_IP");

        DaoInit.init(new ConnectionString(mongoURI));
        Context.accountId.set(1_000_000);

        if (currentInstanceIp != null) {
            AccountSettingsDao.instance.updateOne(
                    AccountSettingsDao.generateFilter(),
                    Updates.set(AccountSettings.CENTRAL_KAFKA_IP, currentInstanceIp+":9092")
            );
        }

        int maxPollRecordsConfig = AccountSettings.DEFAULT_CENTRAL_KAFKA_MAX_POLL_RECORDS_CONFIG;
        String topicName = AccountSettings.DEFAULT_CENTRAL_KAFKA_TOPIC_NAME;
        String groupIdConfig = "analyzer-group-config";

        final Main main = new Main();
        Properties properties = Utils.configProperties(centralBrokerIp, groupIdConfig, maxPollRecordsConfig);
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

        long i = 0;
        try {
            main.consumer.subscribe(Pattern.compile(".*central"));
            while (true) {
                ConsumerRecords<String, String> records = main.consumer.poll(Duration.ofMillis(10000));
                main.consumer.commitSync();
                for (ConsumerRecord<String,String> r: records) {
                    if ( (i<1000 && i%100 == 0) || (i>10_000 && i%10_000==0)) loggerMaker.infoAndAddToDb("Count: " + i, LoggerMaker.LogDb.ANALYSER);
                    i ++;

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
                        loggerMaker.errorAndAddToDb("Error parsing http response params : " + e.getMessage() + " " + e.getCause(), LoggerMaker.LogDb.ANALYSER);
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