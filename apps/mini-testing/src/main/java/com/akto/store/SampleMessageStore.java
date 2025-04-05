package com.akto.store;

import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.data_actor.DbLayer;
import com.akto.dto.*;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.testing.*;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.metrics.AllMetrics;
import com.akto.sql.SampleDataAltDb;

import java.util.*;

public class SampleMessageStore {


    private static final LoggerMaker loggerMaker = new LoggerMaker(SampleMessageStore.class);
    private Map<ApiInfo.ApiInfoKey, List<String>> sampleDataMap = new HashMap<>();
    private Map<String, SingleTypeInfo> singleTypeInfos = new HashMap<>();
    private static final DataActor dataActor = DataActorFactory.fetchInstance();
    
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
        return dataActor.fetchTestRoles();
    }


    public void fetchSampleMessages(Set<Integer> apiCollectionIds) {
        List<SampleData> sampleDataList = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            List<SampleData> sampleDataBatch = dataActor.fetchSampleData(apiCollectionIds,
                    i * DbLayer.SAMPLE_DATA_LIMIT);
            if (sampleDataBatch == null || sampleDataBatch.isEmpty()) {
                break;
            }
            sampleDataList.addAll(sampleDataBatch);
        }
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



    public List<RawApi> fetchAllOriginalMessages(ApiInfoKey apiInfoKey) {
        List<RawApi> messages = new ArrayList<>();
        try {
            long start = System.currentTimeMillis();
            List<String> samples = new ArrayList<>();
            try {
                samples = SampleDataAltDb.findSamplesByApiInfoKey(apiInfoKey);
            } catch (Exception e) {
            }
            if (samples == null) {
                samples = new ArrayList<>();
            }
            AllMetrics.instance.setMultipleSampleDataFetchLatency(System.currentTimeMillis() - start);
            for(String message: samples){
                messages.add(RawApi.buildFromMessage(message));
            }

            if (messages.isEmpty()) {
                List<String> dbSamples = sampleDataMap.get(apiInfoKey);
                if (dbSamples == null || dbSamples.isEmpty())
                    return messages;

                for (String message : dbSamples) {
                    try {
                        messages.add(RawApi.buildFromMessage(message));
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb("Error while building RawAPI for " + apiInfoKey + " : " + e,
                                LogDb.TESTING);
                    }
                }
            }
            return messages;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error while fetching all original messages for "+ apiInfoKey +" : " + e, LogDb.TESTING);
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

    public Map<String, SingleTypeInfo> getSingleTypeInfos() {
        return this.singleTypeInfos;
    }

    public Map<ApiInfoKey, List<String>> getSampleDataMap() {
        return this.sampleDataMap;
    }

}
