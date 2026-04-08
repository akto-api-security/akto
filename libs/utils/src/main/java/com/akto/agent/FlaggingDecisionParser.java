package com.akto.agent;

import java.util.ArrayList;
import java.util.List;

import com.akto.dto.testing.FlaggingCriterion;
import com.akto.dto.testing.FlaggingDecision;
import com.akto.dto.testing.FlaggingEvidenceItem;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Parses optional flagging decision payloads from agent /chat JSON.
 * Supports nested {@code flaggingDecision} or flat {@code flaggingSummary} / {@code flaggingEvidence} / {@code flaggingCriteria}.
 */
public final class FlaggingDecisionParser {

    private FlaggingDecisionParser() {}

    public static FlaggingDecision parse(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        JsonNode nested = root.get("flaggingDecision");
        if (nested != null && nested.isObject()) {
            return parseObject(nested);
        }
        if (root.has("flaggingSummary") || root.has("flaggingEvidence") || root.has("flaggingCriteria")) {
            return parseFlat(root);
        }
        return null;
    }

    private static FlaggingDecision parseObject(JsonNode o) {
        String summary = textOrNull(o.get("summary"));
        if (summary == null) {
            summary = textOrNull(o.get("flaggingSummary"));
        }
        List<FlaggingEvidenceItem> evidence = parseEvidenceArray(o.get("evidence"));
        if (evidence.isEmpty() && o.has("flaggingEvidence")) {
            evidence = parseEvidenceArray(o.get("flaggingEvidence"));
        }
        List<FlaggingCriterion> criteria = parseCriteriaArray(o.get("criteria"));
        if (criteria.isEmpty() && o.has("flaggingCriteria")) {
            criteria = parseCriteriaArray(o.get("flaggingCriteria"));
        }
        if (isEmptyDecision(summary, evidence, criteria)) {
            return null;
        }
        return new FlaggingDecision(summary, evidence, criteria);
    }

    private static FlaggingDecision parseFlat(JsonNode root) {
        String summary = textOrNull(root.get("flaggingSummary"));
        List<FlaggingEvidenceItem> evidence = parseEvidenceArray(root.get("flaggingEvidence"));
        List<FlaggingCriterion> criteria = parseCriteriaArray(root.get("flaggingCriteria"));
        if (isEmptyDecision(summary, evidence, criteria)) {
            return null;
        }
        return new FlaggingDecision(summary, evidence, criteria);
    }

    private static boolean isEmptyDecision(String summary, List<FlaggingEvidenceItem> evidence, List<FlaggingCriterion> criteria) {
        boolean hasSummary = summary != null && !summary.trim().isEmpty();
        boolean hasEvidence = evidence != null && !evidence.isEmpty();
        boolean hasCriteria = criteria != null && !criteria.isEmpty();
        return !hasSummary && !hasEvidence && !hasCriteria;
    }

    private static List<FlaggingEvidenceItem> parseEvidenceArray(JsonNode arr) {
        List<FlaggingEvidenceItem> out = new ArrayList<>();
        if (arr == null || !arr.isArray()) {
            return out;
        }
        for (JsonNode n : arr) {
            if (n == null || !n.isObject()) {
                continue;
            }
            String source = textOrNull(n.get("source"));
            String excerpt = textOrNull(n.get("excerpt"));
            Integer start = intOrNull(n.get("start"));
            Integer end = intOrNull(n.get("end"));
            if (excerpt != null && !excerpt.isEmpty()) {
                out.add(new FlaggingEvidenceItem(source, excerpt, start, end));
            }
        }
        return out;
    }

    private static List<FlaggingCriterion> parseCriteriaArray(JsonNode arr) {
        List<FlaggingCriterion> out = new ArrayList<>();
        if (arr == null || !arr.isArray()) {
            return out;
        }
        for (JsonNode n : arr) {
            if (n == null || !n.isObject()) {
                continue;
            }
            String id = textOrNull(n.get("id"));
            String name = textOrNull(n.get("name"));
            boolean met = n.has("met") && n.get("met").asBoolean(false);
            if (name != null && !name.isEmpty() || id != null && !id.isEmpty()) {
                out.add(new FlaggingCriterion(id, name, met));
            }
        }
        return out;
    }

    private static String textOrNull(JsonNode n) {
        if (n == null || n.isNull() || !n.isTextual()) {
            return null;
        }
        String t = n.asText();
        return t == null || t.isEmpty() ? null : t;
    }

    private static Integer intOrNull(JsonNode n) {
        if (n == null || n.isNull() || !n.isNumber()) {
            return null;
        }
        return n.intValue();
    }
}
