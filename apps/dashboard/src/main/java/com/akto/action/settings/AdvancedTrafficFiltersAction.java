package com.akto.action.settings;

import java.util.List;
import java.util.Map;

import org.bson.conversions.Bson;

import com.akto.action.UserAction;
import com.akto.dao.monitoring.FilterConfigYamlParser;
import com.akto.dao.monitoring.FilterYamlTemplateDao;
import com.akto.dao.runtime_filters.AdvancedTrafficFiltersDao;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.util.Constants;
import com.akto.utils.TrafficFilterUtil;
import com.mongodb.BasicDBList;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Updates;

public class AdvancedTrafficFiltersAction extends UserAction {

    private BasicDBList templatesList;
    private String yamlContent;
    private String templateId;
    private boolean inactive;

    public String fetchAllFilterTemplates(){
        List<YamlTemplate> yamlTemplates =  AdvancedTrafficFiltersDao.instance.findAll(Filters.empty());
        Map<String, FilterConfig> configs = FilterYamlTemplateDao.instance.fetchFilterConfig(true, yamlTemplates);
        this.templatesList = TrafficFilterUtil.getFilterTemplates(configs);
        return SUCCESS.toUpperCase();
    }

    public String saveYamlTemplateForTrafficFilters(){
        FilterConfig filterConfig = new FilterConfig();
        try {
            filterConfig = FilterConfigYamlParser.parseTemplate(yamlContent);
            if (filterConfig.getId() == null) {
                throw new Exception("id field cannot be empty");
            }
            if (filterConfig.getFilter() == null) {
                throw new Exception("filter field cannot be empty");
            }
            List<Bson> updates = TrafficFilterUtil.getDbUpdateForTemplate(this.yamlContent, getSUser().getLogin());
            AdvancedTrafficFiltersDao.instance.updateOne(
                    Filters.eq(Constants.ID, filterConfig.getId()),
                    Updates.combine(updates));

        } catch (Exception e) {
            e.printStackTrace();
            addActionError(e.getMessage());
            return ERROR.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

    public String changeActivityOfFilter() {
        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions();
        options.upsert(false);
        AdvancedTrafficFiltersDao.instance.getMCollection().findOneAndUpdate(
            Filters.eq(Constants.ID, this.templateId),
            Updates.set(YamlTemplate.INACTIVE, this.inactive),
            options
        );

        return SUCCESS.toUpperCase();
    }

    public String deleteAdvancedFilter(){
        AdvancedTrafficFiltersDao.instance.getMCollection().deleteOne(
            Filters.eq(Constants.ID, this.templateId)
        );
        return SUCCESS.toUpperCase();
    }

    public BasicDBList getTemplatesList() {
        return templatesList;
    }

    public void setYamlContent(String yamlContent) {
        this.yamlContent = yamlContent;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public void setInactive(boolean inactive) {
        this.inactive = inactive;
    }

    
}
