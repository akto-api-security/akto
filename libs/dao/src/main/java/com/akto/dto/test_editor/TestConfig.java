package com.akto.dto.test_editor;

public class TestConfig {
    
    private String id;
    
    private Info info;

    private ConfigParserResult apiSelectionFilters;

    private ExecutorConfigParserResult execute;

    private ConfigParserResult validation;

    public TestConfig(String id, Info info, ConfigParserResult apiSelectionFilters, ExecutorConfigParserResult execute, 
        ConfigParserResult validation) {
        
        this.id = id;
        this.info = info;
        this.apiSelectionFilters = apiSelectionFilters;
        this.execute = execute;
        this.validation = validation;
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

    public ConfigParserResult getApiSelectionFilters() {
        return apiSelectionFilters;
    }

    public void setApiSelectionFilters(ConfigParserResult apiSelectionFilters) {
        this.apiSelectionFilters = apiSelectionFilters;
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

}
