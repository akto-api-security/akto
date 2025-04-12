package com.akto.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.bson.conversions.Bson;

import com.akto.dao.context.Context;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.util.enums.GlobalEnums.YamlTemplateSource;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Updates;

public class TrafficFilterUtil {
    public static BasicDBList getFilterTemplates(Map<String, FilterConfig> configs){
        BasicDBList templates = new BasicDBList();
        if (configs != null && !configs.isEmpty()) {
            for (Entry<String, FilterConfig> apiFilterEntry : configs.entrySet()) {
                FilterConfig config = apiFilterEntry.getValue();
                BasicDBObject template = new BasicDBObject();
                template.append(FilterConfig.ID, config.getId());
                template.append(FilterConfig._CONTENT, config.getContent());
                template.append(FilterConfig._AUTHOR, config.getAuthor());
                template.append(FilterConfig.CREATED_AT, config.getCreatedAt());
                template.append(FilterConfig.UPDATED_AT, config.getUpdatedAt());
                template.append(FilterConfig._INFO, config.getInfo());
                templates.add(template);
            }
        }

        return templates;
    }

    public static List<Bson> getDbUpdateForTemplate(String content, String userEmail) throws Exception{
         try {
            String author = userEmail;
            int timeNow = Context.now();
            int createdAt = timeNow;
            int updatedAt = timeNow;

            List<Bson> updates = new ArrayList<>(
                    Arrays.asList(
                            Updates.setOnInsert(YamlTemplate.CREATED_AT, createdAt),
                            Updates.setOnInsert(YamlTemplate.AUTHOR, author),
                            Updates.set(YamlTemplate.UPDATED_AT, updatedAt),
                            Updates.set(YamlTemplate.CONTENT, content),
                            Updates.setOnInsert(YamlTemplate.SOURCE, YamlTemplateSource.CUSTOM)));
            return updates;

        } catch (Exception e) {
            throw e;
        }
    }
}
