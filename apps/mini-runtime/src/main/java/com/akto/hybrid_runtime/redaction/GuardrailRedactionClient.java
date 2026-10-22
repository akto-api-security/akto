package com.akto.hybrid_runtime.redaction;

import com.akto.hybrid_runtime.redaction.RedactionResult.RedactionStatus;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.SecretUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * Asks guardrails-service to mask payloads (POST /api/redact/payloads). The only code in mini-runtime
 * that talks to it.
 *
 * Every item that was sent gets a result, always. A timeout, a refused call, an open circuit
 * breaker, a malformed answer or an item the service did not answer all become UNAVAILABLE, so a
 * failure can never read as "nothing to mask". There are no retries: the caller stores the payload
 * as it is, marked unredacted, and the next occurrence tries again.
 */
public class GuardrailRedactionClient {

    private static final LoggerMaker loggerMaker = new LoggerMaker(GuardrailRedactionClient.class, LogDb.RUNTIME);
    private static final String PATH = "/api/redact/payloads";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    /** One HTTP exchange; replaced by a fake in tests. Returns the response body of a 2xx answer. */
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

    /** Null when redaction is not enabled. */
    public static GuardrailRedactionClient fromEnv(RedactionConfig config) {
        if (config == null || !config.enabled) {
            return null;
        }
        return new GuardrailRedactionClient(config, new OkHttpTransport(), System::currentTimeMillis);
    }

    /**
     * Redacts the items. Nothing about the request other than method, host and path is sent: which
     * policies apply is guardrails-service's decision alone. Returns a result for every item id.
     * Calls are made chunk by chunk until deadlineAtMs (epoch millis); chunks that do not fit
     * before it are answered UNAVAILABLE without a call.
     */
    public Map<String, RedactionResult> redact(List<RedactionItem> items, long deadlineAtMs) {
        Map<String, RedactionResult> results = new HashMap<>();
        if (items == null || items.isEmpty()) {
            return results;
        }

        // A side over the size cap is never sent; it is answered locally. The other side still goes.
        // An item with no payload at all has nothing to redact and is not sent.
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

    /** Adds UNAVAILABLE for any side that was held back for size. */
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
            // Never log payloads. Transport errors name only the endpoint and cause; for anything else
            // (a parse error quotes the text it choked on) only the type is logged.
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

    /**
     * Matches the answer to the items by id. An item the service did not answer, or a side it left
     * out, is UNAVAILABLE. A REDACTED or REDACTED_BASIC side without a payload is UNAVAILABLE too: an
     * empty string must never replace a real payload.
     */
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
