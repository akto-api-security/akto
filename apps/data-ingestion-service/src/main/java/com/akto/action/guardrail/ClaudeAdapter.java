package com.akto.action.guardrail;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ProviderAdapter for Anthropic Claude inference hooks. Parses the "prompt frame"
 * (transcript in messages[], plus request_id / session_id / model / metadata) and
 * renders Anthropic's verdict {@code {"action":"allow"|"deny","deny_reason","reference_id"}}.
 */
public class ClaudeAdapter implements ProviderAdapter {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int DENY_REASON_MAX = 500;
    private static final int REFERENCE_ID_MAX = 50;

    @Override
    public String name() {
        return "claude";
    }

    @Override
    @SuppressWarnings("unchecked")
    public ParsedRequest parse(Map<String, Object> body) {
        ParsedRequest frame = new ParsedRequest();
        if (body == null) {
            frame.allowShortCircuit = true;
            return frame;
        }

        // Only "prompt" is defined today; any other (future) event type still
        // needs a verdict — allow it rather than erroring.
        String type = stringOrEmpty(body.get("type"));
        if (!type.isEmpty() && !"prompt".equals(type)) {
            frame.allowShortCircuit = true;
            return frame;
        }

        frame.requestId = stringOrNull(body.get("request_id"));
        frame.sessionId = stringOrNull(body.get("session_id"));
        frame.tenantId = stringOrNull(body.get("tenant_id"));
        frame.model = stringOrNull(body.get("model"));
        if (body.get("actor") instanceof Map) {
            frame.actor = (Map<String, Object>) body.get("actor");
        }
        if (body.get("metadata") instanceof Map) {
            frame.metadata = (Map<String, Object>) body.get("metadata");
        }
        Object messages = body.get("messages");
        frame.prompt = messages instanceof List ? flatten((List<Object>) messages) : "";
        return frame;
    }

    @Override
    public Map<String, Object> verdict(boolean allow, String reason, String referenceId) {
        Map<String, Object> resp = new HashMap<>();
        if (allow) {
            resp.put("action", "allow");
            return resp;
        }
        resp.put("action", "deny");
        if (reason != null && !reason.isEmpty()) {
            resp.put("deny_reason", reason.length() <= DENY_REASON_MAX ? reason : reason.substring(0, DENY_REASON_MAX));
        }
        String ref = sanitizeReferenceId(referenceId);
        if (!ref.isEmpty()) {
            resp.put("reference_id", ref);
        }
        return resp;
    }

    /** Flatten messages[].content[] into one scannable prompt string. */
    @SuppressWarnings("unchecked")
    private String flatten(List<Object> msgs) {
        StringBuilder sb = new StringBuilder();
        for (Object msgObj : msgs) {
            if (!(msgObj instanceof Map)) {
                continue;
            }
            Map<String, Object> msg = (Map<String, Object>) msgObj;
            String role = stringOrEmpty(msg.get("role"));
            Object content = msg.get("content");

            if (content instanceof String) {
                appendLine(sb, (String) content);
                continue;
            }
            if (!(content instanceof List)) {
                continue;
            }
            for (Object blockObj : (List<Object>) content) {
                if (!(blockObj instanceof Map)) {
                    continue;
                }
                Map<String, Object> block = (Map<String, Object>) blockObj;
                switch (stringOrEmpty(block.get("type"))) {
                    case "tool_use":
                        String toolUse = role + " tool_use " + stringOrEmpty(block.get("tool_name"));
                        Object input = block.get("input");
                        if (input != null) {
                            toolUse += " " + toJson(input);
                        }
                        appendLine(sb, toolUse);
                        break;
                    case "tool_result":
                        String prefix = role + " tool_result " + stringOrEmpty(block.get("tool_name"));
                        if (Boolean.TRUE.equals(block.get("is_error"))) {
                            prefix += " (error)";
                        }
                        appendLine(sb, (prefix + " " + stringOrEmpty(block.get("content"))).trim());
                        break;
                    case "attachment":
                        appendLine(sb, (role + " attachment " + stringOrEmpty(block.get("file_name"))
                                + " " + stringOrEmpty(block.get("text"))).trim());
                        break;
                    default:
                        // "text" and any unrecognized block type.
                        appendLine(sb, stringOrEmpty(block.get("text")));
                        break;
                }
            }
        }
        return sb.toString().trim();
    }

    private static void appendLine(StringBuilder sb, String s) {
        if (s == null || s.isEmpty()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append('\n');
        }
        sb.append(s);
    }

    private static String sanitizeReferenceId(String id) {
        if (id == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < id.length() && b.length() < REFERENCE_ID_MAX; i++) {
            char c = id.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == ':' || c == '/' || c == '-';
            if (ok) {
                b.append(c);
            }
        }
        return b.toString();
    }

    private static String stringOrEmpty(Object v) {
        return v != null ? v.toString() : "";
    }

    private static String stringOrNull(Object v) {
        return v != null ? v.toString() : null;
    }

    private static String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "";
        }
    }
}
