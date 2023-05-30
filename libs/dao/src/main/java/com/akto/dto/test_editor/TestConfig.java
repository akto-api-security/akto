package com.akto.dto.test_editor;

import java.util.List;
import java.util.Map;

public class TestConfig {
    
    private String id;
    
    private Info info;

    private Auth auth;

    private ConfigParserResult apiSelectionFilters;

    private Map<String, List<String>> wordlists;

    private ExecutorConfigParserResult execute;

    private ConfigParserResult validation;

    private Metadata metadata;

    public TestConfig(String id, Info info, Auth auth, ConfigParserResult apiSelectionFilters, Map<String, List<String>> wordlists, ExecutorConfigParserResult execute, 
        ConfigParserResult validation, Metadata metadata) {
        
        this.id = id;
        this.info = info;
        this.auth = auth;
        this.apiSelectionFilters = apiSelectionFilters;
        this.wordlists = wordlists;
        this.execute = execute;
        this.validation = validation;
        this.metadata = metadata;
    }

    public TestConfig() { }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Info getInfo() {
        return info;
    }

    public void setInfo(Info info) {
        this.info = info;
    }

    public Auth getAuth() {
        return auth;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    public ConfigParserResult getApiSelectionFilters() {
        return apiSelectionFilters;
    }

    public void setApiSelectionFilters(ConfigParserResult apiSelectionFilters) {
        this.apiSelectionFilters = apiSelectionFilters;
    }

    public Map<String, List<String>> getWordlists() {
        return wordlists;
    }

    public void setWordlists(Map<String, List<String>> wordlists) {
        this.wordlists = wordlists;
    }

    public ExecutorConfigParserResult getExecute() {
        return execute;
    }

    public void setExecute(ExecutorConfigParserResult execute) {
        this.execute = execute;
    }

    public ConfigParserResult getValidation() {
        return validation;
    }

    public void setValidation(ConfigParserResult validation) {
        this.validation = validation;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }
}
