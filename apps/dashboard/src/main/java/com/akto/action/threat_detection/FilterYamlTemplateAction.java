package com.akto.action.threat_detection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.bson.conversions.Bson;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.FilterConfigYamlParser;
import com.akto.dao.monitoring.FilterYamlTemplateDao;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.YamlTemplateSource;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

public class FilterYamlTemplateAction extends UserAction {

    BasicDBList templates;
    String content;

    public String fetchFilterYamlTemplate() {
        Map<String, FilterConfig> configs = FilterYamlTemplateDao.instance.fetchFilterConfig(true);
        if (configs != null && !configs.isEmpty()) {
            templates = new BasicDBList();
            for (Entry<String, FilterConfig> apiFilterEntry : configs.entrySet()) {
                FilterConfig config = apiFilterEntry.getValue();
                BasicDBObject template = new BasicDBObject();
                template.append(FilterConfig.ID, config.getId());
                template.append(FilterConfig._CONTENT, config.getContent());
                template.append(FilterConfig._AUTHOR, config.getAuthor());
                template.append(FilterConfig.CREATED_AT, config.getCreatedAt());
                template.append(FilterConfig.UPDATED_AT, config.getUpdatedAt());
                templates.add(template);
            }
        }
        return SUCCESS.toUpperCase();
    }

    public String saveFilterYamlTemplate() {

        FilterConfig filterConfig;
        try {
            filterConfig = FilterConfigYamlParser.parseTemplate(content);
            if (filterConfig.getId() == null) {
                addActionError("id field cannot be empty");
                return ERROR.toUpperCase();
            }

            String id = filterConfig.getId();
            if (filterConfig.getFilter() == null) {
                addActionError("filter field cannot be empty");
                return ERROR.toUpperCase();
            }

            String author = getSUser().getLogin();
            int createdAt = Context.now();
            int updatedAt = Context.now();

            List<Bson> updates = new ArrayList<>(
                    Arrays.asList(
                            Updates.setOnInsert(YamlTemplate.CREATED_AT, createdAt),
                            Updates.setOnInsert(YamlTemplate.AUTHOR, author),
                            Updates.set(YamlTemplate.UPDATED_AT, updatedAt),
                            Updates.set(YamlTemplate.CONTENT, content),
                            Updates.setOnInsert(YamlTemplate.SOURCE, YamlTemplateSource.CUSTOM)));
            FilterYamlTemplateDao.instance.updateOne(
                    Filters.eq(Constants.ID, id),
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
