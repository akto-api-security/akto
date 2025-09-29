package com.akto.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bson.conversions.Bson;

import com.akto.dao.context.Context;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.util.enums.GlobalEnums;
import com.mongodb.client.model.Updates;

public class MCPGuardrailUtil {

    public static List<Bson> getDbUpdateForTemplate(String content, String userEmail) throws Exception {
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
                            Updates.setOnInsert(YamlTemplate.SOURCE, GlobalEnums.YamlTemplateSource.CUSTOM)));
            return updates;

        } catch (Exception e) {
            throw e;
        }
    }
}
