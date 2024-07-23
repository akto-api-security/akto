package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiAccessType;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.events.EventsExample;
import com.akto.dto.events.EventsMetrics;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UnwindOptions;
import com.mongodb.client.model.Updates;

import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApiInfoDao extends AccountsContextDao<ApiInfo>{

    public static ApiInfoDao instance = new ApiInfoDao();

    public static final String ID = "_id.";
    public static final int MILESTONES_LIMIT = 500;
    public static final int AKTO_DISCOVERED_APIS_COLLECTION_ID = 1333333333;

    public void createIndicesIfAbsent() {

        boolean exists = false;
        for (String col: clients[0].getDatabase(Context.accountId.get()+"").listCollectionNames()){
            if (getCollName().equalsIgnoreCase(col)){
                exists = true;
                break;
            }
        };

        if (!exists) {
            clients[0].getDatabase(Context.accountId.get()+"").createCollection(getCollName());
        }

        String[] fieldNames = {"_id." + ApiInfo.ApiInfoKey.API_COLLECTION_ID};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);

        fieldNames = new String[]{"_id." + ApiInfo.ApiInfoKey.URL};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] { SingleTypeInfo._COLLECTION_IDS, ApiInfo.ID_URL }, true);
        
        fieldNames = new String[]{"_id." + ApiInfo.ApiInfoKey.API_COLLECTION_ID, "_id." + ApiInfo.ApiInfoKey.URL};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);

        fieldNames = new String[]{ApiInfo.ID_API_COLLECTION_ID, ApiInfo.LAST_SEEN};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{ApiInfo.LAST_TESTED};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{ApiInfo.LAST_CALCULATED_TIME};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] { SingleTypeInfo._COLLECTION_IDS, ApiInfo.ID_URL }, true);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] {ApiInfo.SEVERITY_SCORE }, false);
                
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] {ApiInfo.RISK_SCORE }, false);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] { ApiInfo.RISK_SCORE, ApiInfo.ID_API_COLLECTION_ID }, false);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] { ApiInfo.FIRST_DETECTED_TS, ApiInfo.ID_API_COLLECTION_ID }, false);
    }
    

    public void updateLastTestedField(ApiInfoKey apiInfoKey){
        instance.getMCollection().updateOne(
            getFilter(apiInfoKey), 
            Updates.set(ApiInfo.LAST_TESTED, Context.now())
        );
    }

    public Map<Integer,Integer> getCoverageCount(){
        Map<Integer,Integer> result = new HashMap<>();
        List<Bson> pipeline = new ArrayList<>();
        int oneMonthAgo = Context.now() - Constants.ONE_MONTH_TIMESTAMP ;
        pipeline.add(Aggregates.match(Filters.gte("lastTested", oneMonthAgo)));

        UnwindOptions unwindOptions = new UnwindOptions();
        unwindOptions.preserveNullAndEmptyArrays(false);  
        pipeline.add(Aggregates.unwind("$collectionIds", unwindOptions));

        BasicDBObject groupedId2 = new BasicDBObject("apiCollectionId", "$collectionIds");
        pipeline.add(Aggregates.group(groupedId2, Accumulators.sum("count",1)));
        pipeline.add(Aggregates.project(
            Projections.fields(
                Projections.include("count"),
                Projections.computed("apiCollectionId", "$_id.apiCollectionId")
            )
        ));

        MongoCursor<BasicDBObject> collectionsCursor = ApiInfoDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
        while(collectionsCursor.hasNext()){
            try {
                BasicDBObject basicDBObject = collectionsCursor.next();
                result.put(basicDBObject.getInt("apiCollectionId"), basicDBObject.getInt("count"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    public Map<Integer,Integer> getLastTrafficSeen(){
        Map<Integer,Integer> result = new HashMap<>();
        List<Bson> pipeline = new ArrayList<>();

        UnwindOptions unwindOptions = new UnwindOptions();
        unwindOptions.preserveNullAndEmptyArrays(false);  
        pipeline.add(Aggregates.unwind("$collectionIds", unwindOptions));

        BasicDBObject groupedId = new BasicDBObject("apiCollectionId", "$collectionIds");
        pipeline.add(Aggregates.sort(Sorts.orderBy(Sorts.descending(ApiInfo.ID_API_COLLECTION_ID), Sorts.descending(ApiInfo.LAST_SEEN))));
        pipeline.add(Aggregates.group(groupedId, Accumulators.first(ApiInfo.LAST_SEEN, "$lastSeen")));
        
        MongoCursor<ApiInfo> cursor = ApiInfoDao.instance.getMCollection().aggregate(pipeline, ApiInfo.class).cursor();
        while(cursor.hasNext()){
            try {
               ApiInfo apiInfo = cursor.next();
               result.put(apiInfo.getId().getApiCollectionId(), apiInfo.getLastSeen());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    public static Float getRiskScore(ApiInfo apiInfo, boolean isSensitive, float riskScoreFromSeverityScore){
        float riskScore = 0;
        if(apiInfo != null){
            if(Context.now() - apiInfo.getLastSeen() <= Constants.ONE_MONTH_TIMESTAMP){
                riskScore += 1;
            }
            if(apiInfo.getApiAccessTypes().contains(ApiAccessType.PUBLIC)){
                riskScore += 1;
            }
        }
        if(isSensitive){
            riskScore += 1;
        }
        riskScore += riskScoreFromSeverityScore;
        return riskScore;
    }

    private List<String> convertApiInfoKeysToString(List<ApiInfo> apiInfos){
        List<String> resultantList = new ArrayList<>();
        for(ApiInfo apiInfo: apiInfos){
            ApiInfoKey apiInfoKey = apiInfo.getId();
            String url = apiInfoKey.getMethod().toString() + " " + apiInfoKey.getUrl();
            resultantList.add(url);
        }
        return resultantList;
    }

    private void insertApisGroupDetailsInEventMap(Bson filterCommon, Bson filterCustom, Map<String, EventsExample> localPostureMap, String groupTypeName){
        int apisCount = (int) ApiInfoDao.instance.count(
            Filters.and(filterCommon,filterCustom)
        );
        if(apisCount > 0){
            List<ApiInfo> apiInfos = instance.findAll(Filters.and(filterCommon,filterCustom), 0, 4, Sorts.descending(ApiInfo.FIRST_DETECTED_TS), Projections.include("_id"));
            List<String> groupList = convertApiInfoKeysToString(apiInfos);
            localPostureMap.put(groupTypeName, new EventsExample(apisCount, groupList));
        }
    }
    
    private void insertAccessTypeApisInEventMap(Bson commonFilter, ApiAccessType accessType, Map<String, EventsExample> localPostureMap){
        Bson accessTypeFilter = Filters.empty();
        if (accessType.equals(ApiAccessType.PRIVATE)) {
            accessTypeFilter = Filters.eq(ApiInfo.API_ACCESS_TYPES, Collections.singletonList(accessType));
        } else {
            accessTypeFilter = Filters.in(ApiInfo.API_ACCESS_TYPES, accessType);
        }

        insertApisGroupDetailsInEventMap(commonFilter, accessTypeFilter, localPostureMap, accessType.name());
    }   

    public void insertMetricsForIntercomEvent(EventsMetrics lastEventSent, EventsMetrics currentEvent){
        long count = instance.getMCollection().estimatedDocumentCount();
        if(count % MILESTONES_LIMIT == 0){
            Map<String,Integer> localMilestonesMap = new HashMap<>();
            if(lastEventSent == null || lastEventSent.getMilestones() == null || lastEventSent.getMilestones().get(EventsMetrics.APIS_INFO_COUNT) == null){
                localMilestonesMap.put(EventsMetrics.APIS_INFO_COUNT, (int) count);
            }else if(lastEventSent.getMilestones().get(EventsMetrics.APIS_INFO_COUNT) < count){
                localMilestonesMap.put(EventsMetrics.APIS_INFO_COUNT, (int) count);
            }

            if(!localMilestonesMap.isEmpty()){
                currentEvent.setMilestones(localMilestonesMap);
            }
        }
    }

    public void sendPostureMapToIntercom(EventsMetrics lastEventSent, EventsMetrics currentEvent){
        int lastEventSentEpoch = 0;
        if(lastEventSent != null){
            lastEventSentEpoch = lastEventSent.getCreatedAt();
        }

        Map<String, EventsExample> localPostureMap = new HashMap<>();

        if(currentEvent.getApiSecurityPosture() != null && !currentEvent.getApiSecurityPosture().isEmpty()){
            localPostureMap = currentEvent.getApiSecurityPosture();
        }

        Bson commonFilter = Filters.or(
            Filters.exists(ApiInfo.FIRST_DETECTED_TS, false),
            Filters.eq(ApiInfo.FIRST_DETECTED_TS, lastEventSentEpoch)
        );

        // 1. Shadow apis
        Bson filterShadowApis = Filters.eq(ApiInfo.ID_API_COLLECTION_ID, AKTO_DISCOVERED_APIS_COLLECTION_ID);
        insertApisGroupDetailsInEventMap(commonFilter, filterShadowApis, localPostureMap, "shadowApis");
        

        // 2. Unauthenticated apis
        Bson unauthenticatedFilter = Filters.in(
            ApiInfo.ALL_AUTH_TYPES_FOUND, 
            Collections.singletonList(Collections.singletonList(ApiInfo.AuthType.UNAUTHENTICATED))
        );
        insertApisGroupDetailsInEventMap(commonFilter, unauthenticatedFilter, localPostureMap, "unauthenticatedApis");

        // only possible when there is a CIDR list {Access type dependent apis group}
        AccountSettings accountSettings =  AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
        if(accountSettings != null && accountSettings.getPrivateCidrList() != null && !accountSettings.getPrivateCidrList().isEmpty()){
            insertAccessTypeApisInEventMap(commonFilter, ApiAccessType.PUBLIC, localPostureMap);
            insertAccessTypeApisInEventMap(commonFilter, ApiAccessType.PARTNER, localPostureMap);
            insertAccessTypeApisInEventMap(commonFilter, ApiAccessType.PRIVATE, localPostureMap);
        }


        // 6. Sensitive Apis
        List<String> sensitiveSubtypes = new ArrayList<>();
        sensitiveSubtypes.addAll(SingleTypeInfoDao.instance.sensitiveSubTypeInResponseNames());
        sensitiveSubtypes.addAll(SingleTypeInfoDao.instance.sensitiveSubTypeInRequestNames());
        int countApis = SingleTypeInfoDao.instance.getSensitiveApisCount(sensitiveSubtypes, false, Filters.gt(SingleTypeInfo._TIMESTAMP, lastEventSentEpoch));
        if(countApis > 0){
            localPostureMap.put("sensitiveApis",
                    new EventsExample(countApis, SingleTypeInfoDao.instance.sensitiveApisList(sensitiveSubtypes,
                            Filters.gt(SingleTypeInfo._TIMESTAMP, lastEventSentEpoch), 4)));
        }

        if(!localPostureMap.isEmpty()){
            currentEvent.setApiSecurityPosture(localPostureMap);
        }

    }

    @Override
    public String getCollName() {
        return "api_info";
    }

    @Override
    public Class<ApiInfo> getClassT() {
        return ApiInfo.class;
    }

    public static Bson getFilter(ApiInfo.ApiInfoKey apiInfoKey) {
        return getFilter(apiInfoKey.getUrl(), apiInfoKey.getMethod().name(), apiInfoKey.getApiCollectionId());
    }

    public static Bson getFilter(String url, String method, int apiCollectionId) {
        return Filters.and(
                Filters.eq("_id.url", url),
                Filters.eq("_id.method", method),
                Filters.eq("_id.apiCollectionId", apiCollectionId)
        );
    }

}
