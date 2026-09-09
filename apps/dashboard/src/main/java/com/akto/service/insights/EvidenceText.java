package com.akto.service.insights;

import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.utils.guardrails.PromptSnippet;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;

/**
 * Best-effort evidence text for one violation event — shared by any provider that needs to read
 * or pattern-match what actually triggered a rule (credentials, prompt injection, false-positive
 * checks, ...).
 */
public final class EvidenceText {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private EvidenceText() {}

    /**
     * The guardrails validator writes a top-level "evidence" field into the (possibly stringified)
     * request/response payload when a scanner fires — the same field the dashboard's own
     * ViolationsPage.jsx reads (reqPayload?.evidence / respPayload?.evidence). Falls back to a
     * general prompt/text snippet (PromptSnippet) when no such field is present, so content
     * without a scanner-written evidence field is still checked.
     */
    public static String extract(DashboardMaliciousEvent event) {
        String payload = event.getPayload();
        if (StringUtils.isBlank(payload)) {
            return "";
        }
        try {
            JsonNode envelope = OBJECT_MAPPER.readTree(payload);
            for (String field : new String[]{"requestPayload", "responsePayload"}) {
                JsonNode inner = envelope.path(field);
                String innerText = inner.isTextual() ? inner.asText() : (inner.isMissingNode() || inner.isNull() ? "" : inner.toString());
                if (StringUtils.isBlank(innerText)) {
                    continue;
                }
                try {
                    JsonNode parsedInner = OBJECT_MAPPER.readTree(innerText);
                    JsonNode evidenceNode = parsedInner.path("evidence");
                    if (evidenceNode.isTextual() && StringUtils.isNotBlank(evidenceNode.asText())) {
                        return evidenceNode.asText();
                    }
                } catch (Exception innerParseFailed) {
                    // innerText wasn't JSON (or "evidence" wasn't a plain string) — fall through.
                }
            }
        } catch (Exception envelopeParseFailed) {
            // payload wasn't the expected envelope shape at all — fall through to the snippet.
        }
        return PromptSnippet.of(payload);
    }

    /** Lowercase + trim + collapse whitespace — the normalization idiom already used elsewhere in
     *  this package (AlertFatigueProvider's grouping keys, PromptSnippet's own truncate()), reused
     *  here so two occurrences of "Ignore   previous instructions" vs "ignore previous instructions"
     *  count as the same phrase without a full similarity library (none exists in this repo). */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim().toLowerCase(Locale.US);
    }
}
