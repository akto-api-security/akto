package com.akto.utils.scripts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.akto.dao.AgentUsersDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.GuardrailPoliciesDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.dto.AgenticUsers;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.DeviceTag;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.dto.traffic.CollectionTags;
import com.akto.util.Constants;
import com.akto.util.Pair;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.List;
import java.util.stream.Collectors;

public class BackwardCompatibilityUtils {

    public static void moveUserDataFromModuleInfoToAgenticUsers() {
        List<ModuleInfo> moduleInfos = ModuleInfoDao.instance.findAll(Filters.eq(
            ModuleInfo.MODULE_TYPE, ModuleInfo.ModuleType.MCP_ENDPOINT_SHIELD
        ), Projections.include(
            ModuleInfo.NAME,
            ModuleInfo.ADDITIONAL_DATA
        ));

        Map<String,Set<String>> userNameToDevicesMap = new HashMap<>();
        Map<String,Pair<String,String>> userNameToRoleAndTeamMap = new HashMap<>();

        for (ModuleInfo moduleInfo : moduleInfos) {
            String name = moduleInfo.getName();
            String userName = "";
            if(name == null || name.isEmpty() || !name.contains("-") || moduleInfo.getAdditionalData() == null) continue;
            if(moduleInfo.getAdditionalData().containsKey("username")) {
                userName = (String) moduleInfo.getAdditionalData().get("username");
            }
            
            if(!userNameToDevicesMap.containsKey(userName)) {
                userNameToDevicesMap.put(userName, new HashSet<>());
            }
            if(moduleInfo.getAdditionalData().containsKey("deviceId")) {
                String deviceId = (String) moduleInfo.getAdditionalData().get("deviceId");
                userNameToDevicesMap.get(userName).add(deviceId);
            }

            if(moduleInfo.getAdditionalData().containsKey("userRole") && moduleInfo.getAdditionalData().containsKey("team")) {
                String userRole = (String) moduleInfo.getAdditionalData().get("userRole");
                String team = (String) moduleInfo.getAdditionalData().get("team");
                userNameToRoleAndTeamMap.put(userName, new Pair<>(userRole, team));
            }   
        }

        List<AgenticUsers> agenticUsers = new ArrayList<>();

        int now = Context.now();
        for (Map.Entry<String,Set<String>> entry : userNameToDevicesMap.entrySet()) {
            Set<String> devices = entry.getValue();
            Pair<String,String> roleAndTeam = userNameToRoleAndTeamMap.getOrDefault(entry.getKey(), new Pair<>("", ""));
            String userRole = roleAndTeam.getFirst();
            String team = roleAndTeam.getSecond();

            AgenticUsers agenticUser = new AgenticUsers();
            agenticUser.setUserName(entry.getKey());
            agenticUser.setDevices(new ArrayList<>(devices));
            agenticUser.setLastUpdatedAt(now);

            List<DeviceTag> tags = new ArrayList<>();
            if (team != null && !team.trim().isEmpty()) {
                tags.add(new DeviceTag("team", team.trim().toLowerCase(), "device", now, "migration"));
            }
            if (userRole != null && !userRole.trim().isEmpty()) {
                tags.add(new DeviceTag("role", userRole.trim().toLowerCase(), "device", now, "migration"));
            }
            agenticUser.setDeviceTags(tags);
            agenticUsers.add(agenticUser);
        }
        if(agenticUsers.isEmpty()) return;
        AgentUsersDao.instance.insertMany(agenticUsers);
    }

    /**
     * One-time, idempotent conversion of agent_users' old fixed teamName/userRole fields (pre
     * DeviceTag redesign) into deviceTags. Those fields no longer exist on AgenticUsers, so a
     * plain typed find() would silently drop them — this reads the collection as raw Documents
     * instead, exactly like scripts/migrate_team_role_to_device_tags.js (kept around as a manual
     * reference / one-off fallback), so old field values are still visible.
     *
     * Filters on legacy-field presence, not on deviceTags being empty: a user can already have
     * deviceTags from an unrelated source (e.g. Okta group sync racing this migration at startup)
     * while still carrying unconverted legacy team/role fields, and an empty-deviceTags filter
     * would silently skip them forever since this only ever runs once. For the same reason the
     * write merges into any existing deviceTags — keyed by (key, source) — instead of overwriting:
     * a legacy value is only appended where that (key, source) pair isn't already present, so a
     * fresher write for the same source (a live sync, or an edit through the new UI) is never
     * clobbered with a stale legacy one. Different sources for the same key (e.g. a manual "team"
     * and an Okta "team") simply coexist — there's no single "effective" value to resolve to.
     */
    public static void migrateTeamRoleToDeviceTags() {
        MongoCollection<Document> rawColl = MCollection.getMCollection(
                AgentUsersDao.instance.getDBName(), AgentUsersDao.instance.getCollName(), Document.class);

        Bson hasLegacyFields = Filters.or(
                Filters.exists("ssoTeamName", true),
                Filters.exists("ssoUserRole", true),
                Filters.exists("teamSource", true),
                Filters.exists("roleSource", true));

        int now = Context.now();
        for (Document doc : rawColl.find(hasLegacyFields)) {
            List<DeviceTag> legacyTags = new ArrayList<>();
            addLegacyDeviceTag(legacyTags, "team", doc.getString("ssoTeamName"), "okta", now);
            addLegacyDeviceTag(legacyTags, "role", doc.getString("ssoUserRole"), "okta", now);
            if ("manual".equals(doc.getString("teamSource"))) {
                addLegacyDeviceTag(legacyTags, "team", doc.getString("teamName"), DeviceTag.SOURCE_MANUAL, now);
            }
            if ("manual".equals(doc.getString("roleSource"))) {
                addLegacyDeviceTag(legacyTags, "role", doc.getString("userRole"), DeviceTag.SOURCE_MANUAL, now);
            }
            if (legacyTags.isEmpty()) continue;

            List<DeviceTag> existingTags = parseExistingDeviceTags(doc.getList(AgenticUsers.DEVICE_TAGS, Document.class));
            List<DeviceTag> mergedTags = mergeLegacyDeviceTags(existingTags, legacyTags);

            AgentUsersDao.instance.updateOne(
                    Filters.eq(Constants.ID, doc.get(Constants.ID)),
                    Updates.set(AgenticUsers.DEVICE_TAGS, mergedTags));
        }
    }

    private static void addLegacyDeviceTag(List<DeviceTag> tags, String key, String value, String source, int now) {
        if (value != null && !value.trim().isEmpty()) {
            tags.add(new DeviceTag(key, value.trim().toLowerCase(), source, now, "migration"));
        }
    }

    private static List<DeviceTag> parseExistingDeviceTags(List<Document> raw) {
        List<DeviceTag> tags = new ArrayList<>();
        if (raw == null) return tags;
        for (Document d : raw) {
            tags.add(new DeviceTag(
                    d.getString(DeviceTag.KEY),
                    d.getString(DeviceTag.VALUE),
                    d.getString(DeviceTag.SOURCE),
                    d.getInteger(DeviceTag.LAST_UPDATED_AT, 0),
                    d.getString(DeviceTag.LAST_UPDATED_BY)));
        }
        return tags;
    }

    /** Existing (key, source) pairs win — a legacy value is only appended where that pair is absent. */
    private static List<DeviceTag> mergeLegacyDeviceTags(List<DeviceTag> existing, List<DeviceTag> legacy) {
        Set<Pair<String, String>> seenKeySource = new HashSet<>();
        for (DeviceTag t : existing) {
            seenKeySource.add(new Pair<>(t.getKey(), t.getSource()));
        }
        List<DeviceTag> merged = new ArrayList<>(existing);
        for (DeviceTag t : legacy) {
            if (seenKeySource.add(new Pair<>(t.getKey(), t.getSource()))) {
                merged.add(t);
            }
        }
        return merged;
    }

    /**
     * One-time, idempotent conversion of guardrail_policies' old fixed targetTeams/targetRoles
     * fields into the generic targetTags model. Same raw-Document approach as
     * migrateTeamRoleToDeviceTags, for the same reason (those fields no longer exist on
     * GuardrailPolicies). Run after migrateTeamRoleToDeviceTags so the "team"/"role" keys line up.
     *
     * Filters on legacy-field presence, not on targetTags being empty, and merges rather than
     * overwrites — same reasoning as migrateTeamRoleToDeviceTags: a policy already edited through
     * the new UI before this migration runs could have targetTags set for "team"/"role" that must
     * not be clobbered or duplicated by the stale legacy value.
     */
    public static void migrateGuardrailTargetTeamsRolesToTags() {
        MongoCollection<Document> rawColl = MCollection.getMCollection(
                GuardrailPoliciesDao.instance.getDBName(), GuardrailPoliciesDao.instance.getCollName(), Document.class);

        Bson hasLegacyFields = Filters.or(
                Filters.exists("targetTeams", true),
                Filters.exists("targetRoles", true));

        for (Document doc : rawColl.find(hasLegacyFields)) {
            Map<String, List<String>> legacyTags = new HashMap<>();
            addLegacyTargetTagKey(legacyTags, "team", doc.getList("targetTeams", String.class));
            addLegacyTargetTagKey(legacyTags, "role", doc.getList("targetRoles", String.class));
            if (legacyTags.isEmpty()) continue;

            Map<String, List<String>> existingTags = parseExistingTargetTags(doc.get("targetTags", Document.class));
            boolean changed = false;
            for (Map.Entry<String, List<String>> entry : legacyTags.entrySet()) {
                if (!existingTags.containsKey(entry.getKey())) {
                    existingTags.put(entry.getKey(), entry.getValue());
                    changed = true;
                }
            }
            if (!changed) continue;

            GuardrailPoliciesDao.instance.updateOne(
                    Filters.eq(Constants.ID, doc.get(Constants.ID)),
                    Updates.set("targetTags", existingTags));
        }
    }

    private static Map<String, List<String>> parseExistingTargetTags(Document raw) {
        Map<String, List<String>> tags = new LinkedHashMap<>();
        if (raw == null) return tags;
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (entry.getValue() instanceof List) {
                tags.put(entry.getKey(), (List<String>) entry.getValue());
            }
        }
        return tags;
    }

    private static void addLegacyTargetTagKey(Map<String, List<String>> targetTags, String key, List<String> values) {
        if (values == null || values.isEmpty()) return;
        List<String> normalized = values.stream()
                .filter(v -> v != null && !v.trim().isEmpty())
                .map(v -> v.trim().toLowerCase())
                .collect(Collectors.toList());
        if (!normalized.isEmpty()) targetTags.put(key, normalized);
    }

    public static void cleanupApiInfoTags() {
        List<Integer> collIds = ApiCollectionsDao.instance.findAll(
            Filters.elemMatch(ApiCollection.TAGS_STRING,
                Filters.or(
                    Filters.and(Filters.eq(CollectionTags.KEY_NAME, Constants.AKTO_ENDPOINT_SOURCE_TAG), Filters.eq(CollectionTags.VALUE, Constants.AKTO_ENDPOINT_SOURCE_VALUE)),
                    Filters.and(Filters.eq(CollectionTags.KEY_NAME, Constants.AKTO_ENDPOINT_SOURCE_TAG), Filters.eq(CollectionTags.VALUE, "AGENTIC"))
                )
            ),
            Projections.include(Constants.ID)
        ).stream().map(ApiCollection::getId).collect(Collectors.toList());

        if (collIds.isEmpty()) return;

        Bson collFilter = Filters.in("_id.apiCollectionId", collIds);

        ApiInfoDao.instance.updateMany(
            Filters.and(collFilter, Filters.elemMatch(ApiInfo.TAGS_LIST, Filters.eq(CollectionTags.KEY_NAME, "user-agent"))),
            Updates.pull(ApiInfo.TAGS_LIST, Filters.eq(CollectionTags.KEY_NAME, "user-agent"))
        );

        ApiInfoDao.instance.updateMany(
            Filters.and(collFilter, Filters.elemMatch(ApiInfo.TAGS_LIST, Filters.eq(CollectionTags.KEY_NAME, "referer"))),
            Updates.pull(ApiInfo.TAGS_LIST, Filters.eq(CollectionTags.KEY_NAME, "referer"))
        );
    }

}
