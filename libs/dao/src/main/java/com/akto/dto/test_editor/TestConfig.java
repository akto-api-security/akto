package com.akto.dto.test_editor;

public class TestConfig {
    
    private String id;
    
    private Info info;

    private ConfigParserResult apiSelectionFilters;

    // private List<Roles> environment;

    // private Request request;

    // private Validation validation;

    public TestConfig(String id, Info info, ConfigParserResult apiSelectionFilters) {
        this.id = id;
        this.info = info;
        this.apiSelectionFilters = apiSelectionFilters;
        // this.environment = environment;
        // this.request = request;
        // this.validation = validation;
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

    // public List<Roles> getEnvironment() {
    //     return environment;
    // }

    // public void setEnvironment(List<Roles> environment) {
    //     this.environment = environment;
    // }

    // public Request getRequest() {
    //     return request;
    // }

    // public void setRequest(Request request) {
    //     this.request = request;
    // }

    // public Validation getValidatione() {
    //     return validation;
    // }

    // public void setValidation(Validation validation) {
    //     this.validation = validation;
    // }

}
