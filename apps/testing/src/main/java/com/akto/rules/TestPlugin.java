package com.akto.rules;

import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.testing.TestResult;
import com.akto.runtime.RelationshipSync;
import com.akto.utils.RedactSampleData;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.io.IOException;
import java.util.*;

import static com.akto.runtime.RelationshipSync.extractAllValuesFromPayload;


public abstract class TestPlugin {
    static ObjectMapper mapper = new ObjectMapper();
    static JsonFactory factory = mapper.getFactory();

    public abstract boolean start(ApiInfo.ApiInfoKey apiInfoKey, ObjectId testRunId);

    public abstract String testName();

    public static boolean isStatusGood(int statusCode) {
        return statusCode >= 200 && statusCode<300;
    }

    public static void extractAllValuesFromPayload(String payload, Map<String,Set<String>> payloadMap) throws Exception{
        JsonParser jp = factory.createParser(payload);
        JsonNode node = mapper.readTree(jp);
        RelationshipSync.extractAllValuesFromPayload(node,new ArrayList<>(),payloadMap);
    }

    public static double compareWithOriginalResponse(String originalPayload, String currentPayload) {
        Map<String, Set<String>> originalResponseParamMap = new HashMap<>();
        Map<String, Set<String>> currentResponseParamMap = new HashMap<>();
        try {
            extractAllValuesFromPayload(originalPayload, originalResponseParamMap);
            extractAllValuesFromPayload(currentPayload, currentResponseParamMap);
        } catch (Exception e) {
            return 0.0;
        }

        Set<String> visited = new HashSet<>();
        int matched = 0;
        for (String k1: originalResponseParamMap.keySet()) {
            if (visited.contains(k1)) continue;
            visited.add(k1);
            Set<String> v1 = originalResponseParamMap.get(k1);
            Set<String> v2 = currentResponseParamMap.get(k1);
            if (Objects.equals(v1, v2)) matched +=1;
        }

        for (String k1: currentResponseParamMap.keySet()) {
            if (visited.contains(k1)) continue;
            visited.add(k1);
            Set<String> v1 = originalResponseParamMap.get(k1);
            Set<String> v2 = currentResponseParamMap.get(k1);
            if (Objects.equals(v1, v2)) matched +=1;
        }

        return (100.0*matched)/visited.size();

    }

    public void addWithoutRequestError(ApiInfo.ApiInfoKey apiInfoKey, ObjectId testRunId, TestResult.TestError testError) {
        Bson filter = TestingRunResultDao.generateFilter(testRunId, apiInfoKey.getApiCollectionId(), apiInfoKey.url, apiInfoKey.method.name());
        Bson update = Updates.set("resultMap." + testName(), new TestResult(null,false, Collections.singletonList(testError)));
        TestingRunResultDao.instance.updateOne(filter, update);
    }

    public void addWithRequestError(ApiInfo.ApiInfoKey apiInfoKey, ObjectId testRunId, TestResult.TestError testError, HttpResponseParams httpResponseParams) {
        Bson filter = TestingRunResultDao.generateFilter(testRunId, apiInfoKey.getApiCollectionId(), apiInfoKey.url, apiInfoKey.method.name());

        String message = null;
        try {
            message = RedactSampleData.convertHttpRespToOriginalString(httpResponseParams);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Bson update = Updates.set("resultMap." + testName(), new TestResult(message,false, Collections.singletonList(testError)));
        TestingRunResultDao.instance.updateOne(filter, update);
    }


    public void addTestSuccessResult(ApiInfo.ApiInfoKey apiInfoKey, HttpResponseParams httpResponseParams, ObjectId testRunId, boolean vulnerable) {
        HttpRequestParams httpRequestParams = httpResponseParams.getRequestParams();

        String message = null;
        try {
            message = RedactSampleData.convertHttpRespToOriginalString(httpResponseParams);
        } catch (Exception e) {
            // TODO:
            e.printStackTrace();
            return;
        }

        Bson filter = TestingRunResultDao.generateFilter(testRunId, apiInfoKey);
        Bson update = Updates.set("resultMap." + testName(), new TestResult(message, vulnerable, new ArrayList<>()));
        TestingRunResultDao.instance.updateOne(filter, update);
    }

    public HttpResponseParams generateEmptyResponsePayload(HttpRequestParams httpRequestParams) {
        return new HttpResponseParams(
                "", 0,"", new HashMap<>(), null, httpRequestParams, Context.now(),
                1_000_000+"",false, HttpResponseParams.Source.OTHER, "",""
        );
    }

    public boolean containsRequestPayload(HttpRequestParams httpRequestParams) {
        String url = httpRequestParams.getURL();
        if (url.contains("INTEGER") || url.contains("STRING") || url.contains("?")) return true;

        String payload = httpRequestParams.getPayload();
        if (payload == null || payload.equals("{}") || payload.isEmpty()) return false;

        return true;
    }

}
