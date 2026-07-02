package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.AgenticUsers;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;

public class AgentUsersDao extends AccountsContextDao<AgenticUsers>{
    public static final AgentUsersDao instance = new AgentUsersDao();

    public void createIndicesIfAbsent() {
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[]{AgenticUsers.TEAM_NAME, AgenticUsers.USER_ROLE}, false);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[]{AgenticUsers.USER_NAME}, false);
    }

    /**
     * Dashboard write — overwrites team/role and pins source to "manual" only for fields
     * whose value actually changed. Unchanged fields keep their existing source (e.g. "sso").
     */
    public void upsertTagFromDashboard(String userName, String userEmail, String teamName, String userRole, String lastUpdatedBy) {
        if (userName == null || userName.trim().isEmpty()) return;
        String trimmedName = userName.trim();

        AgenticUsers existing = instance.findOne(Filters.eq(AgenticUsers.USER_NAME, trimmedName));
        String existingTeam = existing != null && existing.getTeamName() != null ? existing.getTeamName() : "";
        String existingRole = existing != null && existing.getUserRole() != null ? existing.getUserRole() : "";

        String newTeam = teamName == null ? "" : teamName.trim();
        String newRole = userRole == null ? "" : userRole.trim();

        List<Bson> updates = new ArrayList<>();
        // When admin clears a field, fall back to the last SSO value immediately.
        String effectiveTeam = newTeam;
        String effectiveRole = newRole;
        String teamSourceToWrite = null;
        String roleSourceToWrite = null;

        if (!newTeam.equals(existingTeam)) {
            if (newTeam.isEmpty()) {
                effectiveTeam = existing != null && existing.getSsoTeamName() != null ? existing.getSsoTeamName() : "";
                teamSourceToWrite = AgenticUsers.SOURCE_SSO;
            } else {
                teamSourceToWrite = AgenticUsers.SOURCE_MANUAL;
            }
        }
        if (!newRole.equals(existingRole)) {
            if (newRole.isEmpty()) {
                effectiveRole = existing != null && existing.getSsoUserRole() != null ? existing.getSsoUserRole() : "";
                roleSourceToWrite = AgenticUsers.SOURCE_SSO;
            } else {
                roleSourceToWrite = AgenticUsers.SOURCE_MANUAL;
            }
        }

        updates.add(Updates.set(AgenticUsers.USER_NAME, trimmedName));
        if (userEmail != null && !userEmail.trim().isEmpty()) {
            updates.add(Updates.set(AgenticUsers.USER_EMAIL, userEmail.trim()));
        }
        updates.add(Updates.set(AgenticUsers.TEAM_NAME, effectiveTeam));
        updates.add(Updates.set(AgenticUsers.USER_ROLE, effectiveRole));
        updates.add(Updates.set(AgenticUsers.LAST_UPDATED_AT, Context.now()));
        updates.add(Updates.set(AgenticUsers.LAST_UPDATED_BY, lastUpdatedBy));
        if (teamSourceToWrite != null) updates.add(Updates.set(AgenticUsers.TEAM_SOURCE, teamSourceToWrite));
        if (roleSourceToWrite != null) updates.add(Updates.set(AgenticUsers.ROLE_SOURCE, roleSourceToWrite));

        if (existing == null) {
            AgenticUsers newUser = new AgenticUsers();
            newUser.setUserName(trimmedName);
            if (userEmail != null && !userEmail.trim().isEmpty()) {
                newUser.setUserEmail(userEmail.trim());
            }
            newUser.setTeamName(effectiveTeam);
            newUser.setUserRole(effectiveRole);
            newUser.setTeamSource(teamSourceToWrite != null ? teamSourceToWrite : AgenticUsers.SOURCE_MANUAL);
            newUser.setRoleSource(roleSourceToWrite != null ? roleSourceToWrite : AgenticUsers.SOURCE_MANUAL);
            newUser.setLastUpdatedAt(Context.now());
            newUser.setLastUpdatedBy(lastUpdatedBy);
            instance.insertOne(newUser);
        } else {
            instance.updateMany(Filters.eq(AgenticUsers.USER_NAME, trimmedName), Updates.combine(updates));
        }
    }

    /** SSO write — skips teamName/userRole if already pinned as "manual" by a dashboard override. */
    public void upsertTagFromSso(String userName, String userEmail, String teamName, String userRole, String lastUpdatedBy) {
        if (userName == null || userName.trim().isEmpty()) return;
        String trimmedName = userName.trim();
        AgenticUsers existing = instance.findOne(Filters.eq(AgenticUsers.USER_NAME, trimmedName));

        List<Bson> updates = new ArrayList<>();
        updates.add(Updates.set(AgenticUsers.USER_NAME, trimmedName));
        updates.add(Updates.set(AgenticUsers.USER_EMAIL, userEmail == null ? "" : userEmail.trim()));
        updates.add(Updates.set(AgenticUsers.LAST_UPDATED_AT, Context.now()));
        updates.add(Updates.set(AgenticUsers.LAST_UPDATED_BY, lastUpdatedBy));

        String ssoTeam = teamName == null ? "" : teamName.trim();
        String ssoRole = userRole == null ? "" : userRole.trim();

        // Always keep shadow fields current so admins can restore SSO values immediately.
        updates.add(Updates.set(AgenticUsers.SSO_TEAM_NAME, ssoTeam));
        updates.add(Updates.set(AgenticUsers.SSO_USER_ROLE, ssoRole));

        boolean teamPinned = existing != null && AgenticUsers.SOURCE_MANUAL.equals(existing.getTeamSource());
        boolean rolePinned = existing != null && AgenticUsers.SOURCE_MANUAL.equals(existing.getRoleSource());

        if (!teamPinned) {
            updates.add(Updates.set(AgenticUsers.TEAM_NAME, ssoTeam));
            updates.add(Updates.set(AgenticUsers.TEAM_SOURCE, AgenticUsers.SOURCE_SSO));
        }
        if (!rolePinned) {
            updates.add(Updates.set(AgenticUsers.USER_ROLE, ssoRole));
            updates.add(Updates.set(AgenticUsers.ROLE_SOURCE, AgenticUsers.SOURCE_SSO));
        }

        instance.updateOne(Filters.eq(AgenticUsers.USER_NAME, trimmedName), Updates.combine(updates));
    }

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
