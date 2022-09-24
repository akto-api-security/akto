package com.akto.store;

import com.akto.dao.SampleDataDao;
import com.akto.dto.*;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.testing.*;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class SampleMessageStore {

    public static Map<ApiInfo.ApiInfoKey, List<String>> sampleDataMap = new HashMap<>();
    public static Map<String, SingleTypeInfo> singleTypeInfos = new HashMap<>();

    private static final Logger logger = LoggerFactory.getLogger(SampleMessageStore.class);
    public static void buildSingleTypeInfoMap(TestingEndpoints testingEndpoints) {
        if (testingEndpoints == null) return;
        TestingEndpoints.Type type = testingEndpoints.getType();
        List<SingleTypeInfo> singleTypeInfoList = new ArrayList<>();
        singleTypeInfos = new HashMap<>();
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

                if (apiInfoKeys.size() == 0) {
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
                singleTypeInfo.clearValues();
                singleTypeInfos.put(singleTypeInfo.composeKeyWithCustomSubType(SingleTypeInfo.GENERIC), singleTypeInfo);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static SingleTypeInfo findSti(String param, boolean isUrlParam,
                                         ApiInfo.ApiInfoKey apiInfoKey, boolean isHeader, int responseCode) {

        String key = SingleTypeInfo.composeKey(
                apiInfoKey.url, apiInfoKey.method.name(), responseCode, isHeader,
                param,SingleTypeInfo.GENERIC, apiInfoKey.getApiCollectionId(), isUrlParam
        );

        return singleTypeInfos.get(key);
    }

    public static void fetchSampleMessages() {
        List<SampleData> sampleDataList = SampleDataDao.instance.findAll(new BasicDBObject());
        System.out.println("SampleDataSize " + sampleDataList.size());
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



    public static List<RawApi> fetchAllOriginalMessages(ApiInfoKey apiInfoKey) {
        List<RawApi> messages = new ArrayList<>();

        List<String> samples = sampleDataMap.get(apiInfoKey);
        if (samples == null || samples.isEmpty()) return messages;

        for (String message: samples) {
            try {
                OriginalHttpRequest request = new OriginalHttpRequest();
                request.buildFromSampleMessage(message);

                OriginalHttpResponse response = new OriginalHttpResponse();
                response.buildFromSampleMessage(message);

                messages.add(new RawApi(request, response, message));

            } catch(Exception ignored) { }

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

}
