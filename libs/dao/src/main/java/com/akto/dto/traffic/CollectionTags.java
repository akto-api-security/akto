package com.akto.dto.traffic;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.akto.dao.context.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CollectionTags {
    int lastUpdatedTs;
    public static final String LAST_UPDATED_TS = "lastUpdatedTs";

    String keyName;
    public static final String KEY_NAME = "keyName";

    String value;
    public static final String VALUE = "value";

    @Override
    public int hashCode() {
        return Objects.hash(lastUpdatedTs, keyName, value);
    }

    public static List<CollectionTags> getUpdatedTagsForCollection(List<CollectionTags> collectionTagsList, String tagsJson) {
        if (tagsJson == null || tagsJson.isEmpty()) {
            return new ArrayList<>();
        }
        Gson gson = new Gson();
        Map<String, String> tagsMap = gson.fromJson(tagsJson, Map.class);
        Map<String, String> dbtagsMap = new HashMap<>();
        boolean shouldUpdate = false;

        for(CollectionTags collectionTag : collectionTagsList) {
            // Tag was deleted
            String key = collectionTag.getKeyName();
            dbtagsMap.put(key, collectionTag.getValue());
            if (!tagsMap.containsKey(key)) {
                shouldUpdate = true;
            } else if(tagsMap.containsKey(key) && !tagsMap.get(key).equals(collectionTag.getValue())) {
                // Tag was updated
                shouldUpdate = true;
            }
        }

        List<CollectionTags> newTags = new ArrayList<>();
        // Detect new tags
        for(Map.Entry<String, String> entry : tagsMap.entrySet()) {
            String key = entry.getKey();
            if (!dbtagsMap.containsKey(key)) {
                // New tag added
                shouldUpdate = true;
            }
            newTags.add(new CollectionTags(Context.now(), key, entry.getValue()));
        }

        return shouldUpdate ? newTags : null;
    }

    public static String calculateTagsDiff(List<CollectionTags> collectionTagsList, String tagsJson) {
        if (tagsJson == null || tagsJson.isEmpty()) {
            return null;
        }

        List<CollectionTags> newTags = getUpdatedTagsForCollection(collectionTagsList, tagsJson);
        if (newTags == null || newTags.isEmpty()) {
            return null;
        }

        return convertTagsFormat(newTags);
    }

    public static String convertTagsFormat(String tagsJson){
        if(tagsJson == null || tagsJson.isEmpty()) {
            return null;
        }

        List<CollectionTags> tagsList = new ArrayList<>();
        Gson gson = new Gson();
        Map<String, String> tagsMap = gson.fromJson(tagsJson, Map.class);
        for (Map.Entry<String, String> entry : tagsMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            CollectionTags collectionTag = new CollectionTags(Context.now(), key, value);
            tagsList.add(collectionTag);
        }
        return convertTagsFormat(tagsList);

    }

    public static String convertTagsFormat(List<CollectionTags> tagsList) {
        if(tagsList == null || tagsList.isEmpty()) {
            return null;
        }
        BasicDBObject tagsListObj = new BasicDBObject();
        tagsListObj.put("tagsList", tagsList);
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(tagsListObj);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }
}
