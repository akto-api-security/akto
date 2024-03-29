package com.akto.dto;

import java.util.Map;

public class CodeAnalysisCollection {
    
    private String name;
    public static final String NAME = "name";

    private Map<String, String> urlsMap;
    public static final String URLS_MAP = "urlsMap";

    private String projectDir;
    public static final String PROJECT_DIR = "projectDir";

    public CodeAnalysisCollection() {
    }

    public CodeAnalysisCollection(String name, Map<String, String> urlsMap, String projectDir) {
        this.name = name;
        this.urlsMap = urlsMap;
        this.projectDir = projectDir;
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

    public String getProjectDir() {
        return projectDir;
    }

    public void setProjectDir(String projectDir) {
        this.projectDir = projectDir;
    }
}
