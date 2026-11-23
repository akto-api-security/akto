package com.akto.agent;

import com.akto.dto.ApiInfo;

import java.util.List;
import java.util.Map;

/**
 * What a /chat request tells the agent server about the target and the test, so it can learn about the target
 * across conversations and runs. The agent server ignores a request without a full identity (memory off for that
 * conversation), so nothing here is guessed: an unknown field is left out.
 */
public class AgentLearningContext {

    static final String VAR_TEST_SUB_TYPE = "testSubType";
    static final String VAR_TEST_CATEGORY = "testCategory";
    static final String VAR_STRATEGY = "wordList_strategy";
    static final String VAR_BASE_PROMPT = "wordList_base_prompt";
    /** The agent server ignores a longer wordlist entry, so it is not sent. */
    static final int MAX_WORDLIST_CHARS = 2000;

    private final int apiCollectionId;
    private final String url;
    private final String method;
    private final String testSubType;
    private final String testCategory;
    private final String strategy;
    private final String basePrompt;

    AgentLearningContext(int apiCollectionId, String url, String method, String testSubType, String testCategory,
                         String strategy, String basePrompt) {
        this.apiCollectionId = apiCollectionId;
        this.url = url;
        this.method = method;
        this.testSubType = testSubType;
        this.testCategory = testCategory;
        this.strategy = strategy;
        this.basePrompt = basePrompt;
    }

    /** Null when the target is unknown: the agent server then runs that conversation without memory. */
    public static AgentLearningContext from(ApiInfo.ApiInfoKey apiInfoKey, Map<String, Object> varMap) {
        if (apiInfoKey == null || apiInfoKey.getUrl() == null || apiInfoKey.getMethod() == null) {
            return null;
        }
        return new AgentLearningContext(apiInfoKey.getApiCollectionId(), apiInfoKey.getUrl(), apiInfoKey.getMethod().name(),
                stringVar(varMap, VAR_TEST_SUB_TYPE), stringVar(varMap, VAR_TEST_CATEGORY),
                firstWordlistEntry(varMap, VAR_STRATEGY), firstWordlistEntry(varMap, VAR_BASE_PROMPT));
    }

    /** Adds the fields to a /chat body; an unknown field is left out, never sent as an empty string. */
    void addTo(Map<String, Object> requestBody) {
        requestBody.put("apiCollectionId", apiCollectionId);
        requestBody.put("url", url);
        requestBody.put("method", method);
        putIfPresent(requestBody, "testSubType", testSubType);
        putIfPresent(requestBody, "testCategory", testCategory);
        putIfPresent(requestBody, "strategy", strategy);
        putIfPresent(requestBody, "basePrompt", basePrompt);
    }

    private static void putIfPresent(Map<String, Object> body, String key, String value) {
        if (value != null) {
            body.put(key, value);
        }
    }

    private static String stringVar(Map<String, Object> varMap, String key) {
        Object value = varMap == null ? null : varMap.get(key);
        return value instanceof String && !((String) value).trim().isEmpty() ? (String) value : null;
    }

    private static String firstWordlistEntry(Map<String, Object> varMap, String key) {
        Object value = varMap == null ? null : varMap.get(key);
        if (!(value instanceof List) || ((List<?>) value).isEmpty()) {
            return null;
        }
        Object first = ((List<?>) value).get(0);
        if (!(first instanceof String)) {
            return null;
        }
        String entry = ((String) first).trim();
        return entry.isEmpty() || entry.length() > MAX_WORDLIST_CHARS ? null : entry;
    }
}
