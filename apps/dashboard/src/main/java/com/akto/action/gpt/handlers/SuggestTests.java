package com.akto.action.gpt.handlers;

import com.akto.action.ExportSampleDataAction;
import com.akto.action.gpt.GptAction;
import com.akto.action.gpt.result_fetchers.ResultFetcherStrategy;
import com.akto.dto.OriginalHttpRequest;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

public class SuggestTests implements QueryHandler{

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(GenerateCurlForTest.class);
    public static final String AUTH_TOKEN = "%AUTH_TOKEN%";
    public static final String ACCESS_TOKEN = "%ACCESS_TOKEN%";
    public static final String COOKIE = "%COOKIE%";

    private String auth_token_value= null;
    private String access_token_value= null;
    private String cookie_value= null;

    private final ResultFetcherStrategy<BasicDBObject> resultFetcherStrategy;

    public SuggestTests(ResultFetcherStrategy<BasicDBObject> resultFetcherStrategy) {
        this.resultFetcherStrategy = resultFetcherStrategy;
    }

    @Override
    public BasicDBObject handleQuery(BasicDBObject meta) {
        BasicDBObject request = new BasicDBObject();
        String curl = "";
        String sampleData = meta.getString("sample_data");
        String modifiedSampleData = minifyRequestHeaders(sampleData);
        try {
            curl = ExportSampleDataAction.getCurl(modifiedSampleData);
            System.out.println("curl: " + curl);
        } catch (Exception e) {
            e.printStackTrace();
        }
        request.put("query_type", GptQuery.SUGGEST_TESTS.getName());
        request.put("curl", curl);
        request.put("response_details", meta.getString("response_details"));
        request.put(GptAction.USER_EMAIL, meta.getString(GptAction.USER_EMAIL));
        System.out.println("request: " + request.toJson());
        BasicDBObject resp =  this.resultFetcherStrategy.fetchResult(request);
        String respStr = resp.toJson();
        respStr = populateRequestHeaders(respStr);
        return BasicDBObject.parse(respStr);
    }

    private String populateRequestHeaders(String respStr) {
        if(auth_token_value != null) {
            respStr = respStr.replace(AUTH_TOKEN, auth_token_value);
        }
        if(access_token_value != null) {
            respStr = respStr.replace(ACCESS_TOKEN, access_token_value);
        }
        if (cookie_value != null) {
            respStr = respStr.replace(COOKIE, cookie_value);
        }
        return respStr;
    }

    private static final Gson gson = new Gson();
    private String minifyRequestHeaders(String sampleData) {
        try {
            Map<String, Object> json = gson.fromJson(sampleData, Map.class);
            Map<String, String> requestHeaders = convertMap(OriginalHttpRequest.buildHeadersMap(json, "requestHeaders"));
            for (Map.Entry<String, String> reqHeaderKeyValue : requestHeaders.entrySet()) {
                String key = reqHeaderKeyValue.getKey();
                if (StringUtils.containsAnyIgnoreCase(key, "Authorization")) {
                    auth_token_value = reqHeaderKeyValue.getValue();
                    requestHeaders.put(key, AUTH_TOKEN);
                } else if (StringUtils.containsAnyIgnoreCase(key, "access-token")) {
                    access_token_value = reqHeaderKeyValue.getValue();
                    requestHeaders.put(key, ACCESS_TOKEN);
                } else if (StringUtils.containsAnyIgnoreCase(key, "cookie")) {
                    cookie_value = reqHeaderKeyValue.getValue();
                    requestHeaders.put(key, COOKIE);
                }
            }
            String modifiedRequestHeaders = gson
                    .toJson(requestHeaders)
                    .replaceAll("\"", "\\\"");
            json.put("requestHeaders", modifiedRequestHeaders);
            return gson.toJson(json);
        } catch (Exception e){
            logger.error("Error while purifying sample data", e);
            return sampleData;
        }
    }

    private Map<String, String> convertMap(Map<String, List<String>> requestHeaders) {
        Map<String, String> result = new java.util.HashMap<>();
        for (Map.Entry<String, List<String>> entry : requestHeaders.entrySet()) {
            String key = entry.getKey();
            List<String> value = entry.getValue();
            if (value != null && !value.isEmpty()) {
                result.put(key, value.get(0));
            }
        }
        return result;
    }
}
