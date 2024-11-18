package com.akto.dao.monitoring;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.mongodb.client.model.Filters;

public class FilterYamlTemplateDao extends AccountsContextDao<YamlTemplate> {

    public static final FilterYamlTemplateDao instance = new FilterYamlTemplateDao();

    public Map<String, FilterConfig> fetchFilterConfig(
            boolean includeYamlContent, boolean shouldParseExecutor) {
        List<YamlTemplate> yamlTemplates = FilterYamlTemplateDao.instance.findAll(Filters.empty());
        return fetchFilterConfig(includeYamlContent, yamlTemplates, shouldParseExecutor);
    }

    public static Map<String, FilterConfig> fetchFilterConfig(
            boolean includeYamlContent,
            List<YamlTemplate> yamlTemplates,
            boolean shouldParseExecutor) {
        Map<String, FilterConfig> filterConfigMap = new HashMap<>();
        for (YamlTemplate yamlTemplate : yamlTemplates) {
            try {
                if (yamlTemplate != null) {
                    FilterConfig filterConfig =
                            FilterConfigYamlParser.parseTemplate(
                                    yamlTemplate.getContent(), shouldParseExecutor);
                    filterConfig.setAuthor(yamlTemplate.getAuthor());
                    filterConfig.setCreatedAt(yamlTemplate.getCreatedAt());
                    filterConfig.setUpdatedAt(yamlTemplate.getUpdatedAt());
                    if (includeYamlContent) {
                        filterConfig.setContent(yamlTemplate.getContent());
                    }
                    filterConfigMap.put(filterConfig.getId(), filterConfig);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return filterConfigMap;
    }

    @Override
    public String getCollName() {
        return "filter_yaml_templates";
    }

    @Override
    public Class<YamlTemplate> getClassT() {
        return YamlTemplate.class;
    }
}
