package com.akto.action;

import com.akto.audit_logs_util.Audit;
import com.akto.dto.audit_logs.Operation;
import com.akto.dto.audit_logs.Resource;
import com.akto.dto.linear_integration.LinearTeamMapping;
import com.akto.dto.port_io_integration.PortIOIntegration;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.linear.PortIOIntegrationService;
import com.akto.log.LoggerMaker;
import com.akto.ticketing.ATicketIntegrationService.TicketCreationResult;
import com.opensymphony.xwork2.Action;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PortIOIntegrationAction extends UserAction {

    private static final LoggerMaker logger = new LoggerMaker(PortIOIntegrationAction.class,
        LoggerMaker.LogDb.DASHBOARD);

    private String clientId;
    private String clientSecret;
    private String workflowId;
    private Map<String, LinearTeamMapping> teamMappings;
    private PortIOIntegration portIOIntegration;
    private List<TestingIssuesId> testingIssuesIdList;
    private String teamId;
    private String errorMessage;
    private String aktoDashboardHost;

    @Audit(description = "User saved Port credentials", resource = Resource.PORT_IO_INTEGRATION, operation = Operation.CREATE)
    public String addPortIOIntegration() {
        try {
            portIOIntegration = new PortIOIntegrationService().saveCredentials(clientId, clientSecret, workflowId);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb("Error adding Port integration: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    @Audit(description = "User saved Port Linear teams", resource = Resource.PORT_IO_INTEGRATION, operation = Operation.UPDATE)
    public String savePortIOTeams() {
        try {
            portIOIntegration = new PortIOIntegrationService().saveTeams(teamMappings);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb("Error saving Port teams: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    public String fetchPortIOIntegration() {
        try {
            portIOIntegration = new PortIOIntegrationService().fetchPortIOIntegration();
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb("Error fetching Port integration: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    @Audit(description = "User removed Port integration", resource = Resource.PORT_IO_INTEGRATION, operation = Operation.DELETE)
    public String removePortIOIntegration() {
        try {
            new PortIOIntegrationService().removeIntegration();
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb("Error removing Port integration: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    public String createPortIOTickets() {
        try {
            TicketCreationResult result = new PortIOIntegrationService()
                .createTickets(testingIssuesIdList, teamId, null, aktoDashboardHost);
            errorMessage = result.getMessage();
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb("Error creating Linear tickets through Port: " + e.getMessage(),
                LoggerMaker.LogDb.DASHBOARD);
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }
}
