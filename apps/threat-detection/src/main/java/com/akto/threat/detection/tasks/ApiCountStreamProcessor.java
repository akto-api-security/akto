package com.akto.threat.detection.tasks;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.ReadOnlyWindowStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.ValueAndTimestamp;
import org.apache.kafka.streams.state.WindowStore;
import org.apache.kafka.streams.state.WindowStoreIterator;
import org.apache.kafka.streams.state.KeyValueIterator;

import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.bson.Document;

import com.akto.dao.context.Context;
import com.akto.kafka.KafkaConfig;
import com.akto.kafka.KafkaConsumerConfig;
import com.akto.kafka.KafkaProducerConfig;
import com.akto.kafka.Serializer;
import com.akto.proto.http_response_param.v1.HttpResponseParam;

import io.lettuce.core.RedisClient;

import org.bson.Document;

import java.time.Duration;
import java.time.Instant;
import java.util.Properties;

public class ApiCountStreamProcessor {

    private static final String CONSUMER_GROUP_ID = "akto.threat_detection";
    private static final String COUNTS_STORE_NAME = "minute-counts-store";
    private static MaliciousTrafficDetectorTask maliciousTrafficDetectorTask;
    private int cnt = 0;

    public ApiCountStreamProcessor() {
    }

    public void start() {
        // Kafka Streams configuration
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "api-count-stream-processor");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:29092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, HttpResponseParamSerde.class.getName());
        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 30000);

        // Streams topology
        StreamsBuilder builder = new StreamsBuilder();

        // StoreBuilder<WindowStore<String, Long>> storeBuilder =
        //     Stores.windowStoreBuilder(
        //         Stores.persistentWindowStore(
        //             COUNTS_STORE_NAME,
        //             Duration.ofHours(1), // Retention period
        //             Duration.ofMinutes(1), // Window size
        //             false // Do not retain duplicates
        //         ),
        //         Serdes.String(),
        //         Serdes.Long()
        //     );

        // builder.addStateStore(storeBuilder);

        KStream<String, HttpResponseParam> inputStream = builder.stream("akto.api.logs2");

        KStream<String, HttpResponseParam> keyedStream = inputStream.selectKey((key, value) -> {
            try {
                return value.getApiCollectionId() + "|" + value.getPath() + "|" + value.getMethod();
            } catch (Exception e) {
                System.err.println("Failed to create key: " + e.getMessage());
                return "invalid_key";
            }
        });

        // 1-minute tumbling window for per-minute counts

        KTable<Windowed<String>, Long> minuteCounts = keyedStream
            .groupByKey()
            .windowedBy(TimeWindows.ofSizeAndGrace(Duration.ofMinutes(1), Duration.ofSeconds(1)))
            .count(Materialized.as(Stores.inMemoryWindowStore("minute-counts", Duration.ofMinutes(1), Duration.ofMinutes(1), false)));

        // Call existing processRecord method

        // inputStream.process(() -> new Processor<String, HttpResponseParam, Void, Void>() {
        //     private WindowStore<String, Long> store;

        //     @Override
        //     public void init(ProcessorContext<Void, Void> context) {
        //         this.store = context.getStateStore(COUNTS_STORE_NAME);
        //     }

        //     @Override
        //     public void process(Record<String, HttpResponseParam> record) {
        //         processRecord(record.value(), store);
        //     }

        //     @Override
        //     public void close() {}
        // }, COUNTS_STORE_NAME);
        
        
        inputStream.foreach((key, value) -> processRecord(value, null));

        // Create key as (api_collection_id, path, method)
        // KStream<String, HttpResponseParam> keyedStream = inputStream.selectKey((key, value) ->
        //     value.getApiCollectionId() + "|" + value.getPath() + "|" + value.getMethod());

        // KStream<String, HttpResponseParam> keyedStream = inputStream.selectKey((key, value) -> {
        //     try {
        //         return value.getApiCollectionId() + "|" + value.getPath() + "|" + value.getMethod();
        //     } catch (Exception e) {
        //         System.err.println("Failed to create key: " + e.getMessage());
        //         return "invalid_key";
        //     }
        // });

        // // 1-minute tumbling window for per-minute counts

        // KTable<Windowed<String>, Long> minuteCounts = keyedStream
        //     .groupByKey()
        //     .windowedBy(TimeWindows.ofSizeAndGrace(Duration.ofMinutes(1), Duration.ofSeconds(10)))
        //     .count(Materialized.<String, Long, WindowStore<Bytes, byte[]>>as(COUNTS_STORE_NAME)
        //         .withKeySerde(Serdes.String())
        //         .withValueSerde(Serdes.Long())
        //         .withRetention(Duration.ofHours(1)));

        // KTable<Windowed<String>, Long> minuteCounts = keyedStream
        //     .groupByKey()
        //     .windowedBy(TimeWindows.of(Duration.ofMinutes(1)).grace(Duration.ofSeconds(10)))
        //     .count();

        // Store minute counts in MongoDB
        minuteCounts.toStream().foreach((windowedKey, count) -> {
            try {
                String[] keyParts = windowedKey.key().split("\\|");
                Document doc = new Document()
                    .append("api_collection_id", Integer.parseInt(keyParts[0]))
                    .append("path", keyParts[1])
                    .append("method", keyParts[2])
                    .append("window_start", windowedKey.window().start())
                    .append("count", count);
                // cyborg call
                System.out.println("data from stream - " + Integer.parseInt(keyParts[0]) + " " + keyParts[1] + " " + keyParts[2] + " " + windowedKey.window().start() +  " " + count);
            } catch (Exception e) {
                System.err.println("Failed to store minute count: " + e.getMessage());
            }
            // minuteCountsCollection.insertOne(doc);
        });

        // // Process flexible window durations
        // for (WindowConfig config : WINDOW_CONFIGS) {
        //     KTable<Windowed<String>, Long> windowCount   s = keyedStream
        //         .groupByKey()
        //         .windowedBy(TimeWindows.of(Duration.ofMinutes(config.durationMinutes))
        //             .advanceBy(Duration.ofMinutes(1))
        //             .grace(Duration.ofSeconds(30)))
        //         .count();

        //     windowCounts.toStream()
        //         .filter((windowedKey, count) -> count > config.threshold)
        //         .foreach((windowedKey, count) -> {
        //             String[] keyParts = windowedKey.key().split("\\|");
        //             Document alert = new Document()
        //                 .append("api_collection_id", Integer.parseInt(keyParts[0]))
        //                 .append("path", keyParts[1])
        //                 .append("method", keyParts[2])
        //                 .append("window_start", windowedKey.window().start())
        //                 .append("window_duration_minutes", config.durationMinutes)
        //                 .append("count", count)
        //                 .append("threshold", config.threshold);
        //             // alertsCollection.insertOne(alert);
        //             System.out.println("Threshold exceeded for " + config.durationMinutes + " minutes: " + alert.toJson());
        //         });
        // }

        // Start Kafka Streams
        KafkaStreams streams = new KafkaStreams(builder.build(), props);
        streams.setUncaughtExceptionHandler((Thread.UncaughtExceptionHandler) (thread, throwable) -> {
            System.err.println("Uncaught exception in Streams thread: " + throwable.getMessage());
            streams.close(); // Explicitly close the streams client
        });
        streams.start();

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            streams.close();
        }));
    }

    private void processRecord(HttpResponseParam httpResponseParam, WindowStore<String, Long> store) {

        cnt++;

        if ((cnt % 5000) == 0) {
            System.out.println("process record " + Context.now() + " " + cnt);
        }

        // try {
        //     maliciousTrafficDetectorTask.processRecord(httpResponseParam, store);            
        // } catch (Exception e) {
        //     // TODO: handle exception
        // }

        //ReadOnlyWindowStore<String, Long> countsStore = (ReadOnlyWindowStore<String, Long>) store;
        // long windowSizeMs = 5 * 60 * 1000L;
        // Instant windowEnd = Instant.ofEpochMilli(Context.now() * 1000L);
        // Instant windowStart = windowEnd.minusMillis(windowSizeMs);

        // String key = httpResponseParam.getApiCollectionId() + "|" + httpResponseParam.getPath() + "|" + httpResponseParam.getMethod();

        // long totalCount = 0;

        // WindowStoreIterator<Long> iterator = store.fetch(key, windowStart, windowEnd);
        // while (iterator.hasNext()) {
        //     KeyValue<Long, ?> entry = iterator.next();
        //     Object value = entry.value;
        //     System.out.println("value log " + value);
        //     if (value instanceof Long) {
        //         totalCount += (Long) value;
        //     } else if (value instanceof ValueAndTimestamp) {
        //         totalCount += ((ValueAndTimestamp<Long>) value).value();
        //     } else {
        //         System.err.println("Unexpected value type in state store: " + value.getClass().getName());
        //     }
        // }

        // System.out.println("total count comes out to be for key - " + key + " " + totalCount);

        // try (KeyValueIterator<Windowed<String>, Long> iterator = countsStore.fetch(key, windowStart, windowEnd)) {
        //     while (iterator.hasNext()) {
        //         KeyValue<Windowed<String>, Long> entry = iterator.next();
        //         totalCount += entry.value;
        //     }
        // } catch (Exception e) {
        //     System.err.println("Failed to query state store: " + e.getMessage());
        //     continue;
        // }


        return;
        // Your existing logic for processing HttpResponseParam
    }

    public static void main(String[] args) {

        KafkaConfig trafficKafka =
        KafkaConfig.newBuilder()
            .setGroupId(CONSUMER_GROUP_ID)
            .setBootstrapServers(System.getenv("AKTO_TRAFFIC_KAFKA_BOOTSTRAP_SERVER"))
            .setConsumerConfig(
                KafkaConsumerConfig.newBuilder()
                    .setMaxPollRecords(500)
                    .setPollDurationMilli(100)
                    .build())
            .setProducerConfig(
                KafkaProducerConfig.newBuilder().setBatchSize(100).setLingerMs(100).build())
            .setKeySerializer(Serializer.STRING)
            .setValueSerializer(Serializer.BYTE_ARRAY)
            .build();

        KafkaConfig internalKafka =
            KafkaConfig.newBuilder()
                .setGroupId(CONSUMER_GROUP_ID)
                .setBootstrapServers(System.getenv("AKTO_INTERNAL_KAFKA_BOOTSTRAP_SERVER"))
                .setConsumerConfig(
                    KafkaConsumerConfig.newBuilder()
                        .setMaxPollRecords(100)
                        .setPollDurationMilli(100)
                        .build())
                .setProducerConfig(
                    KafkaProducerConfig.newBuilder().setBatchSize(100).setLingerMs(100).build())
                .setKeySerializer(Serializer.STRING)
                .setValueSerializer(Serializer.BYTE_ARRAY)
                .build();

        RedisClient localRedis = createLocalRedisClient();

        try {
            maliciousTrafficDetectorTask = new MaliciousTrafficDetectorTask(trafficKafka, internalKafka, localRedis);            
        } catch (Exception e) {
            // TODO: handle exception
        }

        new ApiCountStreamProcessor().start();
    }

    public static RedisClient createLocalRedisClient() {
        return RedisClient.create(System.getenv("AKTO_THREAT_DETECTION_LOCAL_REDIS_URI"));
    }
}