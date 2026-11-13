package com.akto.s3_export;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.json.JSONObject;

import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ExportMaliciousEventsResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListMaliciousRequestsResponse.MaliciousEvent;
import com.akto.utils.threat_detection.ThreatDetectionBackendClient;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;

/** Atlas guardrail activity, from threat-detection-backend. */
public class GuardrailEventsExportSource implements S3ExportSource {

    // Atlas only for now; adding AGENTIC later is a one-line change here
    static final List<CONTEXT_SOURCE> CONTEXT_SOURCES = Arrays.asList(CONTEXT_SOURCE.ENDPOINT);
    private static final int PAGE_LIMIT = 1000;

    private static final String META_RULE = "rule_violated";
    private static final String META_RULE_CAMEL = "ruleViolated";
    private static final String META_BEHAVIOUR = "behaviour";
    private static final String META_REASON = "reason";
    private static final String META_REASON_ALT = "nreason";
    private static final String META_RISK_SCORE = "risk_score";
    private static final String META_RISK_SCORE_CAMEL = "riskScore";

    @Override
    public void export(int accountId, long startMs, long endMs, boolean includePayloads,
                       Consumer<Map<String, Object>> consumer) throws Exception {
        long startTs = toEpochSeconds(startMs);
        long endTs = toEpochSeconds(endMs);
        for (CONTEXT_SOURCE contextSource : CONTEXT_SOURCES) {
            String cursor = "";
            do {
                ExportMaliciousEventsResponse response = ThreatDetectionBackendClient.exportMaliciousEvents(
                    accountId, startTs, endTs, cursor, PAGE_LIMIT, includePayloads, contextSource.name());
                for (MaliciousEvent event : response.getEventsList()) {
                    consumer.accept(toRow(event, contextSource, includePayloads));
                }
                cursor = response.getNextCursor();
            } while (!cursor.isEmpty());
        }
    }

    /** Hour boundaries are whole seconds, so the half-open window converts exactly. */
    static long toEpochSeconds(long ms) {
        if (ms % 1000 != 0) throw new IllegalArgumentException("Window boundary is not a whole second: " + ms);
        return ms / 1000;
    }

    static Map<String, Object> toRow(MaliciousEvent event, CONTEXT_SOURCE contextSource, boolean includePayloads) {
        List<String> owaspIds = new ArrayList<>();
        event.getOwaspCategoriesList().forEach(category -> owaspIds.add(category.getId()));

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", event.getId());
        row.put("refId", event.getRefId());
        row.put("detectedAt", event.getDetectedAt());
        row.put("detectedAtIso", Instant.ofEpochSecond(event.getDetectedAt()).toString());
        row.put("contextSource", contextSource.name());
        row.put("policy", event.getFilterId());
        row.putAll(metadataFields(event.getMetadata()));
        row.put("severity", event.getSeverity());
        row.put("status", event.getStatus());
        row.put("humanResponse", event.getHumanResponse());
        row.put("actor", event.getActor());
        row.put("host", event.getHost());
        row.put("ip", event.getIp());
        row.put("country", event.getCountry());
        row.put("endpoint", event.getEndpoint());
        row.put("method", event.getMethod());
        row.put("sessionId", event.getSessionId());
        row.put("category", event.getCategory());
        row.put("subCategory", event.getSubCategory());
        row.put("label", event.getLabel());
        row.put("owaspCategories", owaspIds);
        row.put("remediation", event.getRemediation());
        if (includePayloads) row.put("payload", event.getPayload());
        return row;
    }

    /** rule, action, reason and riskScore out of the event's metadata JSON; blank/null when absent or unparseable. */
    static Map<String, Object> metadataFields(String metadataJson) {
        JSONObject metadata = parseMetadata(metadataJson);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("rule", firstString(metadata, META_RULE, META_RULE_CAMEL));
        fields.put("action", firstString(metadata, META_BEHAVIOUR));
        fields.put("reason", firstString(metadata, META_REASON, META_REASON_ALT));
        fields.put("riskScore", riskScore(metadata));
        return fields;
    }

    private static JSONObject parseMetadata(String metadata) {
        if (metadata == null || metadata.isEmpty()) return new JSONObject();
        try {
            return new JSONObject(metadata);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static String firstString(JSONObject json, String... keys) {
        for (String key : keys) {
            String value = json.optString(key, "");
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    // null when absent or not a number
    private static Double riskScore(JSONObject metadata) {
        for (String key : new String[] {META_RISK_SCORE_CAMEL, META_RISK_SCORE}) {
            double value = metadata.optDouble(key, Double.NaN);
            if (!Double.isNaN(value)) return value;
        }
        return null;
    }
}
