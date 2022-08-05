package com.akto.testing;

import com.akto.dao.context.Context;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.type.URLMethods;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import kotlin.Pair;
import okhttp3.*;

import java.io.IOException;
import java.net.URI;
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
                System.out.println(e.getMessage());
                body = "{}";
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
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
        // don't lowercase url because query params will change and will result in incorrect request
        String url = httpRequestParams.url;
        url = url.trim();
        if (!url.startsWith("http")) {
            url = makeUrlAbsolute(url, httpRequestParams.getHeaders());
        }
        httpRequestParams.url = url;

        Request.Builder builder = new Request.Builder();

        // add headers
        List<String> forbiddenHeaders = Arrays.asList("content-length", "accept-encoding");
        Map<String, List<String>> headersMap = httpRequestParams.getHeaders();
        if (headersMap == null) headersMap = new HashMap<>();
        for (String headerName: headersMap.keySet()) {
            if (forbiddenHeaders.contains(headerName)) continue;
            List<String> headerValueList = headersMap.get(headerName);
            if (headerValueList == null || headerValueList.isEmpty()) continue;
            for (String headerValue: headerValueList) {
                if (headerValue == null) continue;
                builder.addHeader(headerName, headerValue);
            }
        }

        URLMethods.Method method = URLMethods.Method.valueOf(httpRequestParams.getMethod());

        builder = builder.url(url);

        HttpResponseParams httpResponseParams = null;
        switch (method) {
            case GET:
                httpResponseParams = getRequest(httpRequestParams, builder, url);
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


    public static HttpResponseParams getRequest(HttpRequestParams httpRequestParams, Request.Builder builder, String url)  throws Exception{
        String requestPayload = httpRequestParams.getPayload();
        if (requestPayload != null && !requestPayload.isEmpty()) {
            String rawQuery = getRawQueryFromJson(requestPayload);
            if (rawQuery != null) {
                String newUrl = url + "?" + rawQuery;
                builder.url(newUrl);
            }
        }

        Request request = builder.build();
        return common(request);
    }

    public static String getRawQueryFromJson(String requestPayload) {
        HttpUrl.Builder builder = new HttpUrl.Builder()
                .scheme("https")
                .host("www.google.com");

        BasicDBObject obj = BasicDBObject.parse(requestPayload);
        Set<String> keySet = obj.keySet();
        if (keySet.isEmpty()) return null;

        for(String key: keySet) {
            Object val = obj.get(key);
            builder.addQueryParameter(key, val.toString());
        }

        URI uri = builder.build().uri();

        return uri.getRawQuery();
    }


    public static HttpResponseParams sendWithRequestBody(HttpRequestParams httpRequestParams, Request.Builder builder) throws Exception {
        Map<String,List<String>> headers = httpRequestParams.getHeaders();
        if (headers == null) {
            headers = new HashMap<>();
            httpRequestParams.setHeaders(headers);
        }

        List<String> contentTypes = headers.get("content-type");
        String contentType = "application/json; charset=utf-8";
        if (contentTypes != null && !contentTypes.isEmpty()) {
             contentType = contentTypes.get(0);
        }
        RequestBody body = RequestBody.create(httpRequestParams.getPayload(), MediaType.parse(contentType));
        builder = builder.method(httpRequestParams.getMethod(), body);
        Request request = builder.build();
        return common(request);
    }

}
