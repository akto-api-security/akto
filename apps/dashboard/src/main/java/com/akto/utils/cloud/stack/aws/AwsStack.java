package com.akto.utils.cloud.stack.aws;

import java.util.Set;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import com.akto.utils.cloud.stack.dto.StackState;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationAsync;
import com.amazonaws.services.cloudformation.AmazonCloudFormationAsyncClientBuilder;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
import com.amazonaws.services.cloudformation.model.CreateStackRequest;
import com.amazonaws.services.cloudformation.model.CreateStackResult;
import com.amazonaws.services.cloudformation.model.DescribeStackResourcesRequest;
import com.amazonaws.services.cloudformation.model.DescribeStackResourcesResult;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksResult;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackResource;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AwsStack implements com.akto.utils.cloud.stack.Stack {

    private static final Logger logger = LoggerFactory.getLogger(AwsStack.class);
    private static final Set<String> ACCEPTABLE_STACK_STATUSES = new HashSet<String>(
            Arrays.asList(StackStatus.CREATE_IN_PROGRESS.toString(), StackStatus.CREATE_COMPLETE.toString()));
    private static final int STACK_CREATION_TIMEOUT_MINS = 15;
    private static final List<String> STACK_CREATION_CAPABILITIES = Arrays.asList("CAPABILITY_IAM");
    private static final AmazonCloudFormationAsync CLOUD_FORMATION_ASYNC = AmazonCloudFormationAsyncClientBuilder
            .standard().build();
    private static final AmazonCloudFormation CLOUD_FORMATION_SYNC = AmazonCloudFormationClientBuilder.standard()
            .build();

    @Override
    public String createStack(String stackName, Map<String, String> parameters) throws Exception {
        try {
            CreateStackRequest createRequest = new CreateStackRequest();
            createRequest.setStackName(stackName);
            createRequest.setTimeoutInMinutes(STACK_CREATION_TIMEOUT_MINS);
            createRequest.setParameters(fetchParamters(parameters));
            createRequest.setCapabilities(STACK_CREATION_CAPABILITIES);
            createRequest.setTemplateBody(
                    convertStreamToString(AwsStack.class
                            .getResourceAsStream("/cloud_formation_templates/akto_aws_mirroring.template")));
            Future<CreateStackResult> future = CLOUD_FORMATION_ASYNC.createStackAsync(createRequest);
            CreateStackResult createStackResult = future.get();
            System.out.println("Stack Id: " + createStackResult.getStackId());
            return createStackResult.getStackId();
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    private Collection<Parameter> fetchParamters(Map<String, String> parametersMap) {
        List<Parameter> parameters = new ArrayList<>();
        for (Map.Entry<String, String> entry : parametersMap.entrySet()) {
            Parameter parameter = new Parameter();
            parameter.setParameterKey(entry.getKey());
            parameter.setParameterValue(entry.getValue());
            parameters.add(parameter);
        }
        return parameters;
    }

    @Override
    public StackState fetchStackStatus(String stackName) {
        DescribeStacksRequest describeStackRequest = new DescribeStacksRequest();
        describeStackRequest.setStackName(stackName);
        try {
            DescribeStacksResult result = CLOUD_FORMATION_SYNC.describeStacks(describeStackRequest);
            Stack stack = result.getStacks().get(0);

            String stackStatus = stack.getStackStatus();

            if (!ACCEPTABLE_STACK_STATUSES.contains(stackStatus)) {
                System.out.println("Actual stack status: " + stackStatus);
                return new StackState(StackStatus.CREATION_FAILED.toString(), 0);
            }
            return new StackState(stackStatus, stack.getCreationTime().getTime());
        } catch (Exception e) {
            if (e.getMessage().contains("does not exist")) {
                return new StackState(StackStatus.DOES_NOT_EXISTS.toString(), 0);
            }
            e.printStackTrace();
            return new StackState(StackStatus.FAILED_TO_FETCH_STACK_STATUS.toString(), 0); // TODO: what should we
                                                                                           // return when we fail to
            // fetch
            // stack's status.
        }
    }

    public String fetchResourcePhysicalIdByLogicalId(String stackName, String logicalId){
        if(StringUtils.isEmpty(stackName) || StringUtils.isEmpty(logicalId)){
            return "";
        }
        DescribeStackResourcesRequest req = new DescribeStackResourcesRequest();
        req.setStackName(stackName);
        req.setLogicalResourceId(logicalId);
        try {
            DescribeStackResourcesResult res = CLOUD_FORMATION_SYNC.describeStackResources(req);
            List<StackResource> resources = res.getStackResources();
            System.out.println(resources);
            return resources.get(0).getPhysicalResourceId();
        } catch (Exception e) {
            logger.error("Failed to fetch physical id of resource with logical id {}", logicalId, e);
            return "";
        }
    }

    // Convert a stream into a single, newline separated string
    private static String convertStreamToString(InputStream in) throws Exception {

        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder stringbuilder = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            stringbuilder.append(line + "\n");
        }
        in.close();
        return stringbuilder.toString();
    }

    @Override
    public boolean checkIfStackExists(String stackName) {
        String stackStatus = fetchStackStatus(stackName).getStatus();
        return stackStatus.equals("CREATE_COMPLETE");
    }
}
