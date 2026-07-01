package com.akto.dao;

import com.akto.dto.AgenticUsers;
import com.mongodb.MongoCommandException;
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

    }

    public void upsertAgentUser(String userName, String teamName, String userRole, String device, int lastUpdatedAt) {
        Bson userFilter = Filters.eq(AgenticUsers.USER_NAME, userName);
        boolean hasTeam = teamName != null && !teamName.isEmpty();
        boolean hasRole = userRole != null && !userRole.isEmpty();

        List<Bson> updates = new ArrayList<>();
        updates.add(Updates.setOnInsert(AgenticUsers.USER_NAME, userName));
        updates.add(Updates.set(AgenticUsers.LAST_UPDATED_AT, lastUpdatedAt));
        updates.add(Updates.addToSet(AgenticUsers.DEVICES, device));
        if (hasTeam) updates.add(Updates.set(AgenticUsers.SSO_TEAM_NAME, teamName));
        if (hasRole) updates.add(Updates.set(AgenticUsers.SSO_USER_ROLE, userRole));

        try {
            getMCollection().findOneAndUpdate(userFilter, Updates.combine(updates),
                new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER));
        } catch (MongoCommandException e) {
            if (e.getErrorCode() != 11000) throw e;
            getMCollection().updateOne(userFilter, Updates.combine(updates));
        }

        // Set effective fields only on first write (when not yet populated)
        if (hasTeam)
            getMCollection().updateOne(
                Filters.and(userFilter, Filters.in(AgenticUsers.TEAM_NAME, null, "")),
                Updates.set(AgenticUsers.TEAM_NAME, teamName));
        if (hasRole)
            getMCollection().updateOne(
                Filters.and(userFilter, Filters.in(AgenticUsers.USER_ROLE, null, "")),
                Updates.set(AgenticUsers.USER_ROLE, userRole));
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
