package com.akto.dto.monitoring;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.dto.api_protection_parse_layer.AggregationRules;
import com.akto.dto.test_editor.ConfigParserResult;
import com.akto.dto.test_editor.ExecutorConfigParserResult;
import com.akto.dto.test_editor.Info;

public class FilterConfig {
    private String id;
    public static final String ID = "id";
    private ConfigParserResult filter;
    public static final String FILTER = "filter";
    private ConfigParserResult ignore;
    public static final String IGNORE = "ignore";
    private Map<String, List<String>> wordLists;
    public static final String WORD_LISTS = "wordLists";
    public static final String CREATED_AT = "createdAt";
    private int createdAt;
    public static final String UPDATED_AT = "updatedAt";
    private int updatedAt;
    public static final String _AUTHOR = "author";
    private String author;
    public static final String _CONTENT = "content";
    private String content;
    private AggregationRules aggregationRules;
    public static final String _INFO = "info";
    private Info info;
    public static final String DEFAULT_ALLOW_FILTER = "DEFAULT_ALLOW_FILTER";
    public static final String DEFAULT_BLOCK_FILTER = "DEFAULT_BLOCK_FILTER";

    public enum FILTER_TYPE{
        BLOCKED , ALLOWED, MODIFIED, UNCHANGED, ERROR
    }

    private ExecutorConfigParserResult executor;
    private ConfigParserResult successFilter;
    public static final String SUCCESS_FILTER = "success_filter";
    private ConfigParserResult failureFilter;
    public static final String FAILURE_FILTER = "failure_filter";

    private IdentityExtraction identityExtraction;
    public static final String IDENTITY_EXTRACTION = "identity_extraction";

    public static class IdentityExtraction {
        private String source; // "request_payload", "response_payload", "request_headers"
        private String key;    // JSON key or header name

        public IdentityExtraction() {}

        public IdentityExtraction(String source, String key) {
            this.source = source;
            this.key = key;
        }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
    }

    public FilterConfig(String id, ConfigParserResult filter, Map<String, List<String>> wordLists, AggregationRules aggregationRules) {
        this.id = id;
        this.filter = filter;
        this.wordLists = wordLists;
        this.aggregationRules = aggregationRules;
    }

    public FilterConfig() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ConfigParserResult getFilter() {
        return filter;
    }

    public void setFilter(ConfigParserResult filter) {
        this.filter = filter;
    }

    public int getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(int createdAt) {
        this.createdAt = createdAt;
    }

    public int getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(int updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public Map<String, List<String>> getWordLists() {
        return wordLists;
    }

    public Map<String, Object> resolveVarMap() {
        Map<String, List<String>> wordListsMap = this.wordLists == null ? new HashMap<>() : this.wordLists;
        Map<String, Object> varMap = new HashMap<>();

        for (String key : wordListsMap.keySet()) {
            varMap.put("wordList_" + key, wordListsMap.get(key));
        }
        return varMap;
    }

    public void setWordLists(Map<String, List<String>> wordLists) {
        this.wordLists = wordLists;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public ExecutorConfigParserResult getExecutor() {
        return executor;
    }

    public void setExecutor(ExecutorConfigParserResult executor) {
        this.executor = executor;
    }

    public AggregationRules getAggregationRules() {
        return aggregationRules;
    }

    public void setAggregationRules(AggregationRules aggregationRules) {
        this.aggregationRules = aggregationRules;
    }

    public Info getInfo() {
        return info;
    }

    public void setInfo(Info info) {
        this.info = info;
    }

    public ConfigParserResult getIgnore() {
        return ignore;
    }

    public void setIgnore(ConfigParserResult ignore) {
        this.ignore = ignore;
    }

    public ConfigParserResult getSuccessFilter() {
        return successFilter;
    }

    public void setSuccessFilter(ConfigParserResult successFilter) {
        this.successFilter = successFilter;
    }

    public ConfigParserResult getFailureFilter() {
        return failureFilter;
    }

    public void setFailureFilter(ConfigParserResult failureFilter) {
        this.failureFilter = failureFilter;
    }

    public IdentityExtraction getIdentityExtraction() {
        return identityExtraction;
    }

    public void setIdentityExtraction(IdentityExtraction identityExtraction) {
        this.identityExtraction = identityExtraction;
    }
}
