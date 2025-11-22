package com.akto.action;

import com.akto.dao.GuardrailPoliciesDao;
import com.akto.dao.context.Context;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.User;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;

import lombok.Getter;
import lombok.Setter;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GuardrailPoliciesAction extends UserAction {
    private static final LoggerMaker loggerMaker = new LoggerMaker(GuardrailPoliciesAction.class, LogDb.DASHBOARD);


    @Getter
    @Setter
    GuardrailPolicies policy;
    // For updating existing policies
    @Setter
    private String hexId;

    @Getter
    private List<GuardrailPolicies> guardrailPolicies;

    @Getter
    private long total;

    @Setter
    private List<String> policyIds;


    public String fetchGuardrailPolicies() {
        try {
            this.guardrailPolicies  = GuardrailPoliciesDao.instance.findAllSortedByCreatedTimestamp(0, 20);
            this.total = GuardrailPoliciesDao.instance.getTotalCount();
            
            loggerMaker.info("Fetched " + guardrailPolicies.size() + " guardrail policies out of " + total + " total");

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching guardrail policies: " + e.getMessage(), LogDb.DASHBOARD);
            return ERROR.toUpperCase();
        }
    }

    public String createGuardrailPolicy() {
        try {
            User user = getSUser();
            int currentTime = Context.now();
            
            loggerMaker.info("createGuardrailPolicy called with hexId: " + hexId);
            loggerMaker.info("Policy object received: " + (policy != null ? policy.getName() : "null"));

            // Ensure policy object has required timestamps and user info
            if (hexId != null && !hexId.isEmpty()) {
                // Update existing - keep original createdTimestamp, update updatedTimestamp and updatedBy
                if (policy.getUpdatedTimestamp() == 0) {
                    policy.setUpdatedTimestamp(currentTime);
                }
                policy.setUpdatedBy(user.getLogin()); // Always update who modified it
            } else {
                // Create new - only set creation fields, not update fields
                if (policy.getCreatedTimestamp() == 0) {
                    policy.setCreatedTimestamp(currentTime);
                }
                if (policy.getCreatedBy() == null || policy.getCreatedBy().isEmpty()) {
                    policy.setCreatedBy(user.getLogin());
                }
                // Don't set updatedTimestamp and updatedBy for new records
            }

            // Use upsert operation
            Bson filter = (hexId != null && !hexId.isEmpty()) 
                ? Filters.eq(Constants.ID, new ObjectId(hexId))
                : Filters.eq("name", policy.getName()); // or use another unique identifier
            
            List<Bson> updates = new ArrayList<>();
            updates.add(Updates.set("name", policy.getName()));
            updates.add(Updates.set("description", policy.getDescription()));
            updates.add(Updates.set("blockedMessage", policy.getBlockedMessage()));
            updates.add(Updates.set("severity", policy.getSeverity()));
            updates.add(Updates.set("selectedCollection", policy.getSelectedCollection()));
            updates.add(Updates.set("selectedModel", policy.getSelectedModel()));
            updates.add(Updates.set("deniedTopics", policy.getDeniedTopics()));
            updates.add(Updates.set("piiTypes", policy.getPiiTypes()));
            updates.add(Updates.set("regexPatterns", policy.getRegexPatterns()));
            updates.add(Updates.set("regexPatternsV2", policy.getRegexPatternsV2()));
            updates.add(Updates.set("contentFiltering", policy.getContentFiltering()));
            updates.add(Updates.set("llmRule", policy.getLlmRule()));
            updates.add(Updates.set("selectedMcpServers", policy.getSelectedMcpServers()));
            updates.add(Updates.set("selectedAgentServers", policy.getSelectedAgentServers()));
            updates.add(Updates.set("selectedMcpServersV2", policy.getSelectedMcpServersV2()));
            updates.add(Updates.set("selectedAgentServersV2", policy.getSelectedAgentServersV2()));
            updates.add(Updates.set("applyOnResponse", policy.isApplyOnResponse()));
            updates.add(Updates.set("applyOnRequest", policy.isApplyOnRequest()));
            updates.add(Updates.set("url", policy.getUrl()));
            updates.add(Updates.set("confidenceScore", policy.getConfidenceScore()));
            updates.add(Updates.set("active", policy.isActive()));
            
            // Only set createdBy and createdTimestamp on insert
            updates.add(Updates.setOnInsert("createdBy", user.getLogin()));
            updates.add(Updates.setOnInsert("createdTimestamp", currentTime));
            updates.add(Updates.set("updatedTimestamp", currentTime));

            // Only set updatedTimestamp and updatedBy on actual updates (when hexId exists)
            if (hexId != null && !hexId.isEmpty()) {
                updates.add(Updates.set("updatedBy", user.getLogin()));
            }

            // Perform upsert using updateOne with upsert option
            GuardrailPoliciesDao.instance.getMCollection().updateOne(
                filter,
                Updates.combine(updates),
                new UpdateOptions().upsert(true)
            );
            
            String action = (hexId != null && !hexId.isEmpty()) ? "Updated" : "Created";
            loggerMaker.info(action + " guardrail policy: " + policy.getName() + " by user: " + user.getLogin());

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error creating guardrail policy: " + e.getMessage(), LogDb.DASHBOARD);
            return ERROR.toUpperCase();
        }
    }

    public String deleteGuardrailPolicies() {
        try {
            if (policyIds == null || policyIds.isEmpty()) {
                loggerMaker.errorAndAddToDb("No policy IDs provided for deletion", LogDb.DASHBOARD);
                return ERROR.toUpperCase();
            }

            User user = getSUser();
            List<ObjectId> objectIds = new ArrayList<>();
            for (String id : policyIds) {
                objectIds.add(new ObjectId(id));
            }

            Bson filter = Filters.in(GuardrailPoliciesDao.ID, objectIds);
            GuardrailPoliciesDao.instance.getMCollection().deleteMany(filter);

            loggerMaker.info("Deleted " + policyIds.size() + " guardrail policies by user: " + user.getLogin());

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error deleting guardrail policies: " + e.getMessage(), LogDb.DASHBOARD);
            return ERROR.toUpperCase();
        }
    }

}