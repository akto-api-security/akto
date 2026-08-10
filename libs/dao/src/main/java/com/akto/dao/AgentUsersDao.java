package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.AgenticUsers;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AgentUsersDao extends AccountsContextDao<AgenticUsers>{
    public static final AgentUsersDao instance = new AgentUsersDao();

    public void createIndicesIfAbsent() {
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[]{AgenticUsers.TEAM_NAME, AgenticUsers.USER_ROLE}, false);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[]{AgenticUsers.USER_NAME}, false);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[]{AgenticUsers.USER_EMAIL}, false);
    }

    /**
     * Dashboard write — overwrites team/role and pins source to "manual" only for fields
     * whose value actually changed. Unchanged fields keep their existing source (e.g. "sso").
     */
    public void upsertTagFromDashboard(String userName, String userEmail, String teamName, String userRole, String lastUpdatedBy) {
        if (userName == null || userName.trim().isEmpty()) return;
        String trimmedName = userName.trim();
        String trimmedEmail = userEmail == null ? "" : userEmail.trim();
        AgenticUsers existing = instance.findOne(Filters.eq(AgenticUsers.USER_NAME, trimmedName));
        String existingTeam = existing != null && existing.getTeamName() != null ? existing.getTeamName().trim() : "";
        String existingRole = existing != null && existing.getUserRole() != null ? existing.getUserRole().trim() : "";

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

        if (!trimmedEmail.isEmpty()) {
            updates.add(Updates.set(AgenticUsers.USER_EMAIL, trimmedEmail));
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
            if (!trimmedEmail.isEmpty()) {
                newUser.setUserEmail(trimmedEmail);
            }
            newUser.setTeamName(effectiveTeam);
            newUser.setUserRole(effectiveRole);
            // A brand-new doc with no actual team/role value was never manually set — leave it open for SSO.
            newUser.setTeamSource(teamSourceToWrite != null ? teamSourceToWrite
                    : (effectiveTeam.isEmpty() ? AgenticUsers.SOURCE_SSO : AgenticUsers.SOURCE_MANUAL));
            newUser.setRoleSource(roleSourceToWrite != null ? roleSourceToWrite
                    : (effectiveRole.isEmpty() ? AgenticUsers.SOURCE_SSO : AgenticUsers.SOURCE_MANUAL));
            newUser.setLastUpdatedAt(Context.now());
            newUser.setLastUpdatedBy(lastUpdatedBy);
            instance.insertOne(newUser);
        } else {
            // updateMany still covers multiple devices legitimately sharing this exact username.
            instance.updateMany(Filters.eq(AgenticUsers.USER_NAME, trimmedName), Updates.combine(updates));
        }
    }

    /**
     * SSO write — skips team/role if pinned "manual" with a real value. Renames every matching
     * doc onto the email-derived username so identity fragments converge over time.
     */
    public void upsertTagFromSso(String userName, String userEmail, String teamName, String userRole, String lastUpdatedBy) {
        if (userName == null || userName.trim().isEmpty()) return;
        String trimmedName = userName.trim();
        String trimmedEmail = userEmail == null ? "" : userEmail.trim();
        String derivedUsername = deriveUsernameFromEmail(trimmedEmail);

        List<AgenticUsers> matches = findAllIdentityMatches(trimmedName, trimmedEmail, derivedUsername);
        // Canonicalize on the email's local part (stable OS identifier), not the raw SSO username
        // (often a full email) — matches get renamed to it below so fragments self-heal.
        String identityUserName = derivedUsername != null ? derivedUsername : trimmedName;

        String ssoTeam = teamName == null ? "" : teamName.trim();
        String ssoRole = userRole == null ? "" : userRole.trim();

        List<Bson> baseUpdates = Arrays.asList(
                Updates.set(AgenticUsers.USER_NAME, identityUserName),
                Updates.set(AgenticUsers.USER_EMAIL, trimmedEmail),
                Updates.set(AgenticUsers.LAST_UPDATED_AT, Context.now()),
                Updates.set(AgenticUsers.LAST_UPDATED_BY, lastUpdatedBy));
        if (matches.isEmpty()) {
            // First-ever login for this identity — nothing to updateMany yet, so upsert the doc.
            instance.updateOne(Filters.eq(AgenticUsers.USER_NAME, identityUserName), Updates.combine(baseUpdates));
        } else {
            // Rename every matched doc onto the canonical identity.
            List<String> matchedUsernames = matches.stream().map(AgenticUsers::getUserName).distinct().collect(Collectors.toList());
            instance.updateMany(Filters.in(AgenticUsers.USER_NAME, matchedUsernames), Updates.combine(baseUpdates));
        }

        // Re-scope by the canonical name — the rename above already covers every fragment.
        Bson canonicalFilter = Filters.eq(AgenticUsers.USER_NAME, identityUserName);
        applySsoTeamOrRole(canonicalFilter, AgenticUsers.TEAM_NAME, AgenticUsers.TEAM_SOURCE, AgenticUsers.SSO_TEAM_NAME, ssoTeam);
        applySsoTeamOrRole(canonicalFilter, AgenticUsers.USER_ROLE, AgenticUsers.ROLE_SOURCE, AgenticUsers.SSO_USER_ROLE, ssoRole);
    }

    /** Union of every doc matching username, email, or derived username — not first-tier-wins. */
    private List<AgenticUsers> findAllIdentityMatches(String userName, String userEmail, String derivedUsername) {
        List<Bson> identityMatchers = new ArrayList<>();
        identityMatchers.add(Filters.eq(AgenticUsers.USER_NAME, userName));
        if (userEmail != null && !userEmail.isEmpty()) {
            identityMatchers.add(Filters.eq(AgenticUsers.USER_EMAIL, userEmail));
        }
        if (derivedUsername != null) {
            identityMatchers.add(Filters.eq(AgenticUsers.USER_NAME, derivedUsername));
        }
        return instance.findAll(Filters.or(identityMatchers));
    }

    /**
     * Shadow field (e.g. ssoTeamName) always updates. Real field only updates where it isn't
     * pinned "manual" with a real value — checked live by Mongo, not a stale Java snapshot.
     */
    private void applySsoTeamOrRole(Bson identityFilter, String field, String sourceField, String shadowField, String ssoValue) {
        instance.updateMany(identityFilter, Updates.set(shadowField, ssoValue));

        Bson notPinned = Filters.or(
                Filters.ne(sourceField, AgenticUsers.SOURCE_MANUAL),
                Filters.in(field, Arrays.asList(null, "")));
        instance.updateMany(Filters.and(identityFilter, notPinned), Updates.combine(
                Updates.set(field, ssoValue),
                Updates.set(sourceField, AgenticUsers.SOURCE_SSO)));
    }

    private static String deriveUsernameFromEmail(String email) {
        if (email == null || email.isEmpty() || !email.contains("@")) return null;
        String local = email.substring(0, email.indexOf('@')).trim();
        return local.isEmpty() ? null : local;
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
