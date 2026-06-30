package com.akto.testing.yaml_tests;

import com.akto.dto.ApiInfo;
import com.akto.dto.CustomAuthType;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.*;
import com.akto.dto.testing.*;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.rules.TestPlugin;
import com.akto.test_editor.auth.AuthValidator;
import com.akto.test_editor.execution.Executor;
import com.akto.testing.StatusCodeAnalyser;

import java.util.List;
import java.util.Map;

public class YamlTestTemplate extends SecurityTestTemplate {

    private static final LoggerMaker loggerMaker = new LoggerMaker(YamlTestTemplate.class);
    private final List<CustomAuthType> customAuthTypes;
    public YamlTestTemplate(ApiInfo.ApiInfoKey apiInfoKey, FilterNode filterNode, FilterNode validatorNode,
                            ExecutorNode executorNode, RawApi rawApi, Map<String, Object> varMap, Auth auth,
                            AuthMechanism authMechanism, String logId, TestingRunConfig testingRunConfig,
                            List<CustomAuthType> customAuthTypes, Strategy strategy) {
        super(apiInfoKey, filterNode, validatorNode, executorNode ,rawApi, varMap, auth, authMechanism, logId, testingRunConfig, strategy);
        this.customAuthTypes = customAuthTypes;
    }

    @Override
    public boolean filter() {
        // loggerMaker.infoAndAddToDb("filter started" + logId, LogDb.TESTING);
        
        // Check if this is a WebSocket message — protocolType is set by buildFromSampleMessage
        if (isWebSocketMessage(this.rawApi)) {
            loggerMaker.infoAndAddToDb("WebSocket with events detected, filtering on event payload for " + logId, LogDb.TESTING);
            return filterWebSocketEvents();
        }
        
        List<String> authHeaders = AuthValidator.getHeaders(this.auth, this.authMechanism, this.customAuthTypes);
        // loggerMaker.infoAndAddToDb("found authHeaders " + authHeaders + " " + logId, LogDb.TESTING);
        if (authHeaders != null && authHeaders.size() > 0) {
            this.varMap.put("auth_headers", authHeaders);
        }
        if (this.auth != null && this.auth.getAuthenticated() != null) {
            // loggerMaker.infoAndAddToDb("validating auth, authenticated value is " + this.auth.getAuthenticated() + " " + logId, LogDb.TESTING);
            boolean validAuthHeaders = AuthValidator.validate(this.auth, this.rawApi, this.authMechanism, this.customAuthTypes);
            if (!validAuthHeaders) {
                // loggerMaker.infoAndAddToDb("invalid auth, skipping filter " + logId, LogDb.TESTING);
                return false;
            }
        }
        boolean isValid = TestPlugin.validateFilter(this.getFilterNode(),this.getRawApi(), this.getApiInfoKey(), this.varMap, this.logId);
        // loggerMaker.infoAndAddToDb("filter status " + isValid + " " + logId, LogDb.TESTING);
        return isValid;
    }
    
    private boolean isWebSocketMessage(RawApi rawApi) {
        if (rawApi == null || rawApi.getRequest() == null) return false;
        return rawApi.getRequest().getProtocolType() == com.akto.dto.OriginalHttpRequest.ProtocolType.WEBSOCKET;
    }

    private boolean filterWebSocketEvents() {
        try {

            RawApi wsRawApi = buildWebSocketFilterRawApi();
            if (wsRawApi == null) {
                loggerMaker.infoAndAddToDb("WebSocket filterRawApi could not be built, skipping " + logId, LogDb.TESTING);
                return false;
            }

            ApiInfo.ApiInfoKey wsApiInfoKey = new ApiInfo.ApiInfoKey(
                this.getApiInfoKey().getApiCollectionId(),
                this.getApiInfoKey().getUrl(),
                com.akto.dto.type.URLMethods.Method.POST
            );
            boolean isValid = TestPlugin.validateFilter(this.getFilterNode(), wsRawApi, wsApiInfoKey, this.varMap, this.logId);
            loggerMaker.infoAndAddToDb("WebSocket filter result: " + isValid + " for " + logId, LogDb.TESTING);
            return isValid;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error filtering WebSocket events: " + e.getMessage(), LogDb.TESTING);
            return false;
        }
    }


    private RawApi buildWebSocketFilterRawApi() {
        try {
            String eventPayload = this.rawApi.getRequest().getBody();
            boolean emptyBody = (eventPayload == null || eventPayload.isEmpty() || "{}".equals(eventPayload.trim()));
            if (emptyBody && !this.rawApi.getRequest().isConnectionString()) {

                loggerMaker.infoAndAddToDb("WS filter: no event payload found in request body for " + logId, LogDb.TESTING);
                return null;
            }


            RawApi wsRawApi = this.rawApi.copy();

            wsRawApi.getRequest().setMethod("POST");

            loggerMaker.infoAndAddToDb("WS filter RawApi - method: POST, status: "
                + wsRawApi.getResponse().getStatusCode()
                + ", body(100): " + eventPayload.substring(0, Math.min(100, eventPayload.length())), LogDb.TESTING);

            return wsRawApi;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error building WebSocket filter RawApi: " + e.getMessage(), LogDb.TESTING);
            return null;
        }
    }


    @Override
    public boolean checkAuthBeforeExecution(boolean debug, List<TestingRunResult.TestLog> testLogs) {
        if (this.auth != null && this.auth.getAuthenticated() != null && this.auth.getAuthenticated() == true) {
            // loggerMaker.infoAndAddToDb("running noAuth check " + logId, LogDb.TESTING);
            ExecutionResult res = AuthValidator.checkAuth(this.auth, this.rawApi.copy(), this.testingRunConfig, this.customAuthTypes, debug, testLogs);
            if(res.getSuccess()) {
                OriginalHttpResponse resp = res.getResponse();
                String respBody = resp.getBody().toLowerCase();
                if (respBody.contains("unauthorized") || respBody.contains("unauthorised") || respBody.contains("\"error\"")) {
                    return true;
                }

                int statusCode = StatusCodeAnalyser.getStatusCode(resp.getBody(), resp.getStatusCode());
                if (statusCode >= 200 && statusCode < 300) {
                    loggerMaker.infoAndAddToDb("noAuth check failed, skipping execution " + logId);
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public YamlTestResult executor(boolean debug, List<TestingRunResult.TestLog> testLogs) {
        // loggerMaker.infoAndAddToDb("executor started" + logId, LogDb.TESTING);
        YamlTestResult results = new Executor().execute(this.executorNode, this.rawApi, this.varMap, this.logId,
                this.authMechanism, this.validatorNode, this.apiInfoKey, this.testingRunConfig, this.customAuthTypes,
                debug, testLogs, memory);
        // loggerMaker.infoAndAddToDb("execution result size " + results.size() +  " " + logId, LogDb.TESTING);
        return results;
    }

    @Override
    public void triggerMetaInstructions(Strategy strategy, YamlTestResult attempts) {
        com.akto.test_editor.strategy.Strategy.triggerStrategyInstructions(strategy, attempts);
    }

}
