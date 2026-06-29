package com.akto.action.gpt.handlers;

import com.akto.dao.test_editor.TestConfigYamlParser;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dto.test_editor.FilterNode;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.log.LoggerMaker;
import com.akto.util.Constants;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic, hallucination-free evidence for "missing / unwanted HTTP header"
 * tests (Akto MHH category). The truth is in the test's validate block, e.g.
 *   response_headers -> for_one -> key -> not_contains_either: [Cross-Origin-..., ...]
 * which fires when those security headers are ABSENT. Since an absent header has
 * nothing to highlight in the rendered response, we:
 *  - load the untruncated template from the DB and parse its validate conditions,
 *  - diff the expected/unwanted header names against the headers actually present,
 *  - emit a grouped "informational" segment for the genuinely-missing headers and
 *    a normal (highlightable) segment for any unwanted header that IS present.
 * When the validate has no header key-presence conditions (or the template can't be
 * loaded) this returns nothing, so the caller falls back to the LLM path.
 */
public class HeaderEvidenceDetector {

    private static final LoggerMaker loggerMaker = new LoggerMaker(HeaderEvidenceDetector.class, LoggerMaker.LogDb.DASHBOARD);

    // Operands on a header KEY. "missing" semantics flag absence (the header must
    // be present); "unwanted" semantics flag presence (the header must be absent).
    private static final Set<String> MISSING_OPERANDS = new HashSet<>(Arrays.asList("not_contains", "not_contains_either", "neq"));
    private static final Set<String> UNWANTED_OPERANDS = new HashSet<>(Arrays.asList("contains", "contains_either", "eq"));

    private static class HeaderCondition {
        String location;       // REQUEST or RESPONSE
        boolean missingCheck;  // true = expected header (flag if absent)
        List<String> headers;
    }

    public static BasicDBList detect(String category, BasicDBObject response, BasicDBObject request) {
        BasicDBList segments = new BasicDBList();
        try {
            FilterNode root = loadValidateRoot(category);
            if (root == null) {
                return segments;
            }

            List<HeaderCondition> conditions = new ArrayList<>();
            collect(root, null, null, conditions);
            if (conditions.isEmpty()) {
                return segments;
            }

            Map<String, String> responseHeaders = headerKeyMap(response);
            Map<String, String> requestHeaders = headerKeyMap(request);

            LinkedHashSet<String> missingResponse = new LinkedHashSet<>();
            LinkedHashSet<String> missingRequest = new LinkedHashSet<>();

            for (HeaderCondition hc : conditions) {
                Map<String, String> present = "REQUEST".equals(hc.location) ? requestHeaders : responseHeaders;
                for (String header : hc.headers) {
                    String presentKey = findPresent(present, header);
                    if (hc.missingCheck) {
                        if (presentKey == null) {
                            if ("REQUEST".equals(hc.location)) {
                                missingRequest.add(header);
                            } else {
                                missingResponse.add(header);
                            }
                        }
                    } else if (presentKey != null) {
                        // Unwanted header is present -> highlightable evidence.
                        String where = "REQUEST".equals(hc.location) ? "request" : "response";
                        segments.add(makeSegment(hc.location, presentKey, presentKey,
                                "The " + where + " contains the unwanted header \"" + presentKey + "\", which the test flags.", false));
                    }
                }
            }

            if (!missingResponse.isEmpty()) {
                segments.add(makeSegment("RESPONSE", "", String.join(", ", missingResponse),
                        "The response is missing required security headers that the test expects.", true));
            }
            if (!missingRequest.isEmpty()) {
                segments.add(makeSegment("REQUEST", "", String.join(", ", missingRequest),
                        "The request is missing required headers that the test expects.", true));
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Header evidence detection failed: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
        }
        return segments;
    }

    private static FilterNode loadValidateRoot(String category) throws Exception {
        if (category == null || category.trim().isEmpty()) {
            return null;
        }
        YamlTemplate template = YamlTemplateDao.instance.findOne(
                Filters.eq(Constants.ID, category), Projections.include(YamlTemplate.CONTENT));
        if (template == null || template.getContent() == null) {
            return null;
        }
        TestConfig cfg = TestConfigYamlParser.parseTemplate(template.getContent());
        if (cfg == null || cfg.getValidation() == null) {
            return null;
        }
        return cfg.getValidation().getNode();
    }

    // Recursively collect header key-presence conditions, carrying the nearest
    // concernedProperty/subConcernedProperty down to leaf operand nodes.
    private static void collect(FilterNode node, String inheritedConcerned, String inheritedSub, List<HeaderCondition> out) {
        if (node == null) {
            return;
        }
        String concerned = node.getConcernedProperty() != null ? node.getConcernedProperty() : inheritedConcerned;
        String sub = node.getSubConcernedProperty() != null ? node.getSubConcernedProperty() : inheritedSub;
        String operand = node.getOperand() != null ? node.getOperand().toLowerCase().trim() : null;

        if (operand != null && isHeaderProp(concerned) && "key".equalsIgnoreCase(sub)) {
            boolean missing = MISSING_OPERANDS.contains(operand);
            boolean unwanted = UNWANTED_OPERANDS.contains(operand);
            if (missing || unwanted) {
                List<String> headers = toStringList(node.getValues());
                if (!headers.isEmpty()) {
                    HeaderCondition hc = new HeaderCondition();
                    hc.location = concerned.toLowerCase().startsWith("request") ? "REQUEST" : "RESPONSE";
                    hc.missingCheck = missing;
                    hc.headers = headers;
                    out.add(hc);
                }
            }
        }

        if (node.getChildNodes() != null) {
            for (FilterNode child : node.getChildNodes()) {
                collect(child, concerned, sub, out);
            }
        }
    }

    private static boolean isHeaderProp(String p) {
        if (p == null) {
            return false;
        }
        String s = p.toLowerCase();
        return s.equals("response_headers") || s.equals("request_headers");
    }

    private static List<String> toStringList(Object values) {
        List<String> out = new ArrayList<>();
        if (values instanceof List) {
            for (Object o : (List<?>) values) {
                if (o != null) {
                    String s = o.toString().trim();
                    if (!s.isEmpty()) {
                        out.add(s);
                    }
                }
            }
        } else if (values instanceof String) {
            String s = ((String) values).trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    private static Map<String, String> headerKeyMap(BasicDBObject httpObj) {
        Map<String, String> map = new HashMap<>();
        if (httpObj == null) {
            return map;
        }
        Object node = VulnerabilityEvidenceDetector.asNode(httpObj.get("headers"));
        if (node instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) node).entrySet()) {
                String key = String.valueOf(e.getKey());
                map.put(key.toLowerCase().trim(), key);
            }
        }
        return map;
    }

    // Mirrors the framework's case-insensitive substring matching on header names.
    private static String findPresent(Map<String, String> present, String headerName) {
        String h = headerName.toLowerCase().trim();
        String exact = present.get(h);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, String> e : present.entrySet()) {
            if (e.getKey().contains(h)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static BasicDBObject makeSegment(String location, String field, String phrase, String reason, boolean informational) {
        BasicDBObject segment = new BasicDBObject();
        segment.put("start", 0);
        segment.put("end", 0);
        segment.put("location", location);
        segment.put("field", field == null ? "" : field);
        segment.put("phrase", phrase == null ? "" : phrase);
        segment.put("reason", reason);
        segment.put("source", "deterministic");
        segment.put("informational", informational);
        return segment;
    }
}
