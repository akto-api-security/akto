package com.akto.util.weekly;

import com.akto.data_actor.ClientActor;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls Cyborg {@code /api/getLLMResponseV2} (same shape as guardrails-service session manager).
 */
public final class CyborgWeeklyReportLlmClient {

    private static final LoggerMaker logger = new LoggerMaker(CyborgWeeklyReportLlmClient.class, LogDb.DASHBOARD);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CyborgWeeklyReportLlmClient() {}

    /**
     * @param reportMetrics non-zero / non-empty fields only; full weekly stats the model should use (no invented numbers)
     * @param dashboardHomeUrl full URL to the dashboard home (e.g. https://app.akto.io/dashboard/home), appended verbatim at the end
     */
    public static String getWeeklyEmailBodyFromLlm(Map<String, Object> reportMetrics, String dashboardHomeUrl) {
        String base = System.getenv("DATABASE_ABSTRACTOR_SERVICE_URL");
        if (StringUtils.isBlank(base)) {
            base = ClientActor.CYBORG_URL;
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String endpoint = base + "/api/getLLMResponseV2";

        String metricsJson;
        try {
            metricsJson = MAPPER.writeValueAsString(reportMetrics);
        } catch (Exception e) {
            logger.errorAndAddToDb("weekly report: failed to serialize metrics for LLM: " + e.getMessage());
            return null;
        }
        if (metricsJson.length() > 14_000) {
            metricsJson = metricsJson.substring(0, 14_000) + "...";
        }

        String link = StringUtils.defaultString(dashboardHomeUrl).trim();
        if (StringUtils.isBlank(link)) {
            link = "https://app.akto.io/dashboard/home";
        }

        String systemPrompt =
                "You are an Akto customer support manager writing a weekly API security email to a customer.\n"
                + "Voice: warm, professional, and human—friendly greeting and closing, natural sentences. Not a dry status dump.\n"
                + "Opening: start the first line with \"Hello Team,\" then continue in 1–2 short sentences to frame the week. "
                + "Do NOT use heavy praise or stock phrases like \"I hope this message finds you well\", \"take a moment to appreciate\", "
                + "\"always inspiring\", \"dedication in action\", or similar—avoid generic motivational copy.\n\n"
                + "Formatting: plain text only. Do not use markdown—no **bold**, no # headings, no underscores for emphasis.\n\n"
                + "Structure:\n"
                + "1) Opening as above.\n"
                + "2) One short lead-in to the metrics (one sentence), e.g. sharing weekly highlights—not only \"Here is your digest\".\n"
                + "3) Middle: all numeric highlights ONLY as bullets. Each bullet starts with \"• \" with one fact from the JSON "
                + "(clear labels, include the number). One line per bullet where possible.\n"
                + "4) After the metrics bullets, add a section introduced by the single line: Priority actions: (plain text, not bold). "
                + "Then 2–3 short sub-bullets (each starting with \"- \") for what the customer should do next.\n"
                + "- Actions must be practical and based only on the metrics in the JSON (e.g. review open issues, increase test coverage, investigate endpoints).\n"
                + "- Keep them specific but not overly technical.\n"
                + "- Do not introduce any new numbers or statistics in this section—only qualitative guidance tied to what the JSON already shows.\n"
                + "5) Closing: 2–3 sentences—thanks, invite questions, brief encouraging line.\n"
                + "6) After your sign-off, add one short sentence pointing to the Akto dashboard for more detail, and include this URL exactly "
                + "(copy the full string unchanged):\n"
                + link
                + "\n\n"
                + "Sign-off: \"Warm regards,\" then a new line \"Akto Team\". Never use placeholders like [Your Name].\n\n"
                + "Content rules:\n"
                + "- Use ONLY facts from the JSON for metrics. Do not invent numbers. Do not mention fields not in the JSON.\n"
                + "- Do not bury metrics in long paragraphs; bullets for numbers. Prose for intro, transition, and outro only.\n"
                + "- Priority actions must not add new counts or metrics—only interpret what is already in the JSON.\n"
                + "- No markdown code fences, no markdown emphasis, no HTML tags.\n"
                + "- If the JSON is sparse, still write a natural opening and closing; bullet only what exists.\n\n"
                + "Weekly report data (JSON):\n"
                + metricsJson;

        Map<String, Object> llmPayload = new LinkedHashMap<>();
        llmPayload.put("temperature", 0.35);
        llmPayload.put("top_p", 0.9);
        llmPayload.put("max_tokens", 1100);
        llmPayload.put("frequency_penalty", 0.0);
        llmPayload.put("presence_penalty", 0.2);
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> sysMsg = new HashMap<>();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        messages.add(sysMsg);
        llmPayload.put("messages", messages);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("llmPayload", llmPayload);

        byte[] jsonBytes;
        try {
            jsonBytes = MAPPER.writeValueAsBytes(body);
        } catch (Exception e) {
            logger.errorAndAddToDb("weekly report: failed to build LLM request: " + e.getMessage());
            return null;
        }

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(90_000);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            String token = System.getenv("DATABASE_ABSTRACTOR_SERVICE_TOKEN");
            if (StringUtils.isNotBlank(token)) {
                conn.setRequestProperty("Authorization", token);
            }
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBytes);
            }

            int code = conn.getResponseCode();
            InputStream stream = code >= HttpURLConnection.HTTP_BAD_REQUEST ? conn.getErrorStream() : conn.getInputStream();
            if (stream == null) {
                stream = conn.getInputStream();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = stream.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            String respBody = baos.toString(StandardCharsets.UTF_8.name());
            if (code != HttpURLConnection.HTTP_OK) {
                logger.errorAndAddToDb("weekly report: Cyborg getLLMResponseV2 HTTP " + code + " body=" + respBody);
                return null;
            }

            JsonNode root = MAPPER.readTree(respBody);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                logger.errorAndAddToDb("weekly report: Cyborg response missing choices");
                return null;
            }
            String content = choices.get(0).path("message").path("content").asText("").trim();
            return StringUtils.isBlank(content) ? null : content;
        } catch (Exception e) {
            logger.errorAndAddToDb("weekly report: Cyborg call failed: " + e.getMessage());
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
