package com.akto.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiAccessType;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.ApiInfo.AuthType;
import com.akto.dto.events.EventsExample;
import com.akto.dto.events.EventsMetrics;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

import io.intercom.api.Event;

public class IntercomEventsUtil {
    public static final int MILESTONES_LIMIT = 500;

    public static void fillMetaDataForIntercomEvent(int lastSentCount, EventsMetrics currentEvent){
        long count = ApiInfoDao.instance.estimatedDocumentCount();
        if(count % MILESTONES_LIMIT == 0){
            Map<String,Integer> localMilestonesMap = new HashMap<>();
            if(lastSentCount < count){
                localMilestonesMap.put(EventsMetrics.APIS_INFO_COUNT, (int) count);
            }
            if(!localMilestonesMap.isEmpty()){
                currentEvent.setMilestones(localMilestonesMap);
            }
        }
    }

    public static boolean createAndSendMetaDataForEvent(Event event,EventsMetrics currentEventsMetrics){
        boolean shouldSendEvent = false;
        if(currentEventsMetrics.isDeploymentStarted()){
            shouldSendEvent = true;
            event.putMetadata(EventsMetrics.DEPLOYMENT_STARTED, true);
        }
        if(currentEventsMetrics.getMilestones() != null && !currentEventsMetrics.getMilestones().isEmpty()){
            shouldSendEvent = true;
            for(Map.Entry<String,Integer> entry: currentEventsMetrics.getMilestones().entrySet()){
                event.putMetadata(entry.getKey(), entry.getValue());
            }
        }
        if(currentEventsMetrics.getCustomTemplatesCount() > 0){
            shouldSendEvent = true;
            event.putMetadata(EventsMetrics.CUSTOM_TEMPLATE_COUNT,currentEventsMetrics.getCustomTemplatesCount());
        }
        if(currentEventsMetrics.getApiSecurityPosture() != null && !currentEventsMetrics.getApiSecurityPosture().isEmpty()){
            shouldSendEvent = true;
            for(Map.Entry<String,EventsExample> entry: currentEventsMetrics.getApiSecurityPosture().entrySet()){
                EventsExample.insertMetaDataFormat(entry.getValue(), entry.getKey(), event);
            }
        }
        if(currentEventsMetrics.getSecurityTestFindings() != null && !currentEventsMetrics.getSecurityTestFindings().isEmpty()){
            shouldSendEvent = true;
            for(Map.Entry<String,Integer> entry: currentEventsMetrics.getSecurityTestFindings().entrySet()){
                event.putMetadata(entry.getKey(), entry.getValue());
            }
        }
        return shouldSendEvent;
    }

    private static List<String> convertApiInfoKeysToString(List<ApiInfo> apiInfos){
        List<String> resultantList = new ArrayList<>();
        for(ApiInfo apiInfo: apiInfos){
            ApiInfoKey apiInfoKey = apiInfo.getId();
            String url = apiInfoKey.getMethod().toString() + " " + apiInfoKey.getUrl();
            resultantList.add(url);
        }
        return resultantList;
    }

    private static void insertEventsIntoPostureMap(Map<String,EventsExample> localPostureMap, String key, List<String> apiExampleList){
        EventsExample currentEvent = localPostureMap.get(key);
        if(localPostureMap.isEmpty() || currentEvent == null){
            currentEvent = new EventsExample(1, apiExampleList);
        }else{
            currentEvent.setCount(currentEvent.getCount() + 1);
            if(currentEvent.getCount() < 5){
                List<String> newExamples = currentEvent.getExamples();
                newExamples.addAll(apiExampleList);
                currentEvent.setExamples(newExamples);
            }
        }
        localPostureMap.put(key, currentEvent);
    }

    public static void sendPostureMapToIntercom(int lastEventSentEpoch, EventsMetrics currentEvent){

        Map<String, EventsExample> localPostureMap = new HashMap<>();
        List<BasicDBObject> recentEndpoints = SingleTypeInfoDao.instance.fetchRecentEndpoints(lastEventSentEpoch, Context.now(), new HashSet<>());
        List<ApiInfo> recentApiInfos = ApiInfoDao.getApiInfosFromList(recentEndpoints, -1);

        for(ApiInfo apiInfo: recentApiInfos){
            
            List<String> currentExampleList = convertApiInfoKeysToString(Collections.singletonList(apiInfo));
            
            // 1. Shadow apis
            if(apiInfo.getId().getApiCollectionId() == ApiInfoDao.AKTO_DISCOVERED_APIS_COLLECTION_ID){
                insertEventsIntoPostureMap(localPostureMap, "shadowAPIs", currentExampleList);
            }

            // 2. unauthenticated apis
            List<AuthType> authTypes = apiInfo.getActualAuthType();
            if(authTypes != null && !authTypes.isEmpty() && authTypes.contains(AuthType.UNAUTHENTICATED)){
                insertEventsIntoPostureMap(localPostureMap, "unauthenticatedAPIs", currentExampleList);
            }

            // access-types-dependent
            Set<ApiAccessType> apiAccessTypes = apiInfo.getApiAccessTypes();
            if(apiAccessTypes != null && apiAccessTypes.size() > 0){
                if(apiAccessTypes.contains(ApiAccessType.PARTNER)){
                    insertEventsIntoPostureMap(localPostureMap, ApiAccessType.PARTNER.name() + "APIs", currentExampleList);
                }else{
                    if(apiAccessTypes.contains(ApiAccessType.PUBLIC)){
                        insertEventsIntoPostureMap(localPostureMap, ApiAccessType.PUBLIC.name() + "APIs", currentExampleList);
                    }else{
                        insertEventsIntoPostureMap(localPostureMap, ApiAccessType.PRIVATE.name() + "APIs", currentExampleList);
                    }
                }
            }
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
}
