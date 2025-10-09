package com.akto.testing;

import com.akto.dao.context.Context;
import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.CollectionConditions.ConditionsType;
import com.akto.dto.CollectionConditions.TestConfigsAdvancedSettings;
import com.akto.dto.testing.TLSAuthParam;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.rate_limit.RateLimitHandler;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLMethods.Method;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.metrics.AllMetrics;
import com.akto.util.Constants;
import com.akto.util.HttpRequestResponseUtils;
import com.akto.util.grpc.ProtoBufUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

public class ApiExecutor {
    private static final LoggerMaker loggerMaker = new LoggerMaker(ApiExecutor.class, LogDb.TESTING);

    // Load only first 1 MiB of response body into memory.
    private static final int MAX_RESPONSE_SIZE = 1024*1024;
    private static final ObjectMapper objectMapper = new ObjectMapper();


    private static OriginalHttpResponse common(Request request, boolean followRedirects, boolean debug, List<TestingRunResult.TestLog> testLogs, boolean skipSSRFCheck, boolean nonTestingContext, String requestProtocol, TLSAuthParam authParam) throws Exception {

        Integer accountId = Context.accountId.get();
        if (accountId != null) {
            int i = 0;
            boolean rateLimitHit = true;
            while (RateLimitHandler.getInstance(accountId).shouldWait(request)) {
                if(rateLimitHit){
                    if (!(request.url().toString().contains("insertRuntimeLog") || request.url().toString().contains("insertTestingLog") || request.url().toString().contains("insertProtectionLog"))) {
                        loggerMaker.infoAndAddToDb("Rate limit hit, sleeping", LogDb.TESTING);
                    }else {
                       loggerMaker.info("Rate limit hit, sleeping");
                    }
                }
                rateLimitHit = false;
                Thread.sleep(1000);
                i++;

                if (i%30 == 0) {
                    if (!(request.url().toString().contains("insertRuntimeLog") || request.url().toString().contains("insertTestingLog") || request.url().toString().contains("insertProtectionLog"))) {
                        loggerMaker.infoAndAddToDb("waiting for rate limit availability", LogDb.TESTING);
                    }else{
                        loggerMaker.info("waiting for rate limit availability");
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
                HTTPClientHandler.instance.getHTTPClient(request.isHttps(), followRedirects, requestProtocol);

        if (!skipSSRFCheck && !HostDNSLookup.isRequestValid(request.url().host())) {
            throw new IllegalArgumentException("SSRF attack attempt");
        }
        boolean isCyborgCall = request.url().toString().contains("cyborg.akto.io");
        long start = System.currentTimeMillis();

        if (authParam != null) {
            client = CustomHTTPClientHandler.instance.getClient(authParam, request.isHttps(), followRedirects, requestProtocol);
        }

        Call call = client.newCall(request);
        Response response = null;
        String body = null;
        byte[] grpcBody = null;
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
                    loggerMaker.error("Error while parsing response body: " + e);
                }
                body = "{}";
            }
            if(isCyborgCall){
                AllMetrics.instance.setCyborgCallLatency(System.currentTimeMillis() - start);
                AllMetrics.instance.setCyborgCallCount(1);
                AllMetrics.instance.setCyborgDataSize(request.body() == null ? 0 : request.body().contentLength());
            }
        } catch (IOException e) {
            if (!(request.url().toString().contains("insertRuntimeLog") || request.url().toString().contains("insertTestingLog") || request.url().toString().contains("insertProtectionLog"))) {
                loggerMaker.errorAndAddToDb("Error while executing request " + request.url() + ": " + e, LogDb.TESTING);
            } else {
                loggerMaker.error("Error while executing request " + request.url() + ": " + e);
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

    public static OriginalHttpResponse sendRequest(OriginalHttpRequest request, boolean followRedirects,
        TestingRunConfig testingRunConfig, boolean debug, List<TestingRunResult.TestLog> testLogs,
        boolean skipSSRFCheck) throws Exception {
        return sendRequest(request, followRedirects, testingRunConfig, debug, testLogs, skipSSRFCheck, false);
    }

    private static OriginalHttpResponse sendRequest(OriginalHttpRequest request, boolean followRedirects, TestingRunConfig testingRunConfig, boolean debug, List<TestingRunResult.TestLog> testLogs, boolean skipSSRFCheck, boolean jsonRpcCheck) throws Exception {
        // don't lowercase url because query params will change and will result in incorrect request

        if (!jsonRpcCheck && shouldInitiateSSEStream(request)) {
            return sendRequestWithSse(request, followRedirects, testingRunConfig, debug, testLogs, skipSSRFCheck, false);
        }

        if(testingRunConfig != null && testingRunConfig.getConfigsAdvancedSettings() != null && !testingRunConfig.getConfigsAdvancedSettings().isEmpty()){
            calculateFinalRequestFromAdvancedSettings(request, testingRunConfig.getConfigsAdvancedSettings());
        }

        boolean executeScript = testingRunConfig != null;
        String tempPayload = ApiExecutorUtil.calculateHashAndAddAuth(request, executeScript);

        String url = prepareUrl(request, testingRunConfig);

        if (!(url.contains("insertRuntimeLog") || url.contains("insertTestingLog"))) {
            loggerMaker.infoAndAddToDb("Final url is: " + url, LogDb.TESTING);
        }

        // todo: remove this
        if (url.contains("api.uat.be.edenred.io/api.uat.be.edenred.io")) {
            url = url.replace("api.uat.be.edenred.io/api.uat.be.edenred.io", "api.uat.be.edenred.io");
        }

        if (url.contains("apisummit-uat.edenred.com/apisummit-uat.edenred.com")) {
            url = url.replace("apisummit-uat.edenred.com/apisummit-uat.edenred.com", "apisummit-uat.edenred.com");
        }

        if (url.contains("apisummit-dev.edenred.com/apisummit-dev.edenred.com")) {
            url = url.replace("apisummit-dev.edenred.com/apisummit-dev.edenred.com", "apisummit-dev.edenred.com");
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

        String type = request.findContentType();
        URLMethods.Method method = URLMethods.Method.fromString(request.getMethod());

        builder = builder.url(request.getFullUrlWithParams());

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
                response = sendWithRequestBody(request, builder, followRedirects, debug, testLogs, skipSSRFCheck, nonTestingContext, type);
                break;
            case OTHER:
                throw new Exception("Invalid method name");
        }
        //loggerMaker.infoAndAddToDb("Received response from: " + url, LogDb.TESTING);

        if (url.contains("login_submit")) {
            loggerMaker.infoAndAddToDb("Response Payload " + response.getBody(), LogDb.TESTING);
        }

        request.setBody(tempPayload);
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
                String message = String.format("Error in sending request for api : %s , will retry after %d seconds : %s", request.getUrl(),
                        limit, e.toString());
                loggerMaker.error(message);
                try {
                    Thread.sleep(1000 * limit);
                } catch (Exception f) {
                    String backoffMessage = String.format("Error in exponential backoff at limit %d  : %s", limit, f.toString());
                    loggerMaker.error(backoffMessage);
                }
            }
        }
        return response;
    }

    private static OriginalHttpResponse getRequest(OriginalHttpRequest request, Request.Builder builder, boolean followRedirects, boolean debug, List<TestingRunResult.TestLog> testLogs, boolean skipSSRFCheck, boolean nonTestingContext)  throws Exception{
        Request okHttpRequest = builder.build();
        return common(okHttpRequest, followRedirects, debug, testLogs, skipSSRFCheck, nonTestingContext, "application/json", request.getTlsAuthParam());
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
        Map<String,List<ConditionsType>> urlConditions = new HashMap<>();

        for(TestConfigsAdvancedSettings settings: advancedSettings){
            if(settings.getOperatorType().toLowerCase().contains("header")){
                headerConditions.put(settings.getOperatorType(), settings.getOperationsGroupList());
            }else if(settings.getOperatorType().toLowerCase().contains("url")){
                urlConditions.put(settings.getOperatorType(), settings.getOperationsGroupList());
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

        // modify query params as well from payload conditions only, not handling query conditions separately for now
        Utils.modifyQueryOperations(originalHttpRequest, 
            payloadConditions.getOrDefault(TestEditorEnums.NonTerminalExecutorDataOperands.MODIFY_BODY_PARAM.name(), emptyList),
            emptyList,
            payloadConditions.getOrDefault(TestEditorEnums.TerminalExecutorDataOperands.DELETE_BODY_PARAM.name(), emptyList)
        );

        // modify URL parameters using the fetchUrlModifyPayload functionality
        Utils.modifyUrlParamOperations(originalHttpRequest, 
            urlConditions.getOrDefault("MODIFY_URL_PARAM", emptyList),
            "token_replace"
        );
        Utils.modifyUrlParamOperations(originalHttpRequest, 
            urlConditions.getOrDefault("ADD_URL_PARAM", emptyList),
            "token_insert"
        );
    }

    private static OriginalHttpResponse sendWithRequestBody(OriginalHttpRequest request, Request.Builder builder, boolean followRedirects, boolean debug, List<TestingRunResult.TestLog> testLogs, boolean skipSSRFCheck, boolean nonTestingContext, String requestProtocol) throws Exception {
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

            return common(updatedRequest, followRedirects, debug, testLogs, skipSSRFCheck, nonTestingContext, requestProtocol, request.getTlsAuthParam());
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
            if(payload.startsWith("{")) {
                payload = HttpRequestResponseUtils.jsonToFormUrlEncoded(payload);
                body = RequestBody.create(payload, MediaType.parse(contentType));
            }
        } else if (contentType.contains(HttpRequestResponseUtils.GRPC_CONTENT_TYPE)) {
            try {
                loggerMaker.infoAndAddToDb("encoding to grpc payload:" + payload, LogDb.TESTING);
                payload = ProtoBufUtils.base64EncodedJsonToProtobuf(payload);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Unable to encode grpc payload:" + payload, LogDb.TESTING);
                payload = request.getBody();
            }
            try {// trying decoding payload
                byte[] payloadByteArray = Base64.getDecoder().decode(payload);
                loggerMaker.infoAndAddToDb("Final base64 encoded payload:"+ payload, LogDb.TESTING);
                body = RequestBody.create(payloadByteArray, MediaType.parse(contentType));
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Unable to decode grpc payload:" + payload, LogDb.TESTING);
            }
        }else if(contentType.contains(HttpRequestResponseUtils.SOAP) || contentType.contains(HttpRequestResponseUtils.XML)){
            // here we are assuming that the request is in xml format
            // now convert this into valid json body string

            // get the url and method from temp headers
            if(request.getHeaders().containsKey("x-akto-original-url") && request.getHeaders().containsKey("x-akto-original-method")){
                String url = request.getHeaders().get("x-akto-original-url").get(0);
                String method = request.getHeaders().get("x-akto-original-method").get(0);
                String originalXmlPayload = OriginalReqResPayloadInformation.getInstance().getOriginalReqPayloadMap().get(method + "_" + url); // get original payload
                if(originalXmlPayload != null && !originalXmlPayload.isEmpty()){
                    String modifiedXmlPayload = HttpRequestResponseUtils.updateXmlWithModifiedJson(originalXmlPayload, payload);
                    payload = modifiedXmlPayload;
                }
                // remove the temp headers
                request.getHeaders().remove("x-akto-original-url");
                request.getHeaders().remove("x-akto-original-method");
            }  
        } 

        if (payload == null) payload = "";
        if (body == null) {// body not created by GRPC block yet
            if (request.getHeaders().containsKey("charset") || isJsonRpcRequest(request)) {
                body = RequestBody.create(payload, null);
                request.getHeaders().remove("charset");
            } else {
                body = RequestBody.create(payload, MediaType.parse(contentType));
            }
        }
        builder = builder.method(request.getMethod(), body);
        Request okHttpRequest = builder.build();
        return common(okHttpRequest, followRedirects, debug, testLogs, skipSSRFCheck, nonTestingContext, requestProtocol, request.getTlsAuthParam());
    }

    private static boolean isJsonRpcRequest(OriginalHttpRequest request) {
        try {
            String body = request.getBody();
            if (body == null) {
                return false;
            }
            JsonNode node = objectMapper.readTree(body);
            return node.has("jsonrpc") && node.has("id") && node.has("method");
        } catch (Exception e) {
            return false;
        }
    }

    private static class SseSession {
        String endpoint;
        List<String> messages = new ArrayList<>();
        Response response; // Store the OkHttp Response for cleanup
        Thread readerThread;
    }

    private static SseSession openSseSession(String host, String endpoint, Headers headers, boolean debug) throws Exception {
        SseSession session = new SseSession();
        OkHttpClient client = new OkHttpClient.Builder().build();
        // Use provided endpoint for the SSE request
        Request request = new Request.Builder().url(host + endpoint).headers(headers).build();

        Call call = client.newCall(request);
        Response response = call.execute();
        if (!response.isSuccessful()) {
            loggerMaker.warn("ResponseHeader: {}", response.headers());
            throw new IOException("Failed to open SSE session: " + response);
        }
        session.response = response; // Store the response for later closing
        InputStream is = response.body().byteStream();
        Scanner scanner = new Scanner(is);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.startsWith("event: endpoint")) {
                String dataLine = scanner.nextLine();
                if (dataLine.startsWith("data:")) {
                    session.endpoint = dataLine.substring(5).trim();
                    break;
                }
            }
        }
        // Keep the stream open for later reading
        session.messages = Collections.synchronizedList(new ArrayList<>());
        session.readerThread = new Thread(() -> {
            try {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if (line.startsWith("event: message")) {
                        String dataLine = scanner.nextLine();
                        if (dataLine.startsWith("data:")) {
                            String data = dataLine.substring(5).trim();
                            session.messages.add(data);
                        }
                    }
                }
            } catch (Exception ignored) {}
        });
        session.readerThread.start();
        return session;
    }

    private static String waitForMatchingSseMessage(SseSession session, String id, long timeoutMs) throws Exception {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            synchronized (session.messages) {
                Iterator<String> it = session.messages.iterator();
                while (it.hasNext()) {
                    String msg = it.next();
                    try {
                        JsonNode node = objectMapper.readTree(msg);
                        if (node.has("method")) {
                            continue;
                        }
                        if (node.has("id") && node.get("id").asText().equals(id)) {
                            return msg;
                        }
                    } catch (Exception ignored) {
                        loggerMaker.error("Error parsing SSE message: {}", ignored.getMessage());
                    }
                }
            }
            Thread.sleep(100);
        }
        throw new Exception("Timeout waiting for SSE message with id=" + id);
    }

    public static OriginalHttpResponse sendRequestWithSse(OriginalHttpRequest request, boolean followRedirects,
        TestingRunConfig testingRunConfig, boolean debug, List<TestingRunResult.TestLog> testLogs,
        boolean skipSSRFCheck, boolean overrideMessageEndpoint) throws Exception {
        // Always use prepareUrl to get the absolute URL
        String url = prepareUrl(request, testingRunConfig);
        URI uri = new URI(url);
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalArgumentException("URL must be absolute with scheme and host for SSE: " + url);
        }
        String host = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() != -1 ? ":" + uri.getPort() : "");

        // Use provided SSE endpoint or default to "/sse"
        String sseEndpoint = "/sse"; // Default SSE endpoint
        if (request.getHeaders() != null && request.getHeaders().containsKey("x-akto-sse-endpoint")) {
            sseEndpoint = request.getHeaders().get("x-akto-sse-endpoint").get(0);
            // Remove the custom header to avoid sending it to the server
            request.getHeaders().remove("x-akto-sse-endpoint");
        }

        // Open SSE session with dynamic endpoint and request headers
        Headers headers = request.toOkHttpHeaders();
        SseSession session = openSseSession(host, sseEndpoint, headers, debug);

        if (StringUtils.isEmpty(session.endpoint)) {
            closeSseSession(session);
            throw new Exception("Failed to open SSE session as endpoint not found");
        }

        request.setUrl(url);

        // Add sessionId as query param to actual request
        String[] queryParam = session.endpoint.split("\\?");
        // for cases where MCP tools are discovered by Akto, we need to override/add the message endpoint with the actual one we received from the sse stream
        if (overrideMessageEndpoint) {
            request.setUrl(host + session.endpoint);
        } else {
            if (queryParam.length > 1) {
                request.setQueryParams(queryParam[1]);
            }
        }

        // Send actual request
        OriginalHttpResponse resp = sendRequest(request, followRedirects, testingRunConfig, debug, testLogs, skipSSRFCheck, true);

        if (resp.getStatusCode() >= 400) {
            closeSseSession(session);
            return resp;
        }

        // Wait for matching SSE message
        String body = request.getBody();
        JsonNode node = objectMapper.readTree(body);
        String id = node.get("id").asText();
        String sseMsg = null;
        try {
            sseMsg = waitForMatchingSseMessage(session, id, 60000); // 60s timeout

        } catch(Exception e) {
            throw new Exception("SSE connection timeout, id=" + id + ": " + e.getMessage(), e);
        }
        finally {
            closeSseSession(session);
        }

        JsonNode sseJson = objectMapper.readTree(sseMsg);
        String jsonBody = objectMapper.writeValueAsString(sseJson);
        return new OriginalHttpResponse(jsonBody, resp.getHeaders(), resp.getStatusCode());
    }

    private static void closeSseSession(SseSession session) throws InterruptedException {
        if (session.readerThread != null) {
            session.readerThread.interrupt();
            session.readerThread.join();
        }
        if (session.response != null) {
            if (session.response.body() != null) {
                session.response.body().close();
            }
            session.response.close();
        }
    }

    private static boolean shouldInitiateSSEStream(OriginalHttpRequest request) {

        if (!isJsonRpcRequest(request)) {
            return false;
        }

        if (!Method.POST.name().equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        for (Map.Entry<String, List<String>> entry : request.getHeaders().entrySet()) {
            if (HttpRequestResponseUtils.HEADER_ACCEPT.equalsIgnoreCase(entry.getKey()) && entry.getValue() != null
                && !entry.getValue().isEmpty()) {
                String value = entry.getValue().get(0).toLowerCase();
                if (value.contains(HttpRequestResponseUtils.TEXT_EVENT_STREAM_CONTENT_TYPE) && value.contains(
                    HttpRequestResponseUtils.APPLICATION_JSON)) {
                    return false;
                }
            }
        }
        // Check if x-akto-sse-endpoint header exists, return false if it doesn't
        if (request.findHeaderValue("x-akto-sse-endpoint") == null) {
            return false;
        }
        return true;
    }
}
