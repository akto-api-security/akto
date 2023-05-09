package com.akto.utils.cloud.stack;

import com.akto.utils.cloud.stack.dto.StackState;
import com.amazonaws.services.cloudformation.model.*;

import java.util.List;
import java.util.Map;

public interface Stack {
    public String createStack(String stackName, Map<String, String> parameters, String template, List<Tag> tags) throws Exception;

    public StackState fetchStackStatus(String stackName);

    public boolean checkIfStackExists(String stackName);

    public enum StackStatus {
        DOES_NOT_EXISTS, CREATE_IN_PROGRESS, FAILED_TO_INITIALIZE, CREATION_FAILED, CREATE_COMPLETE,
        FAILED_TO_FETCH_STACK_STATUS, TEMP_DISABLE;
    }

    public String fetchResourcePhysicalIdByLogicalId(String stackName, String logicalId);
}
