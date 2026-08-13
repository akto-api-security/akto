package com.akto.utils.scripts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.akto.dao.AgentUsersDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.dto.AgenticUsers;
import com.akto.dto.ApiCollection;
import com.akto.dto.DeviceTag;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.dto.traffic.CollectionTags;
import com.akto.util.Constants;
import com.akto.util.Pair;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;

import static com.akto.util.Constants.AKTO_GEN_AI_TAG;

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

        for (Map.Entry<String,Set<String>> entry : userNameToDevicesMap.entrySet()) {
            Set<String> devices = entry.getValue();
            Pair<String,String> roleAndTeam = userNameToRoleAndTeamMap.getOrDefault(entry.getKey(), new Pair<>("", ""));
            String userRole = roleAndTeam.getFirst();
            String team = roleAndTeam.getSecond();

            AgenticUsers agenticUser = new AgenticUsers();
            agenticUser.setUserName(entry.getKey());
            agenticUser.setDevices(new ArrayList<>(devices));
            agenticUser.setUserRole(userRole);
            agenticUser.setTeamName(team);
            agenticUser.setLastUpdatedAt(Context.now());
            agenticUsers.add(agenticUser);
        }
        if(agenticUsers.isEmpty()) return;
        AgentUsersDao.instance.insertMany(agenticUsers);
    }

    public static void cleanupApiInfoTags() {
        List<Integer> collIds = ApiCollectionsDao.instance.findAll(
            Filters.elemMatch(ApiCollection.TAGS_STRING,
                Filters.or(
                    Filters.and(Filters.eq(CollectionTags.KEY_NAME, "source"), Filters.eq(CollectionTags.VALUE, "ENDPOINT")),
                    Filters.and(Filters.eq(CollectionTags.KEY_NAME, "source"), Filters.eq(CollectionTags.VALUE, "AGENTIC"))
                )
            ),
            Projections.include("_id")
        ).stream().map(ApiCollection::getId).collect(Collectors.toList());

        if (collIds.isEmpty()) return;

        Bson collFilter = Filters.in("_id.apiCollectionId", collIds);

        ApiInfoDao.instance.updateMany(
            Filters.and(collFilter, Filters.elemMatch("tagsList", Filters.eq(CollectionTags.KEY_NAME, "user-agent"))),
            Updates.pull("tagsList", Filters.eq(CollectionTags.KEY_NAME, "user-agent"))
        );

        ApiInfoDao.instance.updateMany(
            Filters.and(collFilter, Filters.elemMatch("tagsList", Filters.eq(CollectionTags.KEY_NAME, "referer"))),
            Updates.pull("tagsList", Filters.eq(CollectionTags.KEY_NAME, "referer"))
        );
    }

    private static final String OLD_ORPHAN_SUFFIX = ".skill.not-attached";
    private static final String NEW_ORPHAN_SUFFIX = ".ai-agent.not-attached";

    public static void migrateOrphanSkillCollections() {
        List<ApiCollection> oldOrphans = ApiCollectionsDao.instance.findAll(
            Filters.regex(ApiCollection.HOST_NAME, java.util.regex.Pattern.quote(OLD_ORPHAN_SUFFIX) + "$"),
            Projections.include(Constants.ID, ApiCollection.HOST_NAME, ApiCollection.SKILLS)
        );

        if (oldOrphans == null || oldOrphans.isEmpty()) return;

        List<Integer> oldIdsToDelete = new ArrayList<>();

        for (ApiCollection oldColl : oldOrphans) {
            String oldHost = oldColl.getHostName();
            if (oldHost == null) continue;

            String device = oldHost.substring(0, oldHost.length() - OLD_ORPHAN_SUFFIX.length());
            String newHost = device + NEW_ORPHAN_SUFFIX;

            ApiCollection newColl = ApiCollectionsDao.instance.findOne(
                Filters.eq(ApiCollection.HOST_NAME, newHost)
            );
            if (newColl == null) continue;

            List<String> skills = oldColl.getSkills();
            if (skills != null && !skills.isEmpty()) {
                ApiCollectionsDao.instance.updateOne(
                    Filters.eq(Constants.ID, newColl.getId()),
                    Updates.addEachToSet(ApiCollection.SKILLS, skills)
                );
            }

            boolean hasGenAiTag = newColl.getTagsList() != null && newColl.getTagsList().stream()
                .anyMatch(t -> AKTO_GEN_AI_TAG.equals(t.getKeyName()));

            if (!hasGenAiTag) {
                CollectionTags genAiTag = new CollectionTags(
                    Context.now(), AKTO_GEN_AI_TAG, "Gen AI", CollectionTags.TagSource.KUBERNETES
                );
                ApiCollectionsDao.instance.updateOne(
                    Filters.eq(Constants.ID, newColl.getId()),
                    Updates.addToSet(ApiCollection.TAGS_STRING, genAiTag)
                );
            }

            oldIdsToDelete.add(oldColl.getId());
        }

        if (!oldIdsToDelete.isEmpty()) {
            ApiCollectionsDao.instance.getMCollection().deleteMany(
                Filters.in(Constants.ID, oldIdsToDelete)
            );
        }
    }

    /**
     * One-time, idempotent conversion of agent_users' old teamName/userRole fields (written by
     * AgentUsersDao.upsertTag, the dashboard's manual-edit path) into deviceTags — the model the
     * new Okta sync cron (upsertDeviceTags) and any tags-aware UI actually read. teamName/userRole
     * aren't removed here, just mirrored, so this is safe to run even if some other caller still
     * writes them. Only adds a manual tag where one isn't already present for that key, so a
     * fresher manual edit or a synced value from another source is never clobbered or duplicated.
     */
    public static void migrateTeamRoleToDeviceTags() {
        List<AgenticUsers> users = AgentUsersDao.instance.findAll(Filters.or(
                Filters.exists(AgenticUsers.TEAM_NAME, true),
                Filters.exists(AgenticUsers.USER_ROLE, true)));

        int now = Context.now();
        for (AgenticUsers u : users) {
            String team = u.getTeamName();
            String role = u.getUserRole();
            boolean hasTeam = team != null && !team.trim().isEmpty();
            boolean hasRole = role != null && !role.trim().isEmpty();
            if (!hasTeam && !hasRole) continue;

            List<DeviceTag> existing = u.getDeviceTags() != null ? u.getDeviceTags() : new ArrayList<>();
            boolean hasManualTeamTag = existing.stream().anyMatch(t -> "team".equals(t.getKey()) && DeviceTag.SOURCE_MANUAL.equals(t.getSource()));
            boolean hasManualRoleTag = existing.stream().anyMatch(t -> "role".equals(t.getKey()) && DeviceTag.SOURCE_MANUAL.equals(t.getSource()));

            List<DeviceTag> merged = new ArrayList<>(existing);
            boolean changed = false;
            if (hasTeam && !hasManualTeamTag) {
                merged.add(new DeviceTag("team", team.trim().toLowerCase(), DeviceTag.SOURCE_MANUAL, now, "migration"));
                changed = true;
            }
            if (hasRole && !hasManualRoleTag) {
                merged.add(new DeviceTag("role", role.trim().toLowerCase(), DeviceTag.SOURCE_MANUAL, now, "migration"));
                changed = true;
            }
            if (!changed) continue;

            AgentUsersDao.instance.updateOne(
                    Filters.eq(AgenticUsers.USER_NAME, u.getUserName()),
                    Updates.set(AgenticUsers.DEVICE_TAGS, merged));
        }
    }

}
