package com.akto.utils.cloud.stack;

import java.util.Map;

public interface Stack {
    public String createStack(Map<String, String> parameters) throws Exception;

    public String fetchStackStatus();

    public boolean checkIfStackExists();

    public enum StackStatus {
        DOES_NOT_EXISTS, CREATE_IN_PROGRESS, FAILED_TO_INITIALIZE, CREATION_FAILED, CREATE_COMPLETE,
        FAILED_TO_FETCH_STACK_STATUS;
    }

    public Map<String, String> fetchAktoStackParameters() throws Exception;

    public String fetchResourcePhysicalIdByLogicalId(String logicalId) throws Exception;
}
