package com.akto.dao.monitoring;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.mongodb.client.model.Filters;

public class FilterYamlTemplateDao extends AccountsContextDao<YamlTemplate> {

    public static final FilterYamlTemplateDao instance = new FilterYamlTemplateDao();

    public FilterConfig fetchFilterConfig(boolean includeYamlContent) {
        YamlTemplate yamlTemplate = FilterYamlTemplateDao.instance.findOne(Filters.empty());
        FilterConfig filterConfig = null;
        try {
            if (yamlTemplate != null) {
                filterConfig = FilterConfigYamlParser.parseTemplate(yamlTemplate.getContent());
                filterConfig.setAuthor(yamlTemplate.getAuthor());
                filterConfig.setCreatedAt(yamlTemplate.getCreatedAt());
                filterConfig.setUpdatedAt(yamlTemplate.getUpdatedAt());
                if(includeYamlContent){
                    filterConfig.setContent(yamlTemplate.getContent());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return filterConfig;
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
