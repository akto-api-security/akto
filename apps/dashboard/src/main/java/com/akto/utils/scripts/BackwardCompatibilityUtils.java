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
        com.mongodb.client.MongoCollection<org.bson.Document> col =
            ApiCollectionsDao.instance.getMCollection().withDocumentClass(org.bson.Document.class);

        List<org.bson.Document> oldOrphans = new ArrayList<>();
        col.find(Filters.regex(ApiCollection.HOST_NAME,
                java.util.regex.Pattern.quote(OLD_ORPHAN_SUFFIX) + "$"))
            .projection(Projections.include(Constants.ID, ApiCollection.HOST_NAME, "skills"))
            .into(oldOrphans);

        if (oldOrphans.isEmpty()) return;

        List<Integer> oldIdsToDelete = new ArrayList<>();

        for (org.bson.Document oldDoc : oldOrphans) {
            String oldHost = oldDoc.getString(ApiCollection.HOST_NAME);
            if (oldHost == null) continue;

            String device = oldHost.substring(0, oldHost.length() - OLD_ORPHAN_SUFFIX.length());
            String newHost = device + NEW_ORPHAN_SUFFIX;

            org.bson.Document newDoc = col.find(Filters.eq(ApiCollection.HOST_NAME, newHost))
                .projection(Projections.include(Constants.ID, ApiCollection.TAGS_STRING))
                .first();

            if (newDoc == null) continue;

            List<String> skills = oldDoc.getList("skills", String.class);
            if (skills != null && !skills.isEmpty()) {
                col.updateOne(
                    Filters.eq(Constants.ID, newDoc.getInteger(Constants.ID)),
                    Updates.addEachToSet("skills", skills)
                );
            }

            List<org.bson.Document> existingTags = newDoc.getList(ApiCollection.TAGS_STRING, org.bson.Document.class);
            boolean hasGenAiTag = existingTags != null && existingTags.stream()
                .anyMatch(t -> AKTO_GEN_AI_TAG.equals(t.getString("keyName")));

            if (!hasGenAiTag) {
                org.bson.Document genAiTag = new org.bson.Document("lastUpdatedTs", Context.now())
                    .append("keyName", AKTO_GEN_AI_TAG)
                    .append("value", "Gen AI")
                    .append("source", "KUBERNETES");
                col.updateOne(
                    Filters.eq(Constants.ID, newDoc.getInteger(Constants.ID)),
                    Updates.addToSet(ApiCollection.TAGS_STRING, genAiTag)
                );
            }

            oldIdsToDelete.add(oldDoc.getInteger(Constants.ID));
        }

        if (!oldIdsToDelete.isEmpty()) {
            col.deleteMany(Filters.in(Constants.ID, oldIdsToDelete));
        }
    }

}
