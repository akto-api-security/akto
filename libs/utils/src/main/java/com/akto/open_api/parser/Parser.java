package com.akto.open_api.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response.Status;

import com.akto.dao.context.Context;
import com.akto.dto.HttpResponseParams;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.open_api.parser.parameter_parser.CookieParser;
import com.akto.open_api.parser.parameter_parser.HeaderParser;
import com.akto.open_api.parser.parameter_parser.PathParamParser;
import com.akto.open_api.parser.parameter_parser.QueryParamParser;
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

public class Parser {

    private static final LoggerMaker loggerMaker = new LoggerMaker(Parser.class, LogDb.DASHBOARD);
    private static final ObjectMapper mapper = new ObjectMapper();

    public static List<String> convertOpenApiToAkto(OpenAPI openAPI) {

        // replaces all refs with actual objects from components.
        ResolverFully resolverUtil = new ResolverFully();
        resolverUtil.resolveFully(openAPI);

        List<String> messages = new ArrayList<>();

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
            loggerMaker.infoAndAddToDb("unable to parse security schemes " + e.getMessage());
        }

        List<SecurityRequirement> rootSecurity = openAPI.getSecurity();
        if (rootSecurity == null) {
            rootSecurity = new ArrayList<>();
        }

        for (String path : paths.keySet()) {
            String originalPath = String.copyValueOf(path.toCharArray());
            PathItem pathItem = paths.get(path);
            if (pathItem == null)
                continue;

            List<Server> serversFromPath = pathItem.getServers();
            List<Parameter> parametersFromPath = pathItem.getParameters();

            for (PathItem.HttpMethod operationType : pathItem.readOperationsMap().keySet()) {
                PathItem.HttpMethod method = operationType;
                Operation operation = pathItem.readOperationsMap().get(method);

                List<Parameter> parametersFromOperation = operation.getParameters();
                List<Server> serversFromOperation = pathItem.readOperationsMap().get(method).getServers();

                /*
                 * DO NOT CHANGE THE ORDER for passing parameter arrays.
                 * PATH parameters take precedence over ROOT parameters.
                 * OPERATION parameters take precedence over PATH parameters.
                 */
                path = PathParamParser.replacePathParameter(originalPath, Arrays.asList(
                        parametersFromOperation, parametersFromPath));

                path = QueryParamParser.addQueryParameters(path, Arrays.asList(
                        parametersFromPath, parametersFromOperation));

                path = ServerParser.addServer(path, Arrays.asList(
                        serversFromOperation, serversFromPath, servers));

                Map<String, String> requestHeaders = HeaderParser.buildHeaders(
                        Arrays.asList(parametersFromPath, parametersFromOperation));

                Map<String, String> cookieHeaders = CookieParser.getCookieHeader(
                        Arrays.asList(parametersFromPath, parametersFromOperation));

                requestHeaders.putAll(cookieHeaders);

                String requestString = "";
                try {
                    RequestBody requestBody = operation.getRequestBody();
                    if (requestBody != null) {
                        requestBody = new RequestBody();
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
                    loggerMaker.infoAndAddToDb(
                            "unable to handle request body for " + path + " " + method + " " + e.toString());
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
                                } else if (apiKeyIn.equals("cookie")) {
                                    requestHeaders.put("Cookie", apiKeyName + "=" + apiKeyValue);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    loggerMaker.infoAndAddToDb("unable to parse security schemes " + e.getMessage());
                }

                String requestHeadersString = "";
                try {
                    requestHeadersString = mapper.writeValueAsString(requestHeaders);
                } catch (JsonProcessingException e) {
                    loggerMaker.infoAndAddToDb(
                            "unable to parse request headers for " + path + " " + method + " " + e.getMessage());
                }

                String responseString = "";
                String responseHeadersString = "";
                Map<String, String> messageObject = new HashMap<>();

                try {
                    ApiResponses responses = operation.getResponses();

                    if (responses != null) {
                        for (String responseCode : responses.keySet()) {

                            if(responseCode.equals(ApiResponses.DEFAULT)) continue;

                            int statusCode = Integer.parseInt(responseCode);
                            if (HttpResponseParams.validHttpResponseCode(statusCode)) {

                                ApiResponse response = responses.get(responseCode);

                                messageObject.put("statusCode", responseCode);
                                Status status = Status.fromStatusCode(statusCode);
                                if (status != null) {
                                    messageObject.put("status", status.getReasonPhrase());
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
                                } catch (Exception e) {
                                    loggerMaker.infoAndAddToDb("unable to handle response headers for " + path + " "
                                            + method + " " + e.toString());
                                }

                                // save the first valid response and break.
                                break;
                            }
                        }
                    }

                } catch (Exception e) {
                    loggerMaker.infoAndAddToDb(
                            "unable to handle response body for " + path + " " + method + " " + e.toString());
                }

                messageObject.put("akto_account_id", Context.accountId.get().toString());
                messageObject.put("path", path);
                messageObject.put("method", method.toString().toUpperCase());
                messageObject.put("requestHeaders", requestHeadersString);
                messageObject.put("requestPayload", requestString);
                messageObject.put("ip", "null");
                messageObject.put("time", Context.now() + "");
                messageObject.put("type", "HTTP");
                messageObject.put("source", "OTHER");

                messageObject.put("responsePayload", responseString);
                messageObject.put("responseHeaders", responseHeadersString);
                messageObject.putIfAbsent("status", "OK");
                messageObject.putIfAbsent("statusCode", "200");

                try {
                    String s = mapper.writeValueAsString(messageObject);
                    messages.add(s);
                } catch (JsonProcessingException e) {
                    loggerMaker.infoAndAddToDb("unable to parse message object for " + path + " " + method + " "
                            + e.getMessage());
                }
            }
        }
        return messages;
    }

}