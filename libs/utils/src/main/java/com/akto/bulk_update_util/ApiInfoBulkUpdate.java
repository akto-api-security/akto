package com.akto.bulk_update_util;

import com.akto.dao.ApiInfoDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import org.bson.conversions.Bson;

import java.util.*;

public class ApiInfoBulkUpdate {

    public static List<WriteModel<ApiInfo>> getUpdatesForApiInfo(List<ApiInfo> apiInfoList) {

        List<WriteModel<ApiInfo>> updates = new ArrayList<>();
        for (ApiInfo apiInfo: apiInfoList) {

            List<Bson> subUpdates = new ArrayList<>();

            // allAuthTypesFound
            Set<Set<String>> allAuthTypesFound = apiInfo.getAllAuthTypesFound();
            if (allAuthTypesFound.isEmpty()) {
                // to make sure no field is null (so setting empty objects)
                subUpdates.add(Updates.setOnInsert(ApiInfo.ALL_AUTH_TYPES_FOUND, new HashSet<>()));
            } else {
                subUpdates.add(Updates.addEachToSet(ApiInfo.ALL_AUTH_TYPES_FOUND, Arrays.asList(allAuthTypesFound.toArray())));
            }

            // apiAccessType
            Set<ApiInfo.ApiAccessType> apiAccessTypes = apiInfo.getApiAccessTypes();
            if (apiAccessTypes.isEmpty()) {
                // to make sure no field is null (so setting empty objects)
                subUpdates.add(Updates.setOnInsert(ApiInfo.API_ACCESS_TYPES, new HashSet<>()));
            } else {
                subUpdates.add(Updates.addEachToSet(ApiInfo.API_ACCESS_TYPES, Arrays.asList(apiAccessTypes.toArray())));
            }

            // violations
            Map<String,Integer> violationsMap = apiInfo.getViolations();
            if (violationsMap == null || violationsMap.isEmpty()) {
                // to make sure no field is null (so setting empty objects)
                subUpdates.add(Updates.setOnInsert(ApiInfo.VIOLATIONS, new HashMap<>()));
            } else {
                for (String customKey: violationsMap.keySet()) {
                    subUpdates.add(Updates.set(ApiInfo.VIOLATIONS + "." + customKey, violationsMap.get(customKey)));
                }
            }

            // last seen
            subUpdates.add(Updates.set(ApiInfo.LAST_SEEN, apiInfo.getLastSeen()));

            // discoveredTimestamp (only set on insert, not update)
            if (apiInfo.getDiscoveredTimestamp() > 0) {
                subUpdates.add(Updates.setOnInsert(ApiInfo.DISCOVERED_TIMESTAMP, apiInfo.getDiscoveredTimestamp()));
            }

            // apiType
            if (apiInfo.getApiType() != null && !apiInfo.getApiType().isEmpty()) {
                subUpdates.add(Updates.setOnInsert(ApiInfo.API_TYPE, apiInfo.getApiType()));
            }

            // responseCodes
            if (apiInfo.getResponseCodes() != null && !apiInfo.getResponseCodes().isEmpty()) {
                subUpdates.add(Updates.addEachToSet(ApiInfo.RESPONSE_CODES, apiInfo.getResponseCodes()));
            }

            subUpdates.add(Updates.setOnInsert(SingleTypeInfo._COLLECTION_IDS, Arrays.asList(apiInfo.getId().getApiCollectionId())));

            List<String> parentMcpToolNames = apiInfo.getParentMcpToolNames();
            if (parentMcpToolNames == null || parentMcpToolNames.isEmpty()) {
                subUpdates.add(Updates.setOnInsert(ApiInfo.PARENT_MCP_TOOL_NAMES, new ArrayList<>()));
            } else {
                subUpdates.add(Updates.addEachToSet(ApiInfo.PARENT_MCP_TOOL_NAMES, parentMcpToolNames));
            }

            updates.add(
                    new UpdateOneModel<>(
                            ApiInfoDao.getFilter(apiInfo.getId()),
                            Updates.combine(subUpdates),
                            new UpdateOptions().upsert(true)
                    )
            );

        }

        return updates;
    }

}
