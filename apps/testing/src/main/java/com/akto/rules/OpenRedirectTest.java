package com.akto.rules;

import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.testing.TestResult;
import com.akto.dto.type.RequestTemplate;
import com.akto.log.LoggerMaker;
import com.akto.store.SampleMessageStore;
import com.akto.store.TestingUtil;
import com.akto.util.HttpRequestResponseUtils;
import com.akto.util.JSONUtils;
import com.akto.util.modifier.NestedObjectModifier;
import com.akto.util.modifier.SetValueModifier;
import com.mongodb.BasicDBObject;

import java.util.*;

public class OpenRedirectTest extends TestPlugin {

    private final String testRunId;
    private final String testRunResultSummaryId;

    private final static String REDIRECT_KEYWORD = "{{redirect}}";
    private final static String REDIRECT_KEYWORD_TEMP = "AKTOREDIRECT";


    public OpenRedirectTest(String testRunId, String testRunResultSummaryId) {
        this.testRunId = testRunId;
        this.testRunResultSummaryId = testRunResultSummaryId;
    }
    @Override
    public Result start(ApiInfo.ApiInfoKey apiInfoKey, TestingUtil testingUtil) {
        List<RawApi> messages = SampleMessageStore.fetchAllOriginalMessages(apiInfoKey, testingUtil.getSampleMessages());
        RawApi rawApi = null;
        String location = null;

        // todo: check if location doesn't come from request
        boolean flag = false;

        for (RawApi message: messages) {
            if (message.getResponse().getStatusCode() != 302) continue;

            rawApi = message.copy();
            OriginalHttpRequest req = rawApi.getRequest();
            OriginalHttpResponse resp = rawApi.getResponse();

            // find original redirect location
            location = resp.findHeaderValue("location");

            // find if location is being passed in header
            Map<String, List<String>> reqHeaders = req.getHeaders();
            for (String key: reqHeaders.keySet()) {
                List<String> values = reqHeaders.get(key);
                if (values == null) values = new ArrayList<>();
                for (int idx=0; idx<values.size(); idx++) {
                    String v = values.get(idx);
                    if (v.equalsIgnoreCase(location)) {
                        flag = true;
                        values.set(idx, REDIRECT_KEYWORD);
                    }
                }
            }

            // find if location is being passed in queryParams
            String queryJson = HttpRequestResponseUtils.convertFormUrlEncodedToJson(req.getQueryParams());
            if (queryJson != null) {
                BasicDBObject queryObj = BasicDBObject.parse(queryJson);
                for (String key: queryObj.keySet()) {
                    Object valueObj = queryObj.get(key);
                    if (valueObj == null) continue;
                    String value = valueObj.toString();
                    if (value.equalsIgnoreCase(location)) {
                        flag = true;
                        queryObj.put(key, REDIRECT_KEYWORD_TEMP);
                    }
                }
                String modifiedQueryParamString = OriginalHttpRequest.getRawQueryFromJson(queryObj.toJson());
                if (modifiedQueryParamString != null) {
                    modifiedQueryParamString = modifiedQueryParamString.replaceAll(REDIRECT_KEYWORD_TEMP, REDIRECT_KEYWORD);
                    req.setQueryParams(modifiedQueryParamString);
                }
            }

            // find if location is being passed in request body
            String jsonBody = req.getJsonRequestBody();
            BasicDBObject payload = RequestTemplate.parseRequestPayload(jsonBody, null);
            Map<String, Set<Object>> flattenedPayload = JSONUtils.flatten(payload);
            Set<String> payloadKeysToFuzz = new HashSet<>();
            for (String key: flattenedPayload.keySet()) {
                Set<Object> values = flattenedPayload.get(key);
                for (Object v: values) {
                    if (v !=null && v.equals(location))  {
                        flag = true;
                        payloadKeysToFuzz.add(key);
                    }
                }
            }

            Map<String, Object> store = new HashMap<>();
            for (String k: payloadKeysToFuzz) store.put(k, REDIRECT_KEYWORD);

            String modifiedPayload = JSONUtils.modify(jsonBody, payloadKeysToFuzz, new SetValueModifier(store));
            req.setBody(modifiedPayload);

            if (flag) break;
        }

        if (rawApi == null) return null;

        System.out.println("******************");
        System.out.println(rawApi.getRequest().toString());
        System.out.println("******************");


        String origTemplatePath = "https://raw.githubusercontent.com/avneesh-akto/tests-library/temp/redirect_file/redirect_own.yaml";
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
        valuesMap.put("Body", rawApi.getRequest().getBody());
        valuesMap.put("OriginalLocationUrl", location);
        FuzzingTest fuzzingTest = new FuzzingTest(
                testRunId, testRunResultSummaryId, origTemplatePath,subTestName(), testSourceConfigCategory, valuesMap
        );
        try {
            return fuzzingTest.runNucleiTest(rawApi);
        } catch (Exception e ) {
            return null;
        }
    }

    @Override
    public String superTestName() {
        return "MISCONFIGURATION";
    }

    @Override
    public String subTestName() {
        return "OPEN_REDIRECT";
    }
}
