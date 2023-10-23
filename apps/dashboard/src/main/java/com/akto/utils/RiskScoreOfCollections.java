package com.akto.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import org.bson.conversions.Bson;

import com.akto.action.observe.InventoryAction;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.AktoDataTypeDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.CustomDataTypeDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiInfo;
import com.akto.dto.AccountSettings.CronTimers;
import com.akto.dto.AktoDataType;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.CustomDataType;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateManyModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;

public class RiskScoreOfCollections {

    private List<ApiInfoKey> getUpdatedApiInfos(int timeStampFilter){
        List<ApiInfoKey> updatedApiInfoKeys = new ArrayList<>();

        // get freshly updated testing run issues here
        Bson projections = Projections.include("_id", TestingRunIssues.LAST_SEEN);
        Bson filters = Filters.gte(TestingRunIssues.LAST_SEEN, timeStampFilter);

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

    private Map<ApiInfoKey, Float> getSeverityScoreMap(List<TestingRunIssues> issues){
        // Method to calculate severity Score for the apiInfo on the basis of HIGH, LOW, MEDIUM
        InventoryAction inventoryAction = new InventoryAction();
        Map<ApiInfoKey, Float> severityScoreMap = new HashMap<>();

        for(TestingRunIssues issue: issues){
            String severity = issue.getSeverity().toString();
            Float severityScore = (Float) inventoryAction.calculateRiskValueForSeverity(severity);
            ApiInfoKey apiInfoKey = issue.getId().getApiInfoKey();
            if(severityScoreMap.isEmpty() || !severityScoreMap.containsKey(apiInfoKey)){
                severityScoreMap.put(apiInfoKey, severityScore);
            }else{
                Float prev = severityScoreMap.get(apiInfoKey);
                severityScore += prev;
                severityScoreMap.put(apiInfoKey, severityScore);
            }
        }

        return severityScoreMap;
    }

    public void updateSeverityScoreInApiInfo(int timeStampFilter){
        ArrayList<WriteModel<ApiInfo>> bulkUpdatesForApiInfo = new ArrayList<>();
        List<ApiInfoKey> updatedApiInfoKeys = getUpdatedApiInfos(timeStampFilter) ;

        if(updatedApiInfoKeys == null || updatedApiInfoKeys.size() == 0){
            return ;
        }
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

        // after getting the severityScoreMap, we write that in DB
        severityScoreMap.forEach((apiInfoKey, severityScore)->{
            Bson filter = Filters.and(
                Filters.eq("_id.apiCollectionId",apiInfoKey.getApiCollectionId()),
                Filters.eq("_id.method", apiInfoKey.getMethod()),
                Filters.eq("_id.url", apiInfoKey.getUrl())
            );
            bulkUpdatesForApiInfo.add(new UpdateManyModel<>(filter, Updates.set(ApiInfo.SEVERITY_SCORE, (float) severityScore), new UpdateOptions()));
        });

        if (bulkUpdatesForApiInfo.size() > 0) {
            ApiInfoDao.instance.getMCollection().bulkWrite(bulkUpdatesForApiInfo, new BulkWriteOptions().ordered(false));
        }

        // update the last score calculated field in account settings collection in db
        AccountSettingsDao.instance.getMCollection().updateOne(
            AccountSettingsDao.generateFilter(),
            Updates.set(AccountSettings.RISK_SCORE_TIMERS + "."+ CronTimers.LAST_UPDATED_ISSUES, Context.now()),
            new UpdateOptions().upsert(true)
        );
            
    }

    private static void writeUpdatesForSensitiveInfoInApiInfo(List<String> sensitiveInResponse, int timeStampFilter){
        ArrayList<WriteModel<ApiInfo>> bulkUpdatesForApiInfo = new ArrayList<>();
        
        Bson sensitiveSubTypeFilter = Filters.and(
            Filters.in(SingleTypeInfo.SUB_TYPE,sensitiveInResponse), 
            Filters.gt(SingleTypeInfo._RESPONSE_CODE, -1),
            Filters.gte(SingleTypeInfo._TIMESTAMP, timeStampFilter)
        );
        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(sensitiveSubTypeFilter));
        BasicDBObject groupedId =  new BasicDBObject("apiCollectionId", "$apiCollectionId")
                                        .append("url", "$url")
                                        .append("method", "$method");
        pipeline.add(Aggregates.group(groupedId));

        MongoCursor<BasicDBObject> stiCursor = SingleTypeInfoDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
        while(stiCursor.hasNext()){
            try {
                BasicDBObject basicDBObject = stiCursor.next();
                Bson filterQSampleData = Filters.and(
                    Filters.eq("_id.apiCollectionId",((BasicDBObject) basicDBObject.get("_id")).getInt("apiCollectionId")),
                    Filters.eq("_id.method", ((BasicDBObject) basicDBObject.get("_id")).getString("method")),
                    Filters.eq("_id.url", ((BasicDBObject) basicDBObject.get("_id")).getString("url"))
                );
                bulkUpdatesForApiInfo.add(new UpdateManyModel<>(filterQSampleData, Updates.set(ApiInfo.IS_SENSITIVE, true), new UpdateOptions()));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (bulkUpdatesForApiInfo.size() > 0) {
            ApiInfoDao.instance.getMCollection().bulkWrite(bulkUpdatesForApiInfo, new BulkWriteOptions().ordered(false));
        }

        AccountSettingsDao.instance.getMCollection().updateOne(
            AccountSettingsDao.generateFilter(),
            Updates.set((AccountSettings.RISK_SCORE_TIMERS + "."+ CronTimers.LAST_UPDATED_SENSITIVE_MAP), Context.now()),
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
                if (dataType.getSensitiveAlways() || dataType.getSensitivePosition().contains(SingleTypeInfo.Position.RESPONSE_HEADER) || dataType.getSensitivePosition().contains(SingleTypeInfo.Position.RESPONSE_PAYLOAD)) {
                    sensitiveInResponse.add(dataType.getName());
                }
            }

            for(CustomDataType dataType: updaCustomDataTypes){
                if (dataType.isSensitiveAlways() || dataType.getSensitivePosition().contains(SingleTypeInfo.Position.RESPONSE_HEADER) || dataType.getSensitivePosition().contains(SingleTypeInfo.Position.RESPONSE_PAYLOAD)) {
                    sensitiveInResponse.add(dataType.getName());
                }
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

}
