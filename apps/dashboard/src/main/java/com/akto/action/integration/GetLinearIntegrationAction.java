package com.akto.action.integration;

import com.akto.action.UserAction;
import com.akto.dao.LinearIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dto.linear_integration.LinearIntegration;
import com.akto.log.LoggerMaker;
import com.opensymphony.xwork2.Action;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GetLinearIntegrationAction extends UserAction {

    private static final LoggerMaker logger = new LoggerMaker(GetLinearIntegrationAction.class, LoggerMaker.LogDb.DASHBOARD);

    private LinearIntegration linearIntegration;
    private boolean isConfigured;

    @Override
    public String execute() {
        int userId = getSUser().getId();
        Context.accountId.set(userId);

        try {
            List<LinearIntegration> integrations = LinearIntegrationDao.instance.findAll(null);

            if (integrations == null || integrations.isEmpty()) {
                isConfigured = false;
                logger.infoAndAddToDb("Linear integration not configured");
                return Action.SUCCESS.toUpperCase();
            }

            LinearIntegration integration = integrations.get(0);

            // Create masked copy for response (hide API key)
            linearIntegration = new LinearIntegration(
                integration.getAccountId(),
                integration.getWorkspaceUrl(),
                "****",  // Mask API key
                integration.getDefaultProjectId(),
                integration.getDefaultTeamId(),
                integration.getSeverityToPriorityMap(),
                integration.getIssueTemplate(),
                integration.getCreatedTs(),
                integration.getUpdatedTs()
            );

            isConfigured = true;

            logger.infoAndAddToDb("Linear integration retrieved");

            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            logger.errorAndAddToDb("Error retrieving Linear integration: " + e.getMessage());
            addActionError("Error retrieving configuration: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    public LinearIntegration getLinearIntegration() {
        return linearIntegration;
    }

    public void setLinearIntegration(LinearIntegration linearIntegration) {
        this.linearIntegration = linearIntegration;
    }

    public boolean isConfigured() {
        return isConfigured;
    }

    public void setConfigured(boolean configured) {
        isConfigured = configured;
    }
}
