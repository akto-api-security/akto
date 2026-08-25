package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.dto.AgenticUsers;
import com.akto.dto.DeviceTag;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;

public class AgentUsersDao extends AccountsContextDao<AgenticUsers>{
    public static final AgentUsersDao instance = new AgentUsersDao();
    private static final Logger logger = LoggerFactory.getLogger(AgentUsersDao.class);
    private static final int MAX_TAGS_PER_SOURCE = 50;

    public void createIndicesIfAbsent() {
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[]{AgenticUsers.USER_NAME}, false);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[]{AgenticUsers.USER_EMAIL}, false);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[]{AgenticUsers.USER_ID}, false);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[]{AgenticUsers.DEVICE_TAGS + "." + DeviceTag.KEY,
                    AgenticUsers.DEVICE_TAGS + "." + DeviceTag.VALUE}, false);
    }

    /**
     * Upserts many rows sourced from an external identity directory (e.g. Microsoft Graph for
     * Copilot Studio) in one Mongo round trip, each keyed on that source's own user id — a tenant
     * sync can mean tens of thousands of changed users, so this must never be a per-user call.
     * Generic and connector-agnostic — any future ai-agent connector with its own external user
     * id can reuse this the same way ensureConnectorIdentity already serves the inference-hooks
     * source. Rows with a blank userId/userName are skipped.
     */
    public void bulkUpsertExternalIdentities(List<AgenticUsers> updates) {
        if (updates == null || updates.isEmpty()) return;

        int now = Context.now();
        List<WriteModel<AgenticUsers>> writes = new ArrayList<>();
        for (AgenticUsers u : updates) {
            String userId = u.getUserId();
            String userName = u.getUserName();
            if (userId == null || userId.trim().isEmpty() || userName == null || userName.trim().isEmpty()) continue;

            List<Bson> fieldUpdates = new ArrayList<>();
            fieldUpdates.add(Updates.set(AgenticUsers.USER_ID, userId));
            fieldUpdates.add(Updates.set(AgenticUsers.USER_NAME, userName));
            if (u.getUserEmail() != null && !u.getUserEmail().isEmpty()) {
                fieldUpdates.add(Updates.set(AgenticUsers.USER_EMAIL, u.getUserEmail()));
            }
            fieldUpdates.add(Updates.set(AgenticUsers.LAST_UPDATED_AT, now));
            fieldUpdates.add(Updates.set(AgenticUsers.LAST_UPDATED_BY, u.getLastUpdatedBy()));

            writes.add(new UpdateOneModel<>(Filters.eq(AgenticUsers.USER_ID, userId),
                Updates.combine(fieldUpdates), new UpdateOptions().upsert(true)));
        }
        if (!writes.isEmpty()) {
            instance.getMCollection().bulkWrite(writes);
        }
    }

    /**
     * Full replace of a source's tags with the given key→values set — deleting any key not
     * reported, so stale groups don't linger. Correct for SSO, which always reports the
     * identity's *complete* current group membership on every login. Wrong for a dashboard edit,
     * which only ever touches specific fields — see mergeDeviceTags for that case.
     */
    public void upsertDeviceTags(String identityUserName, String source, Map<String, List<String>> keyValues, String lastUpdatedBy) {
        writeDeviceTags(identityUserName, source, keyValues, lastUpdatedBy, t -> !source.equals(t.getSource()));
    }

    /**
     * Only touches the keys present in keyValues — other tags from the same source are left
     * alone. Used by the dashboard, where a single edit only ever sets specific fields, not the
     * admin's complete tag set (unlike SSO, which always reports everything every login).
     */
    public void mergeDeviceTags(String identityUserName, String source, Map<String, List<String>> keyValues, String lastUpdatedBy) {
        Set<String> touchedKeys = new HashSet<>();
        for (String key : keyValues.keySet()) {
            if (key != null && !key.trim().isEmpty()) touchedKeys.add(key.trim().toLowerCase());
        }
        writeDeviceTags(identityUserName, source, keyValues, lastUpdatedBy,
                t -> !(source.equals(t.getSource()) && touchedKeys.contains(t.getKey())));
    }

    private void writeDeviceTags(String identityUserName, String source, Map<String, List<String>> keyValues,
            String lastUpdatedBy, java.util.function.Predicate<DeviceTag> keepExisting) {
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

        // Baseline: any one doc already sharing this identity has the same deviceTags as its
        // siblings — this method always writes them identically via updateMany below.
        AgenticUsers existing = instance.findOne(Filters.eq(AgenticUsers.USER_NAME, identityUserName));
        List<DeviceTag> existingTags = existing != null && existing.getDeviceTags() != null
                ? existing.getDeviceTags() : Collections.emptyList();

        List<DeviceTag> merged = existingTags.stream().filter(keepExisting).collect(Collectors.toCollection(ArrayList::new));
        merged.addAll(newTags);

        instance.updateMany(Filters.eq(AgenticUsers.USER_NAME, identityUserName),
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

    /**
     * Exact-username lookup only — the dashboard always sends back the live device-reported
     * username round-tripped from this DAO's own data, so broader matching (needed for SSO,
     * where the caller only has an email) doesn't help here. Ensures a doc exists.
     */
    public String ensureDashboardIdentity(String userName, String userEmail, String lastUpdatedBy) {
        if (userName == null || userName.trim().isEmpty()) return null;
        String trimmedName = userName.trim();
        String trimmedEmail = userEmail == null ? "" : userEmail.trim();

        AgenticUsers existing = instance.findOne(Filters.eq(AgenticUsers.USER_NAME, trimmedName));
        if (existing == null) {
            AgenticUsers newUser = new AgenticUsers();
            newUser.setUserName(trimmedName);
            if (!trimmedEmail.isEmpty()) newUser.setUserEmail(trimmedEmail);
            newUser.setLastUpdatedAt(Context.now());
            newUser.setLastUpdatedBy(lastUpdatedBy);
            instance.insertOne(newUser);
        } else {
            List<Bson> updates = new ArrayList<>();
            updates.add(Updates.set(AgenticUsers.LAST_UPDATED_AT, Context.now()));
            updates.add(Updates.set(AgenticUsers.LAST_UPDATED_BY, lastUpdatedBy));
            if (!trimmedEmail.isEmpty()) updates.add(Updates.set(AgenticUsers.USER_EMAIL, trimmedEmail));
            instance.updateMany(Filters.eq(AgenticUsers.USER_NAME, trimmedName), Updates.combine(updates));
        }
        return trimmedName;
    }

    private static final Map<String, List<String>> INFERENCE_HOOKS_TAG = Collections.singletonMap("connector", Collections.singletonList("inference-hooks"));

    // Registers a connector-only identity on first contact; on any existing-identity match, adds our label and tag instead of inserting a duplicate.
    public void ensureConnectorIdentity(String email, String deviceLabel, String lastUpdatedBy) {
        if (email == null || email.trim().isEmpty() || deviceLabel == null || deviceLabel.trim().isEmpty()) {
            return;
        }
        String trimmedEmail = email.trim();

        List<AgenticUsers> matches = findAllIdentityMatches(deviceLabel, trimmedEmail, trimmedEmail);
        if (!matches.isEmpty()) {
            List<String> matchedUsernames = matches.stream().map(AgenticUsers::getUserName).distinct().collect(Collectors.toList());
            instance.updateMany(Filters.in(AgenticUsers.USER_NAME, matchedUsernames), Updates.addToSet("devices", deviceLabel));
            for (String matchedUsername : matchedUsernames) {
                mergeDeviceTags(matchedUsername, DeviceTag.SOURCE_INFERENCE_HOOKS, INFERENCE_HOOKS_TAG, lastUpdatedBy);
            }
            return;
        }

        AgenticUsers newUser = new AgenticUsers();
        newUser.setUserName(deviceLabel);
        newUser.setUserEmail(trimmedEmail);
        newUser.setDevices(Collections.singletonList(deviceLabel));
        newUser.setLastUpdatedAt(Context.now());
        newUser.setLastUpdatedBy(lastUpdatedBy);
        instance.insertOne(newUser);
        mergeDeviceTags(deviceLabel, DeviceTag.SOURCE_INFERENCE_HOOKS, INFERENCE_HOOKS_TAG, lastUpdatedBy);
    }

    /**
     * Generalizes findDeviceIdsByTeamsRolesAndDeviceIds to arbitrary tag keys: entries in
     * tagFilters AND together; within one key, any of its values match (OR).
     */
    public List<String> findDeviceIdsByTags(Map<String, List<String>> tagFilters, List<String> deviceIds) {
        List<Bson> conditions = new ArrayList<>();
        if (tagFilters != null) {
            for (Map.Entry<String, List<String>> entry : tagFilters.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) continue;
                List<String> values = entry.getValue().stream()
                        .filter(v -> v != null && !v.trim().isEmpty())
                        .map(v -> v.trim().toLowerCase())
                        .collect(Collectors.toList());
                if (values.isEmpty()) continue;
                conditions.add(Filters.elemMatch(AgenticUsers.DEVICE_TAGS, Filters.and(
                        Filters.eq(DeviceTag.KEY, entry.getKey().trim().toLowerCase()),
                        Filters.in(DeviceTag.VALUE, values))));
            }
        }
        boolean hasTagFilters = !conditions.isEmpty();
        boolean hasDeviceIds = deviceIds != null && !deviceIds.isEmpty();
        if (!hasTagFilters && !hasDeviceIds) {
            return new ArrayList<>();
        }

        // Device IDs come straight from a dropdown built off live module_info data at pick time —
        // trust them directly rather than re-deriving through a username/tag join.
        if (!hasTagFilters) {
            return new ArrayList<>(new HashSet<>(deviceIds));
        }

        // module_info is updated on every heartbeat, unlike AgenticUsers.devices which is only
        // ever backfilled once — resolve devices live instead of trusting the stored field.
        Map<String, Set<String>> liveDevicesByUsername = ModuleInfoDao.instance.fetchUsernameToDeviceIdsForEndpointShield();
        Bson userFilter = conditions.size() == 1 ? conditions.get(0) : Filters.and(conditions);
        Set<String> tagDeviceIds = new HashSet<>();
        for (AgenticUsers user : instance.findAll(userFilter)) {
            Set<String> liveDevices = liveDevicesByUsername.get(user.getUserName());
            if (liveDevices != null) {
                tagDeviceIds.addAll(liveDevices);
            }
        }

        if (!hasDeviceIds) {
            return new ArrayList<>(tagDeviceIds);
        }

        // Both dimensions given — a device must satisfy the tag match AND be explicitly picked.
        tagDeviceIds.retainAll(new HashSet<>(deviceIds));
        return new ArrayList<>(tagDeviceIds);
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
