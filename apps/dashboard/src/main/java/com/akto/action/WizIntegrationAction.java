package com.akto.action;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.opensymphony.xwork2.Action;

import lombok.Getter;
import lombok.Setter;

public class WizIntegrationAction extends UserAction {

    private static final LoggerMaker logger = new LoggerMaker(WizIntegrationAction.class, LogDb.DASHBOARD);

    public String addWizIntegration() {
        return Action.SUCCESS.toUpperCase();
    }
    @Getter
    @Setter
    private TestingIssuesId testingIssuesId;

    public String sendToWiz() {
        return Action.SUCCESS.toUpperCase();
    }
}
