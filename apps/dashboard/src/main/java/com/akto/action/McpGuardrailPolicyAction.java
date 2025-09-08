package com.akto.action;

import com.akto.dto.McpGuardrailPolicy;
import com.akto.dao.McpGuardrailPolicyDao;

import java.util.HashMap;
import java.util.Map;

public class McpGuardrailPolicyAction extends UserAction {
    
    // Request parameters
    private String guardrailType;
    private boolean enabled;
    // Response data - using class variables instead of Map
    private McpGuardrailPolicy policy;
    private String message;
    private String responseGuardrailType;
    private boolean responseEnabled;
    private Map<String, Object> guardrailTypes;
    
    /**
     * Get the single guardrail policy
     */
    public String fetchCurrentPolicy() {
        try {
            policy = McpGuardrailPolicyDao.instance.getOrCreatePolicy();
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            addActionError("Error fetching policy: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }
    
    /**
     * Toggle a specific guardrail on/off
     */
    public String toggleGuardrail() {
        try {
            if (guardrailType == null || guardrailType.trim().isEmpty()) {
                addActionError("Guardrail type is required");
                return ERROR.toUpperCase();
            }
            
            McpGuardrailPolicyDao.instance.toggleGuardrail(guardrailType, enabled);
            
            // Set response data using class variables
            this.message = "Guardrail " + guardrailType + " " + (enabled ? "enabled" : "disabled") + " successfully";
            this.responseGuardrailType = guardrailType;
            this.responseEnabled = enabled;
            
            return SUCCESS.toUpperCase();
            
        } catch (Exception e) {
            addActionError("Error toggling guardrail: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }
    /**
     * Toggle entire policy on/off
     */
    public String togglePolicy() {
        try {
            policy = McpGuardrailPolicyDao.instance.getOrCreatePolicy();
            boolean newStatus = !policy.getActive();
            McpGuardrailPolicyDao.instance.togglePolicyStatus(newStatus);
            
            // Set response data using class variables
            this.message = "Policy " + (newStatus ? "enabled" : "disabled") + " successfully";
            this.responseEnabled = newStatus;
            
            return SUCCESS.toUpperCase();
            
        } catch (Exception e) {
            addActionError("Error toggling policy: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }
    
    /**
     * Get available guardrail types
     */
    public String fetchCurrentGuardrailTypes() {
        try {
            this.guardrailTypes = new HashMap<>();
            for (McpGuardrailPolicy.GuardrailType type : McpGuardrailPolicy.GuardrailType.values()) {
                Map<String, Object> typeInfo = new HashMap<>();
                typeInfo.put("displayName", type.getDisplayName());
                typeInfo.put("description", type.getDescription());
                typeInfo.put("icon", type.getIcon());
                this.guardrailTypes.put(type.name(), typeInfo);
            }
            
            return SUCCESS.toUpperCase();
            
        } catch (Exception e) {
            addActionError("Error fetching guardrail types: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }
    
    /**
     * Get available actions
     */
    // Getters and Setters
    public String getGuardrailType() {
        return guardrailType;
    }
    
    public void setGuardrailType(String guardrailType) {
        this.guardrailType = guardrailType;
    }
    
    public boolean getEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public McpGuardrailPolicy getPolicy() {
        return policy;
    }
    
    public void setPolicy(McpGuardrailPolicy policy) {
        this.policy = policy;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getResponseGuardrailType() {
        return responseGuardrailType;
    }
    
    public void setResponseGuardrailType(String responseGuardrailType) {
        this.responseGuardrailType = responseGuardrailType;
    }
    
    public boolean getResponseEnabled() {
        return responseEnabled;
    }
    
    public void setResponseEnabled(boolean responseEnabled) {
        this.responseEnabled = responseEnabled;
    }
    
    public Map<String, Object> getGuardrailTypes() {
        return this.guardrailTypes;
    }

    public void setGuardrailTypes(Map<String, Object> guardrailTypes) {
        this.guardrailTypes = guardrailTypes;
    }
}
