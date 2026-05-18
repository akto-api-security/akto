package com.akto.action;

import com.akto.dao.context.Context;
import com.akto.dao.nhi_governance.NhiViolationDao;
import com.akto.dao.nhi_governance.NhiPolicyDao;
import com.akto.dao.JiraIntegrationDao;
import com.akto.dto.nhi_governance.NhiViolation;
import com.akto.dto.nhi_governance.NhiPolicy;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.jira_integration.JiraIntegration;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.testing.ApiExecutor;
import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBList;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import lombok.Getter;
import lombok.Setter;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.Map;
import java.util.Base64;

public class NhiGovernanceViolationsAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(NhiGovernanceViolationsAction.class, LogDb.DASHBOARD);

    @Getter
    private List<NhiViolation> violations;

    @Getter
    @Setter
    private NhiViolation violation;

    @Setter
    private String contextSource;

    @Setter
    private List<String> violationIds;

    @Setter
    private String violationId;

    @Setter
    private String userEmail;

    @Setter
    private String projId;

    @Setter
    private String issueType;

    @Setter
    private String aktoDashboardHost;

    @Setter
    private Map<String, Object> jiraMetaData;

    @Getter
    private boolean success = false;

    @Getter
    private String errorMessage;

    public String fetchAllViolations() {
        try {
            Bson filter = (contextSource != null && !contextSource.isEmpty())
                    ? Filters.eq(NhiViolation.CONTEXT_SOURCE, contextSource)
                    : Filters.empty();

            violations = NhiViolationDao.instance.findAll(filter);

            // Collect all unique policyIds across violations
            Set<String> allPolicyIds = violations.stream()
                    .filter(v -> v.getPolicyIds() != null)
                    .flatMap(v -> v.getPolicyIds().stream())
                    .collect(Collectors.toSet());

            // Build hexId → policyName map from a single DB query
            Map<String, String> policyIdToName = new HashMap<>();
            if (!allPolicyIds.isEmpty()) {
                List<ObjectId> objectIds = allPolicyIds.stream()
                        .map(ObjectId::new)
                        .collect(Collectors.toList());
                List<NhiPolicy> policies = NhiPolicyDao.instance.findAll(
                        Filters.in(NhiPolicy.ID, objectIds)
                );
                for (NhiPolicy p : policies) {
                    if (p.getId() != null) {
                        policyIdToName.put(p.getId().toHexString(), p.getPolicyName());
                    }
                }
            }

            // Populate policy names in each violation from policyIds
            for (NhiViolation v : violations) {
                if (v.getPolicyIds() != null && !v.getPolicyIds().isEmpty()) {
                    List<String> names = v.getPolicyIds().stream()
                            .map(id -> policyIdToName.getOrDefault(id, id))
                            .collect(Collectors.toList());
                    v.setPolicy(names);
                }
            }

            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    public String fetchViolations() {
        try {
            int accountId = Context.accountId.get();

            // Handle single violation fetch
            if (violationId != null && !violationId.isEmpty()) {
                violation = NhiViolationDao.instance.findOne(NhiViolation.ID, new ObjectId(violationId));

                if (violation == null) {
                    loggerMaker.errorAndAddToDb("Violation not found: " + violationId);
                    addActionError("Violation not found");
                    return Action.ERROR.toUpperCase();
                }

                return Action.SUCCESS.toUpperCase();
            }

            // Handle multiple violations fetch
            if (violationIds != null && !violationIds.isEmpty()) {
                // Convert string IDs to ObjectIds using standard pattern
                List<ObjectId> objectIds = new ArrayList<>();
                for (String id : violationIds) {
                    objectIds.add(new ObjectId(id));
                }

                Bson filter = Filters.in(NhiViolation.ID, objectIds);
                violations = NhiViolationDao.instance.findAll(filter);
                return Action.SUCCESS.toUpperCase();
            }

            loggerMaker.errorAndAddToDb("No violation ID or IDs provided");
            addActionError("Violation ID or IDs is required");
            return Action.ERROR.toUpperCase();

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching violations: " + e.getMessage());
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    public String markViolationAsFixed() {
        try {
            long currentTime = Context.now();

            if (violationId == null || violationId.isEmpty()) {
                addActionError("Violation ID is required");
                success = false;
                return Action.ERROR.toUpperCase();
            }

            if (userEmail == null || userEmail.isEmpty()) {
                loggerMaker.errorAndAddToDb("User email not provided");
                addActionError("User email is required");
                success = false;
                return Action.ERROR.toUpperCase();
            }

            // Update violation status to Fixed with updatedAt and updatedBy
            Bson filter = Filters.eq(NhiViolation.ID, new ObjectId(violationId));
            Bson update = Updates.combine(
                Updates.set(NhiViolation.STATUS, "Fixed"),
                Updates.set(NhiViolation.UPDATED_AT, currentTime),
                Updates.set(NhiViolation.UPDATED_BY, userEmail)
            );

            NhiViolationDao.instance.updateOne(filter, update);

            success = true;
            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error marking violation as fixed: " + e.getMessage());
            addActionError(e.getMessage());
            success = false;
            return Action.ERROR.toUpperCase();
        }
    }

    public String createJiraTicketFromViolation() {
        try {
            if (violationId == null || violationId.isEmpty()) {
                loggerMaker.errorAndAddToDb("Violation ID not provided");
                addActionError("Violation ID is required");
                success = false;
                return Action.ERROR.toUpperCase();
            }

            if (projId == null || projId.isEmpty()) {
                loggerMaker.errorAndAddToDb("Project ID not provided");
                addActionError("Project ID is required");
                success = false;
                return Action.ERROR.toUpperCase();
            }

            if (issueType == null || issueType.isEmpty()) {
                loggerMaker.errorAndAddToDb("Issue type not provided");
                addActionError("Issue type is required");
                success = false;
                return Action.ERROR.toUpperCase();
            }

            // Fetch Jira integration from database
            JiraIntegration jiraIntegration = JiraIntegrationDao.instance.findOne(new BasicDBObject());
            if (jiraIntegration == null) {
                errorMessage = "Jira is not integrated";
                loggerMaker.errorAndAddToDb(errorMessage);
                addActionError(errorMessage);
                success = false;
                return Action.ERROR.toUpperCase();
            }

            // Fetch the violation
            NhiViolation violation = NhiViolationDao.instance.findOne(NhiViolation.ID, new ObjectId(violationId));
            if (violation == null) {
                errorMessage = "Violation not found";
                loggerMaker.errorAndAddToDb(errorMessage + ": " + violationId);
                addActionError(errorMessage);
                success = false;
                return Action.ERROR.toUpperCase();
            }

            // Build Jira fields object
            BasicDBObject fields = new BasicDBObject();
            fields.put("project", new BasicDBObject("key", projId));
            fields.put("issuetype", new BasicDBObject("name", issueType));
            fields.put("summary", violation.getViolationType());

            // Build description with violation details
            StringBuilder description = new StringBuilder();
            description.append(violation.getDescription()).append("\n\n");
            description.append("Severity: ").append(violation.getSeverity()).append("\n");
            description.append("Agent: ").append(violation.getAgentName()).append("\n");
            if (violation.getAffectedResources() != null && !violation.getAffectedResources().isEmpty()) {
                description.append("Affected Resources: ").append(String.join(", ", violation.getAffectedResources())).append("\n");
            }
            description.append("\nViolation Details: ").append(aktoDashboardHost).append("/dashboard/nhi/violations?id=").append(violationId);
            fields.put("description", description.toString());

            // Add custom fields from jiraMetaData if present
            if (jiraMetaData != null && jiraMetaData.containsKey("additionalIssueFields")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> additionalFields = (Map<String, Object>) jiraMetaData.get("additionalIssueFields");
                if (additionalFields != null) {
                    additionalFields.forEach(fields::put);
                }
            }

            // Add labels if present
            if (jiraMetaData != null && jiraMetaData.containsKey("labels")) {
                String labels = (String) jiraMetaData.get("labels");
                if (labels != null && !labels.isEmpty()) {
                    BasicDBObject labelsObj = new BasicDBObject();
                    fields.put("labels", labels.split(",\\s*"));
                }
            }

            // Build the request payload (single issue creation endpoint)
            BasicDBObject reqPayload = new BasicDBObject();
            reqPayload.put("fields", fields);

            // Build Jira API URL
            String url = jiraIntegration.getBaseUrl() + "/rest/api/3/issues";

            // Build authentication headers
            Map<String, List<String>> headers = new HashMap<>();
            JiraIntegration.JiraType jiraType = jiraIntegration.getJiraType();
            if (jiraType == null) {
                jiraType = JiraIntegration.JiraType.CLOUD;
            }

            if (jiraType == JiraIntegration.JiraType.DATA_CENTER) {
                // Data Center uses Bearer token
                headers.put("Authorization", Collections.singletonList("Bearer " + jiraIntegration.getApiToken()));
            } else {
                // Cloud uses Basic auth (email:token in base64)
                String auth = jiraIntegration.getUserEmail() + ":" + jiraIntegration.getApiToken();
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                headers.put("Authorization", Collections.singletonList("Basic " + encodedAuth));
            }
            headers.put("Content-Type", Collections.singletonList("application/json"));

            // Create and send request
            OriginalHttpRequest request = new OriginalHttpRequest(url, "", "POST", reqPayload.toString(), headers, "");

            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());

            if (response.getStatusCode() > 201) {
                errorMessage = "Error creating Jira ticket: " + response.getStatusCode() + " - " + response.getBody();
                loggerMaker.errorAndAddToDb(errorMessage);
                addActionError("Failed to create Jira ticket");
                success = false;
                return Action.ERROR.toUpperCase();
            }

            loggerMaker.infoAndAddToDb("Successfully created Jira ticket from violation: " + violationId);
            success = true;
            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            errorMessage = "Error creating Jira ticket from violation: " + e.getMessage();
            loggerMaker.errorAndAddToDb(errorMessage);
            addActionError(e.getMessage());
            success = false;
            return Action.ERROR.toUpperCase();
        }
    }
}
