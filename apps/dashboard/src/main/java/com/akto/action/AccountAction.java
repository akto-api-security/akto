package com.akto.action;

import com.akto.dao.AccountsDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.UserAccountEntry;
import com.akto.utils.cloud.serverless.aws.Lambda;
import com.akto.utils.cloud.stack.aws.AwsStack;
import com.akto.utils.cloud.stack.dto.StackState;
import com.akto.utils.platform.DashboardStackDetails;
import com.akto.utils.platform.MirroringStackDetails;
import com.amazonaws.services.lambda.model.*;
import com.mongodb.BasicDBObject;
import com.opensymphony.xwork2.Action;
import com.amazonaws.services.autoscaling.AmazonAutoScaling;
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
    private static AmazonAutoScaling asc = AmazonAutoScalingClientBuilder.standard().build();
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

    public void asgInstanceRefresh(StartInstanceRefreshRequest refreshRequest, String stack, String asg){
        String autoScalingGroup = AwsStack.getInstance().fetchResourcePhysicalIdByLogicalId(stack, asg);
        refreshRequest.setAutoScalingGroupName(autoScalingGroup);
        StartInstanceRefreshResult result = asc.startInstanceRefresh(refreshRequest);
        logger.info("instance refresh called on %s with result %s", asg, result.toString());
    }

    public void lambdaInstanceRefresh(){
        String lambda;
            try {
                lambda = AwsStack.getInstance().fetchResourcePhysicalIdByLogicalId(MirroringStackDetails.getStackName(), MirroringStackDetails.AKTO_CONTEXT_ANALYZER_UPDATE_LAMBDA);
                Lambda.getInstance().invokeFunction(lambda);
                logger.info("Successfully invoked lambda {}", lambda);
            } catch (Exception e) {
                logger.error("Failed to update Akto Context Analyzer", e);
            }
            try{
                lambda = AwsStack.getInstance().fetchResourcePhysicalIdByLogicalId(MirroringStackDetails.getStackName(),MirroringStackDetails.AKTO_DASHBOARD_UPDATE_LAMBDA);
                Lambda.getInstance().invokeFunction(lambda);
                logger.info("Successfully invoked lambda {}", lambda);
            } catch (Exception e) {
                logger.error("Failed to update Akto Dashboard", e);
            }

            try{
                lambda = AwsStack.getInstance().fetchResourcePhysicalIdByLogicalId(MirroringStackDetails.getStackName(), MirroringStackDetails.AKTO_RUNTIME_UPDATE_LAMBDA);
                Lambda.getInstance().invokeFunction(lambda);
                logger.info("Successfully invoked lambda {}", lambda);
            } catch (Exception e) {
                logger.error("Failed to update Akto Traffic Mirroring Instance", e);
            }
    }

    public String takeUpdate() {
        if(checkIfStairwayInstallation()) {
            RefreshPreferences refreshPreferences = new RefreshPreferences();
            StartInstanceRefreshRequest refreshRequest = new StartInstanceRefreshRequest();
            refreshPreferences.setMinHealthyPercentage(0);
            refreshPreferences.setInstanceWarmup(200);
            refreshRequest.setPreferences(refreshPreferences);
            try {
                asgInstanceRefresh(refreshRequest, MirroringStackDetails.getStackName(), MirroringStackDetails.AKTO_CONTEXT_ANALYSER_AUTO_SCALING_GROUP);
                asgInstanceRefresh(refreshRequest, MirroringStackDetails.getStackName(), MirroringStackDetails.AKTO_TRAFFIC_MIRRORING_AUTO_SCALING_GROUP);
                asgInstanceRefresh(refreshRequest, DashboardStackDetails.getStackName(), DashboardStackDetails.AKTO_DASHBOARD_AUTO_SCALING_GROUP);
            } catch (Exception e){
                logger.info("could not invoke instance refresh directly, using lambdas ",e.getMessage());
                lambdaInstanceRefresh();
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
