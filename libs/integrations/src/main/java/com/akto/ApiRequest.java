package com.akto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ApiRequest {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final OkHttpClient client = new OkHttpClient();
    private static OkHttpClient commonClient = new OkHttpClient();

    public static void initCommonHttpClient(OkHttpClient client){
        commonClient = client.newBuilder().build();
    }

    public static JsonNode common(Request request) {
        Call call = commonClient.newCall(request);
        Response response;
        try {
            response = call.execute();
        } catch (IOException e) {
            // TODO: logger
            e.printStackTrace();
            return null;
        }
        ResponseBody responseBody = response.body();
        if (responseBody == null) {
            return null;
        }
        String body;
        try {
            body = responseBody.string();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        try {
            return mapper.readValue(body, JsonNode.class);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static JsonNode processWithTimeout(Request request, TimeoutObject timeoutObj) throws Exception {

        OkHttpClient clientWithTimeout;
        Call call;

        if (timeoutObj != null) {
            clientWithTimeout = new OkHttpClient.Builder()
            .connectTimeout(timeoutObj.getConnectTimeout(), TimeUnit.SECONDS)
            .readTimeout(timeoutObj.getReadTimeout(), TimeUnit.SECONDS)
            .writeTimeout(timeoutObj.getWriteTimeout(), TimeUnit.SECONDS)
            .build();
            call = clientWithTimeout.newCall(request);
        } else {
            call = client.newCall(request);
        }
                
        Response response;
        try {
            response = call.execute();
        } catch (IOException e) {
            e.printStackTrace();
            throw new Exception(e.getMessage());
        }
        ResponseBody responseBody = response.body();
        if (responseBody == null) {
            return null;
        }
        String body;
        try {
            body = responseBody.string();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        try {
            return mapper.readValue(body, JsonNode.class);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static JsonNode getRequest(Map<String, String> headersMap, String url) {
        Request.Builder builder = new Request.Builder().url(url);
        for (String key: headersMap.keySet()) {
            builder.addHeader(key, headersMap.get(key));
        }

        Request request = builder.build();
        return common(request);

    }

    public static JsonNode postRequest(Map<String, String> headersMap, String url, String json) {
        RequestBody body = RequestBody.create( json, MediaType.parse("application/json; charset=utf-8"));

        Request.Builder builder = new Request.Builder().post(body).url(url);
        for (String key: headersMap.keySet()) {
            builder.addHeader(key, headersMap.get(key));
        }
        Request request = builder.build();

        return common(request);
    }

    public static JsonNode postRequestWithTimeout(Map<String, String> headersMap, String url, String json, TimeoutObject timeoutObj) throws Exception {
        RequestBody body = RequestBody.create( json, MediaType.parse("application/json; charset=utf-8"));

        Request.Builder builder = new Request.Builder().post(body).url(url);
        for (String key: headersMap.keySet()) {
            builder.addHeader(key, headersMap.get(key));
        }
        Request request = builder.build();

        return processWithTimeout(request, timeoutObj);
    }

    public static JsonNode putRequest(Map<String, String> headersMap, String url, String json) {
        RequestBody body = RequestBody.create( json, MediaType.parse("application/json; charset=utf-8"));

        Request.Builder builder = new Request.Builder().put(body).url(url);
        for (String key: headersMap.keySet()) {
            builder.addHeader(key, headersMap.get(key));
        }
        Request request = builder.build();

        return common(request);
    }

    public static JsonNode deleteRequest(Map<String, String> headersMap, String url) {
        Request.Builder builder = new Request.Builder().url(url).delete();
        for (String key: headersMap.keySet()) {
            builder.addHeader(key, headersMap.get(key));
        }

        
        Request request = builder.build();
        return common(request);
    }

}
