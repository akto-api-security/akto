package com.akto.action.threat_detection;

import java.util.List;
import java.util.Map;

import org.bson.conversions.Bson;

import com.akto.action.UserAction;
import com.akto.dao.monitoring.FilterConfigYamlParser;
import com.akto.dao.monitoring.FilterYamlTemplateDao;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.util.Constants;
import com.akto.utils.TrafficFilterUtil;
import com.mongodb.BasicDBList;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

public class FilterYamlTemplateAction extends UserAction {

    BasicDBList templates;
    String content;

    public String fetchFilterYamlTemplate() {
        Map<String, FilterConfig> configs = FilterYamlTemplateDao.instance.fetchFilterConfig(true, false);
        this.templates = TrafficFilterUtil.getFilterTemplates(configs);
        return SUCCESS.toUpperCase();
    }

    public String saveFilterYamlTemplate() {

        FilterConfig filterConfig = new FilterConfig();
        try {
            filterConfig = FilterConfigYamlParser.parseTemplate(content, false);
            if (filterConfig.getId() == null) {
                throw new Exception("id field cannot be empty");
            }
            if (filterConfig.getFilter() == null) {
                throw new Exception("filter field cannot be empty");
            }
            List<Bson> updates = TrafficFilterUtil.getDbUpdateForTemplate(this.content, getSUser().getLogin());
            FilterYamlTemplateDao.instance.updateOne(
                    Filters.eq(Constants.ID, filterConfig.getId()),
                    Updates.combine(updates));

        } catch (Exception e) {
            e.printStackTrace();
            addActionError(e.getMessage());
            return ERROR.toUpperCase();
        }
       
        return SUCCESS.toUpperCase();
    }

    public BasicDBList getTemplates() {
        return templates;
    }

    public void setTemplates(BasicDBList templates) {
        this.templates = templates;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

}
