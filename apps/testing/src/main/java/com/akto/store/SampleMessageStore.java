package com.akto.store;

import com.akto.dao.SampleDataDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
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


    public static HttpResponseParams fetchOriginalMessage(ApiInfo.ApiInfoKey apiInfoKey) {
        List<String> samples = sampleDataMap.get(apiInfoKey);
        if (samples == null || samples.isEmpty()) return null;
        String message = samples.get(0);
        try {
            return HttpCallParser.parseKafkaMessage(message);
        } catch(Exception e) {
            return null;
        }
    }

    public static HttpRequestParams fetchPath(ApiInfo.ApiInfoKey apiInfoKey) {
        HttpResponseParams httpResponseParams = fetchOriginalMessage(apiInfoKey);
        if (httpResponseParams == null) return null;
        return httpResponseParams.getRequestParams();
    }

    // TODO: if being used then make sure to add unit tests
    public static HttpRequestParams fetchHappyPath(ApiInfo.ApiInfoKey apiInfoKey) {
        List<String> samples = sampleDataMap.get(apiInfoKey);
        if (samples == null || samples.isEmpty()) return null;

        ListIterator<String> iter = samples.listIterator();
        while(iter.hasNext()){
            String message = iter.next();

            int statusCode = 0;
            HttpRequestParams httpRequestParams;
            try {
                HttpResponseParams httpResponseParams = HttpCallParser.parseKafkaMessage(message);
                httpRequestParams = httpResponseParams.getRequestParams();
                HttpResponseParams result = ApiExecutor.sendRequest(httpRequestParams);
                statusCode = result.getStatusCode();
            } catch (Exception e) {
                e.printStackTrace();
                iter.remove();
                continue;
            }

            // not happy path remove from list
            if (statusCode < 200 || statusCode >= 300) {
                iter.remove();
            } else {
                return httpRequestParams;
            }
        }

        return null;
    }

}
