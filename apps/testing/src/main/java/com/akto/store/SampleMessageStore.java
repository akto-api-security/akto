package com.akto.store;

import com.akto.dao.ParamTypeInfoDao;
import com.akto.dao.SampleDataDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.testing.CollectionWiseTestingEndpoints;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.APICatalog;
import com.akto.dto.type.ParamTypeInfo;
import com.akto.parsers.HttpCallParser;
import com.akto.testing.ApiExecutor;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class SampleMessageStore {

    public static Map<ApiInfo.ApiInfoKey, List<String>> sampleDataMap = new HashMap<>();
    public static Map<String, ParamTypeInfo> paramTypeInfoMap = new HashMap<>();

    private static final Logger logger = LoggerFactory.getLogger(SampleMessageStore.class);
    public static void buildParameterInfoMap(TestingEndpoints testingEndpoints) {
        if (testingEndpoints == null) return;
        TestingEndpoints.Type type = testingEndpoints.getType();
        List<ParamTypeInfo> paramTypeInfoList = new ArrayList<>();
        paramTypeInfoMap = new HashMap<>();
        try {
            if (type.equals(TestingEndpoints.Type.COLLECTION_WISE)) {
                CollectionWiseTestingEndpoints collectionWiseTestingEndpoints = (CollectionWiseTestingEndpoints) testingEndpoints;
                int apiCollectionId = collectionWiseTestingEndpoints.getApiCollectionId();
                paramTypeInfoList = ParamTypeInfoDao.instance.findAll(Filters.eq(ParamTypeInfo.API_COLLECTION_ID, apiCollectionId));
            } else {
                logger.error("ONLY COLLECTION TYPE TESTING ENDPOINTS ALLOWED");
            }

            for (ParamTypeInfo paramTypeInfo: paramTypeInfoList) {
                paramTypeInfoMap.put(paramTypeInfo.composeKey(), paramTypeInfo);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static ParamTypeInfo buildParamTypeInfo(String param, boolean isUrlParam,
                                                   ApiInfo.ApiInfoKey apiInfoKey, boolean isHeader) {

        String url = apiInfoKey.url;
        // this is done because of a bug in runtime where some static urls lose their leading slash
        if (!APICatalog.isTemplateUrl(url) && !url.startsWith("/")) {
            url = "/" + url;
        }

        return new ParamTypeInfo(
                apiInfoKey.getApiCollectionId(), url, apiInfoKey.method.name(), -1, isHeader,
                isUrlParam, param);

    }

    public static State findState(String key) {

        ParamTypeInfo paramTypeInfo = paramTypeInfoMap.get(key);
        if (paramTypeInfo == null) {
            return State.NA;
        }

        long publicCount = paramTypeInfo.getPublicCount();
        long uniqueCount = paramTypeInfo.getUniqueCount();

        double v = (1.0*publicCount) / uniqueCount;
        if (v <= ParamTypeInfo.THRESHOLD) {
            return State.PRIVATE;
        } else {
            return State.PUBLIC;
        }

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

    public enum State {
        PUBLIC, PRIVATE, NA
    }

}
