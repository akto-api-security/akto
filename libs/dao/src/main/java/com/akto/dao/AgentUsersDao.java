package com.akto.dao;

import com.akto.dto.AgenticUsers;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import java.util.ArrayList;
import java.util.List;
import org.bson.conversions.Bson;

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

    /**
     * Returns the flat list of device IDs belonging to users that match any of the
     * given teams or roles. Pass empty/null lists to get an empty result (not all devices).
     */
    public List<String> findDeviceIdsByTeamsAndRoles(List<String> teams, List<String> roles) {
        List<Bson> conditions = new ArrayList<>();
        if (teams != null && !teams.isEmpty()) {
            conditions.add(Filters.or(
                Filters.and(Filters.eq(AgenticUsers.TEAM_SOURCE, AgenticUsers.SOURCE_MANUAL), Filters.in(AgenticUsers.TEAM_NAME, teams)),
                Filters.and(Filters.ne(AgenticUsers.TEAM_SOURCE, AgenticUsers.SOURCE_MANUAL), Filters.in(AgenticUsers.SSO_TEAM_NAME, teams))
            ));
        }
        if (roles != null && !roles.isEmpty()) {
            conditions.add(Filters.or(
                Filters.and(Filters.eq(AgenticUsers.ROLE_SOURCE, AgenticUsers.SOURCE_MANUAL), Filters.in(AgenticUsers.USER_ROLE, roles)),
                Filters.and(Filters.ne(AgenticUsers.ROLE_SOURCE, AgenticUsers.SOURCE_MANUAL), Filters.in(AgenticUsers.SSO_USER_ROLE, roles))
            ));
        }
        if (conditions.isEmpty()) {
            return new ArrayList<>();
        }

        Bson userFilter = conditions.size() == 1 ? conditions.get(0) : Filters.and(conditions);
        List<String> deviceIds = new ArrayList<>();
        for (AgenticUsers user : instance.findAll(userFilter)) {
            if (user.getDevices() != null) {
                deviceIds.addAll(user.getDevices());
            }
        }
        return deviceIds;
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
