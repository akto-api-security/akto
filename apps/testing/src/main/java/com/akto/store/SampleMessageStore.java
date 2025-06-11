package com.akto.store;

import com.akto.dao.SampleDataDao;
import com.akto.dao.testing.TestRolesDao;
import com.akto.dto.*;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.testing.*;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

import org.bson.conversions.Bson;

import java.util.*;
import java.util.stream.Collectors;

public class SampleMessageStore {


    private static final LoggerMaker loggerMaker = new LoggerMaker(SampleMessageStore.class, LogDb.TESTING);
    private Map<ApiInfo.ApiInfoKey, List<String>> sampleDataMap = new HashMap<>();
    private Map<String, SingleTypeInfo> singleTypeInfos = new HashMap<>();
    public void buildSingleTypeInfoMap(TestingEndpoints testingEndpoints) {
        if (testingEndpoints == null) return;
        TestingEndpoints.Type type = testingEndpoints.getType();
        List<SingleTypeInfo> singleTypeInfoList = new ArrayList<>();
        try {
            if (type.equals(TestingEndpoints.Type.COLLECTION_WISE)) {
                CollectionWiseTestingEndpoints collectionWiseTestingEndpoints = (CollectionWiseTestingEndpoints) testingEndpoints;
                int apiCollectionId = collectionWiseTestingEndpoints.getApiCollectionId();
                singleTypeInfoList = SingleTypeInfoDao.instance.findAll(
                        Filters.and(
                                Filters.eq(SingleTypeInfo._API_COLLECTION_ID, apiCollectionId),
                                Filters.eq(SingleTypeInfo._RESPONSE_CODE, -1),
                                Filters.eq(SingleTypeInfo._IS_HEADER, false)
                        )
                );
            } else {
                CustomTestingEndpoints customTestingEndpoints = (CustomTestingEndpoints) testingEndpoints;
                List<ApiInfoKey> apiInfoKeys = customTestingEndpoints.getApisList();

                if (apiInfoKeys.isEmpty()) {
                    return;
                } else {
                    int apiCollectionId = apiInfoKeys.get(0).getApiCollectionId();
                    singleTypeInfoList = SingleTypeInfoDao.instance.findAll(
                            Filters.and(
                                    Filters.eq(SingleTypeInfo._API_COLLECTION_ID, apiCollectionId),
                                    Filters.eq(SingleTypeInfo._RESPONSE_CODE, -1),
                                    Filters.eq(SingleTypeInfo._IS_HEADER, false)
                            )
                    );
                }
            }

            for (SingleTypeInfo singleTypeInfo: singleTypeInfoList) {
                singleTypeInfos.put(singleTypeInfo.composeKeyWithCustomSubType(SingleTypeInfo.GENERIC), singleTypeInfo);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while building STI map: " + e, LogDb.TESTING);
        }
    }

    private SampleMessageStore() {}

    public static SampleMessageStore create() {
        return new SampleMessageStore();
    }

    public static SampleMessageStore create(Map<ApiInfo.ApiInfoKey, List<String>> sampleDataMap) {
        SampleMessageStore ret = new SampleMessageStore();
        ret.sampleDataMap = sampleDataMap;
        return ret;
    }

    public List<TestRoles> fetchTestRoles() {
        return TestRolesDao.instance.findAll(new BasicDBObject());
    }

    private void fillSampleDataMap(List<SampleData> sampleDataList){
        Map<ApiInfo.ApiInfoKey, List<String>> tempSampleDataMap = new HashMap<>();
        for (SampleData sampleData: sampleDataList) {
            if (sampleData.getSamples() == null) continue;
            Key key = sampleData.getId();
            ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(key.getApiCollectionId(), key.getUrl(), key.getMethod());
            if (tempSampleDataMap.containsKey(apiInfoKey)) {
                tempSampleDataMap.get(apiInfoKey).addAll(sampleData.getSamples());
            } else {
                tempSampleDataMap.put(apiInfoKey, sampleData.getSamples());
            }
        }

        sampleDataMap = new HashMap<>(tempSampleDataMap);
    }


    public void fetchSampleMessages(Set<Integer> apiCollectionIds) {
        Bson filterQ = Filters.in(ApiInfo.ID_API_COLLECTION_ID, apiCollectionIds);
        List<SampleData> sampleDataList = SampleDataDao.instance.findAll(filterQ, 0, 10_000, null, Projections.slice("samples", -1));
        loggerMaker.info("Fetched " + sampleDataList.size() + " sample messages for apiCollectionIds: " + apiCollectionIds);
        fillSampleDataMap(sampleDataList);
    }

    public void fetchSampleMessages(List<ApiInfo.ApiInfoKey> apiInfoKeyList){
        List<SampleData> sampleDataList = new ArrayList<>();
        loggerMaker.info("Api info key list size: " + apiInfoKeyList.size());
        for(int i = 0 ; i < apiInfoKeyList.size(); i += 100){
            List<ApiInfoKey> subList = apiInfoKeyList.subList(i, Math.min(i + 100, apiInfoKeyList.size()));
            List<Bson> filters = subList.stream().map(endpoint -> Filters.and(
                    Filters.eq(ApiInfo.ID_API_COLLECTION_ID, endpoint.getApiCollectionId()),
                    Filters.eq(ApiInfo.ID_URL, endpoint.getUrl()),
                    Filters.eq(ApiInfo.ID_METHOD, endpoint.getMethod().name())))
                    .collect(Collectors.toList());
            
            List<SampleData> sampleDataBatch = SampleDataDao.instance.findAll(Filters.or(filters), Projections.slice("samples", -1));
            loggerMaker.info("Fetched " + sampleDataBatch.size() + " sample messages for apiInfoKeyList: " + subList);
            if (sampleDataBatch == null || sampleDataBatch.isEmpty()) {
                break;
            }
            sampleDataList.addAll(sampleDataBatch);
        }
        fillSampleDataMap(sampleDataList);
    }



    public List<RawApi> fetchAllOriginalMessages(ApiInfoKey apiInfoKey) {
        List<RawApi> messages = new ArrayList<>();

        List<String> samples = sampleDataMap.get(apiInfoKey);
        if (samples == null || samples.isEmpty()) return messages;

        String lastSample = samples.get(samples.size() - 1);
        try {
            RawApi rawApi = RawApi.buildFromMessage(lastSample, true);
            messages.add(rawApi);
        } catch(Exception e) {
            loggerMaker.errorAndAddToDb("Error while building RawAPI for "+ apiInfoKey +" : " + e, LogDb.TESTING);
        }
        return messages;
    }

    public static List<RawApi> filterMessagesWithAuthToken(List<RawApi> messages, AuthMechanism authMechanism) {
        List<RawApi> filteredMessages = new ArrayList<>();
        for (RawApi rawApi: messages) {
            OriginalHttpRequest request = rawApi.getRequest();
            boolean containsAuthToken = authMechanism.authTokenPresent(request);
            if (containsAuthToken) filteredMessages.add(rawApi);
        }

        return filteredMessages;
    }

    public List<RawApi> findSampleMessages(int k) {
        List<RawApi> samples = new ArrayList<>();
        if (sampleDataMap == null) return samples;

        for (ApiInfoKey apiInfoKey : sampleDataMap.keySet()) {
            List<String> messages = sampleDataMap.getOrDefault(apiInfoKey, new ArrayList<>());
            if (!messages.isEmpty()) {
                RawApi rawApi = RawApi.buildFromMessage(messages.get(0));
                samples.add(rawApi);
            }
            if (samples.size() >= k) break;
        }

        return samples;
    }

    public Map<String, SingleTypeInfo> getSingleTypeInfos() {
        return this.singleTypeInfos;
    }

    public Map<ApiInfoKey, List<String>> getSampleDataMap() {
        return this.sampleDataMap;
    }

}
