package com.akto.open_api.parser;

import java.net.URL;
import java.util.*;
import javax.ws.rs.core.Response.Status;

import com.akto.dao.context.Context;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.HttpResponseParams.Source;
import com.akto.dto.upload.FileUploadError;
import com.akto.dto.upload.SwaggerUploadLog;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.open_api.parser.parameter_parser.CookieParser;
import com.akto.open_api.parser.parameter_parser.HeaderParser;
import com.akto.open_api.parser.parameter_parser.PathParamParser;
import com.akto.open_api.parser.parameter_parser.QueryParamParser;
import com.akto.testing.ApiExecutor;
import com.akto.util.Pair;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.parser.util.ResolverFully;

import static com.akto.dto.RawApi.convertHeaders;

public class Parser {

    private static final LoggerMaker loggerMaker = new LoggerMaker(Parser.class, LogDb.DASHBOARD);
    private static final ObjectMapper mapper = new ObjectMapper();

    public static ParserResult convertOpenApiToAkto(OpenAPI openAPI, String uploadId, boolean useHost ) {

        List<FileUploadError> fileLevelErrors = new ArrayList<>();
        List<SwaggerUploadLog> uploadLogs = new ArrayList<>();
        // replaces all refs with actual objects from components.
        ResolverFully resolverUtil = new ResolverFully();
        resolverUtil.resolveFully(openAPI);


        Paths paths = openAPI.getPaths();

        List<Server> servers = openAPI.getServers();

        if (servers == null) {
            servers = new ArrayList<>();
            servers.add(new Server().url("/"));
        }

        Components components = openAPI.getComponents();
        Map<String, Pair<String, Pair<String, String>>> parsedSecuritySchemes = new HashMap<>();
        try {
            if (components != null) {
                Map<String, SecurityScheme> securitySchemes = components.getSecuritySchemes();
                if (securitySchemes != null) {
                    for (String securitySchemeName : securitySchemes.keySet()) {
                        SecurityScheme securityScheme = securitySchemes.get(securitySchemeName);
                        if (securityScheme != null) {
                            if (securityScheme.getType().equals(SecurityScheme.Type.APIKEY)) {
                                String apiKeyName = securityScheme.getName();
                                String apiKeyIn = securityScheme.getIn().toString();
                                parsedSecuritySchemes.put(securitySchemeName,
                                        new Pair<>(apiKeyIn,
                                                new Pair<>(apiKeyName, "security-key")));
                            } else if (securityScheme.getType().equals(SecurityScheme.Type.HTTP)) {
                                String scheme = securityScheme.getScheme();
                                if (scheme != null) {
                                    parsedSecuritySchemes.put(securitySchemeName,
                                            new Pair<>("header",
                                                    new Pair<>("Authorization", scheme + " sample-auth-token")));
                                }
                            }
                            // Skipping OAUTH2 and OPENIDCONNECT
                        }
                    }
                }
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "unable to parse security schemes " + e.getMessage());
            fileLevelErrors.add(new FileUploadError("unable to parse security schemes " + e.getMessage(), FileUploadError.ErrorType.WARNING));
        }

        List<SecurityRequirement> rootSecurity = openAPI.getSecurity();
        if (rootSecurity == null) {
            rootSecurity = new ArrayList<>();
        }

        int count = 0;

        loggerMaker.infoAndAddToDb("Parsing " + paths.size() + " paths from the uploaded file");

        for (String path : paths.keySet()) {
            String originalPath = String.copyValueOf(path.toCharArray());
            PathItem pathItem = paths.get(path);
            if (pathItem == null)
                continue;
            try {

                List<Server> serversFromPath = pathItem.getServers();
                List<Parameter> parametersFromPath = pathItem.getParameters();

                for (PathItem.HttpMethod method : pathItem.readOperationsMap().keySet()) {
                    count++;
                    List<FileUploadError> apiLevelErrors = new ArrayList<>();
                    Operation operation = pathItem.readOperationsMap().get(method);

                    List<Parameter> parametersFromOperation = operation.getParameters();
                    List<Server> serversFromOperation = pathItem.readOperationsMap().get(method).getServers();

                    /*
                     * DO NOT CHANGE THE ORDER for passing parameter arrays.
                     * PATH parameters take precedence over ROOT parameters.
                     * OPERATION parameters take precedence over PATH parameters.
                     */
                    Map<String, String> requestHeaders = new HashMap<>();
                    try {
                        path = PathParamParser.replacePathParameter(originalPath, Arrays.asList(
                                parametersFromOperation, parametersFromPath));

                        path = QueryParamParser.addQueryParameters(path, Arrays.asList(
                                parametersFromPath, parametersFromOperation));

                        path = ServerParser.addServer(path, Arrays.asList(
                                serversFromOperation, serversFromPath, servers));

                       requestHeaders = HeaderParser.buildHeaders(
                                Arrays.asList(parametersFromPath, parametersFromOperation));

                        Map<String, String> cookieHeaders = CookieParser.getCookieHeader(
                                Arrays.asList(parametersFromPath, parametersFromOperation));

                        requestHeaders.putAll(cookieHeaders);
                    } catch (Exception e) {
                        loggerMaker.infoAndAddToDb("unable to parse path parameters for " + path + " " + method + " " + e.toString());
                        apiLevelErrors.add(new FileUploadError("unable to parse path parameters: " + e.getMessage(), FileUploadError.ErrorType.ERROR));
                    }

                    String requestString = "";
                    try {
                        RequestBody requestBody = operation.getRequestBody();
                        if (requestBody != null) {
                            Content content = requestBody.getContent();
                            if (content != null) {
                                Pair<String, String> example = ContentParser.getExampleFromContent(content);
                                if (!(example.getFirst().isEmpty())) {
                                    requestHeaders.put("Content-Type", example.getFirst());
                                }
                                requestString = example.getSecond();
                            }
                        }
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(e, "unable to handle request body for " + path + " " + method + " " + e.toString());
                        apiLevelErrors.add(new FileUploadError("Unable to parse request body: " + e.getMessage(), FileUploadError.ErrorType.ERROR));
                    }

                    List<SecurityRequirement> operationSecurity = operation.getSecurity();

                    // adding security schemes for API.
                    try {
                        if (operationSecurity == null) {
                            operationSecurity = rootSecurity;
                        }
                        if (!operationSecurity.isEmpty()) {
                            // adding first security scheme.
                            SecurityRequirement securityRequirement = operationSecurity.get(0);
                            for (String securitySchemeName : securityRequirement.keySet()) {
                                Pair<String, Pair<String, String>> securityScheme = parsedSecuritySchemes
                                        .get(securitySchemeName);
                                if (securityScheme != null) {
                                    String apiKeyIn = securityScheme.getFirst();
                                    String apiKeyName = securityScheme.getSecond().getFirst();
                                    String apiKeyValue = securityScheme.getSecond().getSecond();
                                    if (apiKeyIn.equals("header")) {
                                        requestHeaders.put(apiKeyName, apiKeyValue);
                                    } else if (apiKeyIn.equals("query")) {
                                        if (path.contains("?")) {
                                            path = path + "&" + apiKeyName + "=" + apiKeyValue;
                                        } else {
                                            path = path + "?" + apiKeyName + "=" + apiKeyValue;
                                        }
                                    } else if (apiKeyIn.equals(CookieParser.COOKIE)) {
                                        if (requestHeaders.containsKey(CookieParser.COOKIE)) {
                                            requestHeaders.put(CookieParser.COOKIE,
                                                    requestHeaders.get(CookieParser.COOKIE) + "; " + apiKeyName + "="
                                                            + apiKeyValue);
                                        } else {
                                            requestHeaders.put(CookieParser.COOKIE, apiKeyName + "=" + apiKeyValue);
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(e, "unable to parse security schemes " + e.getMessage());
                        apiLevelErrors.add(new FileUploadError("unable to parse security schemes: " + e.getMessage(), FileUploadError.ErrorType.WARNING));
                    }

                    String requestHeadersString = "";
                    String urlPath = path;
                    try {
                        URL url = new URL(path);
                        // without host/server.
                        urlPath = url.getPath() + (url.getQuery() != null ? "?" + url.getQuery() : "");
                        // Get the domain (including scheme)
                        requestHeaders.putIfAbsent("Host", url.getHost());
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(e, "unable to parse url for " + path + " " + method + " " + e.toString());
                    }

                    if (requestHeaders == null) {
                        requestHeaders = new HashMap<>();
                    }

                    if (requestHeaders != null && !requestHeaders.isEmpty()) {
                        try {
                            requestHeadersString = mapper.writeValueAsString(requestHeaders);
                        } catch (JsonProcessingException e) {
                            loggerMaker.errorAndAddToDb(e, "unable to parse request headers for " + path + " " + method + " " + e.getMessage());
                            apiLevelErrors.add(new FileUploadError("error while converting request headers to string: " + e.getMessage(), FileUploadError.ErrorType.ERROR));
                        }
                    }

                    Map<String, String> messageObject = new HashMap<>();

                    List<Map<String, String>> responseObjectList = new ArrayList<>();

                    try {
                        loggerMaker.infoAndAddToDb("Replaying request for " + path + " " + method + ", replaying request");

                        Map<String, List<String>> modifiedHeaders = new HashMap<>();
                        for (String key : requestHeaders.keySet()) {
                            modifiedHeaders.put(key, Collections.singletonList(requestHeaders.get(key)));
                        }
                        OriginalHttpRequest originalHttpRequest = new OriginalHttpRequest(path, "", method.toString(), requestString, modifiedHeaders, "http");
                        String responseHeadersString;
                        String responsePayload;
                        String statusCode;
                        String status;
                        try {
                            OriginalHttpResponse res = ApiExecutor.sendRequest(originalHttpRequest, true, null, false, new ArrayList<>());
                            responseHeadersString = convertHeaders(res.getHeaders());
                            responsePayload =  res.getBody();
                            statusCode =  res.getStatusCode()+"";
                            status =  "";
                            if(!HttpResponseParams.validHttpResponseCode(res.getStatusCode())){
                                throw new Exception("Found non 2XX response on replaying the API");
                            }
                            Map<String, String> responseObject = new HashMap<>();
                            responseObject.put(mKeys.responsePayload, responsePayload);
                            responseObject.put(mKeys.responseHeaders, responseHeadersString);
                            responseObject.put(mKeys.status, status);
                            responseObject.put(mKeys.statusCode, statusCode);
                            responseObjectList.add(responseObject);
                        } catch (Exception e) {
                            loggerMaker.errorAndAddToDb(e, "Error while making request for " + originalHttpRequest.getFullUrlWithParams() + " : " + e.getMessage());
                            ApiResponses responses = operation.getResponses();
                            if (responses != null) {
                                for (String responseCode : responses.keySet()) {

                                    if (responseCode.equals(ApiResponses.DEFAULT))
                                        continue;

                                    int statusCodeInt = Integer.parseInt(responseCode);
                                    if (HttpResponseParams.validHttpResponseCode(statusCodeInt)) {

                                        String responseString = "";
                                        responseHeadersString = "";
                                        Map<String, String> responseObject = new HashMap<>();

                                        ApiResponse response = responses.get(responseCode);

                                        responseObject.put(mKeys.statusCode, responseCode);
                                        Status statusObj = Status.fromStatusCode(statusCodeInt);
                                        if (statusObj != null) {
                                            responseObject.put(mKeys.status, statusObj.getReasonPhrase());
                                        }

                                        Content content = response.getContent();

                                        Map<String, Header> headers = response.getHeaders();
                                        Map<String, String> responseHeaders = new HashMap<>();
                                        if (headers != null) {
                                            responseHeaders = HeaderParser.buildResponseHeaders(headers);
                                        }

                                        if (content != null) {
                                            Pair<String, String> example = ContentParser.getExampleFromContent(content);
                                            if (!(example.getFirst().isEmpty())) {
                                                responseHeaders.put("Content-Type", example.getFirst());
                                            }
                                            responseString = example.getSecond();
                                        }

                                        try {
                                            responseHeadersString = mapper.writeValueAsString(responseHeaders);
                                        } catch (Exception e1) {
                                            loggerMaker.errorAndAddToDb(e1, "unable to handle response headers for " + path + " "
                                                    + method + " " + e1.getMessage());
                                            apiLevelErrors.add(new FileUploadError("Replaying the request failed, reason: " + e.getMessage(), FileUploadError.ErrorType.ERROR));
                                            apiLevelErrors.add(new FileUploadError("Error while converting response headers to string from example: " +e1.getMessage(),FileUploadError.ErrorType.ERROR));
                                        }

                                        responseObject.put(mKeys.responsePayload, responseString);
                                        responseObject.put(mKeys.responseHeaders, responseHeadersString);
                                        responseObjectList.add(responseObject);
                                    }
                                }
                            }
                            else {
                                apiLevelErrors.add(new FileUploadError("Replaying the request failed, reason: " + e.getMessage(), FileUploadError.ErrorType.ERROR));
                                apiLevelErrors.add(new FileUploadError("No example responses found for the API in the uploaded file", FileUploadError.ErrorType.ERROR));
                            }
                        }

                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(e, "unable to handle response body for " + path + " " + method + " " + e.toString());
                        apiLevelErrors.add(new FileUploadError("Error while converting response to Akto format: " + e.getMessage(), FileUploadError.ErrorType.ERROR));
                    }

                    messageObject.put(mKeys.akto_account_id, Context.accountId.get().toString());
                    if(useHost){
                        messageObject.put(mKeys.path, path);
                    } else {
                        messageObject.put(mKeys.path, urlPath);
                    }
                    messageObject.put(mKeys.method, method.toString().toUpperCase());
                    messageObject.put(mKeys.requestHeaders, requestHeadersString);
                    messageObject.put(mKeys.requestPayload, requestString);
                    messageObject.put(mKeys.ip, "null");
                    messageObject.put(mKeys.time, Context.now() + "");
                    messageObject.put(mKeys.type, "HTTP");
                    // swagger uploads are treated as HAR files.
                    messageObject.put(mKeys.source, Source.OPEN_API.name());

                    if (responseObjectList.isEmpty()) {
                        responseObjectList.add(emptyResponseObject);
                    }

                    for (Map<String, String> responseObject : responseObjectList) {
                        messageObject.putAll(emptyResponseObject);
                        messageObject.putAll(responseObject);

                        /*
                         * if no data is present, no entry is made,
                         * so to avoid that, we add dummy data
                         */

                        fillDummyIfEmptyMessage(messageObject);
                        SwaggerUploadLog log = new SwaggerUploadLog();
                        log.setMethod(method.toString());
                        log.setUrl(urlPath);
                        log.setUploadId(uploadId);
                        try {
                            String s = mapper.writeValueAsString(messageObject);
                            log.setAktoFormat(s);

                        } catch (JsonProcessingException e) {
                            loggerMaker.errorAndAddToDb(e, "unable to parse message object for " + path + " " + method + " " + e.getMessage());
                            apiLevelErrors.add(new FileUploadError("Error while converting message to Akto format: " + e.getMessage(), FileUploadError.ErrorType.ERROR));
                            log.setAktoFormat(null);
                        }
                        if(!apiLevelErrors.isEmpty()) {
                            log.setErrors(apiLevelErrors);
                        }
                        uploadLogs.add(log);
                    }
                }

            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "unable to parse path item for " + path + " " + e.toString());
            }
        }

        loggerMaker.infoAndAddToDb("Parsed " + count + " APIs from the uploaded file");
        loggerMaker.infoAndAddToDb("Created " + uploadLogs.size() + " entries in akto format");
        loggerMaker.infoAndAddToDb("Found " + fileLevelErrors.size() + " errors in the uploaded file");

        ParserResult parserResult = new ParserResult();
        parserResult.setFileErrors(fileLevelErrors);
        parserResult.setUploadLogs(uploadLogs);
        parserResult.setTotalCount(count);
        return parserResult;
    }

    // message keys for akto format.
    public interface mKeys {
        String akto_account_id = "akto_account_id";
        String path = "path";
        String method = "method";
        String requestHeaders = "requestHeaders";
        String requestPayload = "requestPayload";
        String responseHeaders = "responseHeaders";
        String responsePayload = "responsePayload";
        String status = "status";
        String statusCode = "statusCode";
        String ip = "ip";
        String time = "time";
        String type = "type";
        String source = "source";
    }

    private final static Map<String, String> emptyResponseObject = new HashMap<String, String>() {
        {
            put(mKeys.responsePayload, "");
            put(mKeys.responseHeaders, "");
            put(mKeys.status, "OK");
            put(mKeys.statusCode, "200");
        }
    };

    private final static Map<String, String> AKTO_HEADER = new HashMap<String, String>() {
        {
            put("AKTO", "0");
        }
    };

    private static void fillDummyIfEmptyMessage(Map<String, String> messageObject) {

        if (messageObject.get(mKeys.requestHeaders).isEmpty()
                && messageObject.get(mKeys.requestPayload).isEmpty()
                && messageObject.get(mKeys.responseHeaders).isEmpty()
                && messageObject.get(mKeys.responsePayload).isEmpty()
                // ignoring messages with query parameters.
                && !messageObject.get(mKeys.path).contains("?")) {

            try {
                messageObject.put(mKeys.requestHeaders,
                        mapper.writeValueAsString(AKTO_HEADER));
            } catch (JsonProcessingException e) {
            }
        }

    }

}