package com.akto.utils.cloud;

import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
import com.amazonaws.services.cloudformation.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Utils {

    private static final Logger logger = LoggerFactory.getLogger(Utils.class);
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

}
