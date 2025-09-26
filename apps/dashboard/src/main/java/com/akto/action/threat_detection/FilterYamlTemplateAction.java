package com.akto.action.threat_detection;

import java.util.List;
import java.util.Map;

import org.bson.conversions.Bson;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
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
    String templateId;

    public String fetchFilterYamlTemplate() {
        FilterYamlTemplateDao.deleteContextCollectionsForUser(Context.accountId.get(), Context.contextSource.get());
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

            if (!filterConfig.getFilter().getIsValid()) {
                throw new Exception(filterConfig.getFilter().getErrMsg());
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

    public String deleteFilterYamlTemplate() {
        try {
            if (this.templateId == null || this.templateId.isEmpty()) {
                throw new Exception("templateId cannot be empty");
            }

            FilterYamlTemplateDao.instance.getMCollection().deleteOne(
                Filters.eq(Constants.ID, this.templateId)
            );
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

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

}
