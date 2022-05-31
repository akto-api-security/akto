package com.akto.testing;

import com.akto.dao.context.Context;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kotlin.Pair;
import okhttp3.*;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class ApiExecutor {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final OkHttpClient client = new OkHttpClient().newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    public static HttpResponseParams common(Request request) {
        Call call = client.newCall(request);
        Response response = null;
        String body;
        try {
            response = call.execute();
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                return null;
            }
            try {
                body = responseBody.string();
            } catch (IOException e) {
                e.printStackTrace();
                body = "{}";
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            if (response != null) {
                response.close();
            }
        }


        int statusCode = response.code();
        String status = response.message();
        Headers headers = response.headers();
        Iterator<Pair<String, String>> headersIterator = headers.iterator();
        Map<String, List<String>> responseHeaders = new HashMap<>();
        while (headersIterator.hasNext()) {
            Pair<String,String> v = headersIterator.next();
            String headerKey = v.getFirst();
            if (!responseHeaders.containsKey(headerKey)) {
                responseHeaders.put(headerKey, new ArrayList<>());
            }
            String headerValue = v.getSecond();
            responseHeaders.get(headerKey).add(headerValue);
        }

        return new HttpResponseParams("", statusCode, status, responseHeaders, body, null, Context.now(), 1_000_000+"", false, HttpResponseParams.Source.OTHER, "", "");
    }

    public static HttpResponseParams makeRequest(HttpRequestParams httpRequestParams) throws URISyntaxException, UnsupportedEncodingException, JsonProcessingException {
        // initiate builder
        Request.Builder builder = new Request.Builder();

        // add headers
        Map<String, List<String>> headersMap = httpRequestParams.getHeaders();
        for (String headerName: headersMap.keySet()) {
            for (String headerValue: headersMap.get(headerName)) {
                if (headerValue == null) continue;
                builder.addHeader(headerName, headerValue);
            }
        }

        String method = httpRequestParams.getMethod();
        if (!method.equals("GET")) { // GET url is added later in pipeline
            String url = httpRequestParams.getURL();
            builder = builder.url(url);
        }

        HttpResponseParams httpResponseParams = null;
        switch (method) {
            case "GET":
                httpResponseParams = getRequest(httpRequestParams, builder);
                break;
            case "POST":
                httpResponseParams = postRequest(httpRequestParams, builder);
                break;
            case "PUT":
                httpResponseParams = putRequest(httpRequestParams, builder);
                break;
            case "DELETE":
                httpResponseParams = deleteRequest(httpRequestParams, builder);
                break;
            default:
                return null;
        }

        httpResponseParams.requestParams = httpRequestParams;
        return httpResponseParams;
    }

    public static HttpResponseParams getRequest(HttpRequestParams httpRequestParams, Request.Builder builder) throws URISyntaxException, JsonProcessingException, UnsupportedEncodingException {
        String url = httpRequestParams.getURL();
        URI u = new URI(url);

        StringBuilder sb = new StringBuilder(u.getQuery() == null ? "" : u.getQuery());

        // add query params
        String payload = httpRequestParams.getPayload();
        JsonNode node = mapper.readTree(payload);
        if (node.isObject()) {
            Iterator<String> fieldNames = node.fieldNames();
            while(fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                JsonNode fieldValue = node.get(fieldName);
                if (fieldValue.isValueNode()) {
                    if (sb.length() > 0) sb.append('&');
                    sb.append(URLEncoder.encode(fieldName, "UTF-8"));
                    sb.append('=');
                    sb.append(URLEncoder.encode(fieldValue.asText(), "UTF-8")); //TODO: asText is not always the best option
                }
            }
            u = new URI(u.getScheme(), u.getAuthority(), u.getPath(),
                    sb.toString(), u.getFragment());
        }


        System.out.println(u.toString());
        builder = builder.url(u.toString());
        Request request = builder.build();
        return common(request);
    }

    public static HttpResponseParams postRequest(HttpRequestParams httpRequestParams, Request.Builder builder) {
        RequestBody body = RequestBody.create(httpRequestParams.getPayload(), MediaType.parse("application/json; charset=utf-8"));
        builder = builder.post(body);
        Request request = builder.build();

        return common(request);
    }

    public static HttpResponseParams putRequest(HttpRequestParams httpRequestParams, Request.Builder builder) {
        RequestBody body = RequestBody.create( httpRequestParams.getPayload(), MediaType.parse("application/json; charset=utf-8"));
        builder = builder.put(body);
        Request request = builder.build();
        return common(request);
    }

    public static HttpResponseParams deleteRequest(HttpRequestParams httpRequestParams, Request.Builder builder) {
        builder = builder.delete();
        Request request = builder.build();
        return common(request);
    }

}
