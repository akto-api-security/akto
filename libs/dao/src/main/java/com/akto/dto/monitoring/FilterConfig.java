package com.akto.dto.monitoring;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.dto.test_editor.ConfigParserResult;

public class FilterConfig {
    private String id;
    private ConfigParserResult filter;
    public static final String FILTER = "filter";
    private Map<String, List<String>> wordLists;
    public static final String WORD_LISTS = "wordLists";
    private int createdAt;
    private int updatedAt;
    private String author;

    public FilterConfig(String id, ConfigParserResult filter, Map<String, List<String>> wordLists) {
        this.id = id;
        this.filter = filter;
        this.wordLists = wordLists;
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
        Map<String, List<String>> wordListsMap = this.wordLists == null ? new HashMap<>(): this.wordLists;
        Map<String, Object> varMap = new HashMap<>();

        for (String key : wordListsMap.keySet()) {
            varMap.put("wordList_" + key, wordListsMap.get(key));
        }
        return varMap;
    }

    public void setWordLists(Map<String, List<String>> wordLists) {
        this.wordLists = wordLists;
    }

}
