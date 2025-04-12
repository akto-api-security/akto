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
    private String author;

    final public static String DYNAMIC_SEVERITY = "dynamic_severity";
    private List<SeverityParserResult> dynamicSeverityList;

    public static final String SETTINGS = "attributes";
    private TemplateSettings attributes;

    public TestConfig(String id, Info info, Auth auth, ConfigParserResult apiSelectionFilters, Map<String, List<String>> wordlists, ExecutorConfigParserResult execute, 
        ConfigParserResult validation, Strategy strategy, TemplateSettings attributes) {
        
        this.id = id;
        info.setSubCategory(id);
        this.info = info;
        this.auth = auth;
        this.apiSelectionFilters = apiSelectionFilters;
        this.wordlists = wordlists;
        this.execute = execute;
        this.validation = validation;
        this.strategy = strategy;
        this.attributes = attributes;
    }

    public TestConfig() { }

    public static boolean isTestMultiNode(TestConfig testConfig){
        if(testConfig == null || testConfig.getExecute() == null){
            return false;
        }
        try {
            return testConfig.getExecute().getNode().getChildNodes().get(0).getValues().equals("multiple");
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        
    }

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

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public boolean isInactive() {
        return inactive;
    }
    
    public List<SeverityParserResult> getDynamicSeverityList() {
        return dynamicSeverityList;
    }

    public void setDynamicSeverityList(List<SeverityParserResult> dynamicSeverityList) {
        this.dynamicSeverityList = dynamicSeverityList;
    }

    public TemplateSettings getAttributes() {
        return attributes;
    }

    public void setAttributes(TemplateSettings attributes) {
        this.attributes = attributes;
    }
}
