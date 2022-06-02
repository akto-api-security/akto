package com.akto.testing;

import com.akto.DaoInit;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.URLMethods;
import com.akto.parsers.HttpCallParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
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

    public static HttpResponseParams common(Request request) throws Exception {
        Call call = client.newCall(request);
        Response response = null;
        String body;
        try {
            response = call.execute();
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new Exception("Couldn't read response body");
            }
            try {
                body = responseBody.string();
            } catch (IOException e) {
                e.printStackTrace();
                body = "{}";
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new Exception("Api Call failed");
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

    public static String makeUrlAbsolute(String url, Map<String, List<String>> reqHeaders) throws Exception {
        // get host from header
        List<String> hostHeaderValue = reqHeaders.get("host");
        if (hostHeaderValue == null || hostHeaderValue.size() == 0) throw new Exception("Host not found");
        String host = hostHeaderValue.get(0);
        if (host == null) throw new Exception("Host not found");
        if (!url.startsWith("/")) url = "/" + url;
        if (host.endsWith("/")) host = host.substring(0, host.length()-1);

        host = host.toLowerCase();
        if (!host.startsWith("http")) {
            List<String> protocolValues = reqHeaders.get("x-forwarded-proto");
            if (protocolValues != null && protocolValues.size() > 0) {
                String protocol = protocolValues.get(0);
                host = protocol + "://" + host;
            } else {
                String firstChar = host.split("")[0];
                try {
                    Integer.parseInt(firstChar);
                    host = "http://" + host;
                } catch (Exception e) {
                    host = "https://" + host;
                }
            }
        }

        url = host + url;

        return url;
    }

    public static HttpResponseParams sendRequest(HttpRequestParams httpRequestParams) throws Exception {
        String url = httpRequestParams.url.toLowerCase();
        url = url.trim();
        if (!url.startsWith("http")) {
            url = makeUrlAbsolute(url, httpRequestParams.getHeaders());
        }
        httpRequestParams.url = url;

        Request.Builder builder = new Request.Builder();

        // add headers
        Map<String, List<String>> headersMap = httpRequestParams.getHeaders();
        for (String headerName: headersMap.keySet()) {
            List<String> headerValueList = headersMap.get(headerName);
            if (headerValueList == null || headerValueList.isEmpty()) continue;
            for (String headerValue: headerValueList) {
                if (headerValue == null) continue;
                builder.addHeader(headerName, headerValue);
            }
        }

        URLMethods.Method method = URLMethods.Method.valueOf(httpRequestParams.getMethod());

        if (!method.equals(URLMethods.Method.GET)) { // GET url is added later in pipeline
            builder = builder.url(url);
        }

        HttpResponseParams httpResponseParams = null;
        switch (method) {
            case GET:
                httpResponseParams = getRequest(httpRequestParams, builder);
                break;
            case POST:
            case PUT:
            case DELETE:
            case HEAD:
            case OPTIONS:
            case TRACE:
                httpResponseParams = sendWithRequestBody(httpRequestParams, builder);
                break;
            case OTHER:
                throw new Exception("Invalid method name");
        }

        httpResponseParams.requestParams = httpRequestParams;
        return httpResponseParams;
    }

    public static HttpResponseParams getRequest(HttpRequestParams httpRequestParams, Request.Builder builder)  throws Exception{
        String url = httpRequestParams.getURL();
//        URI u = new URI(url);
//
//        StringBuilder sb = new StringBuilder(u.getQuery() == null ? "" : u.getQuery());
//
//        // add query params
//        String payload = httpRequestParams.getPayload();
//        JsonNode node = mapper.readTree(payload);
//        if (node.isObject()) {
//            Iterator<String> fieldNames = node.fieldNames();
//            while(fieldNames.hasNext()) {
//                String fieldName = fieldNames.next();
//                JsonNode fieldValue = node.get(fieldName);
//                if (fieldValue.isValueNode()) {
//                    if (sb.length() > 0) sb.append('&');
//                    sb.append(URLEncoder.encode(fieldName, "UTF-8"));
//                    sb.append('=');
//                    sb.append(URLEncoder.encode(fieldValue.asText(), "UTF-8")); //TODO: asText is not always the best option
//                }
//            }
//            u = new URI(u.getScheme(), u.getAuthority(), u.getPath(),
//                    sb.toString(), u.getFragment());
//        }

        builder = builder.url(url);
        Request request = builder.build();
        return common(request);
    }


    public static HttpResponseParams sendWithRequestBody(HttpRequestParams httpRequestParams, Request.Builder builder) throws Exception {
        RequestBody body = RequestBody.create(httpRequestParams.getPayload(), MediaType.parse("application/json; charset=utf-8"));
        builder = builder.method(httpRequestParams.getMethod(), body);
        Request request = builder.build();
        return common(request);
    }

}
