package com.akto.testing;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
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

    public static OriginalHttpResponse common(Request request) throws Exception {
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

        return new OriginalHttpResponse(body, responseHeaders, statusCode);
    }

    public static String makeUrlAbsolute(String url, String host, String protocol) throws Exception {
        if (host == null) throw new Exception("Host not found");
        if (!url.startsWith("/")) url = "/" + url;
        if (host.endsWith("/")) host = host.substring(0, host.length()-1);

        host = host.toLowerCase();
        if (!host.startsWith("http")) {
            if (protocol != null) {
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

    public static OriginalHttpResponse sendRequest(OriginalHttpRequest request) throws Exception {
        // don't lowercase url because query params will change and will result in incorrect request
        String url = request.getUrl();
        url = url.trim();
        if (!url.startsWith("http")) {
            url = makeUrlAbsolute(url, request.findHostFromHeader(), request.findProtocolFromHeader());
        }

        request.setUrl(url);

        Request.Builder builder = new Request.Builder();

        // add headers
        List<String> forbiddenHeaders = Arrays.asList("content-length", "accept-encoding");
        Map<String, List<String>> headersMap = request.getHeaders();
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

        URLMethods.Method method = URLMethods.Method.fromString(request.getMethod());

        builder = builder.url(request.getFullUrlWithParams());

        OriginalHttpResponse response = null;
        switch (method) {
            case GET:
                response = getRequest(request, builder);
                break;
            case POST:
            case PUT:
            case DELETE:
            case HEAD:
            case OPTIONS:
            case PATCH:
            case TRACE:
                response = sendWithRequestBody(request, builder);
                break;
            case OTHER:
                throw new Exception("Invalid method name");
        }

        return response;
    }


    public static OriginalHttpResponse getRequest(OriginalHttpRequest request, Request.Builder builder)  throws Exception{
        Request okHttpRequest = builder.build();
        return common(okHttpRequest);
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


    public static OriginalHttpResponse sendWithRequestBody(OriginalHttpRequest request, Request.Builder builder) throws Exception {
        Map<String,List<String>> headers = request.getHeaders();
        if (headers == null) {
            headers = new HashMap<>();
            request.setHeaders(headers);
        }

        String contentType = request.findContentType();
        String payload = request.getBody();
        if (contentType == null ) {
            contentType = "application/json; charset=utf-8";
            if (payload == null) payload = "{}";
            payload = payload.trim();
            if (!payload.startsWith("[") && !payload.startsWith("{")) payload = "{}";
        }

        if (payload == null) payload = "";
        RequestBody body = RequestBody.create(payload, MediaType.parse(contentType));
        builder = builder.method(request.getMethod(), body);
        Request okHttpRequest = builder.build();
        return common(okHttpRequest);
    }

}
