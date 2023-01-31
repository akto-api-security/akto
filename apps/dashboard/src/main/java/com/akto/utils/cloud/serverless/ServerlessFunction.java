package com.akto.utils.cloud.serverless;

public interface ServerlessFunction {

    public void invokeFunction(String functionName) throws Exception;

    public void updateFunctionConfiguration(String functionName, UpdateFunctionRequest updateFunctionRequest)
            throws Exception;

}
