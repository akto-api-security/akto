package com.akto.testing;

import com.akto.dto.AccountSettings;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.type.URLMethods;
import com.akto.log.LoggerMaker;
import kotlin.Pair;
import okhttp3.*;

import java.io.IOException;
import java.util.*;

public class ApiExecutor {
    private static final LoggerMaker loggerMaker = new LoggerMaker(ApiExecutor.class);
    public static OriginalHttpResponse common(Request request, boolean followRedirects) throws Exception {

        OkHttpClient client = HTTPClientHandler.instance.getHTTPClient(followRedirects);

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
                loggerMaker.errorAndAddToDb("Error while parsing response body: " + e);
                body = "{}";
            }
        } catch (IOException e) {
            loggerMaker.errorAndAddToDb("Error while executing request: " + e);
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


    public static OriginalHttpResponse sendRequest(OriginalHttpRequest request, boolean followRedirects) throws Exception {
        // don't lowercase url because query params will change and will result in incorrect request
        String url = request.getUrl();
        url = url.trim();
        if (!url.startsWith("http")) {
            url = OriginalHttpRequest.makeUrlAbsolute(url, request.findHostFromHeader(), request.findProtocolFromHeader());
        }

        request.setUrl(url);

        Request.Builder builder = new Request.Builder();

        // add headers
        List<String> forbiddenHeaders = Arrays.asList("content-length", "accept-encoding");
        Map<String, List<String>> headersMap = request.getHeaders();
        if (headersMap == null) headersMap = new HashMap<>();
        headersMap.put(AccountSettings.AKTO_IGNORE_FLAG, Collections.singletonList("0"));
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
            case HEAD:
                response = getRequest(request, builder, followRedirects);
                break;
            case POST:
            case PUT:
            case DELETE:
            case OPTIONS:
            case PATCH:
            case TRACE:
                response = sendWithRequestBody(request, builder, followRedirects);
                break;
            case OTHER:
                throw new Exception("Invalid method name");
        }

        return response;
    }


    public static OriginalHttpResponse getRequest(OriginalHttpRequest request, Request.Builder builder, boolean followRedirects)  throws Exception{
        Request okHttpRequest = builder.build();
        return common(okHttpRequest, followRedirects);
    }



    public static OriginalHttpResponse sendWithRequestBody(OriginalHttpRequest request, Request.Builder builder, boolean followRedirects) throws Exception {
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
        return common(okHttpRequest, followRedirects);
    }
}
