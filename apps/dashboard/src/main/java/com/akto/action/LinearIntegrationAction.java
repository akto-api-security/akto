package com.akto.action;

import com.akto.audit_logs_util.Audit;
import com.akto.dto.audit_logs.Operation;
import com.akto.dto.audit_logs.Resource;
import com.akto.dto.linear_integration.LinearIntegration;
import com.akto.dto.linear_integration.LinearTeamMapping;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.linear.LinearIntegrationService;
import com.akto.log.LoggerMaker;
import com.akto.ticketing.ATicketIntegrationService.TicketCreationResult;
import com.opensymphony.xwork2.Action;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LinearIntegrationAction extends UserAction {

    private static final LoggerMaker logger = new LoggerMaker(LinearIntegrationAction.class,
        LoggerMaker.LogDb.DASHBOARD);

    private String apiToken;
    private Map<String, LinearTeamMapping> teamMappings;
    private String teamName;
    private String teamKey;
    private LinearIntegration linearIntegration;
    private Map<String, String> teamsMap;
    private List<TestingIssuesId> testingIssuesIdList;
    private String teamId;
    private String errorMessage;
    private String aktoDashboardHost;

    @Audit(description = "User saved Linear API key", resource = Resource.LINEAR_INTEGRATION, operation = Operation.CREATE)
    public String addLinearIntegration() {
        try {
            linearIntegration = new LinearIntegrationService(apiToken).saveApiKey();
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb("Error adding Linear integration: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    @Audit(description = "User saved Linear teams", resource = Resource.LINEAR_INTEGRATION, operation = Operation.UPDATE)
    public String saveLinearTeams() {
        try {
            linearIntegration = new LinearIntegrationService().saveTeams(teamMappings);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb("Error saving Linear teams: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    public String fetchLinearIntegration() {
        try {
            linearIntegration = new LinearIntegrationService().fetchLinearIntegration();
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb("Error fetching Linear integration: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    public String fetchLinearTeams() {
        try {
            teamsMap = new LinearIntegrationService(apiToken).fetchWorkspaceTeams();
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb("Error fetching Linear teams: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    @Audit(description = "User created a Linear team", resource = Resource.LINEAR_INTEGRATION, operation = Operation.CREATE)
    public String createLinearTeam() {
        try {
            teamsMap = new LinearIntegrationService(apiToken).createTeam(teamName, teamKey);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb("Error creating Linear team: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    @Audit(description = "User removed Linear integration", resource = Resource.LINEAR_INTEGRATION, operation = Operation.DELETE)
    public String removeLinearIntegration() {
        try {
            new LinearIntegrationService().removeIntegration();
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb("Error removing Linear integration: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    public String createLinearTickets() {
        try {
            TicketCreationResult result = new LinearIntegrationService()
                .createTickets(testingIssuesIdList, teamId, null, aktoDashboardHost);
            errorMessage = result.getMessage();
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb("Error creating Linear tickets: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }
}
