package com.akto.store;

import com.akto.PayloadEncodeUtil;
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
import com.akto.testing_db_layer_client.ClientLayer;

import java.security.interfaces.RSAPrivateKey;
import java.util.*;


public class SampleMessageStore {


    private static final LoggerMaker loggerMaker = new LoggerMaker(SampleMessageStore.class, LogDb.TESTING);
    private Map<ApiInfo.ApiInfoKey, List<String>> sampleDataMap = new HashMap<>();
    // Separate from sampleDataMap so cleanup fetches never wipe the main map.
    private final Map<ApiInfo.ApiInfoKey, List<String>> cleanupSampleDataMap = new HashMap<>();
    private Map<String, SingleTypeInfo> singleTypeInfos = new HashMap<>();
    private static final DataActor dataActor = DataActorFactory.fetchInstance();
    private static final ClientLayer clientLayer = new ClientLayer();
    private static RSAPrivateKey privateKey = PayloadEncodeUtil.getPrivateKey();
    
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
        fillSampleDataMap(sampleDataList);
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

    public void fetchSampleMessages(List<ApiInfo.ApiInfoKey> apiInfoKeyList){
        List<SampleData> sampleDataList = new ArrayList<>();
        for(int i = 0 ; i < apiInfoKeyList.size(); i += DbLayer.SAMPLE_DATA_LIMIT){
            List<ApiInfoKey> subList = new ArrayList<>(apiInfoKeyList.subList(i, Math.min(i + DbLayer.SAMPLE_DATA_LIMIT, apiInfoKeyList.size())));  
            List<SampleData> sampleDataBatch = dataActor.fetchSampleDataForEndpoints(subList);
            if (sampleDataBatch == null || sampleDataBatch.isEmpty()) {
                break;
            }
            sampleDataList.addAll(sampleDataBatch);
        }
        fillSampleDataMap(sampleDataList);
    }

    public List<RawApi> fetchAllOriginalMessages(ApiInfoKey apiInfoKey) {
        List<RawApi> messages = new ArrayList<>();
        try {
            long start = System.currentTimeMillis();
            List<String> encodedSamples = new ArrayList<>();
            List<String> samples = new ArrayList<>();
            if(System.getenv("TESTING_DB_LAYER_SERVICE_URL") != null && !System.getenv("TESTING_DB_LAYER_SERVICE_URL").isEmpty()){
                try {
                    encodedSamples = clientLayer.fetchSamples(apiInfoKey);
                    if(encodedSamples == null || encodedSamples.isEmpty()){
                        loggerMaker.infoAndAddToDb("No samples found for " + apiInfoKey.toString() + " from testing db layer in fetchAllOriginalMessages");
                    } else {
                        for (String sample: encodedSamples) {
                            if (!sample.contains("requestPayload") && privateKey != null) {
                                try {
                                    samples.add(PayloadEncodeUtil.decryptPacked(sample, privateKey));
                                } catch (Exception e) {
                                    loggerMaker.errorAndAddToDb("error while decoding payload " + e.getMessage());
                                }
                            } else {
                                samples.add(sample);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    loggerMaker.errorAndAddToDb(e, "error in fetchAllOriginalMessages " + e.getMessage());
                }
            }
            
            if (samples == null) {
                samples = new ArrayList<>();
            }
            AllMetrics.instance.setMultipleSampleDataFetchLatency(System.currentTimeMillis() - start);
            for(String message: samples){
                messages.add(RawApi.buildFromMessage(message, true));
            }

            if (messages.isEmpty()) {
                List<String> dbSamples = sampleDataMap.get(apiInfoKey);
                if (dbSamples == null || dbSamples.isEmpty())
                    return messages;

                for (String message : dbSamples) {
                    try {
                        messages.add(RawApi.buildFromMessage(message, true));
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(e, "Error while building RawAPI for " + apiInfoKey.toString() + " : " + e.getMessage());
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

    private static Map<ApiInfo.ApiInfoKey, List<String>> buildSampleDataMapFromList(List<SampleData> sampleDataList) {
        Map<ApiInfo.ApiInfoKey, List<String>> tempSampleDataMap = new HashMap<>();
        if (sampleDataList == null) {
            return tempSampleDataMap;
        }
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
        return tempSampleDataMap;
    }

    public Map<ApiInfo.ApiInfoKey, List<String>> fetchSampleMessagesIntoNewMap(List<ApiInfo.ApiInfoKey> apiInfoKeyList) {
        Map<ApiInfo.ApiInfoKey, List<String>> result = new HashMap<>();
        if (apiInfoKeyList == null || apiInfoKeyList.isEmpty()) {
            return result;
        }

        List<ApiInfo.ApiInfoKey> keysToFetch = new ArrayList<>();
        for (ApiInfo.ApiInfoKey key : apiInfoKeyList) {
            if (cleanupSampleDataMap.containsKey(key)) {
                result.put(key, cleanupSampleDataMap.get(key));
            } else {
                keysToFetch.add(key);
            }
        }

        if (!keysToFetch.isEmpty()) {
            List<SampleData> sampleDataList = new ArrayList<>();
            for (int i = 0; i < keysToFetch.size(); i += DbLayer.SAMPLE_DATA_LIMIT) {
                List<ApiInfoKey> subList = new ArrayList<>(keysToFetch.subList(i, Math.min(i + DbLayer.SAMPLE_DATA_LIMIT, keysToFetch.size())));
                List<SampleData> sampleDataBatch = dataActor.fetchSampleDataForEndpoints(subList);
                if (sampleDataBatch == null || sampleDataBatch.isEmpty()) {
                    break;
                }
                sampleDataList.addAll(sampleDataBatch);
            }
            Map<ApiInfo.ApiInfoKey, List<String>> fetched = buildSampleDataMapFromList(sampleDataList);
            for (ApiInfo.ApiInfoKey key : keysToFetch) {
                List<String> samples = fetched.getOrDefault(key, new ArrayList<>());
                cleanupSampleDataMap.put(key, samples);
                result.put(key, samples);
            }
        }

        return result;
    }

}
