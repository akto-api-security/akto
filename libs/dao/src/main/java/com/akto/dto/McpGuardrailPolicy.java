package com.akto.dto;

import com.akto.dao.context.Context;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.types.ObjectId;

import java.util.HashMap;
import java.util.Map;

public class McpGuardrailPolicy {
    
    @BsonId
    private ObjectId id;
    
    public static final String ID = "_id";
    public static final String CREATED_BY = "createdBy";
    public static final String CREATED_AT = "createdAt";
    public static final String UPDATED_AT = "updatedAt";
    public static final String ACTIVE = "active";
    public static final String POLICY_NAME = "policyName";
    public static final String POLICY_DESCRIPTION = "policyDescription";
    public static final String GUARDRAIL_CONFIGS = "guardrailConfigs";
    
    private String createdBy; // User email
    private int createdAt;
    private int updatedAt;
    private boolean active;
    private String policyName;
    private String policyDescription;
    private Map<String, GuardrailConfig> guardrailConfigs;
    
    public McpGuardrailPolicy() {
        this.createdAt = Context.now();
        this.updatedAt = Context.now();
        this.active = true;
    }
    
    public McpGuardrailPolicy(String createdBy, String policyName, String policyDescription) {
        this();
        this.createdBy = createdBy;
        this.policyName = policyName;
        this.policyDescription = policyDescription;
        initializeDefaultConfigs();
    }
    
    /**
     * Initialize default configurations for all 4 guardrail types
     */
    private void initializeDefaultConfigs() {
        this.guardrailConfigs = new HashMap<>();
        
        // PII Guardrails - Default enabled
        guardrailConfigs.put(GuardrailType.PII_GUARDRAILS.name(), new GuardrailConfig(false, "REDACT"));
        
        // Word Mask Guardrails - Default enabled
        guardrailConfigs.put(GuardrailType.WORD_MASK_GUARDRAILS.name(), new GuardrailConfig(false, "REDACT"));
        
        // Injection Guardrails - Default enabled
        guardrailConfigs.put(GuardrailType.INJECTION_GUARDRAILS.name(), new GuardrailConfig(false, "BLOCK"));
        
        // Response Block - Default enabled
        guardrailConfigs.put(GuardrailType.RESPONSE_BLOCK.name(), new GuardrailConfig(false, "BLOCK"));
    }
    
    // Getters and Setters
    public ObjectId getId() {
        return id;
    }
    
    public void setId(ObjectId id) {
        this.id = id;
    }
    
    public String getCreatedBy() {
        return createdBy;
    }
    
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
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
    
    public boolean getActive() {
        return active;
    }
    
    public void setActive(boolean active) {
        this.active = active;
    }
    
    public String getPolicyName() {
        return policyName;
    }
    
    public void setPolicyName(String policyName) {
        this.policyName = policyName;
    }
    
    public String getPolicyDescription() {
        return policyDescription;
    }
    
    public void setPolicyDescription(String policyDescription) {
        this.policyDescription = policyDescription;
    }
    
    public Map<String, GuardrailConfig> getGuardrailConfigs() {
        return guardrailConfigs;
    }
    
    public void setGuardrailConfigs(Map<String, GuardrailConfig> guardrailConfigs) {
        this.guardrailConfigs = guardrailConfigs;
    }
    
    /**
     * Simple method to toggle a specific guardrail on/off
     */
    public void toggleGuardrail(String guardrailType, boolean enabled) {
        if (guardrailConfigs.containsKey(guardrailType)) {
            guardrailConfigs.get(guardrailType).setEnabled(enabled);
            this.updatedAt = Context.now();
        }
    }
    
    /**
     * Check if a specific guardrail is enabled
     */
    public boolean isGuardrailEnabled(String guardrailType) {
        return guardrailConfigs.containsKey(guardrailType) && 
               guardrailConfigs.get(guardrailType).getEnabled();
    }
    
    // Inner class for Guardrail Configuration - Simplified
    public static class GuardrailConfig {
        public static final String ENABLED = "enabled";

        private boolean enabled;

        public GuardrailConfig() {}
        
        public GuardrailConfig(boolean isEnabled, String action) {
            this.enabled = isEnabled;
        }
        
        public boolean getEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
    
    // Enum for the 4 main Guardrail Types
    public enum GuardrailType {
        PII_GUARDRAILS("PII Guardrails", "Protect Personally Identifiable Information", "🔒"),
        WORD_MASK_GUARDRAILS("Word Mask Guardrails", "Mask sensitive words and patterns", "🔄"),
        INJECTION_GUARDRAILS("Injection Guardrails", "Prevent injection attacks", "🛡️"),
        RESPONSE_BLOCK("Response Block", "Block responses based on content", "🚫");
        
        private final String displayName;
        private final String description;
        private final String icon;
        
        GuardrailType(String displayName, String description, String icon) {
            this.displayName = displayName;
            this.description = description;
            this.icon = icon;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getDescription() {
            return description;
        }
        
        public String getIcon() {
            return icon;
        }
    }
    
    // Enum for Actions - Simplified
}
