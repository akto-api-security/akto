package com.akto.action.skill_validation_v3;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.http_util.CoreHTTPClient;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Raises the guardrail activity for a skill that V3 finds malicious: a MaliciousEvent on host =
 * collectionName, endpoint /skills/{skillName}, context ENDPOINT, the same event the endpoint-shield agent
 * sends, which is what SkillsRiskScoreSyncCron matches to mark the endpoint malicious.
 *
 * Nothing is ingested from here: the dashboard's revalidation job writes the result onto the stored skill.
 */
public class SkillThreatReporterV3 {

    private static final LoggerMaker logger = new LoggerMaker(SkillThreatReporterV3.class, LogDb.DB_ABS);

    private static final OkHttpClient httpClient = CoreHTTPClient.client.newBuilder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    private static final MediaType JSON = MediaType.parse("application/json");

    static final String THREAT_DETECTION_API_URL = System.getenv().getOrDefault(
            "THREAT_DETECTION_API_URL",
            "https://tbs.akto.io/api/threat_detection/record_malicious_event");

    static final String ENDPOINT_SOURCE = "ENDPOINT";

    /** Everything about one skill that the event needs. */
    static final class SkillRef {
        final String skillName;
        final String skillDescription;
        final String skillContent;
        final String agentName;
        final String collectionName;
        final String authToken;

        SkillRef(String skillName, String skillDescription, String skillContent, String agentName,
                String collectionName, String authToken) {
            this.skillName = skillName;
            this.skillDescription = skillDescription;
            this.skillContent = skillContent;
            this.agentName = agentName;
            this.collectionName = collectionName;
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

    private SkillThreatReporterV3() {}

    static void reportThreat(SkillRef skill, Threat threat) {
        String body = buildThreatBody(skill, threat, System.currentTimeMillis() / 1000).toString();
        try {
            post(THREAT_DETECTION_API_URL, body, bearer(skill.authToken));
            logger.infoAndAddToDb("[SkillValidationV3] guardrail activity raised skill=" + skill.skillName
                    + " tag=" + threat.tag + " severity=" + threat.severity + " riskScore=" + threat.riskScore,
                    LogDb.DB_ABS);
        } catch (Exception e) {
            logger.errorAndAddToDb("[SkillValidationV3] guardrail activity failed skill=" + skill.skillName
                    + ": " + e.getMessage(), LogDb.DB_ABS);
        }
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
