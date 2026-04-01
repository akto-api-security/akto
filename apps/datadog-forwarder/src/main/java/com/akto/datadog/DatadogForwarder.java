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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class DatadogForwarder {

    private static final LoggerMaker loggerMaker = new LoggerMaker(DatadogForwarder.class, LoggerMaker.LogDb.RUNTIME);
    private static final int MAX_BATCH_SIZE = 100;
    private static final int MAX_BATCH_BYTES = 1024 * 1024; // 1MB (conservative, Datadog has issues above 1MB)

    private final String kafkaBrokerUrl;
    private final String kafkaTopic;
    private final AtomicReference<Config.DatadogForwarderConfig> configRef;

    public DatadogForwarder(String kafkaBrokerUrl, String kafkaTopic,
                            AtomicReference<Config.DatadogForwarderConfig> configRef) {
        this.kafkaBrokerUrl = kafkaBrokerUrl;
        this.kafkaTopic = kafkaTopic;
        this.configRef = configRef;
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

                        JSONObject source = new JSONObject(record.value());
                        if (!isLLMTrace(source)) continue;

                        JSONObject span = buildLLMSpan(source);
                        if (span == null) continue;

                        String spanStr = span.toString();
                        int spanBytes = spanStr.getBytes(StandardCharsets.UTF_8).length;

                        if (batch.size() >= MAX_BATCH_SIZE || (batchBytes + spanBytes) >= MAX_BATCH_BYTES) {
                            flushToAIObservability(batch, config);
                            batch = new ArrayList<>();
                            batchBytes = 0;
                        }

                        batch.add(span);
                        batchBytes += spanBytes;
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb("Error processing Kafka record for Datadog AI Observability: " + e, LoggerMaker.LogDb.RUNTIME);
                    }
                }

                if (!batch.isEmpty()) {
                    flushToAIObservability(batch, config);
                    batch = new ArrayList<>();
                    batchBytes = 0;
                }

                consumer.commitSync();
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("DatadogForwarder error in main loop: " + e, LoggerMaker.LogDb.RUNTIME);
            }
        }
    }

    /**
     * Returns true if the Kafka message is an AGENTIC LLM trace:
     * - contextSource == "AGENTIC"
     * - requestPayload parses as JSON with both "messages" and "model" keys
     */
    private boolean isLLMTrace(JSONObject source) {
        try {
            String tagStr = source.optString("tag", "");
            if (tagStr.isEmpty()) return false;
            JSONObject tagJson = new JSONObject(tagStr);
            if (!"AGENTIC".equals(tagJson.optString("source", ""))) return false;

            String requestPayload = source.optString("requestPayload", "");
            if (requestPayload.isEmpty()) return false;

            JSONObject reqJson = new JSONObject(requestPayload);
            return reqJson.has("messages") && reqJson.has("model");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Builds a Datadog AI Observability span JSONObject from a Kafka record.
     * Returns null if the span cannot be built.
     */
    private JSONObject buildLLMSpan(JSONObject source) {
        try {
            // Parse requestPayload
            String requestPayloadStr = source.optString("requestPayload", "");
            JSONObject reqPayload = new JSONObject(requestPayloadStr);

            String modelName = reqPayload.optString("model", "unknown");
            JSONArray inputMessages = reqPayload.optJSONArray("messages");
            if (inputMessages == null) inputMessages = new JSONArray();

            // Parse tag JSON for session_id and trace_id
            String sessionId = null;
            String traceId = null;
            String tagStr = source.optString("tag", "");
            if (!tagStr.isEmpty()) {
                try {
                    JSONObject tagJson = new JSONObject(tagStr);
                    sessionId = tagJson.optString("session_id", null);
                    traceId = tagJson.optString("trace_id", null);
                } catch (Exception ignored) {}
            }
            if (traceId == null || traceId.isEmpty()) {
                traceId = UUID.randomUUID().toString();
            }

            // Parse timing — time field is unix seconds
            long startNs = 0;
            String timeStr = source.optString("time", "");
            if (!timeStr.isEmpty()) {
                try {
                    startNs = Long.parseLong(timeStr) * 1_000_000_000L;
                } catch (Exception ignored) {}
            }
            if (startNs == 0) {
                startNs = System.currentTimeMillis() * 1_000_000L;
            }

            // Parse responsePayload — detect SSE vs non-SSE
            String responsePayloadStr = source.optString("responsePayload", "");
            JSONArray outputMessages = new JSONArray();
            JSONObject tokenMetrics = null;

            boolean isSse = true;
            if (!responsePayloadStr.isEmpty()) {
                try {
                    JSONObject respJson = new JSONObject(responsePayloadStr);
                    if (respJson.has("choices")) {
                        isSse = false;
                        JSONArray choices = respJson.optJSONArray("choices");
                        if (choices != null && choices.length() > 0) {
                            JSONObject choice = choices.getJSONObject(0);
                            JSONObject message = choice.optJSONObject("message");
                            if (message != null) {
                                outputMessages.put(message);
                            }
                        }
                        JSONObject usage = respJson.optJSONObject("usage");
                        if (usage != null) {
                            tokenMetrics = new JSONObject();
                            tokenMetrics.put("input_tokens", usage.optInt("prompt_tokens", 0));
                            tokenMetrics.put("output_tokens", usage.optInt("completion_tokens", 0));
                            tokenMetrics.put("total_tokens", usage.optInt("total_tokens", 0));
                        }
                    }
                } catch (Exception ignored) {}

                if (isSse) {
                    JSONObject assistantMsg = new JSONObject();
                    assistantMsg.put("role", "assistant");
                    assistantMsg.put("content", responsePayloadStr);
                    outputMessages.put(assistantMsg);
                }
            }

            // Build meta
            JSONObject metaInput = new JSONObject();
            metaInput.put("messages", inputMessages);

            JSONObject metaOutput = new JSONObject();
            metaOutput.put("messages", outputMessages);

            JSONObject meta = new JSONObject();
            meta.put("input", metaInput);
            meta.put("output", metaOutput);
            meta.put("model_name", modelName);
            meta.put("model_provider", "openai");

            // Build span
            meta.put("kind", "llm");

            JSONObject span = new JSONObject();
            span.put("span_id", UUID.randomUUID().toString());
            span.put("trace_id", traceId);
            span.put("parent_id", "undefined");
            span.put("name", "openai.chat");
            span.put("start_ns", startNs);
            span.put("duration", 1_000_000L); // 1ms placeholder — actual duration not tracked through Kafka
            span.put("status", "ok");
            span.put("meta", meta);
            if (tokenMetrics != null) {
                span.put("metrics", tokenMetrics);
            }

            // Build top-level attributes
            JSONObject attributes = new JSONObject();
            attributes.put("ml_app", "akto-agent-shield");
            if (sessionId != null && !sessionId.isEmpty()) {
                attributes.put("session_id", sessionId);
            }
            attributes.put("tags", new JSONArray(new String[]{"env:prod", "service:akto-agent-shield"}));
            attributes.put("spans", new JSONArray(new Object[]{span}));

            // Build payload wrapper
            JSONObject data = new JSONObject();
            data.put("type", "span");
            data.put("attributes", attributes);

            return data;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error building LLM span: " + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    private void flushToAIObservability(List<JSONObject> spans, Config.DatadogForwarderConfig config) {
        // Each element in spans is already a full {"data": {...}} wrapper
        // Datadog AI Observability accepts one span per request; send individually
        for (JSONObject spanData : spans) {
            JSONObject payload = new JSONObject();
            payload.put("data", spanData);
            String payloadStr = payload.toString();

            boolean success = sendToAIObservability(config, payloadStr);
            if (!success) {
                loggerMaker.infoAndAddToDb("Retrying Datadog AI Observability send...", LoggerMaker.LogDb.RUNTIME);
                sendToAIObservability(config, payloadStr);
            }
        }
    }

    private boolean sendToAIObservability(Config.DatadogForwarderConfig config, String payload) {
        String endpointUrl = "https://api." + config.getDatadogSite() + "/api/intake/llm-obs/v1/trace/spans";
        try {
            URL url = new URL(endpointUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("DD-API-KEY", config.getApiKey());
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                return true;
            } else {
                String errorBody = "";
                try {
                    java.io.InputStream errStream = conn.getErrorStream();
                    if (errStream != null) {
                        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
                        byte[] tmp = new byte[4096];
                        int n;
                        while ((n = errStream.read(tmp)) != -1) buf.write(tmp, 0, n);
                        errorBody = buf.toString("UTF-8");
                    }
                } catch (Exception ignored) {}
                loggerMaker.errorAndAddToDb("Datadog AI Observability returned HTTP " + responseCode + " for " + endpointUrl + " | body: " + errorBody, LoggerMaker.LogDb.RUNTIME);
                loggerMaker.errorAndAddToDb("Payload sent: " + payload, LoggerMaker.LogDb.RUNTIME);
                return false;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error sending to Datadog AI Observability: " + e, LoggerMaker.LogDb.RUNTIME);
            return false;
        }
    }
}
