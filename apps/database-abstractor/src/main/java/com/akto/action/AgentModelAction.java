package com.akto.action;

import com.akto.dao.AgentUsersDao;
import com.akto.dao.agents.AgentModelDao;
import com.akto.dto.agents.Model;
import com.akto.dto.agents.ModelType;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

public class AgentModelAction extends ActionSupport {

    private static final LoggerMaker logger = new LoggerMaker(AgentModelAction.class, LogDb.DB_ABS);

    @Setter
    private String type;

    @Getter
    private List<Model> agentModels;

    public String fetchAgentModels() {
        try {
            if (type != null) {
                ModelType modelType = ModelType.valueOf(type.trim().toUpperCase());
                agentModels = AgentModelDao.instance.findAllByType(modelType);
            } else {
                agentModels = AgentModelDao.instance.findAllForAllTypes();
            }
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.error("Error fetching agent models: " + e.getMessage());
            addActionError("Failed to fetch agent models");
            return Action.ERROR.toUpperCase();
        }
    }

    @Setter
    private String identifier;

    @Setter
    private String accountId;

    @Setter
    private String userId;

    @Setter
    private String email;

    @Setter
    private String deviceId;

    @Setter
    private String userName;

    /**
     * Upserts an agent_users row keyed on {identifier}_{accountId}_{userId} (identifier
     * upper-cased), so the same underlying userId reported under different agents/accounts
     * resolves to distinct identities.
     */
    public String upsertAgentUser() {
        try {
            if (identifier == null || identifier.trim().isEmpty()
                || accountId == null || accountId.trim().isEmpty()
                || userId == null || userId.trim().isEmpty()) {
                addActionError("identifier, accountId and userId are required");
                return Action.ERROR.toUpperCase();
            }

            String composedUserId = identifier.trim().toUpperCase() + "_" + accountId.trim() + "_" + userId.trim();
            AgentUsersDao.instance.upsertAgentUserIdentity(composedUserId, userName, email, deviceId, userName);

            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.error("Error upserting agent user: " + e.getMessage());
            addActionError("Failed to upsert agent user");
            return Action.ERROR.toUpperCase();
        }
    }
}
