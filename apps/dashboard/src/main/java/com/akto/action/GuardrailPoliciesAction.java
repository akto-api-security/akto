package com.akto.action;

import com.akto.dao.GuardrailPoliciesDao;
import com.akto.dao.context.Context;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.User;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.mongodb.client.model.Filters;
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
    private List<GuardrailPolicies> guardrailPolicies;
    
    @Getter
    private long total;
    
    // Pagination parameters
    @Setter
    private int limit;
    @Setter
    private int skip;
    
    // For creating/updating guardrails
    @Setter
    private String name;
    @Setter
    private String description;
    @Setter
    private String blockedMessage;
    @Setter
    private String severity;
    @Setter
    private String selectedCollection;
    @Setter
    private String selectedModel;
    @Setter
    private List<GuardrailPolicies.DeniedTopic> deniedTopics;
    @Setter
    private List<GuardrailPolicies.PiiType> piiTypes;
    @Setter
    private List<String> regexPatterns;
    @Setter
    private Map<String, Object> contentFiltering;
    @Setter
    private boolean active;
    @Setter
    private List<String> selectedMcpServers;
    @Setter
    private List<String> selectedAgentServers;
    @Setter
    private boolean applyOnResponse;
    @Setter
    private boolean applyOnRequest;
    
    // For updating existing policies
    @Setter
    private String hexId;

    public String fetchGuardrailPolicies() {
        try {
            if (limit <= 0) {
                limit = 20;
            }
            if (skip < 0) {
                skip = 0;
            }
            
            this.guardrailPolicies = GuardrailPoliciesDao.instance.findAllSortedByCreatedTimestamp(skip, limit);
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
            
            GuardrailPolicies policy = new GuardrailPolicies(
                name,
                description,
                blockedMessage,
                severity,
                currentTime, // createdTimestamp
                currentTime, // updatedTimestamp
                user.getLogin(), // createdBy
                selectedCollection,
                selectedModel,
                deniedTopics,
                piiTypes,
                regexPatterns,
                contentFiltering,
                selectedMcpServers,
                selectedAgentServers,
                applyOnResponse,
                applyOnRequest,
                active
            );
            
            GuardrailPoliciesDao.instance.insertOne(policy);
            
            loggerMaker.info("Created new guardrail policy: " + name + " by user: " + user.getLogin());
            
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error creating guardrail policy: " + e.getMessage(), LogDb.DASHBOARD);
            return ERROR.toUpperCase();
        }
    }

    public String updateGuardrailPolicy() {
        try {
            User user = getSUser();
            int currentTime = Context.now();
            ObjectId id = new ObjectId(hexId);
            
            List<Bson> updates = new ArrayList<>();
            updates.add(Updates.set("name", name));
            updates.add(Updates.set("description", description));
            updates.add(Updates.set("blockedMessage", blockedMessage));
            updates.add(Updates.set("severity", severity));
            updates.add(Updates.set("selectedCollection", selectedCollection));
            updates.add(Updates.set("selectedModel", selectedModel));
            updates.add(Updates.set("deniedTopics", deniedTopics));
            updates.add(Updates.set("piiTypes", piiTypes));
            updates.add(Updates.set("regexPatterns", regexPatterns));
            updates.add(Updates.set("contentFiltering", contentFiltering));
            updates.add(Updates.set("selectedMcpServers", selectedMcpServers));
            updates.add(Updates.set("selectedAgentServers", selectedAgentServers));
            updates.add(Updates.set("applyOnResponse", applyOnResponse));
            updates.add(Updates.set("applyOnRequest", applyOnRequest));
            updates.add(Updates.set("active", active));
            updates.add(Updates.set("updatedTimestamp", currentTime));
            
            GuardrailPoliciesDao.instance.updateOne(
                Filters.eq(Constants.ID, id), 
                Updates.combine(updates)
            );
            
            loggerMaker.info("Updated guardrail policy: " + name + " by user: " + user.getLogin());
            
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error updating guardrail policy: " + e.getMessage(), LogDb.DASHBOARD);
            return ERROR.toUpperCase();
        }
    }

    @Override
    public String execute() throws Exception {
        return "";
    }
}