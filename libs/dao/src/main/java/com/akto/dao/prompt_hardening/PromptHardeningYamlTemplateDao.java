package com.akto.dao.prompt_hardening;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.test_editor.YamlTemplate;
import com.mongodb.client.model.Filters;

public class PromptHardeningYamlTemplateDao extends AccountsContextDao<YamlTemplate> {

    public static final PromptHardeningYamlTemplateDao instance = new PromptHardeningYamlTemplateDao();

    /**
     * Fetches all prompt hardening templates from the database
     * @return Map of template ID to YamlTemplate
     */
    public Map<String, YamlTemplate> fetchAllPromptTemplates() {
        List<YamlTemplate> yamlTemplates = PromptHardeningYamlTemplateDao.instance.findAll(Filters.empty());
        return convertToMap(yamlTemplates);
    }

    /**
     * Converts list of YamlTemplate to a map with ID as key
     * @param yamlTemplates List of YamlTemplate objects
     * @return Map of template ID to YamlTemplate
     */
    private static Map<String, YamlTemplate> convertToMap(List<YamlTemplate> yamlTemplates) {
        Map<String, YamlTemplate> templateMap = new HashMap<>();
        for (YamlTemplate yamlTemplate : yamlTemplates) {
            if (yamlTemplate != null && yamlTemplate.getId() != null) {
                templateMap.put(yamlTemplate.getId(), yamlTemplate);
            }
        }
        return templateMap;
    }

    /**
     * Fetches templates grouped by category
     * @return Map of category to list of templates
     */
    public Map<String, List<YamlTemplate>> fetchTemplatesByCategory() {
        List<YamlTemplate> yamlTemplates = PromptHardeningYamlTemplateDao.instance.findAll(Filters.empty());
        Map<String, List<YamlTemplate>> categoryMap = new HashMap<>();
        
        for (YamlTemplate template : yamlTemplates) {
            if (isValidTemplate(template)) {
                String categoryName = template.getInfo().getCategory().getName();
                categoryMap.computeIfAbsent(categoryName, k -> new ArrayList<>()).add(template);
            }
        }
        
        return categoryMap;
    }
    
    private boolean isValidTemplate(YamlTemplate template) {
        return template != null && 
               template.getInfo() != null && 
               template.getInfo().getCategory() != null && 
               template.getInfo().getCategory().getName() != null;
    }

    @Override
    public String getCollName() {
        return "prompt_hardening_yaml_templates";
    }

    @Override
    public Class<YamlTemplate> getClassT() {
        return YamlTemplate.class;
    }
}

