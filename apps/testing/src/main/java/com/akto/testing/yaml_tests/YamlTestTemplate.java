package com.akto.testing.yaml_tests;

import com.akto.dto.ApiInfo;
import com.akto.dto.CustomAuthType;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.Auth;
import com.akto.dto.test_editor.ExecutionResult;
import com.akto.dto.test_editor.ExecutorNode;
import com.akto.dto.test_editor.FilterNode;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.log.LoggerMaker;
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
                            AuthMechanism authMechanism, String logId, TestingRunConfig testingRunConfig, List<CustomAuthType> customAuthTypes) {
        super(apiInfoKey, filterNode, validatorNode, executorNode ,rawApi, varMap, auth, authMechanism, logId, testingRunConfig);
        this.customAuthTypes = customAuthTypes;
    }

    @Override
    public boolean filter() {
        // loggerMaker.infoAndAddToDb("filter started" + logId, LogDb.TESTING);
        List<String> authHeaders = AuthValidator.getHeaders(this.auth, this.authMechanism);
        // loggerMaker.infoAndAddToDb("found authHeaders " + authHeaders + " " + logId, LogDb.TESTING);
        if (authHeaders != null && authHeaders.size() > 0) {
            this.varMap.put("auth_headers", authHeaders);
        }
        if (this.auth != null && this.auth.getAuthenticated() != null) {
            // loggerMaker.infoAndAddToDb("validating auth, authenticated value is " + this.auth.getAuthenticated() + " " + logId, LogDb.TESTING);
            boolean validAuthHeaders = AuthValidator.validate(this.auth, this.rawApi, this.authMechanism);
            if (!validAuthHeaders) {
                // loggerMaker.infoAndAddToDb("invalid auth, skipping filter " + logId, LogDb.TESTING);
                return false;
            }
        }
        boolean isValid = TestPlugin.validateFilter(this.getFilterNode(),this.getRawApi(), this.getApiInfoKey(), this.varMap, this.logId);
        // loggerMaker.infoAndAddToDb("filter status " + isValid + " " + logId, LogDb.TESTING);
        return isValid;
    }

    @Override
    public boolean checkAuthBeforeExecution() {
        if (this.auth != null && this.auth.getAuthenticated() != null && this.auth.getAuthenticated() == true) {
            // loggerMaker.infoAndAddToDb("running noAuth check " + logId, LogDb.TESTING);
            ExecutionResult res = AuthValidator.checkAuth(this.auth, this.rawApi.copy(), this.testingRunConfig, this.customAuthTypes);
            if(res.getSuccess()) {
                OriginalHttpResponse resp = res.getResponse();
                int statusCode = StatusCodeAnalyser.getStatusCode(resp.getBody(), resp.getStatusCode());
                if (statusCode >= 200 && statusCode < 300) {
                    // loggerMaker.infoAndAddToDb("noAuth check failed, skipping execution " + logId, LogDb.TESTING);
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public List<TestResult>  executor() {
        // loggerMaker.infoAndAddToDb("executor started" + logId, LogDb.TESTING);
        List<TestResult> results = new Executor().execute(this.executorNode, this.rawApi, this.varMap, this.logId,
                this.authMechanism, this.validatorNode, this.apiInfoKey, this.testingRunConfig, this.customAuthTypes);
        // loggerMaker.infoAndAddToDb("execution result size " + results.size() +  " " + logId, LogDb.TESTING);
        return results;
    }

}
