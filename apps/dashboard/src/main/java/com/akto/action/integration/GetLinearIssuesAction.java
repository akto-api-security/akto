package com.akto.action.integration;

import com.akto.action.UserAction;
import com.akto.dao.LinearIssueMappingDao;
import com.akto.dao.context.Context;
import com.akto.dto.linear_integration.LinearIssueMapping;
import com.akto.log.LoggerMaker;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.Action;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GetLinearIssuesAction extends UserAction {

    private static final LoggerMaker logger = new LoggerMaker(GetLinearIssuesAction.class, LoggerMaker.LogDb.DASHBOARD);

    private String status;
    private String severity;
    private Integer apiId;
    private Integer page = 1;
    private static final int PAGE_SIZE = 50;

    private Map<String, Object> response;

    @Override
    public String execute() {
        int userId = getSUser().getId();
        Context.accountId.set(userId);

        try {
            // Build filter based on query parameters
            Bson filter = buildFilter();

            // Query all issues (pagination handled client-side for simplicity)
            List<LinearIssueMapping> allIssues = LinearIssueMappingDao.instance.findAll(filter);
            List<LinearIssueMapping> issues = allIssues;

            // Count total matches
            long total = LinearIssueMappingDao.instance.count(filter);

            // Prepare response
            List<Map<String, Object>> issuesList = new ArrayList<>();
            for (LinearIssueMapping mapping : issues) {
                Map<String, Object> issueMap = new HashMap<>();
                issueMap.put("mappingId", mapping.getId() != null ? mapping.getId().toString() : "");
                issueMap.put("findingId", mapping.getFindingId());
                issueMap.put("findingType", mapping.getFindingType());
                issueMap.put("linearIssueId", mapping.getLinearIssueId());
                issueMap.put("linearIssueKey", mapping.getLinearIssueKey());
                issueMap.put("linearIssueUrl", mapping.getLinearIssueUrl());
                issueMap.put("apiId", mapping.getApiId());
                issueMap.put("apiName", mapping.getApiName());
                issueMap.put("severity", mapping.getSeverity());
                issueMap.put("createdTs", mapping.getCreatedTs());
                issuesList.add(issueMap);
            }

            response = new HashMap<>();
            response.put("issues", issuesList);
            response.put("total", total);
            response.put("page", page);
            response.put("pageSize", PAGE_SIZE);

            logger.infoAndAddToDb(String.format(
                "Retrieved %d Linear issues (page %d)",
                issues.size(), page
            ));

            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            logger.errorAndAddToDb("Error retrieving Linear issues: " + e.getMessage());
            addActionError("Error retrieving issues: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    /**
     * Build MongoDB filter based on query parameters
     */
    private Bson buildFilter() {
        List<Bson> filters = new ArrayList<>();

        // Account ID filter is automatic (AccountsContextDao)

        // Severity filter
        if (severity != null && !severity.trim().isEmpty()) {
            filters.add(Filters.eq(LinearIssueMapping.SEVERITY, severity));
        }

        // API ID filter
        if (apiId != null && apiId > 0) {
            filters.add(Filters.eq(LinearIssueMapping.API_ID, apiId));
        }

        // Finding type filter (status in this context)
        if (status != null && !status.trim().isEmpty()) {
            filters.add(Filters.eq(LinearIssueMapping.FINDING_TYPE, status));
        }

        if (filters.isEmpty()) {
            return null;  // No additional filter, AccountsContextDao handles account scoping
        }

        if (filters.size() == 1) {
            return filters.get(0);
        }

        return Filters.and(filters);
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public Integer getApiId() {
        return apiId;
    }

    public void setApiId(Integer apiId) {
        this.apiId = apiId;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page != null ? page : 1;
    }

    public Map<String, Object> getResponse() {
        return response;
    }

    public void setResponse(Map<String, Object> response) {
        this.response = response;
    }
}
