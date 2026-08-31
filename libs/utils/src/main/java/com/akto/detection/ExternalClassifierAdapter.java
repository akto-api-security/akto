package com.akto.detection;

import com.akto.log.LoggerMaker;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Reads locally-detected candidates from Kafka, calls the external classifier (Enigma/CAPI in production,
 * stub in tests), and publishes refined verdicts back.
 *
 * Runs in customer's VPC where it has access to internal systems.
 * Subscribes to: akto.detection-candidates
 * Publishes to: akto.detection-verdicts
 */
public class ExternalClassifierAdapter implements Runnable {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ExternalClassifierAdapter.class);
    private static final Gson gson = new Gson();

    private final String brokerUrl;
    private final String classifierUrl;
    private final String authToken;
    private final OkHttpClient httpClient;
    private final KafkaConsumer<String, String> candidateConsumer;
    private final KafkaProducer<String, String> verdictProducer;
    private volatile boolean running = true;

    public ExternalClassifierAdapter(String brokerUrl, String classifierUrl, String authToken) {
        this.brokerUrl = brokerUrl;
        this.classifierUrl = classifierUrl;
        this.authToken = authToken;

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();

        // Consumer for candidates
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerUrl);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "external-classifier-adapter");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        this.candidateConsumer = new KafkaConsumer<>(consumerProps);
        candidateConsumer.subscribe(Collections.singletonList("akto.detection-candidates"));

        // Producer for verdicts
        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerUrl);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        this.verdictProducer = new KafkaProducer<>(producerProps);

        loggerMaker.info("[external-classifier-adapter] ready: classifier=" + classifierUrl + " brokers=" + brokerUrl);
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        try {
            while (running) {
                ConsumerRecords<String, String> records = candidateConsumer.poll(Duration.ofSeconds(5));
                if (records.isEmpty()) continue;

                // Batch candidates by param
                Map<String, List<DetectionCandidate>> byParam = new HashMap<>();
                records.forEach(record -> {
                    try {
                        DetectionCandidate candidate = gson.fromJson(record.value(), DetectionCandidate.class);
                        String paramKey = candidate.getApiCollectionId() + "/" + candidate.getUrl()
                                + "/" + candidate.getMethod() + "/" + candidate.getParam();
                        byParam.computeIfAbsent(paramKey, k -> new ArrayList<>()).add(candidate);
                    } catch (Exception e) {
                        loggerMaker.error("[external-classifier-adapter] failed parsing candidate: " + e.getMessage());
                    }
                });

                // Call classifier for each param group (one call per param to refine it)
                for (Map.Entry<String, List<DetectionCandidate>> entry : byParam.entrySet()) {
                    List<DetectionCandidate> candidates = entry.getValue();
                    if (candidates.isEmpty()) continue;

                    DetectionCandidate first = candidates.get(0);
                    String verdict = callClassifier(candidates);
                    if (verdict != null && !verdict.isEmpty()) {
                        publishVerdict(first.getApiCollectionId(), first.getUrl(), first.getMethod(),
                                first.getParam(), verdict);
                    }
                }
            }
        } catch (Exception e) {
            loggerMaker.error("[external-classifier-adapter] fatal error: " + e.getMessage());
        } finally {
            candidateConsumer.close();
            verdictProducer.close();
        }
    }

    /**
     * Call the classifier with the batch of candidates. Returns the refined type, or null if no refinement.
     */
    private String callClassifier(List<DetectionCandidate> candidates) {
        try {
            JsonArray detectionsArray = new JsonArray();
            for (int i = 0; i < candidates.size(); i++) {
                DetectionCandidate c = candidates.get(i);
                JsonObject obj = new JsonObject();
                obj.addProperty("idx", i);
                obj.addProperty("jsonpath", c.getJsonPath());
                obj.addProperty("value", c.getValue());
                obj.addProperty("type", c.getType());
                detectionsArray.add(obj);
            }

            JsonObject body = new JsonObject();
            body.add("detections", detectionsArray);

            Request.Builder builder = new Request.Builder()
                    .url(classifierUrl)
                    .post(RequestBody.create(okhttp3.MediaType.parse("application/json"), gson.toJson(body)))
                    .header("Content-Type", "application/json");

            if (authToken != null && !authToken.isEmpty()) {
                builder.header("Authorization", "Bearer " + authToken);
            }

            try (Response response = httpClient.newCall(builder.build()).execute()) {
                if (response.code() != 200) {
                    loggerMaker.error("[external-classifier-adapter] classifier returned " + response.code());
                    return null;
                }

                JsonObject result = gson.fromJson(response.body().string(), JsonObject.class);
                JsonArray corrections = result.has("corrections") ? result.getAsJsonArray("corrections") : null;
                if (corrections != null && corrections.size() > 0) {
                    // Use the type from the first correction (all candidates on this param should converge to one type)
                    String correctedType = corrections.get(0).getAsJsonObject().get("type").getAsString();
                    loggerMaker.info("[external-classifier-adapter] refined to " + correctedType
                            + " for " + candidates.size() + " values");
                    return correctedType;
                }
            }
        } catch (Exception e) {
            loggerMaker.error("[external-classifier-adapter] call failed: " + e.getMessage());
        }
        return null;
    }

    private void publishVerdict(int collectionId, String url, String method, String param, String correctedType) {
        try {
            DetectionVerdict verdict = new DetectionVerdict(collectionId, url, method, param, correctedType,
                    System.currentTimeMillis());
            ProducerRecord<String, String> record = new ProducerRecord<>("akto.detection-verdicts", param,
                    gson.toJson(verdict));
            verdictProducer.send(record);
        } catch (Exception e) {
            loggerMaker.error("[external-classifier-adapter] failed publishing verdict: " + e.getMessage());
        }
    }
}
