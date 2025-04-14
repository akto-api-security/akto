package com.akto.utils.cloud;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
import com.amazonaws.services.cloudformation.model.*;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksResult;
import com.amazonaws.services.cloudformation.model.Tag;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.RebootInstancesRequest;
import com.amazonaws.services.ec2.model.RebootInstancesResult;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Utils {

    private static final LoggerMaker loggerMaker = new LoggerMaker(Utils.class, LogDb.DASHBOARD);
    public static CloudType getCloudType() {
        if (System.getenv("AWS_REGION") != null) {
            return CloudType.AWS;
        }
        return CloudType.GCP;
    }

    public static List<Tag> fetchTags(String stackName){
        Stack stack = fetchStack(stackName);
        return stack.getTags();
    }

    private static Stack fetchStack(String stackName) {
        DescribeStacksRequest describeStackRequest = new DescribeStacksRequest();
        describeStackRequest.setStackName(stackName);
        AmazonCloudFormation cloudFormation = AmazonCloudFormationClientBuilder.standard()
                .build();
        DescribeStacksResult result = cloudFormation.describeStacks(describeStackRequest);
        Stack stack = result.getStacks().get(0);
        return stack;
    }

    public static Map<String, String> fetchOutputs(String stackName){
        Stack stack = fetchStack(stackName);
        return stack.getOutputs().stream().collect(Collectors.toMap(Output::getOutputKey, Output::getOutputValue));
    }

    public static void rebootInstance(String instanceId){
        final AmazonEC2 ec2 = AmazonEC2ClientBuilder.defaultClient();
        loggerMaker.debugAndAddToDb("Initiating request to reboot instance", LogDb.TESTING);
        RebootInstancesRequest request = new RebootInstancesRequest()
                .withInstanceIds(instanceId);
        String requestString = request.toString();
        loggerMaker.debugAndAddToDb("Request for rebooting instance fired: " + ( requestString!=null ? requestString : "no request" ), LogDb.TESTING);
        RebootInstancesResult response = ec2.rebootInstances(request);
        String responseString = response.toString();
        loggerMaker.debugAndAddToDb("Response for instance reboot: " + ( responseString!=null ? responseString : "no response" ), LogDb.TESTING);
    }
}
