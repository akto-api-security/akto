package com.akto.action.integration;

import com.akto.action.UserAction;
import com.akto.audit_logs_util.Audit;
import com.akto.dao.LinearIntegrationDao;
import com.akto.dao.LinearIssueMappingDao;
import com.akto.dao.LinearProjectDao;
import com.akto.dao.LinearTeamDao;
import com.akto.dao.context.Context;
import com.akto.dto.audit_logs.Operation;
import com.akto.dto.audit_logs.Resource;
import com.akto.log.LoggerMaker;
import com.opensymphony.xwork2.Action;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DisableLinearIntegrationAction extends UserAction {

    private static final LoggerMaker logger = new LoggerMaker(DisableLinearIntegrationAction.class, LoggerMaker.LogDb.DASHBOARD);

    private Map<String, String> response;

    @Audit(description = "User disabled Linear integration", resource = Resource.JIRA_INTEGRATION, operation = Operation.DELETE)
    @Override
    public String execute() {
        int userId = getSUser().getId();
        Context.accountId.set(userId);
        Context.userId.set(userId);

        try {
            List<com.akto.dto.linear_integration.LinearIntegration> integrations = LinearIntegrationDao.instance.findAll(null);

            if (integrations == null || integrations.isEmpty()) {
                addActionError("Linear integration not found");
                return Action.ERROR.toUpperCase();
            }

            // Delete all Linear-related data
            LinearIntegrationDao.instance.deleteAll(null);
            LinearTeamDao.instance.deleteAll(null);
            LinearProjectDao.instance.deleteAll(null);
            LinearIssueMappingDao.instance.deleteAll(null);

            response = new HashMap<>();
            response.put("status", "disabled");

            logger.infoAndAddToDb(String.format(
                "Linear integration disabled by user %d",
                userId
            ));

            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            logger.errorAndAddToDb("Error disabling Linear integration: " + e.getMessage());
            addActionError("Error disabling integration: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    public Map<String, String> getResponse() {
        return response;
    }

    public void setResponse(Map<String, String> response) {
        this.response = response;
    }
}
