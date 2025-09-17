package com.akto.dto.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Map;
import java.util.List;

public class MCPGuardrailConfigYamlParser {
    
    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    
    // YAML field constants
    public static final String ID = "id";
    public static final String NAME = "name";
    public static final String DESCRIPTION = "description";
    public static final String VERSION = "version";
    public static final String TYPE = "type";
    public static final String ENABLED = "enabled";
    public static final String PRIORITY = "priority";
    public static final String CONFIGURATION = "configuration";
    
    // Configuration sub-field constants
    public static final String SENSITIVE_FIELDS = "sensitiveFields";
    public static final String VALIDATION_RULES = "validationRules";
    public static final String OUTPUT_FILTERS = "outputFilters";
    public static final String RATE_LIMIT_CONFIG = "rateLimitConfig";
    
    public static MCPGuardrailConfig parseTemplate(String yamlContent) throws Exception {
        if (yamlContent == null || yamlContent.trim().isEmpty()) {
            throw new IllegalArgumentException("YAML content cannot be null or empty");
        }
        
        try {
            Map<String, Object> yamlData = yamlMapper.readValue(yamlContent, new TypeReference<Map<String, Object>>() {});
            
            MCPGuardrailConfig config = new MCPGuardrailConfig();
            
            // Parse basic information
            config.setId((String) yamlData.get(ID));
            config.setName((String) yamlData.get(NAME));
            config.setDescription((String) yamlData.get(DESCRIPTION));
            config.setVersion((String) yamlData.get(VERSION));
            
            // Parse type
            String typeStr = (String) yamlData.get(TYPE);
            if (typeStr != null) {
                config.setType(typeStr.toUpperCase());
            } else {
                config.setType("CUSTOM");
            }
            
            // Parse enabled status
            Object enabledObj = yamlData.get(ENABLED);
            if (enabledObj instanceof Boolean) {
                config.setEnabled((Boolean) enabledObj);
            } else if (enabledObj instanceof String) {
                config.setEnabled(Boolean.parseBoolean((String) enabledObj));
            } else {
                config.setEnabled(true); // default to enabled
            }
            
            // Parse priority
            Object priorityObj = yamlData.get(PRIORITY);
            if (priorityObj instanceof Integer) {
                config.setPriority((Integer) priorityObj);
            } else if (priorityObj instanceof String) {
                try {
                    config.setPriority(Integer.parseInt((String) priorityObj));
                } catch (NumberFormatException e) {
                    config.setPriority(0);
                }
            } else {
                config.setPriority(0);
            }
            
            // Parse configuration object
            @SuppressWarnings("unchecked")
            Map<String, Object> configurationMap = (Map<String, Object>) yamlData.get(CONFIGURATION);
            if (configurationMap != null) {
                config.setConfiguration(configurationMap);
                
                // Extract specific configuration fields
                @SuppressWarnings("unchecked")
                List<String> sensitiveFields = (List<String>) configurationMap.get(SENSITIVE_FIELDS);
                config.setSensitiveFields(sensitiveFields);
                
                @SuppressWarnings("unchecked")
                Map<String, String> validationRules = (Map<String, String>) configurationMap.get(VALIDATION_RULES);
                config.setValidationRules(validationRules);
                
                @SuppressWarnings("unchecked")
                List<String> outputFilters = (List<String>) configurationMap.get(OUTPUT_FILTERS);
                config.setOutputFilters(outputFilters);
                
                @SuppressWarnings("unchecked")
                Map<String, Object> rateLimitConfig = (Map<String, Object>) configurationMap.get(RATE_LIMIT_CONFIG);
                config.setRateLimitConfig(rateLimitConfig);
            }
            
            return config;
            
        } catch (Exception e) {
            throw new Exception("Failed to parse MCP Guardrail YAML template: " + e.getMessage(), e);
        }
    }
    
    public static String toYaml(MCPGuardrailConfig config) throws Exception {
        try {
            return yamlMapper.writeValueAsString(config);
        } catch (Exception e) {
            throw new Exception("Failed to convert MCP Guardrail config to YAML: " + e.getMessage(), e);
        }
    }
}
