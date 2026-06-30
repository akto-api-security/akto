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
            Key key = sampleData.getId();
            ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(key.getApiCollectionId(), key.getUrl(), key.getMethod());
            
            List<String> dataToAdd = new ArrayList<>();
            
            // For samples, use as-is (handles both HTTP and WebSocket connection samples)
            if (sampleData.getSamples() != null) {
                dataToAdd.addAll(sampleData.getSamples());
            } else {
                // Skip if no samples (don't use events - they require special handling)
                continue;
            }
            
            if (tempSampleDataMap.containsKey(apiInfoKey)) {
                tempSampleDataMap.get(apiInfoKey).addAll(dataToAdd);
            } else {
                tempSampleDataMap.put(apiInfoKey, dataToAdd);
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
            messages.addAll(buildRawApisFromSampleList(samples, apiInfoKey.toString()));

            if (messages.isEmpty()) {
                List<String> dbSamples = sampleDataMap.get(apiInfoKey);
                if (dbSamples == null || dbSamples.isEmpty())
                    return messages;

                messages.addAll(buildRawApisFromSampleList(dbSamples, apiInfoKey.toString()));
            }
            return messages;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error while fetching all original messages for "+ apiInfoKey +" : " + e, LogDb.TESTING);
        }
        return messages;
    }

    /**
     * Builds RawApi instances from a list of raw sample strings.
     * For WebSocket endpoints all samples are merged into one representative RawApi
     * (incoming frame → request body, outgoing frame → response body).
     * For HTTP endpoints one RawApi is produced per sample.
     */
    private static List<RawApi> buildRawApisFromSampleList(List<String> rawMessages, String apiInfoKeyStr) {
        if (rawMessages == null || rawMessages.isEmpty()) return Collections.emptyList();

        // Detect WebSocket by peeking at the first non-null message.
        for (String msg : rawMessages) {
            if (msg != null && !msg.isEmpty()) {
                if (msg.contains("\"WEBSOCKET\"")) {
                    RawApi merged = RawApi.buildFromWebSocketSamples(rawMessages);
                    return merged != null ? Collections.singletonList(merged) : Collections.emptyList();
                }
                break;
            }
        }

        // HTTP path — build one RawApi per sample.
        List<RawApi> result = new ArrayList<>();
        for (String message : rawMessages) {
            try {
                result.add(RawApi.buildFromMessage(message, true));
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error while building RawAPI for " + apiInfoKeyStr + " : " + e.getMessage());
            }
        }
        return result;
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
