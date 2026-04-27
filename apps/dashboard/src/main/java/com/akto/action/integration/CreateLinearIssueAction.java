package com.akto.action.integration;

import com.akto.action.UserAction;
import com.akto.dao.LinearIntegrationDao;
import com.akto.dao.LinearIssueMappingDao;
import com.akto.dao.context.Context;
import com.akto.dto.linear_integration.LinearIntegration;
import com.akto.dto.linear_integration.LinearIssueMapping;
import com.akto.dto.linear_integration.IssueTemplate;
import com.akto.log.LoggerMaker;
import com.akto.utils.linear.LinearApiClient;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.Action;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CreateLinearIssueAction extends UserAction {

    private static final LoggerMaker logger = new LoggerMaker(CreateLinearIssueAction.class, LoggerMaker.LogDb.DASHBOARD);

    private String findingType;
    private String findingId;
    private Integer apiId;
    private String apiName;
    private String apiMethod;
    private String apiPath;
    private String severityLevel;
    private String description;
    private String requestPayload;
    private String responsePayload;

    private Map<String, Object> createResult;

    @Override
    public String execute() {
        int userId = getSUser().getId();
        Context.accountId.set(userId);
        Context.userId.set(userId);

        try {
            // Validation
            if (findingId == null || findingId.trim().isEmpty()) {
                addActionError("Finding ID is required");
                return Action.ERROR.toUpperCase();
            }

            if (description == null || description.trim().isEmpty()) {
                addActionError("Description is required");
                return Action.ERROR.toUpperCase();
            }

            // Check if integration is configured
            List<LinearIntegration> integrations = LinearIntegrationDao.instance.findAll(null);

            if (integrations == null || integrations.isEmpty()) {
                addActionError("Linear integration not configured");
                return Action.ERROR.toUpperCase();
            }

            LinearIntegration integration = integrations.get(0);

            // Check for duplicate (finding already has an issue)
            List<LinearIssueMapping> existing = LinearIssueMappingDao.instance.findAll(
                Filters.eq(LinearIssueMapping.FINDING_ID, findingId)
            );

            if (existing != null && !existing.isEmpty()) {
                LinearIssueMapping mapping = existing.get(0);
                addActionError("Issue already created for this finding");
                createResult = new HashMap<>();
                createResult.put("success", false);
                createResult.put("error", "duplicate");
                createResult.put("linearIssueKey", mapping.getLinearIssueKey());
                createResult.put("linearIssueUrl", mapping.getLinearIssueUrl());
                return "DUPLICATE"; // Return DUPLICATE status instead of ERROR
            }

            // Apply template variables
            String issueTitle = description;
            String issueDescription = description;

            if (integration.getIssueTemplate() != null) {
                IssueTemplate template = integration.getIssueTemplate();
                issueTitle = applyTemplateVariables(
                    template.getTitle() != null ? template.getTitle() : "[{apiName}] {findingType}",
                    apiName, apiMethod, apiPath, findingType, description, severityLevel
                );
                issueDescription = applyTemplateVariables(
                    template.getDescription() != null ? template.getDescription() : description,
                    apiName, apiMethod, apiPath, findingType, description, severityLevel
                );
            }

            // Get priority from severity mapping
            String priority = "medium";
            if (integration.getSeverityToPriorityMap() != null) {
                String mappedPriority = integration.getSeverityToPriorityMap().get(severityLevel);
                if (mappedPriority != null) {
                    priority = mappedPriority;
                }
            }

            // Create issue via Linear API
            LinearApiClient apiClient = new LinearApiClient();
            String decryptedToken = integration.getApiKey();

            List<String> labels = new ArrayList<>();
            if (integration.getIssueTemplate() != null && integration.getIssueTemplate().getLabels() != null) {
                labels = integration.getIssueTemplate().getLabels();
            }

            Map<String, String> issueResponse = apiClient.createIssue(
                decryptedToken,
                integration.getDefaultTeamId(),
                integration.getDefaultProjectId(),
                issueTitle,
                issueDescription,
                priority,
                labels
            );

            // Store mapping
            long currentTs = System.currentTimeMillis() / 1000;
            LinearIssueMapping mapping = new LinearIssueMapping(
                userId,
                findingId,
                findingType,
                issueResponse.get("linearIssueId"),
                issueResponse.get("linearIssueKey"),
                issueResponse.get("linearIssueUrl"),
                apiId != null ? apiId : 0,
                apiName != null ? apiName : "",
                severityLevel != null ? severityLevel : "MEDIUM",
                (int) currentTs
            );

            LinearIssueMappingDao.instance.insertOne(mapping);

            // Prepare response
            createResult = new HashMap<>();
            createResult.put("success", true);
            createResult.put("linearIssueId", issueResponse.get("linearIssueId"));
            createResult.put("linearIssueKey", issueResponse.get("linearIssueKey"));
            createResult.put("linearIssueUrl", issueResponse.get("linearIssueUrl"));

            logger.infoAndAddToDb(String.format(
                "Linear issue created for finding %s: %s",
                findingId, issueResponse.get("linearIssueKey")
            ));

            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            logger.errorAndAddToDb("Error creating Linear issue: " + e.getMessage());
            addActionError("Error creating issue: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    /**
     * Apply template variable substitution
     */
    private String applyTemplateVariables(String template, String apiName, String apiMethod,
                                          String apiPath, String findingType, String description,
                                          String severity) {
        if (template == null) return "";

        String result = template;
        result = result.replace("{apiName}", apiName != null ? apiName : "");
        result = result.replace("{apiMethod}", apiMethod != null ? apiMethod : "");
        result = result.replace("{apiPath}", apiPath != null ? apiPath : "");
        result = result.replace("{findingType}", findingType != null ? findingType : "");
        result = result.replace("{description}", description != null ? description : "");
        result = result.replace("{severity}", severity != null ? severity : "");
        result = result.replace("{timestamp}", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(new Date()));

        return result;
    }

    public String getFindingType() {
        return findingType;
    }

    public void setFindingType(String findingType) {
        this.findingType = findingType;
    }

    public String getFindingId() {
        return findingId;
    }

    public void setFindingId(String findingId) {
        this.findingId = findingId;
    }

    public Integer getApiId() {
        return apiId;
    }

    public void setApiId(Integer apiId) {
        this.apiId = apiId;
    }

    public String getApiName() {
        return apiName;
    }

    public void setApiName(String apiName) {
        this.apiName = apiName;
    }

    public String getSeverityLevel() {
        return severityLevel;
    }

    public void setSeverityLevel(String severityLevel) {
        this.severityLevel = severityLevel;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRequestPayload() {
        return requestPayload;
    }

    public void setRequestPayload(String requestPayload) {
        this.requestPayload = requestPayload;
    }

    public String getResponsePayload() {
        return responsePayload;
    }

    public void setResponsePayload(String responsePayload) {
        this.responsePayload = responsePayload;
    }

    public Map<String, Object> getCreateResult() {
        return createResult;
    }

    public void setCreateResult(Map<String, Object> createResult) {
        this.createResult = createResult;
    }
}
