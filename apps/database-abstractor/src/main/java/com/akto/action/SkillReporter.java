package com.akto.action;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.http_util.CoreHTTPClient;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Publishes a validated skill on behalf of the endpoint-shield agent: the skill endpoint with its tags
 * through data ingestion, and the guardrail activity for a malicious skill through the threat backend.
 *
 * Both mirror what the agent itself sends, so the dashboard treats them the same way:
 *   - ingestion: POST {host = collectionName}/skills/{skillName}, request body is the skill, the
 *     "skill-tags" request header carries the tag labels (the runtime turns each into an API tag)
 *   - threat: a MaliciousEvent on host = collectionName, endpoint /skills/{skillName}, context ENDPOINT,
 *     which is what SkillsRiskScoreSyncCron matches to mark the endpoint malicious
 */
public class SkillReporter {

    private static final LoggerMaker logger = new LoggerMaker(SkillReporter.class, LogDb.DB_ABS);

    private static final OkHttpClient httpClient = CoreHTTPClient.client.newBuilder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    private static final MediaType JSON = MediaType.parse("application/json");

    static final String THREAT_DETECTION_API_URL = System.getenv().getOrDefault(
            "THREAT_DETECTION_API_URL",
            "https://tbs.akto.io/api/threat_detection/record_malicious_event");

    // The agent's AKTO_API_BASE_URL: the data-ingestion host. May contain {accountId}.
    static final String INGESTION_BASE_URL_TEMPLATE = System.getenv().getOrDefault(
            "SKILL_INGESTION_BASE_URL", "https://{accountId}-guardrails.akto.io");

    static final String ENDPOINT_SOURCE = "ENDPOINT";

    // The skill agents the endpoint-shield agent recognises (its skillAgentToClientTypes). Any other
    // name — an orphan skill's "not-attached", an unresolved agent — gets no mcp-client/ai-agent tag.
    private static final Set<String> KNOWN_AGENTS = new HashSet<>(Arrays.asList(
            "cursor", "claude", "windsurf", "antigravity", "github-copilot", "copilot", "vscode", "codex", "kiro",
            "opencode", "microsoft-visual-studio", "jetbrains", "vscode-insiders"));

    /** Everything about one skill that both calls need. */
    static final class SkillRef {
        final String skillName;
        final String skillDescription;
        final String skillContent;
        final String agentName;
        final String filePath;
        final String collectionName;
        final int accountId;
        final String authToken;

        SkillRef(String skillName, String skillDescription, String skillContent, String agentName, String filePath,
                String collectionName, int accountId, String authToken) {
            this.skillName = skillName;
            this.skillDescription = skillDescription;
            this.skillContent = skillContent;
            this.agentName = agentName;
            this.filePath = filePath;
            this.collectionName = collectionName;
            this.accountId = accountId;
            this.authToken = authToken;
        }

        String endpoint() {
            return "/skills/" + skillName;
        }
    }

    /** The guardrail activity for a skill that cleared the malicious bar. */
    static final class Threat {
        final String tag;
        final String severity;
        final double riskScore;
        final String reason;
        final String evidence;
        final String overview;
        final String remediation;
        final List<Map<String, String>> owaspCategories;

        Threat(String tag, String severity, double riskScore, String reason, String evidence, String overview,
                String remediation, List<Map<String, String>> owaspCategories) {
            this.tag = tag;
            this.severity = severity;
            this.riskScore = riskScore;
            this.reason = reason;
            this.evidence = evidence;
            this.overview = overview;
            this.remediation = remediation;
            this.owaspCategories = owaspCategories;
        }
    }

    private SkillReporter() {}

    static void ingestSkill(SkillRef skill, List<String> tagLabels) {
        String url = ingestionUrl(skill.accountId);
        String body = buildIngestBody(skill, tagLabels, System.currentTimeMillis() / 1000).toString();
        try {
            // The ingestion service takes the raw token, unlike the threat backend.
            post(url, body, skill.authToken);
            logger.infoAndAddToDb("[SkillValidation] skill ingested skill=" + skill.skillName
                    + " collection=" + skill.collectionName + " tags=" + tagLabels, LogDb.DB_ABS);
        } catch (Exception e) {
            logger.errorAndAddToDb("[SkillValidation] skill ingestion failed skill=" + skill.skillName
                    + " url=" + url + ": " + e.getMessage(), LogDb.DB_ABS);
        }
    }

    static void reportThreat(SkillRef skill, Threat threat) {
        String body = buildThreatBody(skill, threat, System.currentTimeMillis() / 1000).toString();
        try {
            post(THREAT_DETECTION_API_URL, body, bearer(skill.authToken));
            logger.infoAndAddToDb("[SkillValidation] guardrail activity raised skill=" + skill.skillName
                    + " tag=" + threat.tag + " severity=" + threat.severity, LogDb.DB_ABS);
        } catch (Exception e) {
            logger.errorAndAddToDb("[SkillValidation] guardrail activity failed skill=" + skill.skillName
                    + ": " + e.getMessage(), LogDb.DB_ABS);
        }
    }

    static String ingestionUrl(int accountId) {
        String base = INGESTION_BASE_URL_TEMPLATE.replace("{accountId}", String.valueOf(accountId));
        return base.replaceAll("/+$", "") + "/api/ingestData";
    }

    /** One batch record shaped like the agent's GenerateIngestDataV2 output for a skill. */
    static JSONObject buildIngestBody(SkillRef skill, List<String> tagLabels, long now) {
        JSONObject requestPayload = new JSONObject();
        requestPayload.put("skill_name", skill.skillName);
        requestPayload.put("skill_description", skill.skillDescription);
        requestPayload.put("skill_content", skill.skillContent);
        requestPayload.put("agent", skill.agentName);
        requestPayload.put("file_path", skill.filePath);

        JSONObject requestHeaders = new JSONObject();
        requestHeaders.put("content-type", "application/json");
        requestHeaders.put("host", skill.collectionName);
        requestHeaders.put("x-transport", "STDIO");
        if (!tagLabels.isEmpty()) requestHeaders.put("skill-tags", String.join(",", tagLabels));

        JSONObject tag = new JSONObject();
        tag.put("source", ENDPOINT_SOURCE);
        tag.put("skill", skill.skillName);
        if (KNOWN_AGENTS.contains(skill.agentName)) {
            tag.put("mcp-client", skill.agentName);
            tag.put("ai-agent", skill.agentName);
        }

        JSONObject batch = new JSONObject();
        batch.put("path", skill.endpoint());
        batch.put("requestHeaders", requestHeaders.toString());
        batch.put("responseHeaders", "{}");
        batch.put("method", "POST");
        batch.put("requestPayload", requestPayload.toString());
        batch.put("responsePayload", "{}");
        batch.put("ip", "127.0.0.1");
        batch.put("time", String.valueOf(now));
        batch.put("statusCode", "200");
        batch.put("status", "OK");
        batch.put("akto_account_id", "1000000"); // the agent's fixed value; ingestion resolves the account from the token
        batch.put("akto_vxlan_id", "0");
        batch.put("is_pending", "false");
        batch.put("source", "MIRRORING");
        batch.put("tag", tag.toString());

        return new JSONObject().put("batchData", new JSONArray().put(batch));
    }

    /**
     * The agent's "Path A" malicious-skill threat (sendSkillThreat in skill_detector.go): same filter,
     * category, rule, payload layout and host, so it lands on the same dashboard views.
     */
    static JSONObject buildThreatBody(SkillRef skill, Threat threat, long now) {
        String owaspIds = threat.owaspCategories.stream()
                .map(c -> c.get("id") + "(" + (c.get("confidence") == null || c.get("confidence").isEmpty()
                        ? "MEDIUM" : c.get("confidence")) + ")")
                .reduce((a, b) -> a + "," + b).orElse("");

        JSONObject requestPayload = new JSONObject();
        requestPayload.put("skill_name", skill.skillName);
        requestPayload.put("skill_description", skill.skillDescription);
        requestPayload.put("agent", skill.agentName);
        requestPayload.put("file_path", skill.endpoint() + "/SKILL.md");
        requestPayload.put("content_length", skill.skillContent.length());

        JSONObject responsePayload = new JSONObject();
        responsePayload.put("is_malicious", true);
        responsePayload.put("severity", threat.severity);
        responsePayload.put("evidence", threat.evidence);
        responsePayload.put("reason", threat.reason);
        responsePayload.put("owasp_categories", owaspIds);

        JSONObject apiPayload = new JSONObject();
        apiPayload.put("method", "POST");
        apiPayload.put("requestPayload", requestPayload.toString());
        apiPayload.put("responsePayload", responsePayload.toString());
        apiPayload.put("ip", skill.agentName);
        apiPayload.put("destIp", skill.agentName);
        apiPayload.put("source", "AGENT_SKILL");
        apiPayload.put("type", "http");
        apiPayload.put("akto_vxlan_id", "");
        apiPayload.put("path", skill.endpoint());
        apiPayload.put("requestHeaders", "{}");
        apiPayload.put("responseHeaders", "{}");
        apiPayload.put("time", now);
        apiPayload.put("akto_account_id", "");
        apiPayload.put("statusCode", 200);
        apiPayload.put("status", "OK");

        JSONObject metadata = new JSONObject();
        metadata.put("policy_name", "malicious_skill_detected");
        metadata.put("rule_violated", "malicious_skill");
        metadata.put("risk_score", threat.riskScore);
        metadata.put("reason", threat.reason);
        metadata.put("overview", threat.overview);
        metadata.put("remediation", threat.remediation);

        JSONArray owasp = new JSONArray();
        for (Map<String, String> cat : threat.owaspCategories) {
            owasp.put(new JSONObject()
                    .put("id", cat.getOrDefault("id", ""))
                    .put("name", cat.getOrDefault("name", ""))
                    .put("severity", cat.getOrDefault("severity", ""))
                    .put("confidence", cat.getOrDefault("confidence", "")));
        }

        JSONObject event = new JSONObject();
        event.put("actor", skill.agentName);
        event.put("filterId", "malicious_skill_detected");
        event.put("detectedAt", String.valueOf(now));
        event.put("latestApiIp", skill.agentName);
        event.put("latestApiEndpoint", skill.endpoint());
        event.put("latestApiMethod", "POST");
        event.put("latestApiCollectionId", now);
        event.put("latestApiPayload", apiPayload.toString());
        event.put("eventType", "EVENT_TYPE_SINGLE");
        event.put("category", "malicious_skill_detected");
        event.put("subCategory", "malicious_skill_detected");
        event.put("severity", threat.severity);
        event.put("type", "Rule-Based");
        event.put("metadata", metadata);
        event.put("owaspCategories", owasp);
        event.put("contextSource", ENDPOINT_SOURCE);
        event.put("host", skill.collectionName);
        event.put("sessionId", "");
        event.put("successfulExploit", true);

        return new JSONObject().put("maliciousEvent", event);
    }

    static String bearer(String token) {
        return token.regionMatches(true, 0, "Bearer ", 0, 7) ? token : "Bearer " + token;
    }

    private static void post(String url, String body, String authorization) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body, JSON))
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", authorization)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("HTTP " + response.code());
            }
        }
    }
}
