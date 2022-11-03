package com.akto.utils.cloud.serverless.aws;

import java.util.Map;

import com.akto.utils.cloud.serverless.ServerlessFunction;
import com.akto.utils.cloud.serverless.UpdateFunctionRequest;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.lambda.model.Environment;
import com.amazonaws.services.lambda.model.EnvironmentResponse;
import com.amazonaws.services.lambda.model.FunctionConfiguration;
import com.amazonaws.services.lambda.model.GetFunctionRequest;
import com.amazonaws.services.lambda.model.GetFunctionResult;
import com.amazonaws.services.lambda.model.UpdateFunctionConfigurationRequest;

public class Lambda implements ServerlessFunction {

    private static final AWSLambda awsLambda = AWSLambdaClientBuilder.standard().build();

    @Override
    public void updateFunctionConfiguration(String functionName, UpdateFunctionRequest updateFunctionRequest)
            throws Exception {

        if (functionName == null || functionName.length() == 0 || updateFunctionRequest == null
                || updateFunctionRequest.getEnvironmentVariables() == null) {
            throw new Exception("Invalid updateFunctionConfiguration Request");
        }

        try {
            GetFunctionResult function = getFunction(functionName);
            FunctionConfiguration existingFunctionConfiguration = function.getConfiguration();
            EnvironmentResponse existingEnvironmentVariables = existingFunctionConfiguration.getEnvironment();
            Map<String, String> existingEnvironmentVariablesMap = existingEnvironmentVariables.getVariables();

            // Update the existingEnvVariablesMap with the new env vars and then set this
            // updated map in UpdateFunctionConfigurationRequest
            int keysUpdatedCount = 0;
            for (Map.Entry<String, String> entry : updateFunctionRequest.getEnvironmentVariables().entrySet()) {
                if (existingEnvironmentVariablesMap.containsKey(entry.getKey())) {
                    existingEnvironmentVariablesMap.put(entry.getKey(), entry.getValue());
                    keysUpdatedCount++;
                }
            }

            if (keysUpdatedCount == 0) {
                // no env vars to update, returning
                System.out.println("No env vars to update for funciton: " + functionName + ", returning");
                return;
            }

            UpdateFunctionConfigurationRequest req = new UpdateFunctionConfigurationRequest();
            req.setFunctionName(functionName);
            Environment updatedEnvironment = new Environment();
            updatedEnvironment.setVariables(existingEnvironmentVariablesMap);
            req.setEnvironment(updatedEnvironment);

            awsLambda.updateFunctionConfiguration(req);
            System.out.println("Succeefully updated function configuration for function: " + functionName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private GetFunctionResult getFunction(String functionName) throws Exception {
        GetFunctionRequest getFunctionRequest = new GetFunctionRequest();
        getFunctionRequest.setFunctionName(functionName);
        GetFunctionResult getFunctionResult = awsLambda.getFunction(getFunctionRequest);
        return getFunctionResult;
    }

    @Override
    public void invokeFunction(String functionName) throws Exception {
        // TODO Auto-generated method stub

    }

}
