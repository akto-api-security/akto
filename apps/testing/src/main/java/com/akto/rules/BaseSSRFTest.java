package com.akto.rules;

import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.RawApi;
import com.akto.dto.testing.TestResult;
import com.akto.dto.type.RequestTemplate;
import com.akto.log.LoggerMaker;
import com.akto.store.SampleMessageStore;
import com.akto.store.TestingUtil;
import com.akto.util.HttpRequestResponseUtils;
import com.akto.util.JSONUtils;
import com.akto.util.modifier.SetValueModifier;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public abstract class BaseSSRFTest extends TestPlugin{

    private final String testRunId;
    private final String testRunResultSummaryId;

    private static final String URL_TEMP = "url_temp";
    public BaseSSRFTest(String testRunId, String testRunResultSummaryId) {
        this.testRunId = testRunId;
        this.testRunResultSummaryId = testRunResultSummaryId;
    }


    @Override
    public Result start(ApiInfo.ApiInfoKey apiInfoKey, TestingUtil testingUtil) {
        List<RawApi> messages = SampleMessageStore.fetchAllOriginalMessages(apiInfoKey, testingUtil.getSampleMessages());
        RawApi rawApi = null;
        boolean flag = false;
        for (RawApi message: messages) {
            rawApi = message.copy();
            if(isResponseStatusCodeAllowed(rawApi.getResponse().getStatusCode())) continue;
            OriginalHttpRequest req = rawApi.getRequest();

            // find if url is present in queryParams
            String queryJson = HttpRequestResponseUtils.convertFormUrlEncodedToJson(req.getQueryParams());
            if (queryJson != null) {
                BasicDBObject queryObj = BasicDBObject.parse(queryJson);
                for (String key: queryObj.keySet()) {
                    Object valueObj = queryObj.get(key);
                    if (valueObj == null) continue;
                    String value = valueObj.toString();
                    if (detectUrl(value)) {
                        flag = true;
                        queryObj.put(key, URL_TEMP);
                    }
                }
                String modifiedQueryParamString = OriginalHttpRequest.getRawQueryFromJson(queryObj.toJson());
                if (modifiedQueryParamString != null) {
                    modifiedQueryParamString = modifiedQueryParamString.replaceAll(URL_TEMP, getUrlPlaceholder());
                    req.setQueryParams(modifiedQueryParamString);
                }
            }

            // find if url is present in request body
            String jsonBody = req.getJsonRequestBody();
            if(jsonBody.length() > 0) {
                BasicDBObject payload = RequestTemplate.parseRequestPayload(jsonBody, null);
                Map<String, Set<Object>> flattenedPayload = JSONUtils.flatten(payload);
                Set<String> payloadKeysToFuzz = new HashSet<>();
                for (String key : flattenedPayload.keySet()) {
                    Set<Object> values = flattenedPayload.get(key);
                    for (Object v : values) {
                        if (v != null && detectUrl(v)) {
                            flag = true;
                            payloadKeysToFuzz.add(key);
                        }
                    }
                }

                Map<String, Object> store = new HashMap<>();
                for (String k : payloadKeysToFuzz) store.put(k, getUrlPlaceholder());

                String modifiedPayload = JSONUtils.modify(jsonBody, payloadKeysToFuzz, new SetValueModifier(store));
                req.setBody(modifiedPayload);
            }

            if (flag) break;
        }

        if (rawApi == null || !flag) return null;

        System.out.println("******************");
        System.out.println(rawApi.getRequest().toString());
        System.out.println("******************");


        String templateUrl = getTemplateUrl();
        String testSourceConfigCategory = "";

        Map<String, Object> valuesMap = new HashMap<>();
        valuesMap.put("Method", apiInfoKey.method);
        String baseUrl;
        try {
            baseUrl = rawApi.getRequest().getFullUrlIncludingDomain();
            baseUrl = OriginalHttpRequest.getFullUrlWithParams(baseUrl,rawApi.getRequest().getQueryParams());
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while getting full url including domain: " + e, LoggerMaker.LogDb.TESTING);
            return addWithRequestError( rawApi.getOriginalMessage(), TestResult.TestError.FAILED_BUILDING_URL_WITH_DOMAIN,rawApi.getRequest(), null);
        }


        valuesMap.put("BaseURL", baseUrl);
        valuesMap.put("Body", rawApi.getRequest().getBody() == null ? "" : rawApi.getRequest().getBody());

        FuzzingTest fuzzingTest = new FuzzingTest(
                testRunId, testRunResultSummaryId, templateUrl,subTestName(), testSourceConfigCategory, valuesMap
        );
        try {
            Result result = fuzzingTest.runNucleiTest(rawApi);
            return processResult(result);
        } catch (Exception e ) {
            return null;
        }

    }

    protected abstract String getTemplateUrl();

    private boolean detectUrl(String value) {
        return value.contains("http");
    }

    private boolean detectUrl(Object value) {
        if (value instanceof String) {
            return detectUrl((String) value);
        }
        else if(value instanceof BasicDBObject){
            BasicDBObject obj = (BasicDBObject) value;
            for (String key: obj.keySet()) {
                Object v = obj.get(key);
                if (v != null) {
                    if (detectUrl(v)) {
                        return true;
                    }
                }
            }
            return false;
        }
        else if(value instanceof BasicDBList){
            BasicDBList list = (BasicDBList) value;
            for (Object v: list) {
                if (v != null) {
                    if (detectUrl(v)) {
                        return true;
                    }
                }
            }
            return false;
        }
        return false;
    }


    protected abstract String getUrlPlaceholder();

    protected abstract boolean isResponseStatusCodeAllowed(int statusCode);

    protected Result processResult(Result result) {
        return result;
    }


}
