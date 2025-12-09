package com.akto.action;

import com.akto.devrev.DevRevIntegrationManager;
import com.akto.dto.devrev_integration.DevRevIntegration;
import com.akto.log.LoggerMaker;
import com.opensymphony.xwork2.Action;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class DevRevIntegrationAction extends UserAction {

    private static final LoggerMaker logger = new LoggerMaker(DevRevIntegrationAction.class, LoggerMaker.LogDb.DASHBOARD);

    private String orgUrl;
    private String personalAccessToken;
    private DevRevIntegration devrevIntegration;
    private Map<String, String> partsIdToNameMap;

    public String addDevRevIntegration() {
        try {
            DevRevIntegrationManager manager = new DevRevIntegrationManager(orgUrl, personalAccessToken);
            devrevIntegration = manager.addIntegration(partsIdToNameMap);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb("Error adding DevRev integration: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    public String fetchDevRevIntegration() {
        try {
            DevRevIntegrationManager manager = new DevRevIntegrationManager();
            devrevIntegration = manager.fetchIntegration();
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb("Error fetching DevRev integration: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    public String fetchDevrevProjects() {
        try {
            DevRevIntegrationManager manager = new DevRevIntegrationManager(null, personalAccessToken);
            partsIdToNameMap = manager.fetchDevrevProjects();
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb("Error fetching DevRev projects: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    public String removeDevRevIntegration() {
        try {
            DevRevIntegrationManager manager = new DevRevIntegrationManager();
            manager.removeIntegration();
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb("Error removing DevRev integration: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }
}