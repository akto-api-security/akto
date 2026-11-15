package com.akto.action.skill_validation_v3;

import com.akto.dao.context.Context;
import com.akto.data_actor.DbLayer;
import com.akto.dto.traffic.CollectionTags;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.http_util.CoreHTTPClient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
 * Publishes a skill the endpoint-shield agent sent to V2, on the agent's behalf: the agent collection
 * with its skills and login tags, and the skill endpoint through data ingestion. The guardrail activity
 * for a malicious skill goes through SkillThreatReporterV3, the same one V3 uses.
 *
 * Both mirror what the agent itself used to send, so the dashboard treats them the same way:
 *   - collection: host = collectionName, id = collectionName.hashCode() (the agent's javaHashCode)
 *   - ingestion: POST {host = collectionName}/skills/{skillName}; the request body is the skill file only
 *     (never its scripts) with skill_annotations and skill_verdict, and the "skill-tags" request header
 *     carries the tag labels (the runtime turns each into an API tag)
 */
public class SkillPublisher {

    private static final LoggerMaker logger = new LoggerMaker(SkillPublisher.class, LogDb.DB_ABS);

    private static final OkHttpClient httpClient = CoreHTTPClient.client.newBuilder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    private static final MediaType JSON = MediaType.parse("application/json");

    // The agent's AKTO_API_BASE_URL: the data-ingestion host. May contain {accountId}.
    static final String INGESTION_BASE_URL_TEMPLATE = System.getenv().getOrDefault(
            "SKILL_INGESTION_BASE_URL", "https://{accountId}-guardrails.akto.io");

    static final String ENDPOINT_SOURCE = "ENDPOINT";

    // The skill agents the endpoint-shield agent recognises (its skillAgentToClientTypes). Used only when
    // an older agent does not send aiAgent; any other name (an orphan skill's "not-attached") gets no
    // mcp-client/ai-agent tag.
    private static final Set<String> KNOWN_AGENTS = new HashSet<>(Arrays.asList(
            "cursor", "claude", "windsurf", "antigravity", "github-copilot", "copilot", "vscode", "codex", "kiro",
            "opencode", "microsoft-visual-studio", "jetbrains", "vscode-insiders"));

    /** Everything about one skill that publishing needs. */
    public static final class SkillRef {
        final String skillName;
        final String skillDescription;
        final String skillContent;
        final String agentName;
        // the recognised agent for the mcp-client / ai-agent tags; empty when there is none
        final String aiAgent;
        final String filePath;
        final String collectionName;
        final int accountId;
        final String authToken;

        public SkillRef(String skillName, String skillDescription, String skillContent, String agentName,
                String aiAgent, String filePath, String collectionName, int accountId, String authToken) {
            this.skillName = skillName;
            this.skillDescription = skillDescription;
            this.skillContent = skillContent;
            this.agentName = agentName;
            this.aiAgent = aiAgent == null ? "" : aiAgent;
            this.filePath = filePath;
            this.collectionName = collectionName;
            this.accountId = accountId;
            this.authToken = authToken;
        }

        String endpoint() {
            return "/skills/" + skillName;
        }
    }

    private SkillPublisher() {}

    /** The agent's aiAgent when it sent one; for an older agent, its agent name when that is a known agent. */
    public static String resolveAiAgent(String aiAgent, String agentName) {
        if (aiAgent != null && !aiAgent.trim().isEmpty()) return aiAgent.trim();
        return agentName != null && KNOWN_AGENTS.contains(agentName) ? agentName : "";
    }

    /**
     * Creates the agent collection, or merges into it: the skill is added to its skills and the tags
     * are set. Runs on the request thread (it needs the request's account), before validation, so the
     * skill is listed under its agent even when validation fails.
     */
    public static void ensureAgentCollection(SkillRef skill, List<SkillAgentLogin> logins) {
        try {
            List<String> skills = new ArrayList<>(Collections.singletonList(skill.skillName));
            DbLayer.createCollectionForHostAndVpc(skill.collectionName, skill.collectionName.hashCode(), null,
                    buildCollectionTags(skill.aiAgent, logins, Context.now()), null, skills);
        } catch (Exception e) {
            logger.errorAndAddToDb("[SkillValidationV2] agent collection update failed skill=" + skill.skillName
                    + " collection=" + skill.collectionName + ": " + e.getMessage(), LogDb.DB_ABS);
        }
    }

    /** The agent collection's tags, as the agent's CreateCollectionForAgentSkills built them. */
    static List<CollectionTags> buildCollectionTags(String aiAgent, List<SkillAgentLogin> logins, int now) {
        List<CollectionTags> tags = new ArrayList<>();
        tags.add(new CollectionTags(now, "gen-ai", "Gen AI", CollectionTags.TagSource.KUBERNETES));
        tags.add(new CollectionTags(now, "source", ENDPOINT_SOURCE, CollectionTags.TagSource.KUBERNETES));
        if (aiAgent != null && !aiAgent.isEmpty()) {
            tags.add(new CollectionTags(now, "mcp-client", aiAgent, CollectionTags.TagSource.KUBERNETES));
            tags.add(new CollectionTags(now, "ai-agent", aiAgent, CollectionTags.TagSource.KUBERNETES));
        }
        if (logins == null) return tags;
        for (SkillAgentLogin login : logins) {
            if (login == null) continue;
            String suffix = login.getProfile() == null || login.getProfile().isEmpty() ? "" : "-" + login.getProfile();
            addIfPresent(tags, now, "ai-agent-email" + suffix, login.getEmail());
            addIfPresent(tags, now, "ai-agent-account-type" + suffix, login.getAccountType());
            addIfPresent(tags, now, "login-user-email-type" + suffix, login.getEmailCategory());
        }
        return tags;
    }

    // An unknown value is left out rather than sent empty: the previous value stays on the collection.
    private static void addIfPresent(List<CollectionTags> tags, int now, String key, String value) {
        if (value != null && !value.isEmpty()) {
            tags.add(new CollectionTags(now, key, value, CollectionTags.TagSource.KUBERNETES));
        }
    }

    public static void ingestSkill(SkillRef skill, List<String> tagLabels, List<Map<String, Object>> annotations,
            Map<String, Object> verdict) {
        String url = ingestionUrl(skill.accountId);
        String body = buildIngestBody(skill, tagLabels, annotations, verdict, System.currentTimeMillis() / 1000).toString();
        try {
            // The ingestion service takes the raw token, unlike the threat backend.
            post(url, body, skill.authToken);
            logger.infoAndAddToDb("[SkillValidationV2] skill ingested skill=" + skill.skillName
                    + " collection=" + skill.collectionName + " tags=" + tagLabels, LogDb.DB_ABS);
        } catch (Exception e) {
            logger.errorAndAddToDb("[SkillValidationV2] skill ingestion failed skill=" + skill.skillName
                    + " url=" + url + ": " + e.getMessage(), LogDb.DB_ABS);
        }
    }

    static String ingestionUrl(int accountId) {
        String base = INGESTION_BASE_URL_TEMPLATE.replace("{accountId}", String.valueOf(accountId));
        return base.replaceAll("/+$", "") + "/api/ingestData";
    }

    /**
     * One batch record shaped like the agent's GenerateIngestDataV2 output for a skill. Only the skill
     * content is ingested; skill_annotations and skill_verdict are what V3's revalidation job writes into
     * the same payload, so the dashboard reads both the same way.
     */
    static JSONObject buildIngestBody(SkillRef skill, List<String> tagLabels, List<Map<String, Object>> annotations,
            Map<String, Object> verdict, long now) {
        JSONObject requestPayload = new JSONObject();
        requestPayload.put("skill_name", skill.skillName);
        requestPayload.put("skill_description", skill.skillDescription);
        requestPayload.put("skill_content", skill.skillContent);
        requestPayload.put("agent", skill.agentName);
        requestPayload.put("file_path", skill.filePath);
        requestPayload.put("skill_annotations", new JSONArray(annotations));
        requestPayload.put("skill_verdict", new JSONObject(verdict));

        JSONObject requestHeaders = new JSONObject();
        requestHeaders.put("content-type", "application/json");
        requestHeaders.put("host", skill.collectionName);
        requestHeaders.put("x-transport", "STDIO");
        if (!tagLabels.isEmpty()) requestHeaders.put("skill-tags", String.join(",", tagLabels));

        JSONObject tag = new JSONObject();
        tag.put("source", ENDPOINT_SOURCE);
        tag.put("skill", skill.skillName);
        if (!skill.aiAgent.isEmpty()) {
            tag.put("mcp-client", skill.aiAgent);
            tag.put("ai-agent", skill.aiAgent);
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
