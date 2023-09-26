package com.akto.dto.test_editor;

import com.akto.util.enums.GlobalEnums;

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
    private String content;
    private GlobalEnums.YamlTemplateSource templateSource;
    private int updateTs;

    private Strategy strategy;
    private boolean inactive;

    public TestConfig(String id, Info info, Auth auth, ConfigParserResult apiSelectionFilters, Map<String, List<String>> wordlists, ExecutorConfigParserResult execute, 
        ConfigParserResult validation, Strategy strategy) {
        
        this.id = id;
        this.info = info;
        this.auth = auth;
        this.apiSelectionFilters = apiSelectionFilters;
        this.wordlists = wordlists;
        this.execute = execute;
        this.validation = validation;
        this.strategy = strategy;
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public GlobalEnums.YamlTemplateSource getTemplateSource() {
        return templateSource;
    }

    public void setTemplateSource(GlobalEnums.YamlTemplateSource templateSource) {
        this.templateSource = templateSource;
    }

    public int getUpdateTs() {
        return updateTs;
    }

    public void setUpdateTs(int updateTs) {
        this.updateTs = updateTs;
    }

    public Strategy getStrategy() {
        return strategy;
    }

    public void setStrategy(Strategy strategy) {
        this.strategy = strategy;
    }
    
    public boolean getInactive() {
        return inactive;
    }

    public void setInactive(boolean inactive) {
        this.inactive = inactive;
    }
}
