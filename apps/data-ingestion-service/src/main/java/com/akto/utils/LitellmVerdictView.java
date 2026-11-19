package com.akto.utils;

import com.akto.gateway.Gateway;
import com.mongodb.BasicDBObject;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Narrows what the guardrails judge for long LiteLLM conversations to the newest user turn.
 *
 * LiteLLM's built-in Akto guardrail sends the whole conversation for the verdict: a coding agent's
 * system prompt (OpenCode's is ~18KB of "you must / never" instructions), every earlier turn and all
 * tool definitions. That buries the user's text and makes the agent's own instructions trip
 * prompt-injection policies, so every request is blocked. Akto's custom LiteLLM hook avoids this
 * client-side (_validation_view); this does the same server-side, for any LiteLLM connector.
 *
 * Only the verdict copy is narrowed (Gateway.GUARDRAILS_REQUEST_PAYLOAD); the full requestPayload is
 * still ingested. Short conversations are judged whole, as the custom hook does by default.
 */
public final class LitellmVerdictView {

    static final String LITELLM_CONNECTOR = "litellm";
    // Same default as the custom hook's GUARDRAIL_NARROW_THRESHOLD_BYTES.
    static final int NARROW_THRESHOLD_CHARS = 8000;
    // Harness context coding agents inline into the user turn (e.g. Claude Code's reminders).
    private static final Pattern HARNESS_CONTEXT = Pattern.compile("<system-reminder>.*?</system-reminder>",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private LitellmVerdictView() {}

    /** Adds the narrowed verdict payload to requestData when it is a long LiteLLM conversation. */
    public static void apply(Map<String, Object> requestData) {
        if (!LITELLM_CONNECTOR.equalsIgnoreCase(asString(requestData.get("akto_connector")))) {
            return;
        }
        if (!"true".equalsIgnoreCase(asString(requestData.get("guardrails")))
            && !"true".equalsIgnoreCase(asString(requestData.get("response_guardrails")))) {
            return;
        }
        String narrowed = narrow(asString(requestData.get("requestPayload")));
        if (narrowed != null) {
            requestData.put(Gateway.GUARDRAILS_REQUEST_PAYLOAD, narrowed);
        }
    }

    /**
     * requestPayload {"body": ...} reduced to the model and the newest user turn's text, in the same
     * shape it came in (body as a JSON string, as LiteLLM's guardrail sends it, or as an object, as the
     * custom hook does). Null when there is nothing to narrow.
     */
    static String narrow(String requestPayload) {
        BasicDBObject envelope = parseObject(requestPayload);
        Object rawBody = envelope.get("body");
        boolean bodyIsString = rawBody instanceof String;
        BasicDBObject body = bodyIsString ? parseObject((String) rawBody)
            : rawBody instanceof Map ? new BasicDBObject((Map<?, ?>) rawBody) : null;
        if (body == null || !(body.get("messages") instanceof List)) {
            return null;
        }
        List<?> messages = (List<?>) body.get("messages");
        if (new BasicDBObject("m", messages).toJson().length() <= NARROW_THRESHOLD_CHARS) {
            return null;
        }
        String text = null;
        for (int i = messages.size() - 1; i >= 0 && text == null; i--) {
            Object m = messages.get(i);
            if (m instanceof Map && "user".equals(((Map<?, ?>) m).get("role"))) {
                text = userText(((Map<?, ?>) m).get("content"));
            }
        }
        if (text == null || text.isEmpty()) {
            return null;
        }
        BasicDBObject verdictBody = new BasicDBObject("model", body.get("model"))
            .append("messages", Collections.singletonList(new BasicDBObject("role", "user").append("content", text)));
        return new BasicDBObject("body", bodyIsString ? verdictBody.toJson() : verdictBody).toJson();
    }

    /** Text of a user turn (string or content blocks), with harness context removed and whitespace collapsed. */
    private static String userText(Object content) {
        StringBuilder sb = new StringBuilder();
        if (content instanceof String) {
            sb.append(content);
        } else if (content instanceof List) {
            for (Object block : (List<?>) content) {
                if (block instanceof Map && "text".equals(((Map<?, ?>) block).get("type"))) {
                    Object t = ((Map<?, ?>) block).get("text");
                    if (t != null) {
                        sb.append(t).append(' ');
                    }
                }
            }
        }
        return HARNESS_CONTEXT.matcher(sb).replaceAll(" ").trim().replaceAll("\\s+", " ");
    }

    private static BasicDBObject parseObject(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new BasicDBObject();
        }
        try {
            return BasicDBObject.parse(json);
        } catch (Exception e) {
            return new BasicDBObject();
        }
    }

    private static String asString(Object o) {
        return o != null ? o.toString() : null;
    }
}
