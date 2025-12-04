package com.akto.runtime;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.JSONUtils;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Lightweight HTTP client that forwards prompts to an external Python-based detector
 * implementing the SentenceTransformer + clustering algorithm.
 */
public class BasePromptDetectionClient {

    private static final LoggerMaker logger = new LoggerMaker(BasePromptDetectionClient.class, LogDb.RUNTIME);
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String DEFAULT_ENDPOINT = System.getenv("BASE_PROMPT_DETECTOR_URL");

    private final OkHttpClient httpClient;
    private final String serviceEndpoint;

    public BasePromptDetectionClient() {
        this(DEFAULT_ENDPOINT);
    }

    public BasePromptDetectionClient(String endpoint) {
        this.serviceEndpoint = endpoint;
        this.httpClient = new OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(120))
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(120))
                .writeTimeout(Duration.ofSeconds(120))
                .build();
    }

    public DetectionResult detectBasePrompt(List<String> prompts) {
        if (prompts == null || prompts.isEmpty()) {
            return null;
        }
        if (StringUtils.isBlank(serviceEndpoint)) {
            logger.warnAndAddToDb("BASE_PROMPT_DETECTOR_URL not configured. Skipping remote detection.", LogDb.RUNTIME);
            return null;
        }

        try {
            Map<String, Object> payloadMap = new java.util.HashMap<>();
            payloadMap.put("prompts", prompts);
            String payload = JSONUtils.getString(payloadMap);
            Request request = new Request.Builder()
                    .url(serviceEndpoint)
                    .post(RequestBody.create(payload, JSON))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.warnAndAddToDb("Base prompt detector returned status: " + response.code(), LogDb.RUNTIME);
                    return null;
                }

                String body = response.body() != null ? response.body().string() : "";
                Map<String, Object> resultMap = JSONUtils.getMap(body);

                String basePrompt = (String) resultMap.getOrDefault("basePrompt", "");
                double confidence = resultMap.get("confidence") instanceof Number ?
                        ((Number) resultMap.get("confidence")).doubleValue() : 0d;

                List<Anomaly> anomalies = parseAnomalies(resultMap.get("anomalies"));

                if (StringUtils.isBlank(basePrompt)) {
                    return null;
                }

                return new DetectionResult(basePrompt, confidence, anomalies);
            }
        } catch (Exception e) {
            logger.errorAndAddToDb("Error calling base prompt detector: " + e.getMessage(), LogDb.RUNTIME);
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Anomaly> parseAnomalies(Object data) {
        if (!(data instanceof List)) {
            return Collections.emptyList();
        }
        List<?> list = (List<?>) data;
        List<Anomaly> anomalies = new ArrayList<>();
        for (Object entry : list) {
            if (entry instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) entry;
                Object promptObj = map.getOrDefault("prompt", "");
                Object userInputObj = map.getOrDefault("userInput", "");
                Object similarityObj = map.get("similarity");

                String prompt = promptObj instanceof String ? (String) promptObj : "";
                String userInput = userInputObj instanceof String ? (String) userInputObj : "";
                double similarity = similarityObj instanceof Number ? ((Number) similarityObj).doubleValue() : 0d;

                anomalies.add(new Anomaly(prompt, userInput, similarity));
            }
        }
        return anomalies;
    }

    public static class DetectionResult {
        private final String basePrompt;
        private final double confidence;
        private final List<Anomaly> anomalies;

        public DetectionResult(String basePrompt, double confidence, List<Anomaly> anomalies) {
            this.basePrompt = basePrompt;
            this.confidence = confidence;
            this.anomalies = anomalies;
        }

        public String getBasePrompt() {
            return basePrompt;
        }

        public double getConfidence() {
            return confidence;
        }

        public List<Anomaly> getAnomalies() {
            return anomalies;
        }
    }

    public static class Anomaly {
        private final String prompt;
        private final String userInput;
        private final double similarity;

        public Anomaly(String prompt, String userInput, double similarity) {
            this.prompt = prompt;
            this.userInput = userInput;
            this.similarity = similarity;
        }

        public String getPrompt() {
            return prompt;
        }

        public String getUserInput() {
            return userInput;
        }

        public double getSimilarity() {
            return similarity;
        }
    }
}

