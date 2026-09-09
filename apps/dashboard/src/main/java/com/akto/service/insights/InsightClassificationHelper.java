package com.akto.service.insights;

import com.akto.dao.insights.InsightClassificationCacheDao;
import com.akto.dto.insights.InsightClassificationCache;
import com.akto.gpt.handlers.gpt_prompts.AgentDomainClassifier;
import com.akto.gpt.handlers.gpt_prompts.GuardrailSuggestionClassifier;
import com.akto.gpt.handlers.gpt_prompts.ToolCapabilityClassifier;
import com.mongodb.BasicDBObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

/**
 * Caches the two small classifiers behind insights 6/7/8 (AgentDomainClassifier,
 * ToolCapabilityClassifier) the same way QueryTopicCacheDao caches
 * UserQueryTopicClassifier — content-hash key, TTL as a backstop. DETAIL scope only:
 * providers must never call these from the LIST path.
 */
public final class InsightClassificationHelper {
    private InsightClassificationHelper() {}

    private static final long CLASSIFICATION_TTL_DAYS = 30;

    /** description + observed domains -> {domain: "ON"|"OFF"}. Returns empty map when description is blank. */
    public static Map<String, String> classifyDomains(String description, List<String> domains) {
        if (description == null || description.trim().isEmpty() || domains == null || domains.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> sortedDomains = new ArrayList<>(new TreeSet<>(domains));
        String id = "domain:" + InsightUtil.md5(description.trim() + "|" + String.join(",", sortedDomains));

        String cachedJson = InsightClassificationCacheDao.instance.bulkGet(Collections.singletonList(id)).get(id);
        if (cachedJson != null) return parseStringMap(cachedJson);

        BasicDBObject input = new BasicDBObject(AgentDomainClassifier.DESCRIPTION, description)
                .append(AgentDomainClassifier.DOMAINS, sortedDomains);
        BasicDBObject result = new AgentDomainClassifier().handle(input);
        if (result.containsField("error")) return Collections.emptyMap();

        put(id, "AgentDomainClassifier", result);
        return parseStringMap(result.toJson());
    }

    /** toolName + a request/response sample -> dangerous verdict (never null; SAFE on failure). */
    public static ToolDangerVerdict classifyToolDanger(String toolName, String sampleData) {
        String sample = sampleData != null ? sampleData : "";
        String id = "tool:" + InsightUtil.md5(toolName.toLowerCase() + "|" + sample);
        String cachedJson = InsightClassificationCacheDao.instance.bulkGet(Collections.singletonList(id)).get(id);
        if (cachedJson != null) return parseVerdict(cachedJson);

        BasicDBObject input = new BasicDBObject(ToolCapabilityClassifier.TOOL_NAME, toolName)
                .append(ToolCapabilityClassifier.SAMPLE_DATA, sample);
        BasicDBObject result = new ToolCapabilityClassifier().handle(input);
        if (result.containsField("error")) return new ToolDangerVerdict(false, ToolCapabilityClassifier.SAFE);

        put(id, "ToolCapabilityClassifier", result);
        return new ToolDangerVerdict(result.getBoolean(ToolCapabilityClassifier.DANGEROUS, false),
                result.getString(ToolCapabilityClassifier.CAPABILITY, ToolCapabilityClassifier.SAFE));
    }

    private static ToolDangerVerdict parseVerdict(String json) {
        try {
            org.json.JSONObject obj = new org.json.JSONObject(json);
            return new ToolDangerVerdict(obj.optBoolean(ToolCapabilityClassifier.DANGEROUS, false),
                    obj.optString(ToolCapabilityClassifier.CAPABILITY, ToolCapabilityClassifier.SAFE));
        } catch (Exception e) {
            return new ToolDangerVerdict(false, ToolCapabilityClassifier.SAFE);
        }
    }

    public static final class ToolDangerVerdict {
        public final boolean dangerous;
        public final String capability;
        public ToolDangerVerdict(boolean dangerous, String capability) {
            this.dangerous = dangerous;
            this.capability = capability;
        }
    }

    /**
     * hostName + observed harmful-topic summaries -> a starter guardrail policy, keyed the
     * same shape as GuardrailPolicies' own create fields (name/description/severity/
     * behaviour/deniedTopics) so it can be dropped straight into a GUARDRAIL_TEMPLATE CTA's
     * params. Empty map on blank input or classifier failure.
     */
    public static Map<String, Object> suggestGuardrail(String hostName, List<String> harmfulTopicSummaries) {
        if (hostName == null || harmfulTopicSummaries == null || harmfulTopicSummaries.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> sorted = new ArrayList<>(new TreeSet<>(harmfulTopicSummaries));
        String id = "guardrail:" + InsightUtil.md5(hostName.toLowerCase() + "|" + String.join("|", sorted));

        String cachedJson = InsightClassificationCacheDao.instance.bulkGet(Collections.singletonList(id)).get(id);
        if (cachedJson != null) return parseJsonMap(cachedJson);

        BasicDBObject input = new BasicDBObject(GuardrailSuggestionClassifier.HOST_NAME, hostName)
                .append(GuardrailSuggestionClassifier.HARMFUL_TOPICS, harmfulTopicSummaries);
        BasicDBObject result = new GuardrailSuggestionClassifier().handle(input);
        if (result.containsField("error")) return Collections.emptyMap();

        put(id, "GuardrailSuggestionClassifier", result);
        return parseJsonMap(result.toJson());
    }

    private static Map<String, Object> parseJsonMap(String json) {
        try {
            return new org.json.JSONObject(json).toMap();
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private static void put(String id, String classifier, BasicDBObject result) {
        long now = System.currentTimeMillis() / 1000;
        InsightClassificationCache entry = new InsightClassificationCache(
                id, classifier, result.toJson(), now, new Date((now + TimeUnit.DAYS.toSeconds(CLASSIFICATION_TTL_DAYS)) * 1000L));
        InsightClassificationCacheDao.instance.bulkPut(Collections.singletonList(entry));
    }

    private static Map<String, String> parseStringMap(String json) {
        try {
            org.json.JSONObject obj = new org.json.JSONObject(json);
            Map<String, String> out = new HashMap<>();
            for (java.util.Iterator<?> it = obj.keys(); it.hasNext(); ) {
                String key = String.valueOf(it.next());
                out.put(key, obj.optString(key, ""));
            }
            return out;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

}
