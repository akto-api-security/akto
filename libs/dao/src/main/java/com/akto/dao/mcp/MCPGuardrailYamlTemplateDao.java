package com.akto.dao.mcp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.mcp.MCPGuardrailConfig;
import com.akto.dto.mcp.MCPGuardrailConfigYamlParser;
import com.akto.dto.test_editor.YamlTemplate;
import com.mongodb.client.model.Filters;

public class MCPGuardrailYamlTemplateDao extends AccountsContextDao<YamlTemplate> {

    public static final MCPGuardrailYamlTemplateDao instance = new MCPGuardrailYamlTemplateDao();

    public Map<String, MCPGuardrailConfig> fetchMCPGuardrailConfig(boolean includeYamlContent) {
        List<YamlTemplate> yamlTemplates = MCPGuardrailYamlTemplateDao.instance.findAll(Filters.empty());
        return fetchMCPGuardrailConfig(includeYamlContent, yamlTemplates);
    }

    public Map<String, MCPGuardrailConfig> fetchMCPGuardrailConfig(boolean includeYamlContent, List<YamlTemplate> yamlTemplates) {
        Map<String, MCPGuardrailConfig> guardrailConfigMap = new HashMap<>();
        for (YamlTemplate yamlTemplate : yamlTemplates) {
            try {
                if (yamlTemplate != null) {
                    MCPGuardrailConfig guardrailConfig = MCPGuardrailConfigYamlParser.parseTemplate(yamlTemplate.getContent());
                    guardrailConfig.setAuthor(yamlTemplate.getAuthor());
                    guardrailConfig.setCreatedAt(yamlTemplate.getCreatedAt());
                    guardrailConfig.setUpdatedAt(yamlTemplate.getUpdatedAt());
                    if (includeYamlContent) {
                        guardrailConfig.setContent(yamlTemplate.getContent());
                    }
                    guardrailConfigMap.put(guardrailConfig.getId(), guardrailConfig);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return guardrailConfigMap;
    }

    public List<YamlTemplate> fetchActiveTemplates() {
        return MCPGuardrailYamlTemplateDao.instance.findAll(Filters.eq(YamlTemplate.INACTIVE, false));
    }

    public List<YamlTemplate> fetchTemplatesByType(String type) {
        // Since YamlTemplate doesn't have a type field, we'll filter by tags or info content
        // For now, we'll return all active templates and let the parser handle type filtering
        return fetchActiveTemplates();
    }

    @Override
    public String getCollName() {
        return "mcp_guardrail_yaml_templates";
    }

    @Override
    public Class<YamlTemplate> getClassT() {
        return YamlTemplate.class;
    }
}
