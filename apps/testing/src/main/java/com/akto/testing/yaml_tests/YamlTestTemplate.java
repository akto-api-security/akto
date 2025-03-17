package com.akto.testing.yaml_tests;

import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dto.ApiInfo;
import com.akto.dto.CustomAuthType;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.*;
import com.akto.dto.testing.*;
import com.akto.log.LoggerMaker;
import com.akto.rules.RequiredConfigs;
import com.akto.rules.TestPlugin;
import com.akto.test_editor.Utils;
import com.akto.test_editor.auth.AuthValidator;
import com.akto.test_editor.execution.Executor;
import com.akto.test_editor.filter.data_operands_impl.ValidationResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class YamlTestTemplate extends SecurityTestTemplate {

    private static final LoggerMaker loggerMaker = new LoggerMaker(YamlTestTemplate.class);
    private final List<CustomAuthType> customAuthTypes;
    public YamlTestTemplate(ApiInfo.ApiInfoKey apiInfoKey, FilterNode filterNode, FilterNode validatorNode,
                            ExecutorNode executorNode, RawApi rawApi, Map<String, Object> varMap, Auth auth,
                            String logId, TestingRunConfig testingRunConfig,
                            List<CustomAuthType> customAuthTypes, Strategy strategy) {
        super(apiInfoKey, filterNode, validatorNode, executorNode ,rawApi, varMap, auth, logId, testingRunConfig, strategy);
        this.customAuthTypes = customAuthTypes;
    }

    @Override
    public Set<String> requireConfig(){
        Set<String> requiredConfigsList = new HashSet<>();
        Map<String,Boolean> currentRolesMap = RequiredConfigs.getCurrentConfigsMap();

        if (!currentRolesMap.containsKey("ATTACKER_TOKEN_ALL")) {
            requiredConfigsList.add("ATTACKER_TOKEN_ALL");
        }

        // traverse in filternodes.getValues(), looks for valid key, if key valid, check for that role
        List<FilterNode> childNodes = filterNode.getChildNodes();
        if(childNodes != null && !childNodes.isEmpty()){
            for(FilterNode node: childNodes){
                if(Utils.commandRequiresConfig(node.getOperand().toLowerCase())){
                   if(!node.getChildNodes().isEmpty()){
                        List<String> roles = (List<String>) node.getChildNodes().get(0).getValues();
                        String role = roles.get(0);
                        if(!currentRolesMap.containsKey(role)){
                            requiredConfigsList.add(role.toUpperCase());
                        }
                   }
                }
            }
        }

        List<ExecutorNode> childList = executorNode.getChildNodes();
        if(childList != null && !childList.isEmpty() && childList.size() >= 2){
            ExecutorNode reqNodes = childList.get(1);
            if (reqNodes.getChildNodes() == null || reqNodes.getChildNodes().size() == 0) {
                return requiredConfigsList;
            }

            ExecutorNode reqNode = reqNodes.getChildNodes().get(0);
            for(ExecutorNode node: reqNode.getChildNodes()){
                if(node.getOperationType().equalsIgnoreCase(TestEditorEnums.NonTerminalExecutorDataOperands.MODIFY_HEADER.toString())){
                    for(ExecutorNode child: node.getChildNodes()){
                        if(Utils.commandRequiresConfig(child.getOperationType().toString())){
                            String ACCESS_ROLES_CONTEXT = "${roles_access_context.";
                            String keyStr = child.getOperationType().toString();
                            keyStr = keyStr.replace(ACCESS_ROLES_CONTEXT, "");
                            String roleName = keyStr.substring(0,keyStr.length()-1).trim();

                            if(!currentRolesMap.containsKey(roleName)){
                                requiredConfigsList.add(roleName.toUpperCase());
                            }
                        }
                    }
                }
            }
        }

        return requiredConfigsList;
    }

    @Override
    public ValidationResult filter() {
        // loggerMaker.infoAndAddToDb("filter started" + logId, LogDb.TESTING);
        List<String> authHeaders = AuthValidator.getHeaders(this.auth, this.customAuthTypes);
        // loggerMaker.infoAndAddToDb("found authHeaders " + authHeaders + " " + logId, LogDb.TESTING);
        if (authHeaders != null && authHeaders.size() > 0) {
            this.varMap.put("auth_headers", authHeaders);
        }
        if (this.auth != null && this.auth.getAuthenticated() != null) {
            // loggerMaker.infoAndAddToDb("validating auth, authenticated value is " + this.auth.getAuthenticated() + " " + logId, LogDb.TESTING);
            boolean validAuthHeaders = AuthValidator.validate(this.auth, this.rawApi, this.customAuthTypes);
            if (!validAuthHeaders) {
                ValidationResult validationResult = new ValidationResult(false, "No valid auth headers");
                // loggerMaker.infoAndAddToDb("invalid auth, skipping filter " + logId, LogDb.TESTING);
                return validationResult;
            }
        }
        ValidationResult isValid = TestPlugin.validateFilter(this.getFilterNode(),this.getRawApi(), this.getApiInfoKey(), this.varMap, this.logId);
        // loggerMaker.infoAndAddToDb("filter status " + isValid + " " + logId, LogDb.TESTING);
        return isValid;
    }


    @Override
    public boolean checkAuthBeforeExecution(boolean debug, List<TestingRunResult.TestLog> testLogs) {
        if (this.auth != null && this.auth.getAuthenticated() != null && this.auth.getAuthenticated() == true) {
            // loggerMaker.infoAndAddToDb("running noAuth check " + logId, LogDb.TESTING);
            ExecutionResult res = AuthValidator.checkAuth(this.auth, this.rawApi.copy(), this.testingRunConfig, this.customAuthTypes, debug, testLogs);
            if(res.getSuccess()) {
                OriginalHttpResponse resp = res.getResponse();
                int statusCode = resp.getStatusCode();
                if (statusCode >= 200 && statusCode < 300) {
                    // loggerMaker.infoAndAddToDb("noAuth check failed, skipping execution " + logId, LogDb.TESTING);
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
                this.validatorNode, this.apiInfoKey, this.testingRunConfig, this.customAuthTypes,
                debug, testLogs, memory);
        // loggerMaker.infoAndAddToDb("execution result size " + results.size() +  " " + logId, LogDb.TESTING);
        return results;
    }

    @Override
    public void triggerMetaInstructions(Strategy strategy, YamlTestResult attempts) {
        com.akto.test_editor.strategy.Strategy.triggerStrategyInstructions(strategy, attempts);
    }

}
