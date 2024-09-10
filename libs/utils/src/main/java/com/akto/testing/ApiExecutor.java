package com.akto.testing;

import com.akto.dao.context.Context;
import com.akto.dao.testing.config.TestScriptsDao;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.config.TestScript;
import com.akto.dto.testing.rate_limit.RateLimitHandler;
import com.akto.dto.type.URLMethods;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.akto.util.HttpRequestResponseUtils;
import com.akto.util.grpc.ProtoBufUtils;

import kotlin.Pair;
import okhttp3.*;
import okio.BufferedSink;
import org.apache.commons.lang3.StringUtils;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleScriptContext;
import jdk.nashorn.api.scripting.ScriptObjectMirror;

public class ApiExecutor {
    private static final LoggerMaker loggerMaker = new LoggerMaker(ApiExecutor.class);

    // Load only first 1 MiB of response body into memory.
    private static final int MAX_RESPONSE_SIZE = 1024*1024;
    private static Map<Integer, Integer> lastFetchedMap = new HashMap<>();
    private static Map<Integer, TestScript> testScriptMap = new HashMap<>();
    
    private static OriginalHttpResponse common(Request request, boolean followRedirects, boolean debug, List<TestingRunResult.TestLog> testLogs, boolean skipSSRFCheck, String requestProtocol) throws Exception {

        Integer accountId = Context.accountId.get();
        if (accountId != null) {
            int i = 0;
            boolean rateLimitHit = true;
            while (RateLimitHandler.getInstance(accountId).shouldWait(request)) {
                if(rateLimitHit){
                    if (!(request.url().toString().contains("insertRuntimeLog") || request.url().toString().contains("insertTestingLog") || request.url().toString().contains("insertProtectionLog"))) {
                        loggerMaker.infoAndAddToDb("Rate limit hit, sleeping", LogDb.TESTING);
                    }else {
                        System.out.println("Rate limit hit, sleeping");
                    }
                }
                rateLimitHit = false;
                Thread.sleep(1000);
                i++;

                if (i%30 == 0) {
                    if (!(request.url().toString().contains("insertRuntimeLog") || request.url().toString().contains("insertTestingLog") || request.url().toString().contains("insertProtectionLog"))) {
                        loggerMaker.infoAndAddToDb("waiting for rate limit availability", LogDb.TESTING);
                    }else{
                        System.out.println("waiting for rate limit availability");
                    }                
                }
            }
        }

        boolean isSaasDeployment = "true".equals(System.getenv("IS_SAAS"));

        if (HTTPClientHandler.instance == null) {
            HTTPClientHandler.initHttpClientHandler(isSaasDeployment);
        }

        OkHttpClient client = debug ?
                HTTPClientHandler.instance.getNewDebugClient(isSaasDeployment, followRedirects, testLogs, requestProtocol) :
                HTTPClientHandler.instance.getHTTPClient(followRedirects, requestProtocol);

        if (!skipSSRFCheck && !HostDNSLookup.isRequestValid(request.url().host())) {
            throw new IllegalArgumentException("SSRF attack attempt");
        }

        Call call = client.newCall(request);
        Response response = null;
        String body;
        byte[] grpcBody = null;
        try {
            response = call.execute();
            ResponseBody responseBody = response.peekBody(MAX_RESPONSE_SIZE);
            if (responseBody == null) {
                throw new Exception("Couldn't read response body");
            }
            try {
                if (requestProtocol != null && requestProtocol.contains(HttpRequestResponseUtils.GRPC_CONTENT_TYPE)) {//GRPC request
                    grpcBody = responseBody.bytes();
                    StringBuilder builder = new StringBuilder();
                    builder.append("grpc response binary array: ");
                    for (byte b : grpcBody) {
                        builder.append(b).append(",");
                    }
                    if (!(request.url().toString().contains("insertRuntimeLog") || request.url().toString().contains("insertTestingLog") || request.url().toString().contains("insertProtectionLog"))) {
                        loggerMaker.infoAndAddToDb(builder.toString(), LogDb.TESTING);
                    }else {
                        System.out.println(builder.toString());
                    }
                    String responseBase64Encoded = Base64.getEncoder().encodeToString(grpcBody);
                    if (!(request.url().toString().contains("insertRuntimeLog") || request.url().toString().contains("insertTestingLog") || request.url().toString().contains("insertProtectionLog"))) {
                        loggerMaker.infoAndAddToDb("grpc response base64 encoded:" + responseBase64Encoded, LogDb.TESTING);
                    }else {
                        System.out.println("grpc response base64 encoded:" + responseBase64Encoded);
                    }
                    body = HttpRequestResponseUtils.convertGRPCEncodedToJson(grpcBody);
                } else {
                    body = responseBody.string();
                }
            } catch (IOException e) {
                if (!(request.url().toString().contains("insertRuntimeLog") || request.url().toString().contains("insertTestingLog") || request.url().toString().contains("insertProtectionLog"))) {
                    loggerMaker.errorAndAddToDb("Error while parsing response body: " + e, LogDb.TESTING);
                } else {
                    System.out.println("Error while parsing response body: " + e);
                }
                body = "{}";
            }
        } catch (IOException e) {
            if (!(request.url().toString().contains("insertRuntimeLog") || request.url().toString().contains("insertTestingLog") || request.url().toString().contains("insertProtectionLog"))) {
                loggerMaker.errorAndAddToDb("Error while executing request " + request.url() + ": " + e, LogDb.TESTING);
            } else {
                System.out.println("Error while executing request " + request.url() + ": " + e);
            }
            throw new Exception("Api Call failed");
        } finally {
            if (response != null) {
                response.close();
            }
        }

        int statusCode = response.code();
        Headers headers = response.headers();

        Map<String, List<String>> responseHeaders = generateHeadersMapFromHeadersObject(headers);
        return new OriginalHttpResponse(body, responseHeaders, statusCode);
    }

    public static Map<String, List<String>> generateHeadersMapFromHeadersObject(Headers headers) {
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

        return responseHeaders;
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

    public static String replacePathFromConfig(String url, TestingRunConfig testingRunConfig) {
        if (testingRunConfig != null && !StringUtils.isEmpty(testingRunConfig.getOverriddenTestAppUrl())) {
            URI typedUrl = null;
            try {
                typedUrl = new URI(url);

            } catch (URISyntaxException e) {
                loggerMaker.errorAndAddToDb(e, "error converting req url to uri " + url, LogDb.TESTING);
                throw new RuntimeException(e);
            }

            String newUrl = testingRunConfig.getOverriddenTestAppUrl();

            URI newUri = null;
            try {
                newUri = new URI(newUrl);

            } catch (URISyntaxException e) {
                loggerMaker.errorAndAddToDb(e, "error converting override url to uri " + url, LogDb.TESTING);
                throw new RuntimeException(e);
            }

            String newPath = newUri.getPath();

            if (newPath.equals("") || newPath.equals("/")) {
                newPath = typedUrl.getPath();
            }

            String newHost = newUri.getHost();
            if (newUri.getHost().equals("")) {
                newHost = typedUrl.getHost();
            }

            try {
                String newScheme = newUri.getScheme() == null ? typedUrl.getScheme() : newUri.getScheme();
                int newPort = newUri.getPort() == -1 ? typedUrl.getPort() : newUri.getPort();

                url = new URI(newScheme, null, newHost, newPort, newPath, typedUrl.getQuery(), typedUrl.getFragment()).toString();
            } catch (URISyntaxException e) {
                loggerMaker.errorAndAddToDb(e, "error building new url using override url", LogDb.TESTING);
                throw new RuntimeException(e);
            }
        }
        return url;
    }

    public static String prepareUrl(OriginalHttpRequest request, TestingRunConfig testingRunConfig) throws Exception{
        String url = request.getUrl();
        url = url.trim();

        if (!url.startsWith("http")) {
            url = OriginalHttpRequest.makeUrlAbsolute(url, request.findHostFromHeader(), request.findProtocolFromHeader());
        }

        return replaceHostFromConfig(url, testingRunConfig);
    }

    public static OriginalHttpResponse sendRequest(OriginalHttpRequest request, boolean followRedirects, TestingRunConfig testingRunConfig, boolean debug, List<TestingRunResult.TestLog> testLogs, boolean skipSSRFCheck) throws Exception {
        // don't lowercase url because query params will change and will result in incorrect request

        String url = prepareUrl(request, testingRunConfig);

        if (!(url.contains("insertRuntimeLog") || url.contains("insertTestingLog") || url.contains("insertProtectionLog"))) {
            loggerMaker.infoAndAddToDb("Final url is: " + url, LogDb.TESTING);
        }
        request.setUrl(url);

        Request.Builder builder = new Request.Builder();
        String type = request.findContentType();
        // add headers
        List<String> forbiddenHeaders = Arrays.asList("content-length", "accept-encoding");
        Map<String, List<String>> headersMap = request.getHeaders();
        if (headersMap == null) headersMap = new HashMap<>();
        headersMap.put(Constants.AKTO_IGNORE_FLAG, Collections.singletonList("0"));
        for (String headerName: headersMap.keySet()) {
            if (forbiddenHeaders.contains(headerName)) continue;
            if (headerName.contains(" ")) continue;
            List<String> headerValueList = headersMap.get(headerName);
            if (headerValueList == null || headerValueList.isEmpty()) continue;
            for (String headerValue: headerValueList) {
                if (headerValue == null) continue;
                builder.addHeader(headerName, headerValue);
            }
        }
        URLMethods.Method method = URLMethods.Method.fromString(request.getMethod());

        builder = builder.url(request.getFullUrlWithParams());

        boolean executeScript = testingRunConfig != null;
        calculateHashAndAddAuth(request, executeScript);

        OriginalHttpResponse response = null;
        switch (method) {
            case GET:
            case HEAD:
                response = getRequest(request, builder, followRedirects, debug, testLogs, skipSSRFCheck, type);
                break;
            case POST:
            case PUT:
            case DELETE:
            case OPTIONS:
            case PATCH:
            case TRACK:
            case TRACE:
                response = sendWithRequestBody(request, builder, followRedirects, debug, testLogs, skipSSRFCheck, type);
                break;
            case OTHER:
                throw new Exception("Invalid method name");
        }
        if (!(url.contains("insertRuntimeLog") || url.contains("insertTestingLog") || url.contains("insertProtectionLog"))) {
            loggerMaker.infoAndAddToDb("Received response from: " + url, LogDb.TESTING);
        }

        return response;
    }
    public static OriginalHttpResponse sendRequest(OriginalHttpRequest request, boolean followRedirects, TestingRunConfig testingRunConfig, boolean debug, List<TestingRunResult.TestLog> testLogs) throws Exception {
        return sendRequest(request, followRedirects, testingRunConfig, debug, testLogs, false);
    }


    private static OriginalHttpResponse getRequest(OriginalHttpRequest request, Request.Builder builder, boolean followRedirects, boolean debug, List<TestingRunResult.TestLog> testLogs, boolean skipSSRFCheck, String type)  throws Exception{
        Request okHttpRequest = builder.build();
        return common(okHttpRequest, followRedirects, debug, testLogs, skipSSRFCheck, type);
    }

    public static RequestBody getFileRequestBody(String fileUrl){
        try {
            URL sourceFileUrl = new URL(fileUrl);
            InputStream urlInputStream = sourceFileUrl.openStream();
            final int CHUNK_SIZE = 1 * 1024 * 1024 ;

            RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "filename", new RequestBody() {
                    @Override
                    public MediaType contentType() {
                        return Utils.getMediaType(fileUrl);
                    }

                    @Override
                    public void writeTo(BufferedSink sink) throws IOException {
                        byte[] chunk = new byte[CHUNK_SIZE];
                        int bytesRead;
                        long totalBytesRead = 0; 
                        long maxBytes = 100L * 1024 * 1024; 
                        while ((bytesRead = urlInputStream.read(chunk)) != -1) {
                            totalBytesRead += bytesRead;
                            if (totalBytesRead > maxBytes) {
                                loggerMaker.errorAndAddToDb("File size greater than 100mb, breaking loop.", LogDb.TESTING);
                                break;
                            }
                            sink.write(chunk, 0, bytesRead);
                        }
                    }
                })
                .build();

            return requestBody;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error in file upload " + e.getMessage(), LogDb.TESTING);
            return null;
        }
        
    }

    private static void calculateHashAndAddAuth(OriginalHttpRequest originalHttpRequest, boolean executeScript) {
        if (!executeScript) {
            loggerMaker.infoAndAddToDb("invalid context for hash calculation, returning");
            return;
        }
        int accountId = Context.accountId.get();
        try {
            String script;
            TestScript testScript = testScriptMap.getOrDefault(accountId, null);
            int lastTestScriptFetched = lastFetchedMap.getOrDefault(accountId, 0);
            if (Context.now() - lastTestScriptFetched > 5 * 60) {
                testScript = TestScriptsDao.instance.fetchTestScript();
                lastTestScriptFetched = Context.now();
                testScriptMap.put(accountId, testScript);
                lastFetchedMap.put(accountId, Context.now());
            }
            if (testScript != null && testScript.getJavascript() != null) {
                script = testScript.getJavascript();
            } else {
                loggerMaker.infoAndAddToDb("returning from calculateHashAndAddAuth, no test script present");
                return;
            }

            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine engine = manager.getEngineByName("nashorn");

            SimpleScriptContext sctx = ((SimpleScriptContext) engine.get("context"));
            sctx.setAttribute("method", originalHttpRequest.getMethod(), ScriptContext.ENGINE_SCOPE);
            sctx.setAttribute("headers", originalHttpRequest.getHeaders(), ScriptContext.ENGINE_SCOPE);
            sctx.setAttribute("url", originalHttpRequest.getPath(), ScriptContext.ENGINE_SCOPE);
            sctx.setAttribute("payload", originalHttpRequest.getBody(), ScriptContext.ENGINE_SCOPE);
            sctx.setAttribute("queryParams", originalHttpRequest.getQueryParams(), ScriptContext.ENGINE_SCOPE);
            engine.eval(script);

            String method = (String) sctx.getAttribute("method");
            Map<String, Object> headers = (Map) sctx.getAttribute("headers");
            String url = (String) sctx.getAttribute("url");
            String payload = (String) sctx.getAttribute("payload");
            String queryParams = (String) sctx.getAttribute("queryParams");

            Map<String, List<String>> hs = new HashMap<>();
            for (String key: headers.keySet()) {
                try {
                    ScriptObjectMirror scm = ((ScriptObjectMirror) headers.get(key));
                    List<String> val = new ArrayList<>();
                    for (int i = 0; i < scm.size(); i++) {
                        val.add((String) scm.get(Integer.toString(i)));
                    }
                    hs.put(key, val);
                } catch (Exception e) {
                    hs.put(key, (List) headers.get(key));
                }
            }

            originalHttpRequest.setBody(payload);
            originalHttpRequest.setMethod(method);
            originalHttpRequest.setUrl(url);
            originalHttpRequest.setHeaders(hs);
            originalHttpRequest.setQueryParams(queryParams);

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in calculateHashAndAddAuth " + e.getMessage() + " url " + originalHttpRequest.getUrl());
            e.printStackTrace();
            return;
        }
    }

    private static OriginalHttpResponse sendWithRequestBody(OriginalHttpRequest request, Request.Builder builder, boolean followRedirects, boolean debug, List<TestingRunResult.TestLog> testLogs, boolean skipSSRFCheck, String requestProtocol) throws Exception {
        Map<String,List<String>> headers = request.getHeaders();
        if (headers == null) {
            headers = new HashMap<>();
            request.setHeaders(headers);
        }

        if(headers != null && headers.containsKey(Constants.AKTO_ATTACH_FILE)){
            String fileUrl = headers.get(Constants.AKTO_ATTACH_FILE).get(0);
            RequestBody requestBody = null;
            requestBody = getFileRequestBody(fileUrl);
        
            builder.post(requestBody);
            builder.removeHeader(Constants.AKTO_ATTACH_FILE);
            Request updatedRequest = builder.build();


            return common(updatedRequest, followRedirects, debug, testLogs, skipSSRFCheck, requestProtocol);
        }

        String contentType = request.findContentType();
        String payload = request.getBody();
        RequestBody body = null;
        if (contentType == null ) {
            contentType = "application/json; charset=utf-8";
            if (payload == null) payload = "{}";
            payload = payload.trim();
            if (!payload.startsWith("[") && !payload.startsWith("{")) payload = "{}";
        } else if (contentType.contains(HttpRequestResponseUtils.FORM_URL_ENCODED_CONTENT_TYPE)) {
            if (payload.startsWith("{")) {
                payload = HttpRequestResponseUtils.jsonToFormUrlEncoded(payload, false);
            }

        } else if (contentType.contains(HttpRequestResponseUtils.GRPC_CONTENT_TYPE)) {
            try {
                loggerMaker.infoAndAddToDb("encoding to grpc payload:" + payload, LogDb.TESTING);
                payload = ProtoBufUtils.base64EncodedJsonToProtobuf(payload);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Unable to encode grpc payload:" + payload, LogDb.TESTING);
                payload = request.getBody();
            }
            try {// trying decoding payload
                byte[] payloadByteArray = Base64.getDecoder().decode(payload);
                loggerMaker.infoAndAddToDb("Final base64 encoded payload:"+ payload, LogDb.TESTING);
                body = RequestBody.create(payloadByteArray, MediaType.parse(contentType));
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Unable to decode grpc payload:" + payload, LogDb.TESTING);
            }
        }

        if (payload == null) payload = "";
        if (body == null) {// body not created by GRPC block yet
            body = RequestBody.create(payload, MediaType.parse(contentType));
        }
        builder = builder.method(request.getMethod(), body);
        Request okHttpRequest = builder.build();
        return common(okHttpRequest, followRedirects, debug, testLogs, skipSSRFCheck, requestProtocol);
    }
}
