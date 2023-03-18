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

import java.util.*;

public class SSRFOnAwsMetadataEndpoint extends TestPlugin {

    private final String testRunId;
    private final String testRunResultSummaryId;
    private static final String URL = "{{metadata_url}}";
    private static final String URL_TEMP = "url_temp";
    public SSRFOnAwsMetadataEndpoint(String testRunId, String testRunResultSummaryId) {
        this.testRunId = testRunId;
        this.testRunResultSummaryId = testRunResultSummaryId;
    }

    @Override
    public Result start(ApiInfo.ApiInfoKey apiInfoKey, TestingUtil testingUtil) {
        List<RawApi> messages = SampleMessageStore.fetchAllOriginalMessages(apiInfoKey, testingUtil.getSampleMessages());
        RawApi rawApi = null;
        boolean flag = false;
        for (RawApi message: messages) {
            if (message.getResponse().getStatusCode() != 200) continue;
            rawApi = message.copy();
            OriginalHttpRequest req = rawApi.getRequest();

            // find if there is any url in header
            Map<String, List<String>> reqHeaders = req.getHeaders();
            for (String key: reqHeaders.keySet()) {
                List<String> values = reqHeaders.get(key);
                if (values == null) values = new ArrayList<>();
                for (int idx=0; idx<values.size(); idx++) {
                    String v = values.get(idx);
                    if (detectUrl(v)) {
                        flag = true;
                        values.set(idx, URL);
                    }
                }
            }

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
                    modifiedQueryParamString = modifiedQueryParamString.replaceAll(URL_TEMP, URL);
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
                for (String k : payloadKeysToFuzz) store.put(k, URL);

                String modifiedPayload = JSONUtils.modify(jsonBody, payloadKeysToFuzz, new SetValueModifier(store));
                req.setBody(modifiedPayload);
            }

            if (flag) break;
        }

        if (rawApi == null) return null;

        System.out.println("******************");
        System.out.println(rawApi.getRequest().toString());
        System.out.println("******************");


        String templateUrl = "https://raw.githubusercontent.com/bhavik-dand/tests-library/temp/ssrf_aws_endpoint/SSRF/ssrf_aws_metadata_endpoint.yaml";
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
            return fuzzingTest.runNucleiTest(rawApi);
        } catch (Exception e ) {
            return null;
        }

    }

    private boolean detectUrl(String value) {
        if (value.contains("http://") || value.contains("https://")) {
            return true;
        }
        return false;
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

    @Override
    public String superTestName() {
        return "SSRF";
    }

    @Override
    public String subTestName() {
        return "SSRF_AWS_METADATA_EXPOSED";
    }
}
