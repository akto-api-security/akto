package com.akto.agent_risk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.akto.kafka.AgentRiskKafkaProducer;
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

/** POST {AGENT_EMBED_SERVICE_URL}/embed. Fail-open. kNN stays on Elasticsearch. */
public class EmbedKnnClient {

    private static final MediaType JSON = MediaType.parse("application/json");
    private static final Gson gson = new Gson();
    private static final EmbedKnnClient INSTANCE = new EmbedKnnClient();

    public static EmbedKnnClient instance() {
        return INSTANCE;
    }

    private final OkHttpClient http = CoreHTTPClient.client.newBuilder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build();

    public boolean isConfigured() {
        return AgentRiskKafkaProducer.getEmbedServiceUrl() != null;
    }

    public List<Double> embed(String text) {
        String base = AgentRiskKafkaProducer.getEmbedServiceUrl();
        if (base == null) {
            return null;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", text == null ? "" : text);
        Request request = new Request.Builder()
                .url(base + "/embed")
                .post(RequestBody.create(gson.toJson(body), JSON))
                .addHeader("Content-Type", "application/json")
                .build();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }
            JsonObject resp = gson.fromJson(response.body().string(), JsonObject.class);
            if (resp == null || !resp.has("vector") || !resp.get("vector").isJsonArray()) {
                return null;
            }
            JsonArray arr = resp.getAsJsonArray("vector");
            List<Double> out = new ArrayList<>(arr.size());
            for (JsonElement el : arr) {
                if (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
                    out.add(el.getAsDouble());
                }
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }
}
