package com.akto.runtime.parser;

import com.akto.util.Constants;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Map;

/**
 * Normalizes the Kafka ingest root field {@code tag} (string or object) to a JSON string for {@link com.akto.dto.HttpResponseParams#getTags()}.
 */
public final class IngestRootTagParser {

    private static final Gson GSON = new Gson();

    private IngestRootTagParser() {}

    /**
     * @return JSON object string suitable for tags field, or {@code null} if absent / not representable
     */
    public static String normalizeTagField(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String) {
            String s = ((String) raw).trim();
            if (s.isEmpty()) {
                return null;
            }
            try {
                JsonElement el = JsonParser.parseString(s);
                if (el.isJsonObject()) {
                    return el.getAsJsonObject().toString();
                }
            } catch (Exception ignored) {
                return null;
            }
            return null;
        }
        if (raw instanceof Map) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) raw;
                return GSON.toJson(m);
            } catch (Exception e) {
                return null;
            }
        }
        try {
            return GSON.toJson(raw);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isClickUpIngestService(String tagsJson) {
        if (tagsJson == null || tagsJson.isEmpty()) {
            return false;
        }
        try {
            JsonObject o = JsonParser.parseString(tagsJson).getAsJsonObject();
            if (!o.has(Constants.INGEST_TAG_SERVICE) || o.get(Constants.INGEST_TAG_SERVICE).isJsonNull()) {
                return false;
            }
            return Constants.INGEST_SERVICE_VALUE_CLICKUP.equalsIgnoreCase(o.get(Constants.INGEST_TAG_SERVICE).getAsString());
        } catch (Exception e) {
            return false;
        }
    }
}
