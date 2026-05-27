package com.akto.dao;

import com.akto.dto.AgenticUsers;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;

public class AgentUsersDao extends AccountsContextDao<AgenticUsers>{
    public static final AgentUsersDao instance = new AgentUsersDao();

    public void createIndicesIfAbsent() {
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[]{AgenticUsers.TEAM_NAME, AgenticUsers.USER_ROLE}, false);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[]{AgenticUsers.USER_NAME}, false);
    }

    /**
     * Upserts an AgenticUsers record keyed by userName, setting team/role and
     * adding the device name to the devices set (addToSet — no duplicates).
     */
    public void upsertAgentUser(String userName, String teamName, String userRole, String device, int lastUpdatedAt) {
        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions()
            .upsert(true)
            .returnDocument(ReturnDocument.AFTER);

        getMCollection().findOneAndUpdate(
            Filters.eq(AgenticUsers.USER_NAME, userName),
            Updates.combine(
                Updates.setOnInsert(AgenticUsers.USER_NAME, userName),
                Updates.set(AgenticUsers.TEAM_NAME, teamName),
                Updates.set(AgenticUsers.USER_ROLE, userRole),
                Updates.set(AgenticUsers.LAST_UPDATED_AT, lastUpdatedAt),
                Updates.addToSet(AgenticUsers.DEVICES, device)
            ),
            options
        );
    }

    @Override
    public String getCollName() {
        return "agent_users";
    }

    @Override
    public Class<AgenticUsers> getClassT() {
        return AgenticUsers.class;
    }
}
