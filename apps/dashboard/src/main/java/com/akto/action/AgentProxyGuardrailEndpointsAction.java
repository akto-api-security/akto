package com.akto.action;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiCollection;
import com.akto.dto.User;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;

import lombok.Getter;
import lombok.Setter;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AgentProxyGuardrailEndpointsAction extends UserAction {
    private static final LoggerMaker loggerMaker = new LoggerMaker(AgentProxyGuardrailEndpointsAction.class, LogDb.DASHBOARD);

    @Getter
    @Setter
    private String apiInfoId; // Format: "apiCollectionId url method"

    @Getter
    @Setter
    private boolean enabled;

    @Getter
    @Setter
    private List<String> apiInfoIds; // For bulk operations

    @Getter
    @Setter
    private String host;

    @Getter
    @Setter
    private Integer apiCollectionId;

    @Getter
    private int updatedCount;

    @Getter
    private List<Map<String, Object>> endpoints; // For filtered list response

    public String updateAgentProxyGuardrail() {
        try {
            User user = getSUser();
            
            if (apiInfoId == null || apiInfoId.isEmpty()) {
                loggerMaker.errorAndAddToDb("API Info ID is required", LogDb.DASHBOARD);
                addActionError("API Info ID is required");
                return ERROR.toUpperCase();
            }

            // Parse API Info ID (format: "apiCollectionId url method")
            String[] parts = apiInfoId.split(" ", 3);
            if (parts.length != 3) {
                loggerMaker.errorAndAddToDb("Invalid API Info ID format: " + apiInfoId, LogDb.DASHBOARD);
                addActionError("Invalid API Info ID format");
                return ERROR.toUpperCase();
            }

            int collectionId = Integer.parseInt(parts[0]);
            String url = parts[1];
            String method = parts[2];

            // Find ApiInfo record
            Bson filter = ApiInfoDao.getFilter(url, method, collectionId);
            ApiInfo apiInfo = ApiInfoDao.instance.findOne(filter);

            if (apiInfo == null) {
                loggerMaker.errorAndAddToDb("API Info not found: " + apiInfoId, LogDb.DASHBOARD);
                addActionError("API Info not found");
                return ERROR.toUpperCase();
            }

            // Update guardrail fields
            List<Bson> updates = new ArrayList<>();
            updates.add(Updates.set(ApiInfo.AGENT_PROXY_GUARDRAIL_ENABLED, enabled));
            updates.add(Updates.set(ApiInfo.LAST_SEEN, Context.now()));

            ApiInfoDao.instance.getMCollection().updateOne(filter, Updates.combine(updates));

            loggerMaker.info("Updated agent proxy guardrail for API Info: " + apiInfoId + " by user: " + user.getLogin());

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error updating agent proxy guardrail: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Error updating guardrail: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    public String bulkUpdateAgentProxyGuardrail() {
        try {
            User user = getSUser();

            if (apiInfoIds == null || apiInfoIds.isEmpty()) {
                loggerMaker.errorAndAddToDb("No API Info IDs provided", LogDb.DASHBOARD);
                addActionError("No API Info IDs provided");
                return ERROR.toUpperCase();
            }

            int count = 0;
            int currentTime = Context.now();

            for (String apiInfoIdStr : apiInfoIds) {
                try {
                    // Parse API Info ID
                    String[] parts = apiInfoIdStr.split(" ", 3);
                    if (parts.length != 3) {
                        loggerMaker.errorAndAddToDb("Invalid API Info ID format: " + apiInfoIdStr, LogDb.DASHBOARD);
                        continue;
                    }

                    int collectionId = Integer.parseInt(parts[0]);
                    String url = parts[1];
                    String method = parts[2];

                    Bson filter = ApiInfoDao.getFilter(url, method, collectionId);
                    List<Bson> updates = new ArrayList<>();
                    updates.add(Updates.set(ApiInfo.AGENT_PROXY_GUARDRAIL_ENABLED, enabled));
                    updates.add(Updates.set(ApiInfo.LAST_SEEN, currentTime));

                    ApiInfoDao.instance.getMCollection().updateOne(filter, Updates.combine(updates));
                    count++;
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb("Error updating API Info " + apiInfoIdStr + ": " + e.getMessage(), LogDb.DASHBOARD);
                }
            }

            updatedCount = count;
            loggerMaker.info("Bulk updated " + count + " API Info records for agent proxy guardrails by user: " + user.getLogin());

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error bulk updating agent proxy guardrails: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Error bulk updating guardrails: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    public String fetchAgentProxyGuardrailEndpoints() {
        try {
            List<Bson> filters = new ArrayList<>();
            filters.add(Filters.eq(ApiInfo.AGENT_PROXY_GUARDRAIL_ENABLED, true));

            // Filter by host
            if (host != null && !host.isEmpty()) {
                List<ApiCollection> collections = ApiCollectionsDao.instance.findAll(
                    Filters.eq(ApiCollection.HOST_NAME, host),
                    Projections.include(ApiCollection.ID)
                );
                if (collections.isEmpty()) {
                    endpoints = new ArrayList<>();
                    return SUCCESS.toUpperCase();
                }
                List<Integer> collectionIds = new ArrayList<>();
                for (ApiCollection col : collections) {
                    collectionIds.add(col.getId());
                }
                filters.add(Filters.in(ApiInfo.ID_API_COLLECTION_ID, collectionIds));
            }

            // Note: appType filtering removed - filtering is done by host only

            // Filter by apiCollectionId
            if (apiCollectionId != null) {
                filters.add(Filters.eq(ApiInfo.ID_API_COLLECTION_ID, apiCollectionId));
            }

            Bson query = Filters.and(filters);
            List<ApiInfo> apiInfoList = ApiInfoDao.instance.findAll(query);

            // Transform to response format
            endpoints = new ArrayList<>();
            Map<Integer, String> collectionIdToHostName = new HashMap<>();
            
            // Batch fetch host names
            for (ApiInfo apiInfo : apiInfoList) {
                if (apiInfo.getId() != null) {
                    int colId = apiInfo.getId().getApiCollectionId();
                    if (!collectionIdToHostName.containsKey(colId)) {
                        ApiCollection collection = ApiCollectionsDao.instance.findOne(
                            Filters.eq(Constants.ID, colId),
                            Projections.include(ApiCollection.HOST_NAME)
                        );
                        if (collection != null) {
                            collectionIdToHostName.put(colId, collection.getHostName());
                        }
                    }
                }
            }

            for (ApiInfo apiInfo : apiInfoList) {
                if (apiInfo.getId() == null) continue;

                Map<String, Object> endpoint = new HashMap<>();
                endpoint.put("id", apiInfo.getId().getApiCollectionId() + " " + 
                    apiInfo.getId().getUrl() + " " + apiInfo.getId().getMethod().name());
                endpoint.put("method", apiInfo.getId().getMethod().name());
                endpoint.put("url", apiInfo.getId().getUrl());
                endpoint.put("host", collectionIdToHostName.get(apiInfo.getId().getApiCollectionId()));
                endpoint.put("updatedAt", apiInfo.getLastSeen() * 1000L);
                endpoint.put("isDeleted", false); // ApiInfo doesn't have isDeleted field
                endpoints.add(endpoint);
            }

            loggerMaker.info("Fetched " + endpoints.size() + " agent proxy guardrail endpoints");
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching agent proxy guardrail endpoints: " + e.getMessage(), LogDb.DASHBOARD);
            return ERROR.toUpperCase();
        }
    }
}
