package com.akto.dto.mcp;

import java.util.List;
import java.util.Map;

public class MCPGuardrailConfig {
    
    private String id;
    private String name;
    private String description;
    private String version;
    private String author;
    private int createdAt;
    private int updatedAt;
    private String content;
    private String type;
    private boolean enabled;
    private int priority;
    private Map<String, Object> configuration;
    private List<String> sensitiveFields;
    private Map<String, String> validationRules;
    private List<String> outputFilters;
    private Map<String, Object> rateLimitConfig;

    public MCPGuardrailConfig() {}

    public MCPGuardrailConfig(String id, String name, String description, String version, 
                             String type, boolean enabled, int priority) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.version = version;
        this.type = type;
        this.enabled = enabled;
        this.priority = priority;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public Map<String, Object> getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Map<String, Object> configuration) {
        this.configuration = configuration;
    }

    public List<String> getSensitiveFields() {
        return sensitiveFields;
    }

    public void setSensitiveFields(List<String> sensitiveFields) {
        this.sensitiveFields = sensitiveFields;
    }

    public Map<String, String> getValidationRules() {
        return validationRules;
    }

    public void setValidationRules(Map<String, String> validationRules) {
        this.validationRules = validationRules;
    }

    public List<String> getOutputFilters() {
        return outputFilters;
    }

    public void setOutputFilters(List<String> outputFilters) {
        this.outputFilters = outputFilters;
    }

    public Map<String, Object> getRateLimitConfig() {
        return rateLimitConfig;
    }

    public void setRateLimitConfig(Map<String, Object> rateLimitConfig) {
        this.rateLimitConfig = rateLimitConfig;
    }
}
