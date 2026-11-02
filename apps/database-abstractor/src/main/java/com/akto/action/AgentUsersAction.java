package com.akto.action;

import com.akto.dao.AgentUsersDao;
import com.akto.dto.AgenticUsers;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.BasicDBObject;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** Server side of CopilotStudioAgentUsersCron's agent_users sync (akto/libs/utils, via ClientActor). */
@Getter
@Setter
public class AgentUsersAction extends ActionSupport {

    private static final LoggerMaker loggerMaker = new LoggerMaker(AgentUsersAction.class, LogDb.DB_ABS);

    // Output for fetchAllAgentUsers, input for bulkUpsertAgentUserExternalIdentities.
    private List<AgenticUsers> agentUsersList;

    /** Every agent_users row — for CopilotStudioAgentUsersCron's DB-only cache warm-up on startup. */
    public String fetchAllAgentUsers() {
        try {
            this.agentUsersList = AgentUsersDao.instance.findAll(new BasicDBObject());
        } catch (Exception e) {
            loggerMaker.error("Error in fetchAllAgentUsers", e);
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    /**
     * Upserts many rows sourced from an external identity directory (e.g. Microsoft Graph for
     * Copilot Studio) in one Mongo round trip — a tenant sync can mean tens of thousands of
     * changed users, so the caller (ClientActor) batches into this rather than calling per user.
     */
    public String bulkUpsertAgentUserExternalIdentities() {
        try {
            AgentUsersDao.instance.bulkUpsertExternalIdentities(agentUsersList);
        } catch (Exception e) {
            loggerMaker.error("Error in bulkUpsertAgentUserExternalIdentities", e);
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }
}
