package com.akto.action;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.service.posture.AgentDetailResult;
import com.akto.service.posture.ArgusAgentDetailService;

import lombok.Getter;
import lombok.Setter;

public class ArgusAgentDetailAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ArgusAgentDetailAction.class, LogDb.DASHBOARD);

    private final ArgusAgentDetailService agentDetailService = new ArgusAgentDetailService();

    @Getter @Setter private int collectionId;
    @Getter @Setter private String finding;

    @Getter private AgentDetailResult agentDetail;

    public String fetchArgusAgentDetail() {
        try {
            agentDetail = agentDetailService.fetchAgentDetail(collectionId, finding);
            if (agentDetail == null) {
                addActionError("Agent not found");
                return ERROR.toUpperCase();
            }
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error building Argus agent detail: " + e.getMessage());
            addActionError("Failed to build Argus agent detail");
            return ERROR.toUpperCase();
        }
    }

    @Override
    public String execute() {
        return SUCCESS.toUpperCase();
    }
}
