package com.akto.action;

import com.akto.dao.mcp.MCPGuardrailYamlTemplateDao;
import com.akto.dto.mcp.MCPGuardrailConfig;
import com.akto.dto.mcp.MCPGuardrailType;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.log.LoggerMaker;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.ActionSupport;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class MCPGuardrailsAction extends ActionSupport {
    
    private static final LoggerMaker loggerMaker = new LoggerMaker(MCPGuardrailsAction.class, LoggerMaker.LogDb.DASHBOARD);
    
    // Response fields
    private List<YamlTemplate> mcpGuardrailTemplates;
    private Map<String, MCPGuardrailConfig> mcpGuardrailConfigs;
    private YamlTemplate mcpGuardrailTemplate;
    
    // Request fields
    private String templateId;
    private String guardrailType;
    private boolean includeYamlContent = true;
    private boolean activeOnly = true;

    /**
     * Fetch all MCP Guardrail YAML templates
     */
    public String fetchMCPGuardrailTemplates() {
        try {
            if (activeOnly) {
                mcpGuardrailTemplates = MCPGuardrailYamlTemplateDao.instance.fetchActiveTemplates();
            } else {
                mcpGuardrailTemplates = MCPGuardrailYamlTemplateDao.instance.findAll(Filters.empty());
            }
            
            loggerMaker.infoAndAddToDb("Fetched " + mcpGuardrailTemplates.size() + " MCP Guardrail templates", LoggerMaker.LogDb.DASHBOARD);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching MCP Guardrail templates: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            addActionError("Failed to fetch MCP Guardrail templates");
            return ERROR.toUpperCase();
        }
    }

    /**
     * Fetch MCP Guardrail templates by type
     */
    public String fetchMCPGuardrailTemplatesByType() {
        try {
            if (guardrailType == null || guardrailType.trim().isEmpty()) {
                addActionError("Guardrail type is required");
                return ERROR.toUpperCase();
            }
            
            mcpGuardrailTemplates = MCPGuardrailYamlTemplateDao.instance.fetchTemplatesByType(guardrailType.toUpperCase());
            
            loggerMaker.infoAndAddToDb("Fetched " + mcpGuardrailTemplates.size() + " MCP Guardrail templates for type: " + guardrailType, LoggerMaker.LogDb.DASHBOARD);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching MCP Guardrail templates by type: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            addActionError("Failed to fetch MCP Guardrail templates by type");
            return ERROR.toUpperCase();
        }
    }

    /**
     * Fetch parsed MCP Guardrail configurations
     */
    public String fetchMCPGuardrailConfigs() {
        try {
            mcpGuardrailConfigs = MCPGuardrailYamlTemplateDao.instance.fetchMCPGuardrailConfig(includeYamlContent);
            
            loggerMaker.infoAndAddToDb("Fetched " + mcpGuardrailConfigs.size() + " MCP Guardrail configurations", LoggerMaker.LogDb.DASHBOARD);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching MCP Guardrail configurations: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            addActionError("Failed to fetch MCP Guardrail configurations");
            return ERROR.toUpperCase();
        }
    }

    /**
     * Fetch a specific MCP Guardrail template by ID
     */
    public String fetchMCPGuardrailTemplate() {
        try {
            if (templateId == null || templateId.trim().isEmpty()) {
                addActionError("Template ID is required");
                return ERROR.toUpperCase();
            }
            
            mcpGuardrailTemplate = MCPGuardrailYamlTemplateDao.instance.findOne("id", templateId);
            
            if (mcpGuardrailTemplate == null) {
                addActionError("MCP Guardrail template not found");
                return ERROR.toUpperCase();
            }
            
            loggerMaker.infoAndAddToDb("Fetched MCP Guardrail template: " + templateId, LoggerMaker.LogDb.DASHBOARD);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching MCP Guardrail template: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            addActionError("Failed to fetch MCP Guardrail template");
            return ERROR.toUpperCase();
        }
    }

    /**
     * Get available MCP Guardrail types
     */
    public String fetchMCPGuardrailTypes() {
        try {
            // Return all available types as a simple list
            MCPGuardrailType[] types = MCPGuardrailType.values();
            StringBuilder typesJson = new StringBuilder("[");
            for (int i = 0; i < types.length; i++) {
                if (i > 0) typesJson.append(",");
                typesJson.append("\"").append(types[i].name()).append("\"");
            }
            typesJson.append("]");
            
            loggerMaker.infoAndAddToDb("Fetched MCP Guardrail types", LoggerMaker.LogDb.DASHBOARD);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching MCP Guardrail types: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            addActionError("Failed to fetch MCP Guardrail types");
            return ERROR.toUpperCase();
        }
    }
}
