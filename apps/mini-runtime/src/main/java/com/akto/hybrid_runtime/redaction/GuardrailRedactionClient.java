package com.akto.hybrid_runtime.redaction;

import com.akto.hybrid_runtime.redaction.RedactionResult.RedactionStatus;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.SecretUtils;
import com.google.gson.*;
import okhttp3.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/** The only mini-runtime code that calls POST /api/redact/payloads. Every item gets a result; any failure is UNAVAILABLE, never "nothing to mask". */
public class GuardrailRedactionClient {

    private static final LoggerMaker loggerMaker = new LoggerMaker(GuardrailRedactionClient.class, LogDb.RUNTIME);
    private static final String PATH = "/api/redact/payloads";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    interface Transport {
        String post(String url, String body, String token, int connectTimeoutMs, int callTimeoutMs) throws IOException;
    }

    private final RedactionConfig config;
    private final Transport transport;
    private final LongSupplier clock;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile long breakerOpenUntil;

    GuardrailRedactionClient(RedactionConfig config, Transport transport, LongSupplier clock) {
        this.config = config;
        this.transport = transport;
        this.clock = clock;
    }

    public static GuardrailRedactionClient fromEnv(RedactionConfig config) {
        if (config == null || !config.enabled) {
            return null;
        }
        return new GuardrailRedactionClient(config, new OkHttpTransport(), System::currentTimeMillis);
    }

    public Map<String, RedactionResult> redact(List<RedactionItem> items, long deadlineAtMs) {
        Map<String, RedactionResult> results = new HashMap<>();
        if (items == null || items.isEmpty()) {
            return results;
        }

        List<RedactionItem> sendable = new ArrayList<>(items.size());
        Map<String, boolean[]> oversize = new HashMap<>();
        for (RedactionItem item : items) {
            if (item.getRequestPayload() == null && item.getResponsePayload() == null) {
                results.put(item.getId(), new RedactionResult(item.getId(), null, null));
                continue;
            }
            boolean reqTooBig = tooBig(item.getRequestPayload());
            boolean respTooBig = tooBig(item.getResponsePayload());
            if (reqTooBig || respTooBig) {
                oversize.put(item.getId(), new boolean[]{reqTooBig, respTooBig});
            }
            sendable.add(new RedactionItem(item.getId(), item.getMethod(), item.getHost(), item.getPath(),
                    reqTooBig ? null : item.getRequestPayload(), respTooBig ? null : item.getResponsePayload()));
        }

        Map<String, RedactionItem> originals = new HashMap<>();
        for (RedactionItem item : items) {
            originals.put(item.getId(), item);
        }

        for (int from = 0; from < sendable.size(); from += config.chunkSize) {
            List<RedactionItem> chunk = sendable.subList(from, Math.min(from + config.chunkSize, sendable.size()));
            Map<String, RedactionResult> answered = callChunk(chunk, deadlineAtMs);
            for (RedactionItem sent : chunk) {
                RedactionItem original = originals.get(sent.getId());
                RedactionResult answer = answered.get(sent.getId());
                results.put(sent.getId(), withOversizeSides(original, answer, oversize.get(sent.getId())));
            }
        }
        return results;
    }

    private boolean tooBig(String payload) {
        return payload != null && payload.length() > config.maxPayloadBytes;
    }

    private static RedactionResult withOversizeSides(RedactionItem original, RedactionResult answer, boolean[] tooBig) {
        if (tooBig == null) {
            return answer;
        }
        RedactionResult.Side request = answer.getRequest();
        RedactionResult.Side response = answer.getResponse();
        if (tooBig[0]) {
            request = new RedactionResult.Side(RedactionStatus.UNAVAILABLE, null);
        }
        if (tooBig[1]) {
            response = new RedactionResult.Side(RedactionStatus.UNAVAILABLE, null);
        }
        return new RedactionResult(original.getId(), request, response);
    }

    private Map<String, RedactionResult> callChunk(List<RedactionItem> chunk, long deadlineAtMs) {
        long remaining = deadlineAtMs - clock.getAsLong();
        if (remaining <= 0) {
            loggerMaker.info("Payload redaction budget used up; " + chunk.size() + " item(s) left unredacted");
            return allUnavailable(chunk);
        }
        if (breakerIsOpen()) {
            return allUnavailable(chunk);
        }

        int callTimeout = (int) Math.min(config.callTimeoutMs, remaining);
        try {
            String body = buildRequest(chunk);
            String token = SecretUtils.readSecret("DATABASE_ABSTRACTOR_SERVICE_TOKEN");
            String response = transport.post(config.baseUrl + PATH, body, token, config.connectTimeoutMs, callTimeout);
            Map<String, RedactionResult> parsed = parseResponse(chunk, response);
            consecutiveFailures.set(0);
            return parsed;
        } catch (Exception e) {
            recordFailure();
            // Never log payloads: parse errors quote the text they choked on, so only the type is logged.
            loggerMaker.errorAndAddToDb("Payload redaction call failed for " + chunk.size() + " item(s): "
                    + (e instanceof IOException ? e.toString() : e.getClass().getSimpleName()));
            return allUnavailable(chunk);
        }
    }

    private boolean breakerIsOpen() {
        return consecutiveFailures.get() >= config.breakerThreshold && clock.getAsLong() < breakerOpenUntil;
    }

    private void recordFailure() {
        if (consecutiveFailures.incrementAndGet() >= config.breakerThreshold) {
            breakerOpenUntil = clock.getAsLong() + config.breakerOpenMs;
        }
    }

    static String buildRequest(List<RedactionItem> chunk) {
        JsonObject root = new JsonObject();
        JsonArray array = new JsonArray();
        for (RedactionItem item : chunk) {
            JsonObject o = new JsonObject();
            o.addProperty("id", item.getId());
            o.addProperty("method", item.getMethod() == null ? "" : item.getMethod());
            o.addProperty("host", item.getHost() == null ? "" : item.getHost());
            o.addProperty("path", item.getPath() == null ? "" : item.getPath());
            if (item.getRequestPayload() != null) {
                o.addProperty("requestPayload", item.getRequestPayload());
            }
            if (item.getResponsePayload() != null) {
                o.addProperty("responsePayload", item.getResponsePayload());
            }
            array.add(o);
        }
        root.add("items", array);
        return root.toString();
    }

    static Map<String, RedactionResult> parseResponse(List<RedactionItem> chunk, String responseBody) {
        Map<String, JsonObject> byId = new HashMap<>();
        JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
        JsonElement itemsElement = root.get("items");
        if (itemsElement != null && itemsElement.isJsonArray()) {
            for (JsonElement e : itemsElement.getAsJsonArray()) {
                if (e.isJsonObject() && e.getAsJsonObject().has("id")) {
                    byId.put(e.getAsJsonObject().get("id").getAsString(), e.getAsJsonObject());
                }
            }
        }

        Map<String, RedactionResult> results = new HashMap<>();
        for (RedactionItem sent : chunk) {
            JsonObject answer = byId.get(sent.getId());
            results.put(sent.getId(), new RedactionResult(sent.getId(),
                    sideOf(sent.getRequestPayload(), answer, "request"),
                    sideOf(sent.getResponsePayload(), answer, "response")));
        }
        return results;
    }

    private static RedactionResult.Side sideOf(String sentPayload, JsonObject answer, String key) {
        if (sentPayload == null) {
            return null;
        }
        JsonElement element = answer == null ? null : answer.get(key);
        if (element == null || !element.isJsonObject()) {
            return new RedactionResult.Side(RedactionStatus.UNAVAILABLE, null);
        }
        JsonObject side = element.getAsJsonObject();
        RedactionStatus status = RedactionStatus.parse(
                side.has("status") && !side.get("status").isJsonNull() ? side.get("status").getAsString() : null);
        String payload = side.has("payload") && !side.get("payload").isJsonNull() ? side.get("payload").getAsString() : null;
        if (RedactionResult.Side.carriesPayload(status) && (payload == null || payload.isEmpty())) {
            status = RedactionStatus.UNAVAILABLE;
        }
        return new RedactionResult.Side(status, payload);
    }

    private static Map<String, RedactionResult> allUnavailable(List<RedactionItem> chunk) {
        Map<String, RedactionResult> results = new HashMap<>();
        for (RedactionItem item : chunk) {
            results.put(item.getId(), RedactionResult.unavailable(item));
        }
        return results;
    }

    private static final class OkHttpTransport implements Transport {
        private final OkHttpClient base = new OkHttpClient();

        @Override
        public String post(String url, String body, String token, int connectTimeoutMs, int callTimeoutMs) throws IOException {
            OkHttpClient client = base.newBuilder()
                    .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                    .readTimeout(callTimeoutMs, TimeUnit.MILLISECONDS)
                    .writeTimeout(callTimeoutMs, TimeUnit.MILLISECONDS)
                    .callTimeout(callTimeoutMs, TimeUnit.MILLISECONDS)
                    .build();
            Request.Builder request = new Request.Builder().url(url).post(RequestBody.create(body, JSON));
            if (token != null && !token.isEmpty()) {
                request.header("Authorization", token);
            }
            try (Response response = client.newCall(request.build()).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new IOException("guardrails-service answered HTTP " + response.code());
                }
                return response.body().string();
            }
        }
    }
}
