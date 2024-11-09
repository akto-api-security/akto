package com.akto.suspect_data;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import com.akto.dao.SuspectSampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dto.traffic.SuspectSampleData;
import com.akto.runtime.utils.Utils;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.WriteModel;

public class FlushMessagesTask {

    private static final ExecutorService pollingExecutor = Executors.newSingleThreadExecutor();
    private final Consumer<String, String> consumer;

    private FlushMessagesTask() {
        String kafkaBrokerUrl = "127.0.0.1:29092";
        String isKubernetes = System.getenv("IS_KUBERNETES");
        if (isKubernetes != null && isKubernetes.equalsIgnoreCase("true")) {
            kafkaBrokerUrl = "127.0.0.1:29092";
        }

        String groupId = "akto-flush-malicious-messages";

        Properties properties = Utils.configProperties(kafkaBrokerUrl, groupId, 100);
        this.consumer = new KafkaConsumer<>(properties);
    }

    public static FlushMessagesTask instance = new FlushMessagesTask();

    public void init() {
        consumer.subscribe(Collections.singletonList("akto.malicious"));
        pollingExecutor.execute(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                        processRecords(records);
                    } catch (Exception e) {
                        e.printStackTrace();
                        consumer.close();
                    }
                }
            }
        });
    }

    public void processRecords(ConsumerRecords<String, String> records) {
        Map<String, List<SuspectSampleData>> accWiseMessages = new HashMap<>();
        for (ConsumerRecord<String, String> record : records) {
            String msgStr = record.value();
            Message.unmarshall(msgStr).ifPresent(msg -> {
                accWiseMessages.computeIfAbsent(msg.getAccountId(), k -> new ArrayList<>()).add(msg.getData());
            });
        }

        for (Map.Entry<String, List<SuspectSampleData>> entry : accWiseMessages.entrySet()) {
            String accountId = entry.getKey();
            List<SuspectSampleData> sampleDatas = entry.getValue();
            Context.accountId.set(Integer.parseInt(accountId));

            try {
                List<WriteModel<SuspectSampleData>> bulkUpdates = new ArrayList<>();
                sampleDatas
                        .forEach(sampleData -> bulkUpdates.add(new InsertOneModel<>(sampleData)));

                SuspectSampleDataDao.instance.bulkWrite(bulkUpdates, new BulkWriteOptions().ordered(false));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
