package com.akto.detection;

import com.akto.dto.type.SingleTypeInfo.SubType;
import com.akto.log.LoggerMaker;
import com.akto.util.http_util.CoreHTTPClient;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Calls an external HTTP service to refine locally detected data types.
 *
 * The wire format follows the detection-corrector convention, so a service already written against
 * that contract works unchanged:
 *
 *   POST { "detections":  [ { "idx": 0, "jsonpath": "...", "value": "...", "type": "EMAIL" } ] }
 *   200  { "corrections": [ { "idx": 0, "type": "AGODA_EMAIL_CUSTOMER" } ] }
 *
 * A candidate the service cannot place is left out of "corrections" and keeps its local type.
 *
 * Three things keep this off the critical path of a high volume runtime:
 *  - only values whose local type is a configured trigger are ever sent
 *  - answers are cached by value, negative answers included, so repeats cost nothing
 *  - a circuit breaker stops calling after repeated failures, since runtime processing is
 *    single threaded and a hanging dependency would otherwise throttle the whole pipeline
 *
 * Every failure path returns "no corrections" rather than throwing.
 */
public class HttpDetectionCorrector implements DetectionCorrector {

    private static final LoggerMaker loggerMaker = new LoggerMaker(HttpDetectionCorrector.class);
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final Gson gson = new Gson();

    /** Cached marker for "the service looked and had nothing to say about this value". */
    private static final String NO_CORRECTION = "__AKTO_NO_CORRECTION__";


    private final DetectionCorrectorConfig config;
    private final OkHttpClient client;
    private final String authHeader;
    private final Map<String, CacheEntry> cache;

    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile long breakerOpenUntilMs = 0L;

    private static class CacheEntry {
        final String label;
        final long insertedAtMs;

        CacheEntry(String label, long insertedAtMs) {
            this.label = label;
            this.insertedAtMs = insertedAtMs;
        }
    }

    public HttpDetectionCorrector(DetectionCorrectorConfig config) {
        this.config = config;

        this.client = CoreHTTPClient.client.newBuilder()
                .connectTimeout(config.getTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(config.getTimeoutMs(), TimeUnit.MILLISECONDS)
                .writeTimeout(config.getTimeoutMs(), TimeUnit.MILLISECONDS)
                .callTimeout(config.getTimeoutMs(), TimeUnit.MILLISECONDS)
                .build();

        String token = config.getAuthToken();
        this.authHeader = (token == null || token.trim().isEmpty()) ? null : "Bearer " + token.trim();

        loggerMaker.info("[detection-corrector] ready: url=" + config.getUrl()
                + " triggers=" + config.getTriggerSubTypes()
                + " timeoutMs=" + config.getTimeoutMs()
                + " typeAliases=" + config.getTypeAliases()
                + " maxBatchSize=" + config.getMaxBatchSize()
                + " cacheSize=" + config.getCacheSize()
                + " auth=" + (authHeader == null ? "none" : "bearer")
                + " debugLogging=" + DetectionCorrectorRegistry.isDebugEnabled());

        final int maxEntries = config.getCacheSize();
        this.cache = Collections.synchronizedMap(new LinkedHashMap<String, CacheEntry>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                return size() > maxEntries;
            }
        });
    }

    @Override
    public boolean isTrigger(SubType localSubType) {
        if (localSubType == null || localSubType.getName() == null) return false;
        return config.getTriggerSubTypes().contains(localSubType.getName());
    }

    @Override
    public Map<Integer, String> correct(List<DetectionCandidate> candidates) {
        Map<Integer, String> out = new HashMap<>();
        if (candidates == null || candidates.isEmpty()) return out;

        // Distinct values that still need an answer, each mapped to the candidates waiting on it.
        Map<String, List<DetectionCandidate>> unresolved = new LinkedHashMap<>();
        int cacheHits = 0;
        int duplicates = 0;

        for (DetectionCandidate candidate : candidates) {
            String value = candidate.getValue();
            if (value == null) continue;

            String cached = lookup(value);
            if (cached != null) {
                cacheHits++;
                if (!NO_CORRECTION.equals(cached)) out.put(candidate.getIdx(), cached);
                continue;
            }
            List<DetectionCandidate> waiting = unresolved.get(value);
            if (waiting == null) {
                waiting = new ArrayList<>();
                unresolved.put(value, waiting);
            } else {
                duplicates++;   // same value already queued in this batch, costs no extra lookup
            }
            waiting.add(candidate);
        }

        if (unresolved.isEmpty()) {
            loggerMaker.info("[detection-corrector] candidates=" + candidates.size()
                    + " cacheHits=" + cacheHits + " lookups=0 (no call made) cacheSize=" + cache.size());
            return out;
        }

        if (isBreakerOpen()) {
            loggerMaker.warn("[detection-corrector] breaker is open, skipping " + unresolved.size()
                    + " unresolved values and keeping their locally detected types");
            return out;
        }

        // candidates = cacheHits + duplicates + lookups. Kept separate on purpose: cache hits and
        // in-batch duplicates avoid work for different reasons and tune differently.
        loggerMaker.info("[detection-corrector] candidates=" + candidates.size()
                + " cacheHits=" + cacheHits
                + " inBatchDuplicates=" + duplicates
                + " lookups=" + unresolved.size()
                + " cacheSize=" + cache.size());

        List<Map.Entry<String, List<DetectionCandidate>>> pending = new ArrayList<>(unresolved.entrySet());
        int batchSize = config.getMaxBatchSize();

        for (int from = 0; from < pending.size(); from += batchSize) {
            int to = Math.min(from + batchSize, pending.size());
            List<Map.Entry<String, List<DetectionCandidate>>> chunk = pending.subList(from, to);

            Map<Integer, String> answers = post(chunk);
            if (answers == null) {
                // Call failed. Leave the rest uncached so they are retried later, and stop early:
                // the breaker has been told, and hammering a sick dependency helps nobody.
                return out;
            }

            long now = System.currentTimeMillis();
            for (int i = 0; i < chunk.size(); i++) {
                Map.Entry<String, List<DetectionCandidate>> entry = chunk.get(i);
                String label = answers.get(i);

                // Negative answers are cached too, else every unrecognised value is re-asked forever.
                cache.put(entry.getKey(), new CacheEntry(label == null ? NO_CORRECTION : label, now));

                if (label == null) continue;
                for (DetectionCandidate waiting : entry.getValue()) {
                    out.put(waiting.getIdx(), label);
                }
            }
        }

        return out;
    }

    /**
     * @return chunk relative idx -> corrected type for the entries the service placed, or null if
     *         the call failed. An empty map means the service answered and placed nothing.
     */
    private Map<Integer, String> post(List<Map.Entry<String, List<DetectionCandidate>>> chunk) {
        long start = System.currentTimeMillis();
        try {
            JsonArray detections = new JsonArray();
            for (int i = 0; i < chunk.size(); i++) {
                Map.Entry<String, List<DetectionCandidate>> entry = chunk.get(i);
                DetectionCandidate representative = entry.getValue().get(0);

                JsonObject detection = new JsonObject();
                detection.addProperty("idx", i);
                detection.addProperty("jsonpath", representative.getJsonPath());
                detection.addProperty("value", entry.getKey());
                // Akto's name is translated to the classifier's vocabulary here.
                detection.addProperty("type", config.wireTypeFor(representative.getType()));
                detections.add(detection);
            }

            JsonObject payload = new JsonObject();
            payload.add("detections", detections);

            Request.Builder builder = new Request.Builder()
                    .url(config.getUrl())
                    .post(RequestBody.create(gson.toJson(payload), JSON))
                    .header("Content-Type", "application/json");
            if (authHeader != null) builder.header("Authorization", authHeader);

            String requestBody = gson.toJson(payload);
            if (DetectionCorrectorRegistry.isDebugEnabled()) {
                // Contains raw values: only ever emitted when debugging is explicitly turned on.
                loggerMaker.info("[detection-corrector] POST " + config.getUrl() + " body=" + requestBody);
            }

            try (Response response = client.newCall(builder.build()).execute()) {
                long latencyMs = System.currentTimeMillis() - start;

                if (!response.isSuccessful() || response.body() == null) {
                    onFailure("POST " + config.getUrl() + " returned status " + response.code()
                            + " after " + latencyMs + "ms for " + chunk.size() + " values");
                    return null;
                }

                String responseBody = response.body().string();
                if (DetectionCorrectorRegistry.isDebugEnabled()) {
                    loggerMaker.info("[detection-corrector] response=" + responseBody);
                }

                Map<Integer, String> parsed = parse(responseBody);
                onSuccess();
                loggerMaker.info("[detection-corrector] sent=" + chunk.size() + " placed="
                        + parsed.size() + " status=" + response.code() + " tookMs=" + latencyMs);
                return parsed;
            }
        } catch (Exception e) {
            onFailure("POST " + config.getUrl() + " failed after "
                    + (System.currentTimeMillis() - start) + "ms for " + chunk.size()
                    + " values: " + e.getClass().getSimpleName() + " " + e.getMessage());
            return null;
        }
    }

    Map<Integer, String> parse(String body) {
        Map<Integer, String> corrections = new HashMap<>();
        if (body == null || body.trim().isEmpty()) return corrections;

        JsonObject root = gson.fromJson(body, JsonObject.class);
        if (root == null || !root.has("corrections")) return corrections;

        JsonElement correctionsElement = root.get("corrections");
        if (!correctionsElement.isJsonArray()) return corrections;

        for (JsonElement element : correctionsElement.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject correction = element.getAsJsonObject();
            if (!correction.has("idx") || !correction.has("type")) continue;

            String type = correction.get("type").getAsString();
            if (type == null || type.trim().isEmpty()) continue;

            corrections.put(correction.get("idx").getAsInt(), type.trim());
        }

        return corrections;
    }

    private String lookup(String value) {
        CacheEntry entry = cache.get(value);
        if (entry == null) return null;

        long ageMs = System.currentTimeMillis() - entry.insertedAtMs;
        if (ageMs > config.getCacheTtlSeconds() * 1000L) {
            cache.remove(value);
            return null;
        }

        return entry.label;
    }

    private void onSuccess() {
        consecutiveFailures.set(0);
    }


    private void onFailure(String message) {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= config.getFailureThreshold()) {
            breakerOpenUntilMs = System.currentTimeMillis() + (config.getBreakerCoolOffSeconds() * 1000L);
            loggerMaker.error("[detection-corrector] breaker OPENED after " + failures
                    + " consecutive failures, pausing calls for " + config.getBreakerCoolOffSeconds()
                    + "s and keeping locally detected types. Last error: " + message);
        } else {
            loggerMaker.error("[detection-corrector] failure " + failures + "/"
                    + config.getFailureThreshold() + ": " + message);
        }
    }

    private boolean isBreakerOpen() {
        long openUntil = breakerOpenUntilMs;
        if (openUntil == 0L) return false;
        if (System.currentTimeMillis() >= openUntil) {
            // Cool off elapsed: let one batch through and see how it goes.
            breakerOpenUntilMs = 0L;
            consecutiveFailures.set(0);
            loggerMaker.info("[detection-corrector] breaker cool-off elapsed, probing the service again");
            return false;
        }
        return true;
    }

    /** Test and metrics helper. */
    public int cacheSize() {
        return cache.size();
    }
}
