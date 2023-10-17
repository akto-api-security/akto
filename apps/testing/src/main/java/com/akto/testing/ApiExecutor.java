package com.akto.testing;

import com.akto.dao.context.Context;
import com.akto.dto.AccountSettings;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.rate_limit.RateLimitHandler;
import com.akto.dto.type.URLMethods;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import kotlin.Pair;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

public class ApiExecutor {
    private static final LoggerMaker loggerMaker = new LoggerMaker(ApiExecutor.class);

    private static OriginalHttpResponse common(Request request, boolean followRedirects) throws Exception {

        Integer accountId = Context.accountId.get();
        if (accountId != null) {
            int i = 0;
            while (RateLimitHandler.getInstance(accountId).shouldWait(request)) {
                Thread.sleep(1000);
                i++;

                if (i%30 == 0) {
                    loggerMaker.infoAndAddToDb("waiting for rate limit availability", LogDb.TESTING);
                }
            }
        }

        boolean isSaasDeployment = "true".equals(System.getenv("IS_SAAS"));

        if (HTTPClientHandler.instance == null) {
            HTTPClientHandler.initHttpClientHandler(isSaasDeployment);
        }
        
        OkHttpClient client = HTTPClientHandler.instance.getHTTPClient(followRedirects);
        if (!Main.SKIP_SSRF_CHECK && !HostDNSLookup.isRequestValid(request.url().host())) {
            throw new IllegalArgumentException("SSRF attack attempt");
        }

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
                loggerMaker.errorAndAddToDb("Error while parsing response body: " + e, LogDb.TESTING);
                body = "{}";
            }
        } catch (IOException e) {
            loggerMaker.errorAndAddToDb("Error while executing request " + request.url() + ": " + e, LogDb.TESTING);
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

    public static String replaceHostFromConfig(String url, TestingRunConfig testingRunConfig) {
        if (testingRunConfig != null && !StringUtils.isEmpty(testingRunConfig.getOverriddenTestAppUrl())) {
            URI typedUrl = null;
            try {
                typedUrl = new URI(url);

            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
            String newHost = testingRunConfig.getOverriddenTestAppUrl();
            if (newHost.endsWith("/")) {
                newHost = newHost.substring(0, newHost.length()-1);
            }
            URI newHostURI = null;
            try {
                newHostURI = new URI(newHost);
            } catch (URISyntaxException e) {
                return url;
            }

            try {
                String newScheme = newHostURI.getScheme() == null ? typedUrl.getScheme() : newHostURI.getScheme();
                int newPort = newHostURI.getPort() == -1 ? typedUrl.getPort() : newHostURI.getPort();

                url = new URI(newScheme, null, newHostURI.getHost(), newPort, typedUrl.getPath(), typedUrl.getQuery(), typedUrl.getFragment()).toString();
            } catch (URISyntaxException e) {
                return url;
            }
        }
        return url;
    }

    public static OriginalHttpResponse sendRequest(OriginalHttpRequest request, boolean followRedirects, TestingRunConfig testingRunConfig) throws Exception {
        // don't lowercase url because query params will change and will result in incorrect request
        String url = request.getUrl();
        url = url.trim();

        if (!url.startsWith("http")) {
            url = OriginalHttpRequest.makeUrlAbsolute(url, request.findHostFromHeader(), request.findProtocolFromHeader());
        }

        url = replaceHostFromConfig(url, testingRunConfig);

        loggerMaker.infoAndAddToDb("Final url is: " + url, LogDb.TESTING);
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
            case TRACK:
            case TRACE:
                response = sendWithRequestBody(request, builder, followRedirects);
                break;
            case OTHER:
                throw new Exception("Invalid method name");
        }
        loggerMaker.infoAndAddToDb("Received response from: " + url, LogDb.TESTING);

        return response;
    }


    private static OriginalHttpResponse getRequest(OriginalHttpRequest request, Request.Builder builder, boolean followRedirects)  throws Exception{
        Request okHttpRequest = builder.build();
        return common(okHttpRequest, followRedirects);
    }



    private static OriginalHttpResponse sendWithRequestBody(OriginalHttpRequest request, Request.Builder builder, boolean followRedirects) throws Exception {
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
