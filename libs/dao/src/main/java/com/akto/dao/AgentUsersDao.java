package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.AgenticUsers;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;

public class AgentUsersDao extends AccountsContextDao<AgenticUsers>{
    public static final AgentUsersDao instance = new AgentUsersDao();

    public void createIndicesIfAbsent() {
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[]{AgenticUsers.TEAM_NAME, AgenticUsers.USER_ROLE}, false);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[]{AgenticUsers.USER_NAME}, false);
    }

    public void upsertTag(String userName, String userEmail, String teamName, String userRole, String lastUpdatedBy) {
        if (userName == null || userName.trim().isEmpty()) return;
        String trimmedName = userName.trim();
        Bson filter = Filters.eq(AgenticUsers.USER_NAME, trimmedName);
        Bson update = Updates.combine(
            Updates.set(AgenticUsers.USER_NAME, trimmedName),
            Updates.set(AgenticUsers.USER_EMAIL, userEmail == null ? "" : userEmail.trim()),
            Updates.set(AgenticUsers.TEAM_NAME, teamName == null ? "" : teamName.trim()),
            Updates.set(AgenticUsers.USER_ROLE, userRole == null ? "" : userRole.trim()),
            Updates.set(AgenticUsers.LAST_UPDATED_AT, Context.now()),
            Updates.set(AgenticUsers.LAST_UPDATED_BY, lastUpdatedBy)
        );
        instance.updateOne(filter, update);
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
