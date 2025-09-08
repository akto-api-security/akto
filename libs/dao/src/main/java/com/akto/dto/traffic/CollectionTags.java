package com.akto.dto.traffic;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.akto.dao.context.Context;
import com.google.gson.Gson;
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


    public enum TagSource {
        KUBERNETES, 
        USER
    }

    TagSource source;
    public static final String SOURCE = "source";

    @Override
    public int hashCode() {
        return Objects.hash(keyName, source);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        CollectionTags that = (CollectionTags) obj;
        return Objects.equals(keyName, that.keyName) &&
               source == that.source;
    }


    public static List<CollectionTags> calculateTagsDiff(List<CollectionTags> collectionTagsList, String tagsJson) {
        if (tagsJson == null || tagsJson.isEmpty()) {
            return null;
        }

        Gson gson = new Gson();
        Map<String, String> tagsMap = gson.fromJson(tagsJson, Map.class);
        Map<String, String> dbtagsMap = new HashMap<>();
        boolean shouldUpdate = false;

        for(CollectionTags collectionTag : collectionTagsList) {
            // Compare only K8 tags source.
            if (!collectionTag.getSource().equals(CollectionTags.TagSource.KUBERNETES)) {
                continue;
            }
            String key = collectionTag.getKeyName();
            dbtagsMap.put(key, collectionTag.getValue());
            // Tag was deleted
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
            newTags.add(new CollectionTags(Context.now(), key, entry.getValue(), TagSource.KUBERNETES));
        }

        return shouldUpdate ? newTags : null;
    }

    public static List<CollectionTags>convertTagsFormat(String tagsJson){
        if(tagsJson == null || tagsJson.isEmpty()) {
            return null;
        }

        List<CollectionTags> tagsList = new ArrayList<>();
        Gson gson = new Gson();
        Map<String, String> tagsMap = gson.fromJson(tagsJson, Map.class);
        for (Map.Entry<String, String> entry : tagsMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            CollectionTags collectionTag = new CollectionTags(Context.now(), key, value, TagSource.KUBERNETES);
            tagsList.add(collectionTag);
        }
        return tagsList;

    }

}