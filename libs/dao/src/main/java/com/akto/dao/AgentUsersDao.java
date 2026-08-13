package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.AgenticUsers;
import com.akto.dto.DeviceTag;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AgentUsersDao extends AccountsContextDao<AgenticUsers>{
    public static final AgentUsersDao instance = new AgentUsersDao();
    private static final Logger logger = LoggerFactory.getLogger(AgentUsersDao.class);
    // Ported from the dashboard's DeviceTag redesign, for the periodic Okta user-sync cron only.
    private static final int MAX_TAGS_PER_SOURCE = 50;

    public void createIndicesIfAbsent() {
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[]{AgenticUsers.TEAM_NAME, AgenticUsers.USER_ROLE}, false);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[]{AgenticUsers.USER_NAME}, false);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[]{AgenticUsers.DEVICE_TAGS + "." + DeviceTag.KEY,
                    AgenticUsers.DEVICE_TAGS + "." + DeviceTag.VALUE}, false);
    }

    /**
     * Full replace of a source's tags with the given key→values set — deleting any key not
     * reported, so stale groups don't linger. Correct for SSO, which always reports the
     * identity's *complete* current group membership on every sync.
     */
    public void upsertDeviceTags(String identityUserName, String source, Map<String, List<String>> keyValues, String lastUpdatedBy) {
        if (identityUserName == null || identityUserName.trim().isEmpty() || source == null || source.trim().isEmpty()) return;
        int now = Context.now();

        List<DeviceTag> newTags = new ArrayList<>();
        outer:
        for (Map.Entry<String, List<String>> entry : keyValues.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.trim().isEmpty() || entry.getValue() == null) continue;
            for (String rawValue : entry.getValue()) {
                if (rawValue == null || rawValue.trim().isEmpty()) continue;
                if (newTags.size() >= MAX_TAGS_PER_SOURCE) {
                    logger.warn("[{}] tag cap ({}) reached for {} — remaining values dropped", source, MAX_TAGS_PER_SOURCE, identityUserName);
                    break outer;
                }
                newTags.add(new DeviceTag(key.trim().toLowerCase(), rawValue.trim().toLowerCase(), source, now, lastUpdatedBy));
            }
        }

        AgenticUsers existing = instance.findOne(Filters.eq(AgenticUsers.USER_NAME, identityUserName));
        List<DeviceTag> existingTags = existing != null && existing.getDeviceTags() != null
                ? existing.getDeviceTags() : Collections.emptyList();

        List<DeviceTag> merged = existingTags.stream()
                .filter(t -> !source.equals(t.getSource()))
                .collect(Collectors.toCollection(ArrayList::new));
        merged.addAll(newTags);

        instance.updateOne(Filters.eq(AgenticUsers.USER_NAME, identityUserName),
                Updates.set(AgenticUsers.DEVICE_TAGS, merged));
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

    private static String deriveUsernameFromEmail(String email) {
        if (email == null || email.isEmpty() || !email.contains("@")) return null;
        String local = email.substring(0, email.indexOf('@')).trim();
        return local.isEmpty() ? null : local;
    }

    /**
     * Read-side counterpart to syncSsoIdentity's write-side convergence — resolves a user's
     * AgenticUsers doc (and their synced device tags) starting from any of the 3 spellings a
     * caller might have on hand: a raw username/login, an email, or the email's derived
     * local-part. Lets a future caller (e.g. a device-to-owner tag lookup) find the right doc
     * without needing to already know which spelling ended up as the canonical userName.
     * Prefers the canonical (derived-local-part) doc when more than one match exists — e.g.
     * before a sync has had a chance to converge stray fragments onto it — so callers get a
     * stable answer even mid-convergence.
     */
    public AgenticUsers findByAnyIdentity(String userName, String userEmail) {
        String trimmedName = userName == null ? "" : userName.trim();
        String trimmedEmail = userEmail == null ? "" : userEmail.trim();
        // A caller sometimes only has one string in hand and doesn't know whether it's a username
        // or an email (e.g. a device report using an email-shaped string as its "username" field,
        // with no separate email available) — if no email was separately supplied but the name
        // looks like one, use it for the email-side match too.
        if (trimmedEmail.isEmpty() && trimmedName.contains("@")) {
            trimmedEmail = trimmedName;
        }
        if (trimmedName.isEmpty() && trimmedEmail.isEmpty()) return null;
        String derivedUsername = deriveUsernameFromEmail(trimmedEmail);

        List<AgenticUsers> matches = findAllIdentityMatches(trimmedName, trimmedEmail, derivedUsername);
        if (matches.isEmpty()) return null;
        if (derivedUsername != null) {
            for (AgenticUsers u : matches) {
                if (derivedUsername.equals(u.getUserName())) return u;
            }
        }
        return matches.get(0);
    }

    /**
     * Resolves this SSO identity (union-match across username/email/derived-username,
     * canonicalizing every match onto the email-derived username so fragments converge over
     * time), ensures a doc exists, and refreshes email/timestamp. Callers apply tags afterward
     * via upsertDeviceTags using the returned canonical username.
     */
    public String syncSsoIdentity(String userName, String userEmail, String lastUpdatedBy) {
        if (userName == null || userName.trim().isEmpty()) return null;
        String trimmedName = userName.trim();
        String trimmedEmail = userEmail == null ? "" : userEmail.trim();
        String derivedUsername = deriveUsernameFromEmail(trimmedEmail);

        List<AgenticUsers> matches = findAllIdentityMatches(trimmedName, trimmedEmail, derivedUsername);
        String identityUserName = derivedUsername != null ? derivedUsername : trimmedName;

        List<Bson> baseUpdates = Arrays.asList(
                Updates.set(AgenticUsers.USER_NAME, identityUserName),
                Updates.set(AgenticUsers.USER_EMAIL, trimmedEmail),
                Updates.set(AgenticUsers.LAST_UPDATED_AT, Context.now()),
                Updates.set(AgenticUsers.LAST_UPDATED_BY, lastUpdatedBy));
        if (matches.isEmpty()) {
            instance.updateOne(Filters.eq(AgenticUsers.USER_NAME, identityUserName), Updates.combine(baseUpdates));
        } else {
            List<String> matchedUsernames = matches.stream().map(AgenticUsers::getUserName).distinct().collect(Collectors.toList());
            instance.updateMany(Filters.in(AgenticUsers.USER_NAME, matchedUsernames), Updates.combine(baseUpdates));
        }
        return identityUserName;
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
