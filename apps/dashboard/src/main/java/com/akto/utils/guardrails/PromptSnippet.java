package com.akto.utils.guardrails;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

/**
 * Extracts a short, human-readable prompt from a stored Akto payload envelope.
 *
 * <p>Used to label the violations a policy edit stops catching, so the reader can recognise which
 * prompts changed status rather than only seeing a count.
 *
 * <p>Producers write several shapes — chat {@code messages[]}, MCP {@code tools/call} frames, a
 * gateway {@code body}, a bare {@code prompt}/{@code text} — so extraction is best-effort and
 * always yields something rather than failing. Deliberately a standalone pure function: it lives
 * outside the Struts action so it can be tested without loading that class's HTTP client and
 * logger statics.
 */
public class PromptSnippet {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** Long enough to recognise a prompt, short enough to render in a list. */
    private static final int LIMIT = 240;

    private PromptSnippet() {}

    /**
     * @param envelope the stored envelope (malicious_events.latestApiOrig)
     * @return a flattened, length-bounded prompt, or "" when there is nothing to show
     */
    public static String of(String envelope) {
        if (StringUtils.isBlank(envelope)) {
            return "";
        }
        try {
            JsonNode request = objectMapper.readTree(
                objectMapper.readTree(envelope).path("requestPayload").asText(""));

            // Chat completions: the last textual turn is what the guardrail was evaluating.
            JsonNode messages = request.path("messages");
            if (!messages.isArray()) {
                messages = request.path("body").path("messages");
            }
            if (messages.isArray()) {
                for (int i = messages.size() - 1; i >= 0; i--) {
                    JsonNode content = messages.get(i).path("content");
                    if (content.isTextual() && StringUtils.isNotBlank(content.asText())) {
                        return truncate(content.asText());
                    }
                }
            }

            for (String field : new String[] { "prompt", "text", "input" }) {
                if (request.path(field).isTextual()) {
                    return truncate(request.path(field).asText());
                }
            }

            // MCP tools/call — the dominant agentic shape. The tool name plus its arguments is what
            // a reader recognises; the surrounding JSON-RPC frame is noise.
            JsonNode params = request.path("params");
            if (params.isObject()) {
                String tool = params.path("name").asText("");
                JsonNode args = params.path("arguments");
                String rendered = args.isTextual() ? args.asText()
                    : (args.isMissingNode() || args.isNull() ? "" : args.toString());
                if (StringUtils.isNotBlank(tool) || StringUtils.isNotBlank(rendered)) {
                    return truncate(StringUtils.isBlank(tool) ? rendered : tool + ": " + rendered);
                }
            }

            JsonNode body = request.path("body");
            if (body.isTextual()) {
                return truncate(body.asText());
            }
            if (!body.isMissingNode() && !body.isNull()) {
                return truncate(body.toString());
            }
            return truncate(request.isMissingNode() ? envelope : request.toString());
        } catch (Exception e) {
            // An unfamiliar or malformed envelope still deserves a row rather than a blank.
            return truncate(envelope);
        }
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= LIMIT ? flat : flat.substring(0, LIMIT) + "…";
    }
}
