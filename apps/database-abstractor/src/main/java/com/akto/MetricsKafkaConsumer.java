package com.akto;

import com.akto.dao.context.Context;
import com.akto.data_actor.DbLayer;
import com.akto.dto.metrics.MetricData;
import com.akto.kafka.MetricsKafkaProducer;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mongodb.BasicDBList;
import com.mongodb.ConnectionString;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;


public class MetricsKafkaConsumer implements Runnable {

    private static final LoggerMaker loggerMaker = new LoggerMaker(MetricsKafkaConsumer.class, LogDb.DB_ABS);
    private static final Gson gson = new Gson();
    private static final Type METRIC_DATA_LIST_TYPE = new TypeToken<List<MetricData>>() {}.getType();
    private static final Type OBJECT_LIST_TYPE = new TypeToken<List<Object>>() {}.getType();

    private static final int FLUSH_DOC_THRESHOLD = 5000;
    private static final long FLUSH_TIME_THRESHOLD_MS = 5000;

    private final KafkaConsumer<String, String> consumer;
    private final AtomicBoolean running;
    private final String topicName;
    private long totalProcessed = 0;

    public MetricsKafkaConsumer(
            String brokerUrl,
            String topicName,
            String groupId,
            int maxPollRecords
    ) {
        this.topicName = topicName;
        this.running = new AtomicBoolean(true);

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerUrl);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        this.consumer = new KafkaConsumer<>(props);

        loggerMaker.infoAndAddToDb("MetricsKafkaConsumer initialized: topic=" + topicName +
            ", group=" + groupId + ", broker=" + brokerUrl, LogDb.DB_ABS);
    }

    public void start() {
        subscribe();

        Thread consumerThread = new Thread(this, "metrics-consumer");
        consumerThread.setDaemon(false);
        consumerThread.start();

        loggerMaker.infoAndAddToDb("MetricsKafkaConsumer thread started", LogDb.DB_ABS);
    }

    private void subscribe() {
        consumer.subscribe(Collections.singletonList(topicName));
        loggerMaker.infoAndAddToDb("MetricsKafkaConsumer subscribed to topic: " + topicName, LogDb.DB_ABS);
    }

    @Override
    public void run() {
        loggerMaker.infoAndAddToDb("MetricsKafkaConsumer: Starting consumer loop", LogDb.DB_ABS);

        try {
            while (running.get()) {
                try {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(200));
                    if (records.isEmpty()) {
                        continue;
                    }

                    Map<Integer, List<MetricData>> metricDataBuffer = new HashMap<>();
                    Map<Integer, BasicDBList> runtimeMetricsBuffer = new HashMap<>();
                    int bufferedDocs = 0;
                    long batchStartMs = System.currentTimeMillis();

                    for (ConsumerRecord<String, String> record : records) {
                        try {
                            bufferedDocs += bufferMessage(record.value(), metricDataBuffer, runtimeMetricsBuffer);
                            totalProcessed++;
                        } catch (Exception e) {
                            loggerMaker.errorAndAddToDb(e, "Error buffering metrics message: " + e.getMessage(), LogDb.DB_ABS);
                        }

                        boolean sizeTrigger = bufferedDocs >= FLUSH_DOC_THRESHOLD;
                        boolean timeTrigger = (System.currentTimeMillis() - batchStartMs) >= FLUSH_TIME_THRESHOLD_MS;
                        if (sizeTrigger || timeTrigger) {
                            flush(metricDataBuffer, runtimeMetricsBuffer);
                            bufferedDocs = 0;
                            batchStartMs = System.currentTimeMillis();
                        }
                    }

                    flush(metricDataBuffer, runtimeMetricsBuffer);

                    try {
                        consumer.commitSync();
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(e, "Error committing metrics consumer offset: " + e.getMessage(), LogDb.DB_ABS);
                    }

                    if (totalProcessed % 10_000 == 0) {
                        loggerMaker.infoAndAddToDb("MetricsKafkaConsumer: Total processed: " + totalProcessed, LogDb.DB_ABS);
                    }
                } catch (org.apache.kafka.common.errors.WakeupException e) {
                    break;
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Error in metrics consumer loop: " + e.getMessage(), LogDb.DB_ABS);
                }
            }
        } finally {
            try {
                consumer.close();
                loggerMaker.infoAndAddToDb("MetricsKafkaConsumer stopped. Total processed: " + totalProcessed, LogDb.DB_ABS);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error closing metrics consumer: " + e.getMessage(), LogDb.DB_ABS);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private int bufferMessage(String message, Map<Integer, List<MetricData>> metricDataBuffer,
                               Map<Integer, BasicDBList> runtimeMetricsBuffer) {
        Map<String, Object> json = gson.fromJson(message, Map.class);
        if (json == null) {
            return 0;
        }
        String triggerMethod = (String) json.get("triggerMethod");
        String payload = (String) json.get("payload");
        Object accIdObj = json.get("accountId");
        if (accIdObj == null || payload == null || triggerMethod == null) {
            return 0;
        }
        int accountId = ((Number) accIdObj).intValue();

        switch (triggerMethod) {
            case "ingestMetricsData": {
                List<MetricData> parsed = gson.fromJson(payload, METRIC_DATA_LIST_TYPE);
                if (parsed == null || parsed.isEmpty()) {
                    return 0;
                }
                metricDataBuffer.computeIfAbsent(accountId, k -> new ArrayList<>()).addAll(parsed);
                return parsed.size();
            }
            case "insertRuntimeMetricsData": {
                List<Object> parsed = gson.fromJson(payload, OBJECT_LIST_TYPE);
                if (parsed == null || parsed.isEmpty()) {
                    return 0;
                }
                runtimeMetricsBuffer.computeIfAbsent(accountId, k -> new BasicDBList()).addAll(parsed);
                return parsed.size();
            }
            default:
                loggerMaker.infoAndAddToDb("MetricsKafkaConsumer: unknown triggerMethod " + triggerMethod, LogDb.DB_ABS);
                return 0;
        }
    }

    private void flush(Map<Integer, List<MetricData>> metricDataBuffer, Map<Integer, BasicDBList> runtimeMetricsBuffer) {
        for (Map.Entry<Integer, List<MetricData>> entry : metricDataBuffer.entrySet()) {
            try {
                Context.accountId.set(entry.getKey());
                DbLayer.ingestMetricsDataFromConsumer(entry.getValue());
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error flushing metrics data for account " + entry.getKey() + ": " + e.getMessage(), LogDb.DB_ABS);
            }
        }
        metricDataBuffer.clear();

        for (Map.Entry<Integer, BasicDBList> entry : runtimeMetricsBuffer.entrySet()) {
            try {
                Context.accountId.set(entry.getKey());
                DbLayer.insertRuntimeMetricsData(entry.getValue());
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error flushing runtime metrics for account " + entry.getKey() + ": " + e.getMessage(), LogDb.DB_ABS);
            }
        }
        runtimeMetricsBuffer.clear();
    }

    public void shutdown() {
        loggerMaker.infoAndAddToDb("MetricsKafkaConsumer shutdown initiated", LogDb.DB_ABS);
        running.set(false);
        consumer.wakeup();
    }

    public long getTotalProcessed() {
        return totalProcessed;
    }


    public static void main(String[] args) throws Exception {
        if (!MetricsKafkaProducer.isMetricsKafkaConsumerEnabled()) {
            loggerMaker.infoAndAddToDb("METRICS_KAFKA_CONSUMER_ENABLED is not true, exiting", LogDb.DB_ABS);
            return;
        }
        String mongoURI = System.getenv("AKTO_MONGO_CONN");
        DaoInit.init(new ConnectionString(mongoURI));

        MetricsKafkaConsumer consumer = new MetricsKafkaConsumer(
            MetricsKafkaProducer.getMetricsBrokerUrl(),
            MetricsKafkaProducer.getMetricsTopicName(),
            MetricsKafkaProducer.getMetricsConsumerGroupId(),
            MetricsKafkaProducer.getMetricsMaxPollRecords()
        );
        // subscribe + run() directly on the calling thread, rather than start()'s
        // background thread, so this process's lifecycle is the consumer's lifecycle.
        consumer.subscribe();
        consumer.run();
    }
}
