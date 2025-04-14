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

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.utils.cloud.stack.dto.StackState;
import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClientBuilder;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationAsync;
import com.amazonaws.services.cloudformation.AmazonCloudFormationAsyncClientBuilder;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
import com.amazonaws.services.cloudformation.model.*;
import org.apache.commons.lang3.StringUtils;

public class AwsStack implements com.akto.utils.cloud.stack.Stack {

    private static final LoggerMaker logger = new LoggerMaker(AwsStack.class, LogDb.DASHBOARD);
    private static final Set<String> ACCEPTABLE_STACK_STATUSES = new HashSet<String>(
            Arrays.asList(StackStatus.CREATE_IN_PROGRESS.toString(), StackStatus.CREATE_COMPLETE.toString()));
    private static final int STACK_CREATION_TIMEOUT_MINS = 20;
    private static final List<String> STACK_CREATION_CAPABILITIES = Arrays.asList("CAPABILITY_IAM");
    private static final AmazonCloudFormationAsync CLOUD_FORMATION_ASYNC = AmazonCloudFormationAsyncClientBuilder
            .standard().build();
    private static final AmazonCloudFormation CLOUD_FORMATION_SYNC = AmazonCloudFormationClientBuilder.standard()
            .build();

    private static final AmazonAutoScaling asc = AmazonAutoScalingClientBuilder.standard().build();


    private AwsStack(){
    }

    public static AwsStack instance = null;

    public static AwsStack getInstance() {
        if(instance == null){
            instance = new AwsStack();
        }
        return instance;
    }

    @Override
    public String createStack(String stackName, Map<String, String> parameters, String template, List<Tag> tags) throws Exception {
        try {
            CreateStackRequest createRequest = new CreateStackRequest();
            createRequest.setStackName(stackName);
            createRequest.setTimeoutInMinutes(STACK_CREATION_TIMEOUT_MINS);
            createRequest.setParameters(fetchParamters(parameters));
            createRequest.setCapabilities(STACK_CREATION_CAPABILITIES);
            createRequest.setTemplateBody(template);
            if(tags != null && !tags.isEmpty()) {
                createRequest.setTags(tags);
            }
            Future<CreateStackResult> future = CLOUD_FORMATION_ASYNC.createStackAsync(createRequest);
            CreateStackResult createStackResult = future.get();
            logger.debugAndAddToDb("Stack Id: " + createStackResult.getStackId(), LogDb.DASHBOARD);
            return createStackResult.getStackId();
        } catch (Exception e) {
            logger.errorAndAddToDb(e, e.toString(), LogDb.DASHBOARD);
            throw e;
        }
    }

    public AmazonAutoScaling getAsc() {
        return asc;
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
                logger.debugAndAddToDb("Actual stack status: " + stackStatus, LogDb.DASHBOARD);
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
            logger.debug(String.valueOf(resources));
            return resources.get(0).getPhysicalResourceId();
        } catch (Exception e) {
            logger.errorAndAddToDb(e, String.format("Failed to fetch physical id of resource with logical id %s : %s", logicalId, e.toString()), LogDb.DASHBOARD);
            return "";
        }
    }

    @Override
    public boolean checkIfStackExists(String stackName) {
        String stackStatus = fetchStackStatus(stackName).getStatus();
        return stackStatus.equals("CREATE_COMPLETE");
    }
}
