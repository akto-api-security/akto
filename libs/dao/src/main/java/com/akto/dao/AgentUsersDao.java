package com.akto.dao;

import com.akto.dto.AgenticUsers;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;

public class AgentUsersDao extends AccountsContextDao<AgenticUsers>{
    public static final AgentUsersDao instance = new AgentUsersDao();

    public void createIndicesIfAbsent() {
        // _id is the username, so it's already a unique index. No extra indices needed today.
    }

    public void upsertTag(String userName, String userEmail, String teamName, String userRole) {
        if (userName == null || userName.trim().isEmpty()) return;
        String trimmedName = userName.trim();
        Bson filter = Filters.eq(AgenticUsers.ID, trimmedName);
        Bson update = Updates.combine(
            Updates.setOnInsert(AgenticUsers.ID, trimmedName),
            Updates.set(AgenticUsers.USER_NAME, trimmedName),
            Updates.set(AgenticUsers.USER_EMAIL, userEmail == null ? "" : userEmail.trim()),
            Updates.set(AgenticUsers.TEAM_NAME, teamName == null ? "" : teamName.trim()),
            Updates.set(AgenticUsers.USER_ROLE, userRole == null ? "" : userRole.trim())
        );
        getMCollection().updateOne(filter, update, new UpdateOptions().upsert(true));
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
