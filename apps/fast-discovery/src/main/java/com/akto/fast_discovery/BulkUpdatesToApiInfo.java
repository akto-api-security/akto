package com.akto.fast_discovery;

import com.akto.dto.ApiInfo;
import com.akto.dto.bulk_updates.BulkUpdates;
import com.akto.dto.type.URLMethods;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Map;

/**
 * Helper class to convert BulkUpdates to ApiInfo objects.
 */
public class BulkUpdatesToApiInfo {

    /**
     * Convert a single BulkUpdates object to ApiInfo.
     * Only sets fields that are provided in the BulkUpdates.
     */
    public static ApiInfo convert(BulkUpdates write) {
        ApiInfo apiInfo = new ApiInfo();
        Map<String, Object> filters = write.getFilters();

        // Extract _id (ApiInfoKey)
        if (filters.containsKey("_id")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> idMap = (Map<String, Object>) filters.get("_id");
            int apiCollectionId = ((Number) idMap.get("apiCollectionId")).intValue();
            String url = (String) idMap.get("url");
            String method = (String) idMap.get("method");
            URLMethods.Method methodEnum = URLMethods.Method.fromString(method);

            ApiInfo.ApiInfoKey key = new ApiInfo.ApiInfoKey(apiCollectionId, url, methodEnum);
            apiInfo.setId(key);
        }

        // Apply updates to ApiInfo object
        for (String updateStr : write.getUpdates()) {
            try {
                // Parse update JSON: {"field": "lastSeen", "val": 123456, "op": "set"}
                JsonObject update = JsonParser.parseString(updateStr).getAsJsonObject();
                String field = update.get("field").getAsString();
                JsonElement valElement = update.get("val");

                // Set fields on ApiInfo object based on field name
                if ("lastSeen".equals(field)) {
                    apiInfo.setLastSeen(valElement.getAsInt());
                }
                // Ignore other fields like discoveredTimestamp, collectionIds etc. as they will be handled by upsert logic
            } catch (Exception e) {
                // Skip invalid updates
            }
        }

        return apiInfo;
    }
}
