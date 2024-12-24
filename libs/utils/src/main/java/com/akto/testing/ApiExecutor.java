package com.akto.testing;

import com.akto.dao.context.Context;
import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dao.testing.config.TestScriptsDao;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.CollectionConditions.ConditionsType;
import com.akto.dto.CollectionConditions.TestConfigsAdvancedSettings;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.config.TestScript;
import com.akto.dto.testing.rate_limit.RateLimitHandler;
import com.akto.dto.type.URLMethods;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.metrics.AllMetrics;
import com.akto.util.Constants;

import kotlin.Pair;
import okhttp3.*;
import okio.BufferedSink;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger logger = LoggerFactory.getLogger(ApiExecutor.class);

    // Load only first 1 MiB of response body into memory.
    private static final int MAX_RESPONSE_SIZE = 1024*1024;
    private static Map<Integer, Integer> lastFetchedMap = new HashMap<>();
    private static Map<Integer, TestScript> testScriptMap = new HashMap<>();

    private static final DataActor dataActor = DataActorFactory.fetchInstance();
    
    private static OriginalHttpResponse common(Request request, boolean followRedirects, boolean debug, List<TestingRunResult.TestLog> testLogs, boolean skipSSRFCheck, boolean nonTestingContext) throws Exception {

        Integer accountId = Context.accountId.get();
        if (accountId != null) {
            int i = 0;
            boolean rateLimitHit = true;
            while (RateLimitHandler.getInstance(accountId).shouldWait(request)) {
                if(rateLimitHit){
                    if (!(request.url().toString().contains("insertRuntimeLog") || request.url().toString().contains("insertTestingLog"))) {
                        loggerMaker.infoAndAddToDb("Rate limit hit, sleeping", LogDb.TESTING);
                    }else {
                        System.out.println("Rate limit hit, sleeping");
                    }
                }
                rateLimitHit = false;
                Thread.sleep(1000);
                i++;

                if (i%30 == 0) {
                    if (!(request.url().toString().contains("insertRuntimeLog") || request.url().toString().contains("insertTestingLog"))) {
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
                HTTPClientHandler.instance.getNewDebugClient(isSaasDeployment, followRedirects, testLogs) :
                HTTPClientHandler.instance.getHTTPClient(followRedirects);

        if (!skipSSRFCheck && !HostDNSLookup.isRequestValid(request.url().host())) {
            throw new IllegalArgumentException("SSRF attack attempt");
        }
        boolean isCyborgCall = request.url().toString().contains("cyborg.akto.io");
        long start = System.currentTimeMillis();

        Call call = client.newCall(request);
        Response response = null;
        String body;
        try {
            response = call.execute();
            ResponseBody responseBody = null;
            if (nonTestingContext) {
                responseBody = response.body();
            } else {
                responseBody = response.peekBody(MAX_RESPONSE_SIZE);
            }
            if (responseBody == null) {
                throw new Exception("Couldn't read response body");
            }
            try {
                body = responseBody.string();
            } catch (IOException e) {
                if (!(request.url().toString().contains("insertRuntimeLog") || request.url().toString().contains("insertTestingLog"))) {
                    loggerMaker.errorAndAddToDb("Error while parsing response body: " + e, LogDb.TESTING);
                } else {
                    System.out.println("Error while parsing response body: " + e);
                }
                body = "{}";
            }
            if(isCyborgCall){
                AllMetrics.instance.setCyborgCallLatency(System.currentTimeMillis() - start);
                AllMetrics.instance.setCyborgCallCount(1);
                AllMetrics.instance.setCyborgDataSize(request.body() == null ? 0 : request.body().contentLength());
            }
        } catch (IOException e) {
            if (!(request.url().toString().contains("insertRuntimeLog") || request.url().toString().contains("insertTestingLog"))) {
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

        if (!(url.contains("insertRuntimeLog") || url.contains("insertTestingLog"))) {
            loggerMaker.infoAndAddToDb("Final url is: " + url, LogDb.TESTING);
        }

        if (url.contains("login_submit")) {
            loggerMaker.infoAndAddToDb("Request Payload " + request.getBody(), LogDb.TESTING);
        }
        request.setUrl(url);

        Request.Builder builder = new Request.Builder();

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

        if(testingRunConfig != null && testingRunConfig.getConfigsAdvancedSettings() != null && !testingRunConfig.getConfigsAdvancedSettings().isEmpty()){
            calculateFinalRequestFromAdvancedSettings(request, testingRunConfig.getConfigsAdvancedSettings());
        }

        boolean nonTestingContext = false;
        if (testingRunConfig == null) {
            nonTestingContext = true;
        }

        OriginalHttpResponse response = null;
        switch (method) {
            case GET:
            case HEAD:
                response = getRequest(request, builder, followRedirects, debug, testLogs, skipSSRFCheck, nonTestingContext);
                break;
            case POST:
            case PUT:
            case DELETE:
            case OPTIONS:
            case PATCH:
            case TRACK:
            case TRACE:
                response = sendWithRequestBody(request, builder, followRedirects, debug, testLogs, skipSSRFCheck, nonTestingContext);
                break;
            case OTHER:
                throw new Exception("Invalid method name");
        }
        //loggerMaker.infoAndAddToDb("Received response from: " + url, LogDb.TESTING);

        if (url.contains("login_submit")) {
            loggerMaker.infoAndAddToDb("Response Payload " + response.getBody(), LogDb.TESTING);
        }
        return response;
    }
    public static OriginalHttpResponse sendRequest(OriginalHttpRequest request, boolean followRedirects, TestingRunConfig testingRunConfig, boolean debug, List<TestingRunResult.TestLog> testLogs) throws Exception {
        return sendRequest(request, followRedirects, testingRunConfig, debug, testLogs, false);
    }

    private static final List<Integer> BACK_OFF_LIMITS = new ArrayList<>(Arrays.asList(1, 2, 5));

    public static OriginalHttpResponse sendRequestBackOff(OriginalHttpRequest request, boolean followRedirects, TestingRunConfig testingRunConfig, boolean debug, List<TestingRunResult.TestLog> testLogs) throws Exception {
        OriginalHttpResponse response = null;

        for (int limit : BACK_OFF_LIMITS) {
            try {
                response = sendRequest(request, followRedirects, testingRunConfig, debug, testLogs, false);
                if (response == null) {
                    throw new NullPointerException(String.format("Response is null"));
                }
                if (response.getStatusCode() != 200) {
                    throw new Exception(String.format("Invalid response code %d", response.getStatusCode()));
                }
                break;
            } catch (Exception e) {
                logger.error("Error in sending request for api : {} , will retry after {} seconds : {}", request.getUrl(),
                        limit, e.toString());
                try {
                    Thread.sleep(1000 * limit);
                } catch (Exception f) {
                    logger.error("Error in exponential backoff at limit {} : {}", limit, f.toString());
                }
            }
        }
        return response;
    }

    private static OriginalHttpResponse getRequest(OriginalHttpRequest request, Request.Builder builder, boolean followRedirects, boolean debug, List<TestingRunResult.TestLog> testLogs, boolean skipSSRFCheck, boolean nonTestingContext)  throws Exception{
        Request okHttpRequest = builder.build();
        return common(okHttpRequest, followRedirects, debug, testLogs, skipSSRFCheck, nonTestingContext);
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

    private static void calculateFinalRequestFromAdvancedSettings(OriginalHttpRequest originalHttpRequest, List<TestConfigsAdvancedSettings> advancedSettings){
        Map<String,List<ConditionsType>> headerConditions = new HashMap<>();
        Map<String,List<ConditionsType>> payloadConditions = new HashMap<>();

        for(TestConfigsAdvancedSettings settings: advancedSettings){
            if(settings.getOperatorType().toLowerCase().contains("header")){
                headerConditions.put(settings.getOperatorType(), settings.getOperationsGroupList());
            }else{
                payloadConditions.put(settings.getOperatorType(), settings.getOperationsGroupList());
            }
        }
        List<ConditionsType> emptyList = new ArrayList<>();

        Utils.modifyHeaderOperations(originalHttpRequest, 
            headerConditions.getOrDefault(TestEditorEnums.NonTerminalExecutorDataOperands.MODIFY_HEADER.name(), emptyList), 
            headerConditions.getOrDefault(TestEditorEnums.NonTerminalExecutorDataOperands.ADD_HEADER.name(), emptyList),
            headerConditions.getOrDefault(TestEditorEnums.TerminalExecutorDataOperands.DELETE_HEADER.name(), emptyList)
        );

        Utils.modifyBodyOperations(originalHttpRequest, 
            payloadConditions.getOrDefault(TestEditorEnums.NonTerminalExecutorDataOperands.MODIFY_BODY_PARAM.name(), emptyList), 
            payloadConditions.getOrDefault(TestEditorEnums.NonTerminalExecutorDataOperands.ADD_BODY_PARAM.name(), emptyList),
            payloadConditions.getOrDefault(TestEditorEnums.TerminalExecutorDataOperands.DELETE_BODY_PARAM.name(), emptyList)
        );
    }

    private static OriginalHttpResponse sendWithRequestBody(OriginalHttpRequest request, Request.Builder builder, boolean followRedirects, boolean debug, List<TestingRunResult.TestLog> testLogs, boolean skipSSRFCheck, boolean nonTestingContext) throws Exception {
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


            return common(updatedRequest, followRedirects, debug, testLogs, skipSSRFCheck, nonTestingContext);
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
        return common(okHttpRequest, followRedirects, debug, testLogs, skipSSRFCheck, nonTestingContext);
    }
}
