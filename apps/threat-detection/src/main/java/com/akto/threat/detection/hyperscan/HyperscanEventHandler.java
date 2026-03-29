package com.akto.threat.detection.hyperscan;

import com.akto.dto.ApiInfo;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.RawApiMetadata;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.enums.RedactionType;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.proto.generated.threat_detection.message.malicious_event.event_type.v1.EventType;
import com.akto.proto.generated.threat_detection.message.sample_request.v1.SampleMaliciousRequest;
import com.akto.proto.generated.threat_detection.message.sample_request.v1.SchemaConformanceError;
import com.akto.threat.detection.utils.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles Hyperscan-based threat detection and event generation.
 * Scans requests/responses using HyperscanThreatMatcher, groups matches by category,
 * and pushes each category as a separate malicious event through the existing pipeline.
 */
public class HyperscanEventHandler {

    private static final LoggerMaker logger = new LoggerMaker(HyperscanEventHandler.class, LogDb.THREAT_DETECTION);

    @FunctionalInterface
    public interface MaliciousEventPusher {
        void push(FilterConfig filter, String actor, HttpResponseParams responseParam,
                  SampleMaliciousRequest maliciousReq, EventType eventType);
    }

    private final MaliciousEventPusher eventPusher;

    public HyperscanEventHandler(MaliciousEventPusher eventPusher) {
        this.eventPusher = eventPusher;
    }

    /**
     * Run Hyperscan detection on a request/response and push events for each detected category.
     *
     * @return true if any threat was detected
     */
    public boolean detectAndPushEvents(
            HttpResponseParams responseParam,
            ApiInfo.ApiInfoKey apiInfoKey,
            String actor,
            RawApiMetadata metadata,
            boolean successfulExploit,
            boolean isIgnoredEvent,
            RedactionType redactionType) {

        HyperscanThreatMatcher matcher = HyperscanThreatMatcher.getInstance();
        if (!matcher.isInitialized()) {
            return false;
        }

        try {
            Map<String, List<SchemaConformanceError>> errorsByCategory = new HashMap<>();

            // Scan request
            if (responseParam.getRequestParams() != null) {
                Map<String, List<HyperscanThreatMatcher.MatchResult>> reqMatches = matcher.scanRequest(
                        safeStr(responseParam.getRequestParams().getURL()),
                        String.valueOf(responseParam.getRequestParams().getHeaders()),
                        safeStr(responseParam.getRequestParams().getPayload()));
                collectMatchErrors(reqMatches, errorsByCategory);
            }

            // Scan response
            Map<String, List<HyperscanThreatMatcher.MatchResult>> respMatches = matcher.scanResponse(
                    responseParam.getHeaders() != null ? String.valueOf(responseParam.getHeaders()) : "",
                    safeStr(responseParam.getPayload()));
            collectMatchErrors(respMatches, errorsByCategory);

            if (errorsByCategory.isEmpty()) {
                return false;
            }

            // Push one event per category
            for (Map.Entry<String, List<SchemaConformanceError>> entry : errorsByCategory.entrySet()) {
                FilterConfig filter = Utils.buildHyperscanFilterConfig(entry.getKey(), null);
                SampleMaliciousRequest req = Utils.buildSampleMaliciousRequest(
                        actor, responseParam, filter, metadata,
                        entry.getValue(), successfulExploit, isIgnoredEvent, redactionType);
                eventPusher.push(filter, actor, responseParam, req, EventType.EVENT_TYPE_SINGLE);
            }

            logger.debugAndAddToDb("Hyperscan detected " + errorsByCategory.size()
                    + " threat categories for " + apiInfoKey.getUrl() + " actor: " + actor);
            return true;

        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error in Hyperscan detection: " + e.getMessage());
            return false;
        }
    }

    private static void collectMatchErrors(
            Map<String, List<HyperscanThreatMatcher.MatchResult>> matches,
            Map<String, List<SchemaConformanceError>> errorsByCategory) {

        for (List<HyperscanThreatMatcher.MatchResult> matchList : matches.values()) {
            for (HyperscanThreatMatcher.MatchResult match : matchList) {
                SchemaConformanceError error = SchemaConformanceError.newBuilder()
                        .setMessage(String.format("%s [chars %d-%d]", match.matchedText, match.startOffset, match.endOffset))
                        .setSchemaPath(match.prefix)
                        .setInstancePath(match.location)
                        .setAttribute("threat_detected")
                        .setLocation(convertLocation(match.location))
                        .setStart(-1)
                        .setEnd(-1)
                        .setPhrase(match.matchedText)
                        .build();

                String topCategory = extractTopCategory(match.prefix);
                errorsByCategory.computeIfAbsent(topCategory, k -> new ArrayList<>()).add(error);
            }
        }
    }

    static String extractTopCategory(String prefix) {
        if (prefix == null || prefix.isEmpty()) return "unknown";
        int idx = prefix.indexOf('_');
        return idx > 0 ? prefix.substring(0, idx) : prefix;
    }

    private static SchemaConformanceError.Location convertLocation(String location) {
        switch (location) {
            case "url": return SchemaConformanceError.Location.LOCATION_URL;
            case "headers":
            case "resp_headers": return SchemaConformanceError.Location.LOCATION_HEADER;
            case "body":
            case "resp_body": return SchemaConformanceError.Location.LOCATION_BODY;
            default: return SchemaConformanceError.Location.LOCATION_UNSPECIFIED;
        }
    }

    private static String safeStr(String s) {
        return s != null ? s : "";
    }
}
