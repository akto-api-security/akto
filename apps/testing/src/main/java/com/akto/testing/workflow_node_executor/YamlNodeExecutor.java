package com.akto.testing.workflow_node_executor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.CustomAuthType;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.api_workflow.Node;
import com.akto.dto.test_editor.ExecutionResult;
import com.akto.dto.test_editor.ExecutorNode;
import com.akto.dto.test_editor.ExecutorSingleRequest;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.GenericTestResult;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.WorkflowTestResult;
import com.akto.dto.testing.NodeDetails.YamlNodeDetails;
import com.akto.dto.testing.WorkflowTestResult.NodeResult;
import com.akto.store.SampleMessageStore;
import com.akto.store.TestingUtil;
import com.akto.test_editor.execution.Executor;
import com.akto.testing.ApiExecutor;
import com.akto.testing.TestExecutor;
import com.akto.utils.RedactSampleData;
import com.google.gson.Gson;

public class YamlNodeExecutor extends NodeExecutor {
    
    private static final Gson gson = new Gson();

    public NodeResult processNode(Node node, Map<String, Object> varMap, Boolean allowAllStatusCodes) {
        List<String> testErrors = new ArrayList<>();

        YamlNodeDetails yamlNodeDetails = (YamlNodeDetails) node.getWorkflowNodeDetails().getNodeDetails();

        if (yamlNodeDetails.getTestId() != null && yamlNodeDetails.getTestId().length() > 0) {
            return processYamlNode(node, varMap, allowAllStatusCodes, yamlNodeDetails);
        }
        
        RawApi rawApi = yamlNodeDetails.getRawApi();
        RawApi sampleRawApi = rawApi.copy();
        List<RawApi> rawApis = new ArrayList<>();
        rawApis.add(rawApi);

        Executor executor = new Executor();
        ExecutorNode executorNode = yamlNodeDetails.getExecutorNode();
        AuthMechanism authMechanism = yamlNodeDetails.getAuthMechanism();
        List<CustomAuthType> customAuthTypes = yamlNodeDetails.getCustomAuthTypes();
        ExecutorSingleRequest singleReq = executor.buildTestRequest(executorNode, null, rawApis, varMap, authMechanism, customAuthTypes);
        List<RawApi> testRawApis = singleReq.getRawApis();
        TestingRunConfig testingRunConfig = new TestingRunConfig();
        String logId = "";
        List<TestResult> result = new ArrayList<>();
        boolean vulnerable = true;

        OriginalHttpResponse testResponse;
        List<String> message = new ArrayList<>();
        //String message = null;

        for (RawApi testReq: testRawApis) {
            try {
                testResponse = ApiExecutor.sendRequest(testReq.getRequest(), singleReq.getFollowRedirect(), testingRunConfig);                
                ExecutionResult attempt = new ExecutionResult(singleReq.getSuccess(), singleReq.getErrMsg(), testReq.getRequest(), testResponse);
                TestResult res = executor.validate(attempt, sampleRawApi, varMap, logId, yamlNodeDetails.getValidatorNode(), yamlNodeDetails.getApiInfoKey());
                if (res != null) {
                    result.add(res);
                }
                vulnerable = res.getVulnerable();
                if (vulnerable) {
                    try {
                        message.add(RedactSampleData.convertOriginalReqRespToString(testReq.getRequest(), testResponse));
                    } catch (Exception e) {
                        ;
                    }
                }
            } catch (Exception e) {
                // TODO: handle exception
            }
            if (vulnerable) {
                // fill varMap with entries
            }
        }

        // call executor's build request, which returns all rawapi by taking a rawapi argument
        // valuemap -> use varmap

        // loop on all rawapis and hit requests
        // call validate node present in node
        // if vulnerable, populate map

        // 

        return new WorkflowTestResult.NodeResult(message.toString(), vulnerable, testErrors);

    }


    public WorkflowTestResult.NodeResult processYamlNode(Node node, Map<String, Object> valuesMap, Boolean allowAllStatusCodes, YamlNodeDetails yamlNodeDetails) {

        String testSubCategory = yamlNodeDetails.getTestId();
        Map<String, TestConfig> testConfigMap = YamlTemplateDao.instance.fetchTestConfigMap(false, false);
        TestConfig testConfig = testConfigMap.get(testSubCategory); 
        RawApi rawApi = yamlNodeDetails.getRawApi();

        Map<String, Object> m = new HashMap<>();
        for (String k: rawApi.getRequest().getHeaders().keySet()) {
            List<String> v = rawApi.getRequest().getHeaders().getOrDefault(k, new ArrayList<>());
            if (v.size() > 0) {
                m.put(k, v.get(0).toString());
            }
        }

        Map<String, Object> m2 = new HashMap<>();
        for (String k: rawApi.getResponse().getHeaders().keySet()) {
            List<String> v = rawApi.getRequest().getHeaders().getOrDefault(k, new ArrayList<>());
            if (v.size() > 0) {
                m2.put(k, v.get(0).toString());
            }
        }

        JSONObject json = new JSONObject() ;
        json.put("method", rawApi.getRequest().getMethod());
        json.put("requestPayload", rawApi.getRequest().getBody());
        json.put("path", rawApi.getRequest().getUrl());
        json.put("requestHeaders", gson.toJson(m));
        json.put("type", "");
        json.put("responsePayload", rawApi.getResponse().getBody());
        json.put("responseHeaders", gson.toJson(m2));
        json.put("statusCode", Integer.toString(rawApi.getResponse().getStatusCode()));
        
        AuthMechanism authMechanism = yamlNodeDetails.getAuthMechanism();
        Map<ApiInfo.ApiInfoKey, List<String>> sampleDataMap = new HashMap<>();
        sampleDataMap.put(yamlNodeDetails.getApiInfoKey(), Collections.singletonList(json.toString()));
        SampleMessageStore messageStore = SampleMessageStore.create(sampleDataMap);
        List<CustomAuthType> customAuthTypes = yamlNodeDetails.getCustomAuthTypes();
        TestingUtil testingUtil = new TestingUtil(authMechanism, messageStore, null, null, customAuthTypes);
        TestExecutor executor = new TestExecutor();
        TestingRunResult testingRunResult = executor.runTestNew(yamlNodeDetails.getApiInfoKey(), null, testingUtil, null, testConfig, null);

        List<String> errors = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        if (testingRunResult.isVulnerable()) {
            List<GenericTestResult> testResults = testingRunResult.getTestResults();
            for (GenericTestResult testResult: testResults) {
                TestResult t = (TestResult) testResult;
                messages.add(t.getMessage());
            }
        }

        return new WorkflowTestResult.NodeResult(messages.toString(), testingRunResult.isVulnerable(), errors);
    }
    
}
