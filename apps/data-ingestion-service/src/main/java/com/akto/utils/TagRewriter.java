package com.akto.utils;

import com.akto.log.LoggerMaker;
import com.mongodb.BasicDBObject;

public class TagRewriter {

    private static final LoggerMaker loggerMaker = new LoggerMaker(TagRewriter.class, LoggerMaker.LogDb.DATA_INGESTION);

    private static final String OLLAMA_KEYWORD = "ollama";
    private static final String HOST_HEADER = "host";

    private static final String BROWSER_LLM_KEY = "browser-llm";
    private static final String BROWSER_LLM_VALUE = "Browser LLM";
    private static final String BROWSER_LLM_AGENT_KEY = "browser-llm-agent";
    private static final String AI_AGENT_KEY = "ai-agent";

    private TagRewriter() {}

    public static String rewriteForOllama(String requestHeaders, String existingTag) {
        if (!hostContainsOllama(requestHeaders)) {
            return existingTag;
        }

        BasicDBObject tagObj;
        if (existingTag == null || existingTag.isEmpty()) {
            tagObj = new BasicDBObject();
        } else {
            try {
                tagObj = BasicDBObject.parse(existingTag);
            } catch (Exception e) {
                loggerMaker.warn("Failed to parse existing tag JSON, starting fresh: " + e.getMessage());
                tagObj = new BasicDBObject();
            }
        }

        Object aiAgentValue = tagObj.remove(AI_AGENT_KEY);
        tagObj.put(BROWSER_LLM_KEY, BROWSER_LLM_VALUE);
        if (aiAgentValue != null) {
            tagObj.put(BROWSER_LLM_AGENT_KEY, aiAgentValue);
        }
        return tagObj.toJson();
    }

    private static boolean hostContainsOllama(String requestHeaders) {
        if (requestHeaders == null || requestHeaders.isEmpty()) return false;
        try {
            BasicDBObject headers = BasicDBObject.parse(requestHeaders);
            for (String key : headers.keySet()) {
                if (HOST_HEADER.equalsIgnoreCase(key)) {
                    Object hostValue = headers.get(key);
                    if (hostValue != null && hostValue.toString().toLowerCase().contains(OLLAMA_KEYWORD)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            loggerMaker.warn("Failed to parse request headers when checking for ollama host: " + e.getMessage());
        }
        return false;
    }
}
