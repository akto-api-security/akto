package com.akto.datadog;

import com.akto.dto.Config;
import com.akto.log.LoggerMaker;

import java.util.Arrays;
import java.util.HashSet;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

public class DatadogForwarder {

    private static final LoggerMaker loggerMaker = new LoggerMaker(DatadogForwarder.class, LoggerMaker.LogDb.RUNTIME);
    private static final int MAX_BATCH_SIZE = 500;
    private static final int MAX_BATCH_BYTES = 4 * 1024 * 1024; // 4MB (Datadog limit is 5MB)
    private static final HashSet<String> ALLOWED_FIELDS = new HashSet<>(Arrays.asList(
        "path", "requestHeaders", "responseHeaders", "method", "requestPayload", "responsePayload",
        "ip", "time", "statusCode", "status", "tag", "contextSource"
    ));
    private static final HashSet<String> JSON_STRING_FIELDS = new HashSet<>(Arrays.asList(
        "requestHeaders", "responseHeaders", "requestPayload", "responsePayload", "tag"
    ));

    private final String kafkaBrokerUrl;
    private final String kafkaTopic;
    private final AtomicReference<Config.DatadogForwarderConfig> configRef;
    private final String hostname;

    public DatadogForwarder(String kafkaBrokerUrl, String kafkaTopic,
                            AtomicReference<Config.DatadogForwarderConfig> configRef) {
        this.kafkaBrokerUrl = kafkaBrokerUrl;
        this.kafkaTopic = kafkaTopic;
        this.configRef = configRef;
        String h;
        try {
            h = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            h = "akto-mini-runtime";
        }
        this.hostname = h;
    }

    public void run() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBrokerUrl);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "akto-datadog-forwarder");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList(kafkaTopic));

        loggerMaker.infoAndAddToDb("DatadogForwarder started, subscribed to topic: " + kafkaTopic, LoggerMaker.LogDb.RUNTIME);

        List<JSONObject> batch = new ArrayList<>();
        int batchBytes = 0;

        while (true) {
            try {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(10000));

                Config.DatadogForwarderConfig config = configRef.get();
                if (config == null || !config.isEnabled()) {
                    consumer.commitSync();
                    continue;
                }

                for (ConsumerRecord<String, String> record : records) {
                    try {
                        if (record.value() == null || record.value().isEmpty()) continue;

                        JSONObject logEntry = buildLogEntry(record.value());
                        String entryStr = logEntry.toString();
                        int entryBytes = entryStr.getBytes(StandardCharsets.UTF_8).length;

                        if (batch.size() >= MAX_BATCH_SIZE || (batchBytes + entryBytes) >= MAX_BATCH_BYTES) {
                            flush(batch, config);
                            batch = new ArrayList<>();
                            batchBytes = 0;
                        }

                        batch.add(logEntry);
                        batchBytes += entryBytes;
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb("Error processing Kafka record for Datadog: " + e, LoggerMaker.LogDb.RUNTIME);
                    }
                }

                if (!batch.isEmpty()) {
                    flush(batch, config);
                    batch = new ArrayList<>();
                    batchBytes = 0;
                }

                consumer.commitSync();
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("DatadogForwarder error in main loop: " + e, LoggerMaker.LogDb.RUNTIME);
            }
        }
    }

    private JSONObject buildLogEntry(String rawMessage) {
        JSONObject source = new JSONObject(rawMessage);
        JSONObject entry = new JSONObject();

        for (String key : source.keySet()) {
            if (!ALLOWED_FIELDS.contains(key)) continue;
            Object value = source.get(key);
            if (value == null || value == JSONObject.NULL) continue;

            if (JSON_STRING_FIELDS.contains(key) && value instanceof String) {
                try {
                    entry.put(key, new JSONObject((String) value));
                } catch (Exception e) {
                    try {
                        entry.put(key, new JSONArray((String) value));
                    } catch (Exception e2) {
                        entry.put(key, value);
                    }
                }
            } else {
                entry.put(key, value);
            }
        }

        entry.put("ddsource", "akto");
        entry.put("service", "akto-mini-runtime");
        entry.put("ddtags", "env:prod");
        entry.put("hostname", hostname);
        return entry;
    }

    private void flush(List<JSONObject> batch, Config.DatadogForwarderConfig config) {
        String endpointUrl = "https://http-intake.logs." + config.getDatadogSite() + "/v1/input";
        String payload = new JSONArray(batch).toString();

        boolean success = sendToDatadog(endpointUrl, payload, config.getApiKey());
        if (!success) {
            loggerMaker.infoAndAddToDb("Retrying Datadog flush...", LoggerMaker.LogDb.RUNTIME);
            sendToDatadog(endpointUrl, payload, config.getApiKey());
        }
    }

    private boolean sendToDatadog(String endpointUrl, String payload, String apiKey) {
        try {
            URL url = new URL(endpointUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("DD-API-KEY", apiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                return true;
            } else {
                loggerMaker.errorAndAddToDb("Datadog returned HTTP " + responseCode + " for " + endpointUrl, LoggerMaker.LogDb.RUNTIME);
                return false;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error sending to Datadog: " + e, LoggerMaker.LogDb.RUNTIME);
            return false;
        }
    }
}
