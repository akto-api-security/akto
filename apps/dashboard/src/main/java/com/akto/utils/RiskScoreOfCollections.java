package com.akto.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;

import org.bson.conversions.Bson;

import com.akto.action.ApiCollectionsAction;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.AktoDataTypeDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.CustomDataTypeDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiInfo;
import com.akto.dto.AktoDataType;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.CustomDataType;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.RiskScoreTestingEndpoints;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.LastCronRunInfo;
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
import com.opensymphony.xwork2.Action;

import static com.akto.utils.Utils.calculateRiskValueForSeverity;
public class RiskScoreOfCollections {
    private static final LoggerMaker loggerMaker = new LoggerMaker(RiskScoreOfCollections.class);

    private List<ApiInfoKey> getUpdatedApiInfos(int timeStampFilter){
        List<ApiInfoKey> updatedApiInfoKeys = new ArrayList<>();

        // get freshly updated testing run issues here
        Bson projections = Projections.include("_id", TestingRunIssues.LAST_SEEN, TestingRunIssues.LAST_UPDATED);
        Bson filters = Filters.or(Filters.gte(TestingRunIssues.LAST_SEEN, timeStampFilter), 
            Filters.gte(TestingRunIssues.LAST_UPDATED, timeStampFilter));

        List<TestingRunIssues> issues = new ArrayList<>();
        try {
            issues = TestingRunIssuesDao.instance.findAll(filters, projections);   
        } catch (Exception e) {
            e.printStackTrace();
        }

        // after getting issues, get updated apiinfokeys related to that issues only
        if(issues == null || issues.size() == 0){
            return updatedApiInfoKeys;
        }
        for(TestingRunIssues issue: issues){
            TestingIssuesId issueId = issue.getId();
            ApiInfoKey apiInfoKey = issueId.getApiInfoKey();
            updatedApiInfoKeys.add(apiInfoKey);
        }

        return updatedApiInfoKeys;
    }

    private static void updateRiskScoreInCollections(Map<Integer,Float> collectionRiskScoreMap){
        if(!collectionRiskScoreMap.isEmpty()){
            List<Integer> apiCollectionIds = new ArrayList<>(collectionRiskScoreMap.keySet());
            List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(
                Filters.in(ApiCollection.ID, apiCollectionIds),Projections.include(ApiCollection.ID, ApiCollection.RISK_SCORE)
            );

            ArrayList<WriteModel<ApiCollection>> bulkUpdatesForApiCollections = new ArrayList<>();

            for(ApiCollection collection: apiCollections){
                float riskScore = Math.max(collection.getRiskScore(), collectionRiskScoreMap.get(collection.getId()));
                bulkUpdatesForApiCollections.add(new UpdateManyModel<>(
                    Filters.eq(ApiCollection.ID, collection.getId()), 
                    Updates.set(ApiCollection.RISK_SCORE, riskScore),
                    new UpdateOptions().upsert(false)));
            }

            ApiCollectionsDao.instance.getMCollection().bulkWrite(bulkUpdatesForApiCollections,  new BulkWriteOptions().ordered(false));

        }
    }

    private static void fillRiskScoreInCollections(Map<Integer,Double> collectionRiskScoreMap){
        ArrayList<WriteModel<ApiCollection>> bulkUpdatesForApiCollections = new ArrayList<>();
        for(Integer collectionId: collectionRiskScoreMap.keySet()){
            bulkUpdatesForApiCollections.add(new UpdateManyModel<>(
                    Filters.eq(ApiCollection.ID, collectionId), 
                    Updates.set(ApiCollection.RISK_SCORE, collectionRiskScoreMap.get(collectionId)),
                    new UpdateOptions().upsert(false)));
        }
        ApiCollectionsDao.instance.getMCollection().bulkWrite(bulkUpdatesForApiCollections,  new BulkWriteOptions().ordered(false));
        
    }

    private Map<ApiInfoKey, Float> getSeverityScoreMap(List<TestingRunIssues> issues){
        // Method to calculate severity Score for the apiInfo on the basis of HIGH, LOW, MEDIUM
        Map<ApiInfoKey, Float> severityScoreMap = new HashMap<>();

        for(TestingRunIssues issue: issues){
            String severity = issue.getSeverity().toString();
            float severityScore = (float) calculateRiskValueForSeverity(severity);
            ApiInfoKey apiInfoKey = issue.getId().getApiInfoKey();
            if(severityScoreMap.isEmpty() || !severityScoreMap.containsKey(apiInfoKey)){
                severityScoreMap.put(apiInfoKey, severityScore);
            }else{
                float prev = severityScoreMap.get(apiInfoKey);
                severityScore += prev;
                severityScoreMap.put(apiInfoKey, severityScore);
            }
        }

        return severityScoreMap;
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
        ArrayList<WriteModel<ApiInfo>> bulkUpdatesForApiInfo = new ArrayList<>();
        List<ApiInfoKey> updatedApiInfoKeys = getUpdatedApiInfos(timeStampFilter) ;

        if(updatedApiInfoKeys == null || updatedApiInfoKeys.size() == 0){
            return ;
        }

        loggerMaker.infoAndAddToDb("Updating severity score for " + updatedApiInfoKeys.size() + " apis at timestamp " + Context.now() , LogDb.DASHBOARD);

        Bson filterQ = Filters.and(
            Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, "OPEN"),
            Filters.in("_id.apiInfoKey", updatedApiInfoKeys)
        );

        List<TestingRunIssues> updatedIssues = new ArrayList<>();
        try {
            updatedIssues = TestingRunIssuesDao.instance.findAll(filterQ);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if(updatedIssues == null || updatedIssues.size() == 0){
            return ;
        }

        Map<ApiInfoKey, Float> severityScoreMap = getSeverityScoreMap(updatedIssues);  

        RiskScoreTestingEndpointsUtils riskScoreTestingEndpointsUtils = new RiskScoreTestingEndpointsUtils();
        Map<Integer, Float> collectionRiskScoreMap = new HashMap<>();

        // after getting the severityScoreMap, we write that in DB
        if(severityScoreMap != null){
            severityScoreMap.forEach((apiInfoKey, severityScore)->{
                Bson filter = ApiInfoDao.getFilter(apiInfoKey);
                ApiInfo apiInfo = ApiInfoDao.instance.findOne(filter);
                boolean isSensitive = apiInfo != null ? apiInfo.getIsSensitive() : false;
                float riskScore = ApiInfoDao.getRiskScore(apiInfo, isSensitive, Utils.getRiskScoreValueFromSeverityScore(severityScore));

                if(apiInfo.getCollectionIds() != null){
                    for(int collectionId: apiInfo.getCollectionIds()){
                        float storedRiskScore = collectionRiskScoreMap.getOrDefault(collectionId, (float) 0);
                        collectionRiskScoreMap.put(collectionId, Math.max(riskScore, storedRiskScore));
                    }   
                }else{
                    float storedRiskScore = collectionRiskScoreMap.getOrDefault(apiInfo.getId().getApiCollectionId(), (float) 0);
                    collectionRiskScoreMap.put(apiInfo.getId().getApiCollectionId(), Math.max(riskScore, storedRiskScore));
                }

                if (apiInfo != null) {
                    if (apiInfo.getRiskScore() != riskScore) {
                        riskScoreTestingEndpointsUtils.updateApiRiskScoreGroup(apiInfo, riskScore);
                    }
                }
                
                Bson update = Updates.combine(
                    Updates.set(ApiInfo.SEVERITY_SCORE, severityScore),
                    Updates.set(ApiInfo.RISK_SCORE, riskScore)
                );

                bulkUpdatesForApiInfo.add(new UpdateManyModel<>(filter, update,new UpdateOptions().upsert(false)));
            });
        }

        if (bulkUpdatesForApiInfo.size() > 0) {
            ApiInfoDao.instance.getMCollection().bulkWrite(bulkUpdatesForApiInfo, new BulkWriteOptions().ordered(false));
        }

        updateRiskScoreInCollections(collectionRiskScoreMap);

        riskScoreTestingEndpointsUtils.syncRiskScoreGroupApis();  
    }

    private static void writeUpdatesForSensitiveInfoInApiInfo(List<String> updatedDataTypes, int timeStampFilter){
        ArrayList<WriteModel<ApiInfo>> bulkUpdatesForApiInfo = new ArrayList<>();
        
        Bson sensitiveSubTypeFilter = Filters.and(
            Filters.in(SingleTypeInfo.SUB_TYPE,updatedDataTypes), 
            Filters.gt(SingleTypeInfo._RESPONSE_CODE, -1),
            Filters.gte(SingleTypeInfo._TIMESTAMP, timeStampFilter)
        );
        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(sensitiveSubTypeFilter));
        BasicDBObject groupedId =  new BasicDBObject("apiCollectionId", "$apiCollectionId")
                                        .append("url", "$url")
                                        .append("method", "$method");
        pipeline.add(Aggregates.group(groupedId,Accumulators.addToSet("subTypes", "$subType")));

        MongoCursor<BasicDBObject> stiCursor = SingleTypeInfoDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
        Map<Integer, Float> collectionRiskScoreMap = new HashMap<>();
        while(stiCursor.hasNext()){
            try {
                BasicDBObject basicDBObject = stiCursor.next();
                boolean isSensitive = checkForSensitive((List<String>) basicDBObject.get("subTypes"));
                Bson filterQSampleData = Filters.and(
                    Filters.eq("_id.apiCollectionId",((BasicDBObject) basicDBObject.get("_id")).getInt("apiCollectionId")),
                    Filters.eq("_id.method", ((BasicDBObject) basicDBObject.get("_id")).getString("method")),
                    Filters.eq("_id.url", ((BasicDBObject) basicDBObject.get("_id")).getString("url"))
                );
                ApiInfo apiInfo = ApiInfoDao.instance.findOne(filterQSampleData);
                if(apiInfo == null){
                    continue;
                }
                float riskScore = ApiInfoDao.getRiskScore(apiInfo, isSensitive, Utils.getRiskScoreValueFromSeverityScore(apiInfo.getSeverityScore()));
                Bson update = Updates.combine(
                    Updates.set(ApiInfo.IS_SENSITIVE, isSensitive),
                    Updates.set(ApiInfo.RISK_SCORE, riskScore)
                );

                if(apiInfo.getCollectionIds() != null){
                    for(int collectionId: apiInfo.getCollectionIds()){
                        float storedRiskScore = collectionRiskScoreMap.getOrDefault(collectionId, (float) 0);
                        collectionRiskScoreMap.put(collectionId, Math.max(riskScore, storedRiskScore));
                    }   
                }else{
                    float storedRiskScore = collectionRiskScoreMap.getOrDefault(apiInfo.getId().getApiCollectionId(), (float) 0);
                    collectionRiskScoreMap.put(apiInfo.getId().getApiCollectionId(), Math.max(riskScore, storedRiskScore));
                }
                bulkUpdatesForApiInfo.add(new UpdateManyModel<>(filterQSampleData, update, new UpdateOptions().upsert(false)));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (bulkUpdatesForApiInfo.size() > 0) {
            ApiInfoDao.instance.getMCollection().bulkWrite(bulkUpdatesForApiInfo, new BulkWriteOptions().ordered(false));
            updateRiskScoreInCollections(collectionRiskScoreMap);
        }

        AccountSettingsDao.instance.getMCollection().updateOne(
            AccountSettingsDao.generateFilter(),
            Updates.set((AccountSettings.LAST_UPDATED_CRON_INFO + "."+ LastCronRunInfo.LAST_UPDATED_SENSITIVE_MAP), Context.now()),
            new UpdateOptions().upsert(true)
        );
    }

    private static void updatesForNewEndpoints(int timeStampFilter){
        List<String> sensitiveInResponse = SingleTypeInfoDao.instance.sensitiveSubTypeInResponseNames();
        sensitiveInResponse.addAll(SingleTypeInfoDao.instance.sensitiveSubTypeNames());
        writeUpdatesForSensitiveInfoInApiInfo(sensitiveInResponse, timeStampFilter);
    }

    public void mapSensitiveSTIsInApiInfo(int timeStampFilter,int cronTime){
        List<String> sensitiveInResponse = new ArrayList<>();
        if(timeStampFilter == 0){
            sensitiveInResponse = SingleTypeInfoDao.instance.sensitiveSubTypeInResponseNames();
            sensitiveInResponse.addAll(SingleTypeInfoDao.instance.sensitiveSubTypeNames());
        }else{
            Bson filter = Filters.gte("timestamp", timeStampFilter);
            List<AktoDataType> updatedTypes = AktoDataTypeDao.instance.findAll(filter);
            List<CustomDataType> updaCustomDataTypes = CustomDataTypeDao.instance.findAll(filter);
            for(AktoDataType dataType: updatedTypes){
                sensitiveInResponse.add(dataType.getName());
            }

            for(CustomDataType dataType: updaCustomDataTypes){
                sensitiveInResponse.add(dataType.getName());
            }
        }
        if(sensitiveInResponse.size() > 0){
            writeUpdatesForSensitiveInfoInApiInfo(sensitiveInResponse, 0);
        }
        if(timeStampFilter != 0){
            int filterTime = Context.now() - ((cronTime + 2) * 60) ; 
            updatesForNewEndpoints(filterTime);
        }
    }

    public void calculateRiskScoreForAllApis() {
        int timeStamp = Context.now() - 24*60*60;
        int limit = 10_000;

        List<WriteModel<ApiInfo>> bulkUpdates = new ArrayList<>();
        Bson projection = Projections.include("_id", ApiInfo.API_ACCESS_TYPES, ApiInfo.LAST_SEEN, ApiInfo.SEVERITY_SCORE, ApiInfo.IS_SENSITIVE, ApiInfo.COLLECTION_IDS, ApiInfo.RISK_SCORE);

        RiskScoreTestingEndpointsUtils riskScoreTestingEndpointsUtils = new RiskScoreTestingEndpointsUtils();
        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(
            Filters.empty(),Projections.include(ApiCollection.ID, ApiCollection.RISK_SCORE)
        );

        Map<Integer,Double> collectionRiskScoreMap = new HashMap<>();

        for(ApiCollection apiCollection: apiCollections){
            Bson filter = Filters.and(
                    Filters.or(
                            Filters.exists(ApiInfo.LAST_CALCULATED_TIME, false),
                            Filters.lte(ApiInfo.LAST_CALCULATED_TIME, timeStamp)),
                    Filters.eq(ApiInfo.ID_API_COLLECTION_ID, apiCollection.getId())
            );

            List<ApiInfo> apiInfos = ApiInfoDao.instance.findAll(filter,0, limit, Sorts.descending(ApiInfo.LAST_CALCULATED_TIME), projection);
            double maxRiskScore = 0;
            HashSet<Integer> collectionIdsSet = new HashSet<>();
            collectionIdsSet.add(apiCollection.getId());
            for(ApiInfo apiInfo: apiInfos){
                float riskScore = ApiInfoDao.getRiskScore(apiInfo, apiInfo.getIsSensitive(), Utils.getRiskScoreValueFromSeverityScore(apiInfo.getSeverityScore()));
                Bson update = Updates.combine(
                    Updates.set(ApiInfo.RISK_SCORE, riskScore),
                    Updates.set(ApiInfo.LAST_CALCULATED_TIME, Context.now())
                );
                Bson filterQ = ApiInfoDao.getFilter(apiInfo.getId());

                maxRiskScore = Math.max(riskScore, maxRiskScore);
                
                bulkUpdates.add(new UpdateManyModel<>(filterQ, update, new UpdateOptions().upsert(false)));
                
                List<Integer> collectionIds = apiInfo.getCollectionIds();
                if(collectionIds != null && collectionIds.size() > 1){
                    collectionIdsSet.addAll(collectionIds);
                }
                
                float oldRiskScore = apiInfo.getRiskScore();
                RiskScoreTestingEndpoints.RiskScoreGroupType oldRiskScoreGroupType = RiskScoreTestingEndpoints.calculateRiskScoreGroup(oldRiskScore);
                int oldRiskScoreGroupCollectionId = RiskScoreTestingEndpoints.getApiCollectionId(oldRiskScoreGroupType);

                if (!collectionIds.contains(oldRiskScoreGroupCollectionId)) {
                    // Add API to risk score API group if it is not already added
                    riskScoreTestingEndpointsUtils.updateApiRiskScoreGroup(apiInfo, riskScore);
                } else if (oldRiskScore != riskScore) {
                    // Update API in risk score API group if risk score has changed
                    riskScoreTestingEndpointsUtils.updateApiRiskScoreGroup(apiInfo, riskScore);
                }
            }
            if(bulkUpdates.size() > 0){
                ApiInfoDao.instance.bulkWrite(bulkUpdates, new BulkWriteOptions().ordered(false));
            }
            bulkUpdates.clear();

            for(Integer id: collectionIdsSet){
                collectionRiskScoreMap.put(id, maxRiskScore);
            }
        }

        fillRiskScoreInCollections(collectionRiskScoreMap);

        riskScoreTestingEndpointsUtils.syncRiskScoreGroupApis();
    }

    public static void fillInitialRiskScoreInCollections(){
        ApiCollectionsAction apiCollectionsAction = new ApiCollectionsAction();
        String actionString = apiCollectionsAction.fetchRiskScoreInfo();
        if(actionString.equalsIgnoreCase(Action.SUCCESS)){
            Map<Integer,Double> riskScoreMap = apiCollectionsAction.getRiskScoreOfCollectionsMap();
            fillRiskScoreInCollections(riskScoreMap);
        }
    }
}
