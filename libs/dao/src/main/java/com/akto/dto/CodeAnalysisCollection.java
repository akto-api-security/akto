package com.akto.dto;

import java.util.Map;

public class CodeAnalysisCollection {
    
    private String name;
    public static final String NAME = "name";

    private Map<String, CodeAnalysisApi> codeAnalysisApisMap;
    public static final String CODE_ANALYSIS_APIS_MAP = "codeAnalysisApisMap";

    private String projectDir;
    public static final String PROJECT_DIR = "projectDir";

    public CodeAnalysisCollection() {
    }

    public CodeAnalysisCollection(String name, Map<String, CodeAnalysisApi> codeAnalysisApisMap, String projectDir) {
        this.name = name;
        this.codeAnalysisApisMap = codeAnalysisApisMap;
        this.projectDir = projectDir;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, CodeAnalysisApi> getCodeAnalysisApisMap() {
        return codeAnalysisApisMap;
    }

    public void setCodeAnalysisApisMap(Map<String, CodeAnalysisApi> codeAnalysisApisMap) {
        this.codeAnalysisApisMap = codeAnalysisApisMap;
    }

    public String getProjectDir() {
        return projectDir;
    }

    public void setProjectDir(String projectDir) {
        this.projectDir = projectDir;
    }
}
