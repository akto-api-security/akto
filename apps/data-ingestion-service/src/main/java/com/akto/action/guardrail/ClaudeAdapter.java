package com.akto.action.guardrail;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
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
    private static final String PROMPT = "prompt";
    private static final String TOOL_CALL = "tool_call";
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

        // "prompt" and "tool_call" are defined; any other (future) event type still needs a verdict, so allow it.
        String type = stringOrEmpty(body.get("type"));
        boolean toolCall = TOOL_CALL.equals(type);
        if (!type.isEmpty() && !PROMPT.equals(type) && !toolCall) {
            frame.allowShortCircuit = true;
            return frame;
        }
        frame.eventType = toolCall ? TOOL_CALL : PROMPT;

        frame.requestId = stringOrNull(body.get("request_id"));
        frame.sessionId = stringOrNull(body.get("session_id"));
        frame.tenantId = stringOrNull(body.get("tenant_id"));
        frame.model = stringOrNull(body.get("model"));
        if (body.get("actor") instanceof Map) {
            frame.actor = (Map<String, Object>) body.get("actor");
        }
        // source.application is an open string (claude-code = CLI, claude-ai =
        // app, config-test = connection test). Carry any value through as-is;
        // never reject an unrecognized one.
        if (body.get("source") instanceof Map) {
            frame.application = stringOrNull(((Map<String, Object>) body.get("source")).get("application"));
        }
        if (body.get("metadata") instanceof Map) {
            frame.metadata = (Map<String, Object>) body.get("metadata");
        }
        Object messages = body.get("messages");
        if (!(messages instanceof List)) {
            frame.prompt = "";
            return frame;
        }
        List<Object> msgs = (List<Object>) messages;
        if (toolCall) {
            // A tool_call frame carries the latest assistant message as the last entry; earlier turns were in the prompt frame.
            if (msgs.isEmpty()) {
                frame.prompt = "";
                return frame;
            }
            msgs = msgs.subList(msgs.size() - 1, msgs.size());
            frame.toolInfos = collectToolInfos(msgs);
        }
        frame.prompt = flatten(msgs);
        return frame;
    }

    /** tool_info of every tool_use block, in order; tool_info is omitted (never null) when it does not apply. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> collectToolInfos(List<Object> msgs) {
        List<Map<String, Object>> infos = new ArrayList<>();
        for (Object msgObj : msgs) {
            if (!(msgObj instanceof Map) || !(((Map<String, Object>) msgObj).get("content") instanceof List)) {
                continue;
            }
            for (Object blockObj : (List<Object>) ((Map<String, Object>) msgObj).get("content")) {
                if (!(blockObj instanceof Map)) {
                    continue;
                }
                Map<String, Object> block = (Map<String, Object>) blockObj;
                if (!"tool_use".equals(stringOrEmpty(block.get("type"))) || !(block.get("tool_info") instanceof Map)) {
                    continue;
                }
                Map<String, Object> info = new HashMap<>((Map<String, Object>) block.get("tool_info"));
                info.put("tool_name", stringOrNull(block.get("tool_name")));
                infos.add(info);
            }
        }
        return infos;
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
                        // tool_info (who runs/provides the tool) only exists on tool_call frames.
                        Object toolInfo = block.get("tool_info");
                        if (toolInfo != null) {
                            toolUse += " tool_info " + toJson(toolInfo);
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
