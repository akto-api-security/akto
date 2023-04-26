package com.akto.testing.yaml_tests;

import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.Auth;
import com.akto.dto.test_editor.ExecutionResult;
import com.akto.dto.test_editor.ExecutorNode;
import com.akto.dto.test_editor.FilterNode;
import com.akto.dto.testing.TestResult;
import com.akto.rules.TestPlugin;
import com.akto.test_editor.auth.AuthValidator;
import com.akto.test_editor.execution.Executor;
import com.akto.testing.StatusCodeAnalyser;
import com.akto.utils.RedactSampleData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class YamlTestTemplate extends SecurityTestTemplate {

    public YamlTestTemplate(ApiInfo.ApiInfoKey apiInfoKey, FilterNode filterNode, FilterNode validatorNode, ExecutorNode executorNode, RawApi rawApi, Map<String, Object> varMap) {
        super(apiInfoKey, filterNode, validatorNode, executorNode ,rawApi, varMap);
    }

    @Override
    public boolean filter(Auth auth) {
        boolean validAuthHeaders = AuthValidator.validate(auth, rawApi);
        if (!validAuthHeaders) {
            return false;
        }
        return TestPlugin.validateFilter(this.getFilterNode(),this.getRawApi(), this.getApiInfoKey(), this.varMap);
    }

    @Override
    public List<ExecutionResult>  executor(Auth auth) {
        ExecutionResult res = AuthValidator.checkAuth(auth, rawApi);
        if(res.getSuccess()) {
            OriginalHttpResponse resp = res.getResponse();
            int statusCode = StatusCodeAnalyser.getStatusCode(resp.getBody(), resp.getStatusCode());
            if (statusCode >= 200 && statusCode < 300) {
                return null;
            }
        }
        return new Executor().execute(this.executorNode, this.rawApi, this.varMap);
    }

    @Override
    public List<TestResult> validator(List<ExecutionResult> attempts) {
        List<TestResult> testResults = new ArrayList<>();
        for (ExecutionResult attempt: attempts) {
            String msg = RedactSampleData.convertOriginalReqRespToString(attempt.getRequest(), attempt.getResponse());
            RawApi testRawApi = new RawApi(attempt.getRequest(), attempt.getResponse(), msg);
            boolean vulnerable = TestPlugin.validateValidator(this.getValidatorNode(), this.getRawApi(), testRawApi , this.getApiInfoKey(), this.varMap);
            double percentageMatch = TestPlugin.compareWithOriginalResponse(
                    this.rawApi.getOriginalMessage(), testRawApi.getOriginalMessage(), new HashMap<>()
            );

            // todo: fix errors
            TestResult testResult = new TestResult(
                    msg, this.getRawApi().getOriginalMessage(), new ArrayList<>(), percentageMatch, vulnerable, TestResult.Confidence.HIGH, null
            );

            testResults.add(testResult);
        }

        return testResults;
    }
}
