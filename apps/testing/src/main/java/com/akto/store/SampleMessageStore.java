package com.akto.store;

import com.akto.dao.SampleDataDao;
import com.akto.dto.*;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.parsers.HttpCallParser;
import com.akto.testing.ApiExecutor;
import com.akto.utils.RedactSampleData;
import com.mongodb.BasicDBObject;

import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

public class SampleMessageStore {

    public static Map<ApiInfo.ApiInfoKey, List<String>> sampleDataMap = new HashMap<>();

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


    public static RawApi fetchOriginalMessage(ApiInfo.ApiInfoKey apiInfoKey) {
        List<String> samples = sampleDataMap.get(apiInfoKey);
        if (samples == null || samples.isEmpty()) return null;
        String message = samples.get(0);
        try {
            OriginalHttpRequest request = new OriginalHttpRequest();
            request.buildFromSampleMessage(message);

            OriginalHttpResponse response = new OriginalHttpResponse();
            response.buildFromSampleMessage(message);

            return new RawApi(request, response);
        } catch(Exception e) {
            return null;
        }
    }

}
