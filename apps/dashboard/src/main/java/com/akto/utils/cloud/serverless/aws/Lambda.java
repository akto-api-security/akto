package com.akto.utils.cloud.serverless.aws;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.utils.cloud.serverless.ServerlessFunction;
import com.akto.utils.cloud.serverless.UpdateFunctionRequest;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.lambda.model.AWSLambdaException;
import com.amazonaws.services.lambda.model.Environment;
import com.amazonaws.services.lambda.model.EnvironmentResponse;
import com.amazonaws.services.lambda.model.FunctionConfiguration;
import com.amazonaws.services.lambda.model.GetFunctionRequest;
import com.amazonaws.services.lambda.model.GetFunctionResult;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.lambda.model.UpdateFunctionConfigurationRequest;

public class Lambda implements ServerlessFunction {

    private static Lambda instance = null;
    
    public static final Lambda getInstance(){
        if(instance == null){
            instance = new Lambda();
        }
        return instance;
    }

    private Lambda(){

    }

    private static final Logger logger = LoggerFactory.getLogger(ServerlessFunction.class);

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

        InvokeRequest invokeRequest = new InvokeRequest()
            .withFunctionName(functionName)
            .withPayload("{}");
        InvokeResult invokeResult = null;
        try {

            System.out.println("Invoke lambda "+functionName);
            invokeResult = awsLambda.invoke(invokeRequest);

            String resp = new String(invokeResult.getPayload().array(), StandardCharsets.UTF_8);
            logger.info("Function: {}, response: {}", functionName, resp);
        } catch (AWSLambdaException e) {
            logger.error(String.format("Error while invoking Lambda: %s", functionName), e);
        }


    }

}
