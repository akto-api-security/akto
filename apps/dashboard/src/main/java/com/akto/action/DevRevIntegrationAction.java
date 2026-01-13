package com.akto.action;

import com.akto.devrev.DevRevIntegrationService;
import com.akto.dto.devrev_integration.DevRevIntegration;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.log.LoggerMaker;
import com.akto.ticketing.ATicketIntegrationService.TicketCreationResult;
import com.opensymphony.xwork2.Action;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DevRevIntegrationAction extends UserAction {

    private static final LoggerMaker logger = new LoggerMaker(DevRevIntegrationAction.class, LoggerMaker.LogDb.DASHBOARD);

    private String orgUrl;
    private String personalAccessToken;
    private DevRevIntegration devrevIntegration;
    private Map<String, String> partsIdToNameMap;
    private List<TestingIssuesId> testingIssuesIdList;
    private String partId;
    private String workItemType;
    private String errorMessage;
    private String aktoDashboardHost;
    private List<String> partTypes;
    private String partName;

    public String addDevRevIntegration() {
        try {
            DevRevIntegrationService devRevService = new DevRevIntegrationService(orgUrl, personalAccessToken);
            devrevIntegration = devRevService.addIntegration(partsIdToNameMap);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb("Error adding DevRev integration: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    public String fetchDevRevIntegration() {
        try {
            DevRevIntegrationService devRevService = new DevRevIntegrationService();
            devrevIntegration = devRevService.fetchDevRevIntegration();
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb("Error fetching DevRev integration: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    public String fetchDevRevParts() {
        try {
            DevRevIntegrationService devRevService = new DevRevIntegrationService(null, personalAccessToken);
            partsIdToNameMap = devRevService.fetchDevrevProjects(partTypes, partName);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb("Error fetching DevRev projects: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    public String removeDevRevIntegration() {
        try {
            DevRevIntegrationService devRevService = new DevRevIntegrationService();
            devRevService.removeIntegration();
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb("Error removing DevRev integration: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    public String createDevRevTickets() {
        try {

            DevRevIntegrationService devRevService = new DevRevIntegrationService();
            TicketCreationResult result = devRevService.createTickets(testingIssuesIdList, partId, workItemType, aktoDashboardHost);

            this.errorMessage = result.getMessage();

            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb("Error creating DevRev tickets: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }
}