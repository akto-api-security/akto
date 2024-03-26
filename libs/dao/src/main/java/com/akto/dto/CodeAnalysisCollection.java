package com.akto.dto;

import java.util.Map;

public class CodeAnalysisCollection {
    
    private String name;
    private Map<String, String> urlsMap;

    public CodeAnalysisCollection() {
    }

    public CodeAnalysisCollection(String name, Map<String, String> urlsMap) {
        this.name = name;
        this.urlsMap = urlsMap;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, String> getUrlsMap() {
        return urlsMap;
    }

    public void setUrlsMap(Map<String, String> urlsMap) {
        this.urlsMap = urlsMap;
    }
}
