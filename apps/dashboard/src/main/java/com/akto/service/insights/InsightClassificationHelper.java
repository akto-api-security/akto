package com.akto.service.insights;

import com.akto.dao.insights.InsightClassificationCacheDao;
import com.akto.dto.insights.InsightClassificationCache;
import com.akto.gpt.handlers.gpt_prompts.AgentDomainClassifier;
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

    /** toolName/description -> capability class (never null; UNCLASSIFIED on failure). */
    public static String classifyToolCapability(String toolName, String toolDescription) {
        String id = "tool:" + InsightUtil.md5(toolName.toLowerCase() + "|" + (toolDescription != null ? toolDescription : ""));
        String cachedJson = InsightClassificationCacheDao.instance.bulkGet(Collections.singletonList(id)).get(id);
        if (cachedJson != null) {
            Map<String, String> parsed = parseStringMap(cachedJson);
            return parsed.getOrDefault(ToolCapabilityClassifier.CAPABILITY, ToolCapabilityClassifier.UNCLASSIFIED);
        }

        BasicDBObject input = new BasicDBObject(ToolCapabilityClassifier.TOOL_NAME, toolName)
                .append(ToolCapabilityClassifier.TOOL_DESCRIPTION, toolDescription != null ? toolDescription : "");
        BasicDBObject result = new ToolCapabilityClassifier().handle(input);
        if (result.containsField("error")) return ToolCapabilityClassifier.UNCLASSIFIED;

        put(id, "ToolCapabilityClassifier", result);
        return result.getString(ToolCapabilityClassifier.CAPABILITY, ToolCapabilityClassifier.UNCLASSIFIED);
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
