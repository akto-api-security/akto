package com.akto.action;

import com.akto.dao.AccountsDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.UserAccountEntry;
import com.akto.utils.cloud.serverless.aws.Lambda;
import com.akto.utils.cloud.stack.Stack;
import com.akto.utils.cloud.stack.aws.AwsStack;
import com.akto.utils.cloud.stack.dto.StackState;
import com.akto.utils.platform.DashboardStackDetails;
import com.akto.utils.platform.MirroringStackDetails;
import com.amazonaws.services.lambda.model.*;
import com.mongodb.BasicDBObject;
import com.opensymphony.xwork2.Action;
import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClientBuilder;
import com.amazonaws.services.autoscaling.model.RefreshPreferences;
import com.amazonaws.services.autoscaling.model.StartInstanceRefreshRequest;
import com.amazonaws.services.autoscaling.model.StartInstanceRefreshResult;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;

public class AccountAction extends UserAction {

    private String newAccountName;
    private int newAccountId;
    private static final Logger logger = LoggerFactory.getLogger(AccountAction.class);

    public static final int MAX_NUM_OF_LAMBDAS_TO_FETCH = 50;

    @Override
    public String execute() {

        return Action.SUCCESS.toUpperCase();
    }

    private void invokeExactLambda(String functionName, AWSLambda awsLambda) {

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

    private void listMatchingLambda(String functionName) {
        AWSLambda awsLambda = AWSLambdaClientBuilder.standard().build();
        try {
            ListFunctionsRequest request = new ListFunctionsRequest();
            request.setMaxItems(MAX_NUM_OF_LAMBDAS_TO_FETCH);

            boolean done = false;
            while(!done){
                ListFunctionsResult functionResult = awsLambda
                        .listFunctions(request);
                List<FunctionConfiguration> list = functionResult.getFunctions();
                logger.info("Found {} functions", list.size());

                for (FunctionConfiguration config: list) {
                    logger.info("Found function: {}",config.getFunctionName());

                    if(config.getFunctionName().contains(functionName)) {
                        logger.info("Invoking function: {}", config.getFunctionName());
                        invokeExactLambda(config.getFunctionName(), awsLambda);
                    }
                }

                if(functionResult.getNextMarker() == null){
                    done = true;
                }
                request = new ListFunctionsRequest();
                request.setMaxItems(MAX_NUM_OF_LAMBDAS_TO_FETCH);
                request.setMarker(functionResult.getNextMarker());
            }


        } catch (AWSLambdaException e) {
            logger.error("Error while updating Akto",e);
        }
    }

    public String takeUpdate() {
        if(checkIfStairwayInstallation()) {
            String autoScalingGroup;
            AmazonAutoScaling asc = AmazonAutoScalingClientBuilder.standard().build();
            RefreshPreferences refreshPreferences = new RefreshPreferences();
            refreshPreferences.setMinHealthyPercentage(0);
            refreshPreferences.setInstanceWarmup(200);
            StartInstanceRefreshRequest refreshRequest = new StartInstanceRefreshRequest();
            refreshRequest.setPreferences(refreshPreferences);
            StartInstanceRefreshResult result = new StartInstanceRefreshResult();
            try {
                autoScalingGroup = AwsStack.getInstance().fetchResourcePhysicalIdByLogicalId(MirroringStackDetails.getStackName(), MirroringStackDetails.AKTO_CONTEXT_ANALYSER_AUTO_SCALING_GROUP);
                refreshRequest.setAutoScalingGroupName(autoScalingGroup);
                result = asc.startInstanceRefresh(refreshRequest);
                logger.info("Instance refresh on context analyser called with result {}", result);
            } catch (Exception e){
                logger.error("Failed to update Akto Context Analyzer", e);
            }
            try {
                autoScalingGroup = AwsStack.getInstance().fetchResourcePhysicalIdByLogicalId(MirroringStackDetails.getStackName(), MirroringStackDetails.AKTO_TRAFFIC_MIRRORING_AUTO_SCALING_GROUP);
                refreshRequest.setAutoScalingGroupName(autoScalingGroup);
                asc.startInstanceRefresh(refreshRequest);
                logger.info("Instance refresh on traffic mirroring instance called with result {}", result);
            } catch (Exception e){
                logger.error("Failed to update Akto Traffic Mirroring Instance", e);
            }
            try {
                autoScalingGroup = AwsStack.getInstance().fetchResourcePhysicalIdByLogicalId(DashboardStackDetails.getStackName(), DashboardStackDetails.AKTO_DASHBOARD_AUTO_SCALING_GROUP);
                refreshRequest.setAutoScalingGroupName(autoScalingGroup);
                asc.startInstanceRefresh(refreshRequest);
                logger.info("Instance refresh on dashboard called with result {}", result);
            } catch (Exception e){
                logger.error("Failed to update Akto Dashboard", e);
            }
        } else {
            logger.info("This is an old installation, updating via old way");
            listMatchingLambda("InstanceRefresh");
        }
        
        return Action.SUCCESS.toUpperCase();
    }

    private boolean checkIfStairwayInstallation() {
        StackState stackStatus = AwsStack.getInstance().fetchStackStatus(MirroringStackDetails.getStackName());
        return "CREATE_COMPLETE".equalsIgnoreCase(stackStatus.getStatus().toString());
    }

    public String createNewAccount() {
        newAccountId = Context.getId();
        System.out.println(AccountsDao.instance.insertOne(new Account(newAccountId, newAccountName)));

        UserAccountEntry uae = new UserAccountEntry();
        uae.setAccountId(newAccountId);
        BasicDBObject set = new BasicDBObject("$set", new BasicDBObject("accounts."+newAccountId, uae));

        UsersDao.instance.getMCollection().updateOne(eq("login", getSUser().getLogin()), set);

        getSession().put("accountId", newAccountId);
        Context.accountId.set(newAccountId);

        return Action.SUCCESS.toUpperCase();
    }

    public String goToAccount() {
        if (getSUser().getAccounts().containsKey(newAccountId+"")) {
            getSession().put("accountId", newAccountId);
            Context.accountId.set(newAccountId);
            return SUCCESS.toUpperCase();
        }

        return ERROR.toUpperCase();
    }

    public String getNewAccountName() {
        return newAccountName;
    }

    public void setNewAccountName(String newAccountName) {
        this.newAccountName = newAccountName;
    }

    public int getNewAccountId() {
        return newAccountId;
    }

    public void setNewAccountId(int newAccountId) {
        this.newAccountId = newAccountId;
    }
}
