package com.akto.action;

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
}
