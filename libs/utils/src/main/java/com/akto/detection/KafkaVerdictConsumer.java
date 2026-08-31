package com.akto.detection;

import com.akto.log.LoggerMaker;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

/**
 * Consumes detection verdicts from Kafka and updates the param-level cache.
 * Runs in a background thread, allowing the runtime to apply cached corrections immediately
 * on the next batch after a verdict arrives.
 *
 * Topic format (from async detection-corrector service):
 *   { "apiCollectionId": 1, "url": "/api/user", "method": "POST", "param": "email",
 *     "correctedType": "VERIFIED_CUSTOMER_EMAIL", "timestamp": 1234567890 }
 */
public class KafkaVerdictConsumer implements Runnable {

    private static final LoggerMaker loggerMaker = new LoggerMaker(KafkaVerdictConsumer.class);
    private static final Gson gson = new Gson();

    private final String brokerUrl;
    private final String topic;
    private volatile boolean running = true;

    public KafkaVerdictConsumer(String brokerUrl, String topic) {
        this.brokerUrl = brokerUrl;
        this.topic = topic;
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        try {
            Properties props = new Properties();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerUrl);
            props.put(ConsumerConfig.GROUP_ID_CONFIG, "detection-verdict-consumer");
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
            props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);

            KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
            consumer.subscribe(Collections.singletonList(topic));

            loggerMaker.info("[verdict-consumer] started on topic " + topic + " brokers " + brokerUrl);

            while (running) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
                if (records.isEmpty()) continue;

                ParamVerdictCache cache = DetectionCorrectorInstaller.getParamCache();
                int processed = 0;

                records.forEach(record -> {
                    try {
                        JsonObject obj = gson.fromJson(record.value(), JsonObject.class);
                        int collectionId = obj.has("apiCollectionId") ? obj.get("apiCollectionId").getAsInt() : 0;
                        String url = obj.has("url") ? obj.get("url").getAsString() : "";
                        String method = obj.has("method") ? obj.get("method").getAsString() : "";
                        String param = obj.has("param") ? obj.get("param").getAsString() : "";
                        String correctedType = obj.has("correctedType") ? obj.get("correctedType").getAsString() : null;

                        if (correctedType != null && !correctedType.isEmpty()) {
                            cache.putVerdict(collectionId, url, method, param, correctedType);
                            loggerMaker.info("[verdict-consumer] cached " + url + " " + method
                                    + " param=" + param + " type=" + correctedType);
                        }
                    } catch (Exception e) {
                        loggerMaker.error("[verdict-consumer] failed parsing verdict: " + e.getMessage());
                    }
                });

                if (records.count() > 0) {
                    loggerMaker.info("[verdict-consumer] processed " + records.count() + " verdicts, cache size="
                            + cache.size());
                }
            }

            consumer.close();
            loggerMaker.info("[verdict-consumer] stopped");
        } catch (Exception e) {
            loggerMaker.error("[verdict-consumer] fatal error: " + e.getMessage());
        }
    }
}
