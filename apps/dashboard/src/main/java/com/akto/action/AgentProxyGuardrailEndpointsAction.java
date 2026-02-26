package com.akto.action;

import com.akto.dao.ApiInfoDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.User;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;

import lombok.Getter;
import lombok.Setter;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;

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
    private int updatedCount;


    public String bulkUpdateAgentProxyGuardrail() {
        try {
            User user = getSUser();

            if (apiInfoIds == null || apiInfoIds.isEmpty()) {
                loggerMaker.errorAndAddToDb("No API Info IDs provided", LogDb.DASHBOARD);
                addActionError("No API Info IDs provided");
                return ERROR.toUpperCase();
            }

            List<Bson> filters = new ArrayList<>();

            // Parse all API Info IDs and build filters
            for (String apiInfoIdStr : apiInfoIds) {
                try {
                    // Parse API Info ID (format: "apiCollectionId url method")
                    String[] parts = apiInfoIdStr.split(" ", 3);
                    if (parts.length != 3) {
                        loggerMaker.errorAndAddToDb("Invalid API Info ID format: " + apiInfoIdStr, LogDb.DASHBOARD);
                        continue;
                    }

                    int collectionId = Integer.parseInt(parts[0]);
                    String url = parts[1];
                    String method = parts[2];

                    Bson filter = ApiInfoDao.getFilter(url, method, collectionId);
                    filters.add(filter);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb("Error parsing API Info ID " + apiInfoIdStr + ": " + e.getMessage(), LogDb.DASHBOARD);
                }
            }

            int updatedCount = 0;
            if (!filters.isEmpty()) {
                Bson queryFilter = Filters.or(filters);
                Bson update = Updates.set(ApiInfo.AGENT_PROXY_GUARDRAIL_ENABLED, enabled);
                
                UpdateResult result = ApiInfoDao.instance.getMCollection()
                    .updateMany(queryFilter, update);
                
                updatedCount = (int) result.getModifiedCount();
            }

            this.updatedCount = updatedCount;
            loggerMaker.info("Bulk updated " + updatedCount + " API Info records for agent proxy guardrails by user: " + user.getLogin());

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error bulk updating agent proxy guardrails: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Error bulk updating guardrails: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

}
