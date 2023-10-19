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

    public void updateSeverityScoreInApiInfo(int timeStampFilter){
        Bson projections = Projections.include("_id", "lastSeen");
        ArrayList<WriteModel<ApiInfo>> bulkUpdatesForApiInfo = new ArrayList<>();
        
        Bson filters = Filters.gte("lastSeen", timeStampFilter);
        List<TestingRunIssues> issues = TestingRunIssuesDao.instance.findAll(filters, projections);
        if(issues.size() > 0){
            List<ApiInfoKey> updatedApiInfoKeys = new ArrayList<>();

            for(TestingRunIssues issue: issues){
                TestingIssuesId issueId = issue.getId();
                ApiInfoKey apiInfoKey = issueId.getApiInfoKey();
                updatedApiInfoKeys.add(apiInfoKey);
            }

            Bson filterQ = Filters.and(
                Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, "OPEN"),
                Filters.in("_id.apiInfoKey", updatedApiInfoKeys)
            );
            try {
                List<TestingRunIssues> updatedIssues = TestingRunIssuesDao.instance.findAll(filterQ);
                InventoryAction inventoryAction = new InventoryAction();

                Map<ApiInfoKey, Float> riskScoreMap = new HashMap<>();

                for(TestingRunIssues issue: updatedIssues){
                    String severity = issue.getSeverity().toString();
                    Float riskScore = (Float) inventoryAction.calculateRiskValueForSeverity(severity);
                    ApiInfoKey apiInfoKey = issue.getId().getApiInfoKey();
                    if(riskScoreMap.isEmpty() || !riskScoreMap.containsKey(apiInfoKey)){
                        riskScoreMap.put(apiInfoKey, riskScore);
                    }else{
                        Float prev = riskScoreMap.get(apiInfoKey);
                        riskScore += prev;
                        riskScoreMap.put(apiInfoKey, riskScore);
                    }
                }
                riskScoreMap.forEach((apiInfoKey, riskScore)->{
                    Bson filter = Filters.and(
                        Filters.eq("_id.apiCollectionId",apiInfoKey.getApiCollectionId()),
                        Filters.eq("_id.method", apiInfoKey.getMethod()),
                        Filters.eq("_id.url", apiInfoKey.getUrl())
                    );
                    bulkUpdatesForApiInfo.add(new UpdateManyModel<>(filter, Updates.set(ApiInfo.SEVERITY_SCORE, (float) riskScore), new UpdateOptions()));
                });

                if (bulkUpdatesForApiInfo.size() > 0) {
                    ApiInfoDao.instance.getMCollection().bulkWrite(bulkUpdatesForApiInfo, new BulkWriteOptions().ordered(false));
                }
                AccountSettingsDao.instance.getMCollection().updateOne(
                    AccountSettingsDao.generateFilter(),
                    Updates.set(AccountSettings.TIMERS + "."+ CronTimers.LAST_UPDATED_ISSUES, Context.now()),
                    new UpdateOptions().upsert(true)
                );
            } catch (Exception e) {
                // TODO: handle exception
                e.printStackTrace();
            }
            
        }
    }

    private static void writeUpdates(List<String> sensitiveInResponse, int timeStampFilter){
        try {
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
                Updates.set((AccountSettings.TIMERS + "."+ CronTimers.LAST_UPDATED_SENSITIVE_MAP), Context.now()),
                new UpdateOptions().upsert(true)
            );
            
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
        }
    }

    private static void updatesForNewEndpoints(int timeStampFilter){
        List<String> sensitiveInResponse = SingleTypeInfoDao.instance.sensitiveSubTypeInResponseNames();
        sensitiveInResponse.addAll(SingleTypeInfoDao.instance.sensitiveSubTypeNames());
        writeUpdates(sensitiveInResponse, timeStampFilter);
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
            writeUpdates(sensitiveInResponse, 0);
        }
        int filterTime = Context.now() - ((cronTime + 2) * 60) ; 
        updatesForNewEndpoints(filterTime);
    }

}
