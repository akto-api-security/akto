package com.akto.utils.cloud;

import com.akto.utils.platform.DashboardStackDetails;
import com.akto.utils.platform.MirroringStackDetails;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksResult;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class Utils {

    private static final Logger logger = LoggerFactory.getLogger(Utils.class);
    public static CloudType getCloudType() {
        if (System.getenv("AWS_REGION") != null) {
            return CloudType.AWS;
        }
        return CloudType.GCP;
    }

    public static String addTagsToTemplate(String template){
        DescribeStacksRequest describeStackRequest = new DescribeStacksRequest();
        describeStackRequest.setStackName(DashboardStackDetails.getStackName());
        AmazonCloudFormation cloudFormation = AmazonCloudFormationClientBuilder.standard()
                .build();
        DescribeStacksResult result = cloudFormation.describeStacks(describeStackRequest);
        com.amazonaws.services.cloudformation.model.Stack stack = result.getStacks().get(0);
        List<Parameter> parameters = stack.getParameters();

        ObjectMapper mapper = new ObjectMapper();
        List<ObjectNode> autoScalingGroupTags = new ArrayList<>();
        List<ObjectNode> tags = new ArrayList<>();
        for(Parameter parameter: parameters){
            if(parameter.getParameterKey().equalsIgnoreCase("tags")){
                String tagStr = parameter.getParameterValue();
                String[] splitTags = tagStr.split(",");
                for(String tagSplit: splitTags){
                    String[] split = tagSplit.split("=");
                    ObjectNode node = mapper.createObjectNode();
                    node.put("Key", split[0]);
                    node.put("Value", split[1]);
                    tags.add(node);
                    ObjectNode asgNode = node.deepCopy();
                    asgNode.put("PropagateAtLaunch", true);
                    autoScalingGroupTags.add(asgNode);
                }
                break;
            }
        }
        if(autoScalingGroupTags.size() == 0){
            return template;
        }

        ArrayNode asgTagsArray = mapper.valueToTree(autoScalingGroupTags);
        ArrayNode tagsArray = mapper.valueToTree(tags);
        try {
            JsonNode jsonTemplate = mapper.readValue(template, JsonNode.class);
            JsonNode resources = jsonTemplate.get("Resources");
            setupTagsInASG(asgTagsArray, resources, MirroringStackDetails.AKTO_CONTEXT_ANALYZER_AUTO_SCALING_GROUP);
            setupTagsInASG(asgTagsArray, resources, MirroringStackDetails.AKTO_AUTO_SCALING_GROUP);

            setupTagsInASG(tagsArray, resources, MirroringStackDetails.TRAFFIC_MIRROR_TARGET);
            setupTagsInASG(tagsArray, resources, MirroringStackDetails.LB_TRAFFIC_MIRROR_FILTER);

            setupTagsInASG(tagsArray, resources, MirroringStackDetails.LAMBDA_LOG_GROUP);
            setupTagsInASG(tagsArray, resources, MirroringStackDetails.LAMBDA_BASIC_EXECUTION_ROLE);
            setupTagsInASG(tagsArray, resources, MirroringStackDetails.GET_AKTO_SETUP_DETAILS_LAMBDA_BASIC_EXECUTION_ROLE);
            setupTagsInASG(tagsArray, resources, MirroringStackDetails.GET_AKTO_SETUP_DETAILS_LAMBDA);
            setupTagsInASG(tagsArray, resources, MirroringStackDetails.CREATE_MIRROR_SESSION_LAMBDA);
            setupTagsInASG(tagsArray, resources, MirroringStackDetails.LAMBDA_VPC_ACCESS_ROLE);
            setupTagsInASG(tagsArray, resources, MirroringStackDetails.LAMBDA_SECURITY_GROUP_VPC);

            setupTagsInASG(tagsArray, resources, MirroringStackDetails.SAVE_COLLECTION_NAMES_LAMBDA);
            setupTagsInASG(tagsArray, resources, MirroringStackDetails.GET_VPC_DETAILS_LAMBDA_ROLE);
            setupTagsInASG(tagsArray, resources, MirroringStackDetails.GET_VPC_DETAILS_LAMBDA);

            setupTagsInASG(tagsArray, resources, MirroringStackDetails.AKTO_CONTEXT_ANALYZER_SECURITY_GROUP);
            setupTagsInASG(tagsArray, resources, MirroringStackDetails.AKTO_CONTEXT_ANALYZER_INSTANCE_REFRESH_HANDLER_LAMBDA);
            setupTagsInASG(tagsArray, resources, MirroringStackDetails.REFRESH_HANDLER_LAMBDA_BASIC_EXECUTION_ROLE);
            setupTagsInASG(tagsArray, resources, MirroringStackDetails.AKTO_SECURITY_GROUP);

            setupTagsInASG(tagsArray, resources, MirroringStackDetails.AKTO_NLB);
            setupTagsInASG(tagsArray, resources, MirroringStackDetails.AKTO_TRAFFIC_MIRRORING_TARGET_GROUP);
            setupTagsInASG(tagsArray, resources, MirroringStackDetails.AKTO_KAFKA_TARGET_GROUP);
            setupTagsInASG(tagsArray, resources, MirroringStackDetails.DASHBOARD_INSTANCE_REFRESH_HANDLER_LAMBDA);
            setupTagsInASG(tagsArray, resources, MirroringStackDetails.TRAFFIC_MIRRORING_INSTANCE_REFRESH_HANDLER_LAMBDA);
            setupTagsInASG(tagsArray, resources, MirroringStackDetails.INSTANCE_REFRESH_HANDLER_LAMBDA_ROLE);

            template = jsonTemplate.toString();
            logger.info("Updated template: {}", template);
        } catch (JsonProcessingException e) {
            logger.error("Failed to add tags", e);
        }
        return template;
    }

    private static void setupTagsInASG(ArrayNode tagsArray, JsonNode resources, String asgName) {
        JsonNode contextAnalyzerASG = resources.get(asgName);
        JsonNode properties = contextAnalyzerASG.get("Properties");
        ((ObjectNode)properties).put("Tags", tagsArray);
    }


}
