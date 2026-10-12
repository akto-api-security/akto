package com.akto.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.conversions.Bson;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.ActivitiesDao;
import com.akto.dao.AktoDataTypeDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.CustomDataTypeDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiInfo;
import com.akto.dto.AktoDataType;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.CustomDataType;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.RiskScoreTestingEndpoints;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.LastCronRunInfo;
import com.akto.util.enums.GlobalEnums.TestRunIssueStatus;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateManyModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;

import static com.akto.utils.Utils.calculateRiskValueForSeverity;
import static com.akto.jobs.utils.Utils.getRiskScoreValueFromSeverityScore;
public class RiskScoreOfCollections {
    private static final LoggerMaker loggerMaker = new LoggerMaker(RiskScoreOfCollections.class, LogDb.DASHBOARD);
    private static final Bson API_INFO_EXCLUDE_PROJECTION = Projections.exclude(ApiInfo.ALL_AUTH_TYPES_FOUND);
    private static final int QUERY_CHUNK_SIZE = 1000;
    // the sensitive cron looks back this far before its last run, so STIs written while it was running are not missed
    private static final int SENSITIVE_SYNC_BUFFER_SECONDS = 5 * 60;
    private static final String ISSUE_API_INFO_KEY = "_id.apiInfoKey";
    private static final List<String> ALL_ISSUE_STATUSES = Arrays.stream(TestRunIssueStatus.values())
            .map(Enum::name).collect(Collectors.toList());

    private static void addSeverityScore(Map<ApiInfoKey, Float> severityScoreMap, ApiInfoKey apiInfoKey, String severity, int issueCount){
        if (apiInfoKey == null || severity == null) {
            return;
        }
        severityScoreMap.merge(apiInfoKey, calculateRiskValueForSeverity(severity) * issueCount, Float::sum);
    }

    private static Bson issueApiInfoKeysFilter(Collection<ApiInfoKey> apiInfoKeys){
        List<Bson> filters = new ArrayList<>();
        for (ApiInfoKey apiInfoKey : apiInfoKeys) {
            filters.add(Filters.and(
                Filters.eq(ISSUE_API_INFO_KEY + "." + ApiInfoKey.API_COLLECTION_ID, apiInfoKey.getApiCollectionId()),
                Filters.eq(ISSUE_API_INFO_KEY + "." + ApiInfoKey.URL, apiInfoKey.getUrl()),
                Filters.eq(ISSUE_API_INFO_KEY + "." + ApiInfoKey.METHOD, apiInfoKey.getMethod().name())
            ));
        }
        return Filters.or(filters);
    }

    private static Map<ApiInfoKey, ApiInfo> fetchApiInfos(Collection<ApiInfoKey> apiInfoKeys){
        Map<ApiInfoKey, ApiInfo> apiInfos = new HashMap<>();
        try (MongoCursor<ApiInfo> cursor = ApiInfoDao.instance.getMCollection()
                .find(ApiInfoDao.getFilter(apiInfoKeys)).projection(API_INFO_EXCLUDE_PROJECTION).cursor()) {
            while (cursor.hasNext()) {
                ApiInfo apiInfo = cursor.next();
                apiInfos.put(apiInfo.getId(), apiInfo);
            }
        }
        return apiInfos;
    }

    private static ApiInfoKey apiInfoKeyFromGroupId(BasicDBObject id){
        return new ApiInfoKey(id.getInt(ApiInfoKey.API_COLLECTION_ID), id.getString(ApiInfoKey.URL),
            URLMethods.Method.fromString(id.getString(ApiInfoKey.METHOD)));
    }

    // keys of apis having an issue seen/updated since the timestamp; each time field has its own {status, field} index
    private static Set<ApiInfoKey> fetchApiInfoKeysOfUpdatedIssues(int timeStampFilter){
        Set<ApiInfoKey> apiInfoKeys = new HashSet<>();
        for (String timeField : Arrays.asList(TestingRunIssues.LAST_SEEN, TestingRunIssues.LAST_UPDATED)) {
            Bson filter = Filters.and(
                Filters.in(TestingRunIssues.TEST_RUN_ISSUES_STATUS, ALL_ISSUE_STATUSES),
                Filters.gte(timeField, timeStampFilter)
            );
            try (MongoCursor<TestingRunIssues> cursor = TestingRunIssuesDao.instance.getMCollection()
                    .find(filter).projection(Projections.include("_id")).cursor()) {
                while (cursor.hasNext()) {
                    apiInfoKeys.add(cursor.next().getId().getApiInfoKey());
                }
            }
        }
        return apiInfoKeys;
    }

    // severity score of every api with open issues, summed inside mongo so no issue document reaches the JVM
    private Map<ApiInfoKey, Float> getSeverityScoreMapOfAllOpenIssues(){
        Map<ApiInfoKey, Float> severityScoreMap = new HashMap<>();
        List<Bson> pipeline = Arrays.asList(
            Aggregates.match(Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestRunIssueStatus.OPEN.name())),
            Aggregates.group(
                new BasicDBObject("apiInfoKey", "$" + ISSUE_API_INFO_KEY).append(TestingRunIssues.KEY_SEVERITY, "$" + TestingRunIssues.KEY_SEVERITY),
                Accumulators.sum("count", 1))
        );

        try (MongoCursor<BasicDBObject> cursor = TestingRunIssuesDao.instance.getMCollection()
                .aggregate(pipeline, BasicDBObject.class).allowDiskUse(true).cursor()) {
            while (cursor.hasNext()) {
                try {
                    BasicDBObject group = cursor.next();
                    BasicDBObject id = (BasicDBObject) group.get("_id");
                    addSeverityScore(severityScoreMap, apiInfoKeyFromGroupId((BasicDBObject) id.get("apiInfoKey")),
                        id.getString(TestingRunIssues.KEY_SEVERITY), group.getInt("count"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return severityScoreMap;
    }

    private Map<ApiInfoKey, Float> getUpdatedApiInfosMap(int timeStampFilter){
        try {
            if (timeStampFilter <= 0) {
                return getSeverityScoreMapOfAllOpenIssues();
            }

            // get apiinfokeys of freshly updated testing run issues only
            List<ApiInfoKey> updatedApiInfoKeys = new ArrayList<>(fetchApiInfoKeysOfUpdatedIssues(timeStampFilter));
            if(updatedApiInfoKeys.isEmpty()){
                return new HashMap<>();
            }

            loggerMaker.debugAndAddToDb("Updating severity score for " + updatedApiInfoKeys.size() + " apis at timestamp " + Context.now() , LogDb.DASHBOARD);

            // apis whose issues all got closed have no open issue below, but their score still needs to go down to 0
            Map<ApiInfoKey, Float> severityScoreMap = new HashMap<>();
            for (ApiInfoKey apiInfoKey : updatedApiInfoKeys) {
                severityScoreMap.put(apiInfoKey, 0f);
            }

            for (int i = 0; i < updatedApiInfoKeys.size(); i += QUERY_CHUNK_SIZE) {
                List<ApiInfoKey> chunk = updatedApiInfoKeys.subList(i, Math.min(i + QUERY_CHUNK_SIZE, updatedApiInfoKeys.size()));
                Bson filterQ = Filters.and(
                    Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestRunIssueStatus.OPEN.name()),
                    issueApiInfoKeysFilter(chunk)
                );
                try (MongoCursor<TestingRunIssues> cursor = TestingRunIssuesDao.instance.getMCollection().find(filterQ)
                        .projection(Projections.include("_id", TestingRunIssues.KEY_SEVERITY)).cursor()) {
                    while (cursor.hasNext()) {
                        TestingRunIssues issue = cursor.next();
                        addSeverityScore(severityScoreMap, issue.getId().getApiInfoKey(),
                            issue.getSeverity() == null ? null : issue.getSeverity().toString(), 1);
                    }
                }
            }
            return severityScoreMap;
        } catch (Exception e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    private static boolean checkForSensitive(List<String> subTypes){
        boolean isSensitive = false;
        Map<String, AktoDataType> aktoDataTypeMap = SingleTypeInfo.getAktoDataTypeMap(Context.accountId.get());
        Map<String, CustomDataType> customDataTypeMap = SingleTypeInfo.getCustomDataTypeMap(Context.accountId.get());

        for(String subTypeName : subTypes){
            if(aktoDataTypeMap.containsKey(subTypeName)){
                AktoDataType dataType = aktoDataTypeMap.get(subTypeName);
                if(dataType.getSensitiveAlways() || dataType.getSensitivePosition().contains(SingleTypeInfo.Position.RESPONSE_HEADER) || dataType.getSensitivePosition().contains(SingleTypeInfo.Position.RESPONSE_PAYLOAD)){
                    isSensitive = true;
                    break;
                }
            }else if(customDataTypeMap.containsKey(subTypeName)){
                CustomDataType dataType = customDataTypeMap.get(subTypeName);
                if(dataType.isSensitiveAlways() || dataType.getSensitivePosition().contains(SingleTypeInfo.Position.RESPONSE_HEADER) || dataType.getSensitivePosition().contains(SingleTypeInfo.Position.RESPONSE_PAYLOAD)){
                    isSensitive = true;
                    break;
                }
            }
        }

        return isSensitive;
    }
    
    public void updateSeverityScoreInApiInfo(int timeStampFilter){
        int startTime = Context.now();

        RiskScoreTestingEndpointsUtils riskScoreTestingEndpointsUtils = new RiskScoreTestingEndpointsUtils();

        Map<ApiInfoKey, Float> severityScoreMap = getUpdatedApiInfosMap(timeStampFilter);
        int severityScoreMapSize = severityScoreMap != null ? severityScoreMap.size() : 0;
        int riskScoreGroupUpdateCount = 0;
        int apiInfoNotFoundCount = 0;
        int bulkUpdatesSize = 0;
        int bulkWriteModifiedCount = 0;

        final McpApiRiskScoreUtils.McpRiskScoreContext mcpRiskScoreContext =
                (severityScoreMap != null && !severityScoreMap.isEmpty())
                        ? McpApiRiskScoreUtils.buildMcpRiskScoreContext()
                        : null;
        // after getting the severityScoreMap, we write that in DB
        if(severityScoreMap != null){
            List<ApiInfoKey> apiInfoKeys = new ArrayList<>(severityScoreMap.keySet());
            for (int i = 0; i < apiInfoKeys.size(); i += QUERY_CHUNK_SIZE) {
                List<ApiInfoKey> chunk = apiInfoKeys.subList(i, Math.min(i + QUERY_CHUNK_SIZE, apiInfoKeys.size()));
                Map<ApiInfoKey, ApiInfo> apiInfos = fetchApiInfos(chunk);
                ArrayList<WriteModel<ApiInfo>> bulkUpdatesForApiInfo = new ArrayList<>();

                for (ApiInfoKey apiInfoKey : chunk) {
                    ApiInfo apiInfo = apiInfos.get(apiInfoKey);
                    if (apiInfo == null) {
                        apiInfoNotFoundCount++;
                        continue;
                    }

                    float severityScore = severityScoreMap.get(apiInfoKey);
                    float severityRiskPart = getRiskScoreValueFromSeverityScore(severityScore);
                    float baseScore = ApiInfoDao.getRiskScore(apiInfo, apiInfo.getIsSensitive(), severityRiskPart);
                    float riskScore = mcpRiskScoreContext == null
                            ? baseScore
                            : McpApiRiskScoreUtils.getRiskScoreWithMcpAdjustments(apiInfo, baseScore, mcpRiskScoreContext);

                    if (apiInfo.getRiskScore() != riskScore) {
                        riskScoreTestingEndpointsUtils.updateApiRiskScoreGroup(apiInfo, riskScore);
                        riskScoreGroupUpdateCount++;
                    }

                    Bson update = Updates.combine(
                        Updates.set(ApiInfo.SEVERITY_SCORE, severityScore),
                        Updates.set(ApiInfo.RISK_SCORE, riskScore)
                    );
                    bulkUpdatesForApiInfo.add(new UpdateManyModel<>(ApiInfoDao.getFilter(apiInfoKey), update, new UpdateOptions()));
                }

                if (!bulkUpdatesForApiInfo.isEmpty()) {
                    bulkUpdatesSize += bulkUpdatesForApiInfo.size();
                    bulkWriteModifiedCount += ApiInfoDao.instance.getMCollection().bulkWrite(bulkUpdatesForApiInfo, new BulkWriteOptions().ordered(false)).getModifiedCount();
                }
            }
        }

        riskScoreTestingEndpointsUtils.syncRiskScoreGroupApis();

        // Log activity for account 1763355072
        if (Context.accountId.get() == 1763355072) {
            int endTime = Context.now();
            int duration = endTime - startTime;
            String message = String.format("Incremental update | severityScoreMap size: %d | riskScoreGroup updates: %d | apiInfo not found: %d | bulkUpdates prepared: %d | bulkWrite modified: %d | timeStampFilter: %d (window: %d sec) | duration: %d sec",
                severityScoreMapSize, riskScoreGroupUpdateCount, apiInfoNotFoundCount, bulkUpdatesSize, bulkWriteModifiedCount, timeStampFilter, (startTime - timeStampFilter), duration);
            ActivitiesDao.instance.insertActivity("Severity score incremental update", message);
        }
    }

    // builds the mcp risk score context (collection scans) only once, and only if there is something to write
    private static final class LazyMcpRiskScoreContext {
        private McpApiRiskScoreUtils.McpRiskScoreContext context;

        McpApiRiskScoreUtils.McpRiskScoreContext get() {
            if (context == null) {
                context = McpApiRiskScoreUtils.buildMcpRiskScoreContext();
            }
            return context;
        }
    }

    private static Bson stiApiInfoKeysFilter(Collection<ApiInfoKey> apiInfoKeys){
        List<Bson> filters = new ArrayList<>();
        for (ApiInfoKey apiInfoKey : apiInfoKeys) {
            filters.add(Filters.and(
                Filters.eq(SingleTypeInfo._API_COLLECTION_ID, apiInfoKey.getApiCollectionId()),
                Filters.eq(SingleTypeInfo._URL, apiInfoKey.getUrl()),
                Filters.eq(SingleTypeInfo._METHOD, apiInfoKey.getMethod().name())
            ));
        }
        return Filters.or(filters);
    }

    private static BasicDBObject stiApiInfoKeyGroupId(){
        return new BasicDBObject(ApiInfoKey.API_COLLECTION_ID, "$" + SingleTypeInfo._API_COLLECTION_ID)
                .append(ApiInfoKey.URL, "$" + SingleTypeInfo._URL)
                .append(ApiInfoKey.METHOD, "$" + SingleTypeInfo._METHOD);
    }

    private static List<String> sensitiveSubTypeNamesInResponse(){
        List<String> subTypes = SingleTypeInfoDao.instance.sensitiveSubTypeInResponseNames();
        subTypes.addAll(SingleTypeInfoDao.instance.sensitiveSubTypeNames());
        return subTypes;
    }

    private static List<String> allDataTypeNames(){
        List<String> names = new ArrayList<>();
        for (AktoDataType dataType : AktoDataTypeDao.instance.findAll(Filters.empty())) {
            names.add(dataType.getName());
        }
        for (CustomDataType dataType : CustomDataTypeDao.instance.findAll(Filters.empty())) {
            names.add(dataType.getName());
        }
        return names;
    }

    private static void writeSensitiveInfoInApiInfo(Map<ApiInfoKey, List<String>> subTypesByApi, boolean onlyMarkSensitive, LazyMcpRiskScoreContext mcpRiskScoreContext){
        if (subTypesByApi.isEmpty()) {
            return;
        }
        Map<ApiInfoKey, ApiInfo> apiInfos = fetchApiInfos(subTypesByApi.keySet());
        ArrayList<WriteModel<ApiInfo>> bulkUpdatesForApiInfo = new ArrayList<>();

        for (Map.Entry<ApiInfoKey, List<String>> entry : subTypesByApi.entrySet()) {
            ApiInfo apiInfo = apiInfos.get(entry.getKey());
            if (apiInfo == null) {
                continue;
            }
            boolean isSensitive = checkForSensitive(entry.getValue());
            // a time bounded pass only sees recently added STIs, so it can prove an api became sensitive but never that it stopped being so
            if (onlyMarkSensitive && (!isSensitive || apiInfo.getIsSensitive())) {
                continue;
            }

            McpApiRiskScoreUtils.McpRiskScoreContext context = mcpRiskScoreContext.get();
            float baseScore = ApiInfoDao.getRiskScore(apiInfo, isSensitive, getRiskScoreValueFromSeverityScore(apiInfo.getSeverityScore()));
            float riskScore = context.isEmpty()
                    ? baseScore
                    : McpApiRiskScoreUtils.getRiskScoreWithMcpAdjustments(apiInfo, baseScore, context);
            Bson update = Updates.combine(
                Updates.set(ApiInfo.IS_SENSITIVE, isSensitive),
                Updates.set(ApiInfo.RISK_SCORE, riskScore)
            );
            bulkUpdatesForApiInfo.add(new UpdateManyModel<>(ApiInfoDao.getFilter(entry.getKey()), update, new UpdateOptions()));
        }

        if (!bulkUpdatesForApiInfo.isEmpty()) {
            ApiInfoDao.instance.getMCollection().bulkWrite(bulkUpdatesForApiInfo, new BulkWriteOptions().ordered(false));
        }
    }

    // groups the matching STIs per api and writes sensitivity in chunks, so neither STIs nor writes pile up in memory
    private static void updateSensitiveInfoFromStis(Bson stiFilter, boolean onlyMarkSensitive, LazyMcpRiskScoreContext mcpRiskScoreContext){
        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(stiFilter));
        pipeline.add(Aggregates.group(stiApiInfoKeyGroupId(), Accumulators.addToSet("subTypes", "$" + SingleTypeInfo.SUB_TYPE)));

        Map<ApiInfoKey, List<String>> subTypesByApi = new HashMap<>();
        try (MongoCursor<BasicDBObject> stiCursor = SingleTypeInfoDao.instance.getMCollection()
                .aggregate(pipeline, BasicDBObject.class).allowDiskUse(true).cursor()) {
            while(stiCursor.hasNext()){
                try {
                    BasicDBObject group = stiCursor.next();
                    subTypesByApi.put(apiInfoKeyFromGroupId((BasicDBObject) group.get("_id")), (List<String>) group.get("subTypes"));
                    if (subTypesByApi.size() >= QUERY_CHUNK_SIZE) {
                        Map<ApiInfoKey, List<String>> chunk = new HashMap<>(subTypesByApi);
                        subTypesByApi.clear();
                        writeSensitiveInfoInApiInfo(chunk, onlyMarkSensitive, mcpRiskScoreContext);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        writeSensitiveInfoInApiInfo(subTypesByApi, onlyMarkSensitive, mcpRiskScoreContext);
    }

    // data types edited since the timestamp can make already mapped apis flip either way: re-evaluate just the apis carrying them
    private static void reevaluateApisOfModifiedDataTypes(int modifiedSince, LazyMcpRiskScoreContext mcpRiskScoreContext){
        // both AktoDataType and CustomDataType keep their last edit time in "timestamp"
        Bson modifiedFilter = Filters.gte(CustomDataType.TIMESTAMP, modifiedSince);
        Set<String> modifiedDataTypes = new HashSet<>();
        for (AktoDataType dataType : AktoDataTypeDao.instance.findAll(modifiedFilter)) {
            modifiedDataTypes.add(dataType.getName());
        }
        for (CustomDataType dataType : CustomDataTypeDao.instance.findAll(modifiedFilter)) {
            modifiedDataTypes.add(dataType.getName());
        }
        if (modifiedDataTypes.isEmpty()) {
            return;
        }

        List<String> allDataTypeNames = allDataTypeNames();
        Bson responseStiFilter = Filters.gt(SingleTypeInfo._RESPONSE_CODE, -1);

        List<ApiInfoKey> apiInfoKeys = new ArrayList<>();
        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(Filters.and(Filters.in(SingleTypeInfo.SUB_TYPE, modifiedDataTypes), responseStiFilter)));
        pipeline.add(Aggregates.group(stiApiInfoKeyGroupId()));
        try (MongoCursor<BasicDBObject> cursor = SingleTypeInfoDao.instance.getMCollection()
                .aggregate(pipeline, BasicDBObject.class).allowDiskUse(true).cursor()) {
            while (cursor.hasNext()) {
                try {
                    apiInfoKeys.add(apiInfoKeyFromGroupId((BasicDBObject) cursor.next().get("_id")));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        for (int i = 0; i < apiInfoKeys.size(); i += QUERY_CHUNK_SIZE) {
            List<ApiInfoKey> chunk = apiInfoKeys.subList(i, Math.min(i + QUERY_CHUNK_SIZE, apiInfoKeys.size()));
            Bson stiFilter = Filters.and(
                stiApiInfoKeysFilter(chunk),
                Filters.in(SingleTypeInfo.SUB_TYPE, allDataTypeNames),
                responseStiFilter
            );
            updateSensitiveInfoFromStis(stiFilter, false, mcpRiskScoreContext);
        }
    }

    /**
     * @param lastSyncedTs when the sensitive map was last updated, 0 if never. Only STIs created after it are looked at,
     *                     plus the apis of data types edited after it.
     */
    public void mapSensitiveSTIsInApiInfo(int lastSyncedTs){
        int startTs = Context.now();
        LazyMcpRiskScoreContext mcpRiskScoreContext = new LazyMcpRiskScoreContext();

        int stiTimeStampFilter = lastSyncedTs == 0 ? 0 : lastSyncedTs - SENSITIVE_SYNC_BUFFER_SECONDS;
        List<String> sensitiveSubTypes = sensitiveSubTypeNamesInResponse();
        if (!sensitiveSubTypes.isEmpty()) {
            Bson stiFilter = Filters.and(
                Filters.in(SingleTypeInfo.SUB_TYPE, sensitiveSubTypes),
                Filters.gt(SingleTypeInfo._RESPONSE_CODE, -1),
                Filters.gte(SingleTypeInfo._TIMESTAMP, stiTimeStampFilter)
            );
            updateSensitiveInfoFromStis(stiFilter, true, mcpRiskScoreContext);
        }

        if (lastSyncedTs != 0) {
            reevaluateApisOfModifiedDataTypes(lastSyncedTs, mcpRiskScoreContext);
        }

        AccountSettingsDao.instance.getMCollection().updateOne(
            AccountSettingsDao.generateFilter(),
            Updates.set((AccountSettings.LAST_UPDATED_CRON_INFO + "."+ LastCronRunInfo.LAST_UPDATED_SENSITIVE_MAP), startTs),
            new UpdateOptions().upsert(true)
        );
    }

    public void calculateRiskScoreForAllApis() {
        int startTime = Context.now();
        int timeStamp = startTime - 24*60*60;
        int limit = 1000;
        int count = 0; 

        List<WriteModel<ApiInfo>> bulkUpdates = new ArrayList<>();
        Bson filter = Filters.or(
            Filters.exists(ApiInfo.LAST_CALCULATED_TIME, false),
            Filters.lte(ApiInfo.LAST_CALCULATED_TIME, timeStamp)
        );
        Bson projection = Projections.include("_id", ApiInfo.API_ACCESS_TYPES, ApiInfo.LAST_SEEN, ApiInfo.SEVERITY_SCORE, ApiInfo.IS_SENSITIVE, ApiInfo.COLLECTION_IDS, ApiInfo.RISK_SCORE, ApiInfo.PARENT_MCP_TOOL_NAMES, ApiInfo.THREAT_SCORE, ApiInfo.DISCOVERED_TIMESTAMP);

        RiskScoreTestingEndpointsUtils riskScoreTestingEndpointsUtils = new RiskScoreTestingEndpointsUtils();

        // create a set for severityScore
        Map<ApiInfoKey, Float> initialSeverityScoreMap = getUpdatedApiInfosMap(0);
        int initialSeverityScoreMapSize = initialSeverityScoreMap != null ? initialSeverityScoreMap.size() : 0;
        int totalApisProcessed = 0;
        int riskScoreGroupUpdates = 0;
        int totalBulkWrites = 0;
        int totalModifiedCount = 0;

        McpApiRiskScoreUtils.McpRiskScoreContext mcpRiskScoreContext = McpApiRiskScoreUtils.buildMcpRiskScoreContext();
        while(count < 100){
            List<ApiInfo> apiInfos = ApiInfoDao.instance.getMCollection().find(filter).sort(Sorts.descending(ApiInfo.LAST_CALCULATED_TIME)).limit(limit).projection(projection).into(new ArrayList<>());
            for(ApiInfo apiInfo: apiInfos){
                float severityRiskPart = getRiskScoreValueFromSeverityScore(
                        initialSeverityScoreMap.getOrDefault(apiInfo.getId(), (float) 0));
                float baseScore = ApiInfoDao.getRiskScore(apiInfo, apiInfo.getIsSensitive(), severityRiskPart);
                float riskScore = mcpRiskScoreContext.isEmpty()
                        ? baseScore
                        : McpApiRiskScoreUtils.getRiskScoreWithMcpAdjustments(apiInfo, baseScore, mcpRiskScoreContext);
                Bson update = Updates.combine(
                    Updates.set(ApiInfo.RISK_SCORE, riskScore),
                    Updates.set(ApiInfo.LAST_CALCULATED_TIME, Context.now())
                );
                Bson filterQ = ApiInfoDao.getFilter(apiInfo.getId());
                
                bulkUpdates.add(new UpdateManyModel<>(filterQ, update, new UpdateOptions().upsert(false)));
                
                List<Integer> collectionIds = apiInfo.getCollectionIds();
                float oldRiskScore = apiInfo.getRiskScore();
                RiskScoreTestingEndpoints.RiskScoreGroupType oldRiskScoreGroupType = RiskScoreTestingEndpoints.calculateRiskScoreGroup(oldRiskScore);
                int oldRiskScoreGroupCollectionId = RiskScoreTestingEndpoints.getApiCollectionId(oldRiskScoreGroupType);

                if (!collectionIds.contains(oldRiskScoreGroupCollectionId)) {
                    // Add API to risk score API group if it is not already added
                    riskScoreTestingEndpointsUtils.updateApiRiskScoreGroup(apiInfo, riskScore);
                    riskScoreGroupUpdates++;
                } else if (oldRiskScore != riskScore) {
                    // Update API in risk score API group if risk score has changed
                    riskScoreTestingEndpointsUtils.updateApiRiskScoreGroup(apiInfo, riskScore);
                    riskScoreGroupUpdates++;
                }
                totalApisProcessed++;
            }
            if(bulkUpdates.size() > 0){
                totalBulkWrites++;
                totalModifiedCount += ApiInfoDao.instance.bulkWrite(bulkUpdates, new BulkWriteOptions().ordered(false)).getModifiedCount();
            }
            bulkUpdates.clear();
            count++;
            if(apiInfos.size() < limit){
                break;
            }
        }

        // Log activity for account 1763355072
        if (Context.accountId.get() == 1763355072) {
            int endTime = Context.now();
            int duration = endTime - startTime;
            String message = String.format("Full reset | initialSeverityScoreMap size: %d | APIs processed: %d | riskScoreGroup updates: %d | bulkWrites executed: %d | total modified: %d | batches: %d | timeStamp filter: %d | duration: %d sec",
                initialSeverityScoreMapSize, totalApisProcessed, riskScoreGroupUpdates, totalBulkWrites, totalModifiedCount, count, timeStamp, duration);
            ActivitiesDao.instance.insertActivity("Risk score calculation", message);
        }

        riskScoreTestingEndpointsUtils.syncRiskScoreGroupApis();
    }
}