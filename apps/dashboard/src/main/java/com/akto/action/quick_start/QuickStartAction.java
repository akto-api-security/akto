package com.akto.action.quick_start;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

import com.akto.utils.cloud.stack.dto.StackState;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.action.UserAction;
import com.akto.dao.ApiTokensDao;
import com.akto.dao.AwsResourcesDao;
import com.akto.dao.BackwardCompatibilityDao;
import com.akto.dao.context.Context;
import com.akto.dto.AwsResources;
import com.akto.dto.BackwardCompatibility;
import com.akto.utils.cloud.CloudType;
import com.akto.utils.cloud.Utils;
import com.akto.utils.cloud.serverless.ServerlessFunction;
import com.akto.utils.cloud.serverless.UpdateFunctionRequest;
import com.akto.utils.cloud.serverless.aws.Lambda;
import com.akto.utils.cloud.stack.Stack;
import com.akto.utils.cloud.stack.Stack.StackStatus;
import com.akto.utils.cloud.stack.aws.AwsStack;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingClientBuilder;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersResult;
import com.amazonaws.services.elasticloadbalancingv2.model.LoadBalancer;
import com.akto.dto.ApiToken;
import com.akto.dto.AwsResource;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;

public class QuickStartAction extends UserAction {

    private boolean dashboardHasNecessaryRole;
    private List<AwsResource> availableLBs;
    private List<AwsResource> selectedLBs;
    private boolean isFirstSetup;
    private StackState stackState;
    private List<String> configuredItems;
    private String awsRegion;
    private String awsAccountId;

    private final Stack stack = new AwsStack();
    private final ServerlessFunction serverlessFunction = new Lambda();

    private static final Logger logger = LoggerFactory.getLogger(QuickStartAction.class);

    public String fetchQuickStartPageState() {

        configuredItems = new ArrayList<>();

        // Fetching cloud integration
        CloudType type = Utils.getCloudType();
        configuredItems.add(type.toString());

        // Looking if burp is integrated or not
        ApiToken burpToken = ApiTokensDao.instance.findOne(Filters.eq(ApiToken.UTILITY, ApiToken.Utility.BURP));
        if (burpToken != null) {
            configuredItems.add(ApiToken.Utility.BURP.toString());
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchLoadBalancers() {
        List<AwsResource> availableLBs = new ArrayList<>();
        List<AwsResource> selectedLBs = new ArrayList<>();
        try {
            AmazonElasticLoadBalancing amazonElasticLoadBalancingClient = AmazonElasticLoadBalancingClientBuilder
                    .defaultClient();
            DescribeLoadBalancersResult result = amazonElasticLoadBalancingClient
                    .describeLoadBalancers(new DescribeLoadBalancersRequest());
            Map<String, AwsResource> lbInfo = new HashMap<>();
            for (LoadBalancer lb : result.getLoadBalancers()) {
                if (lb.getLoadBalancerName().equals("AktoLBDashboard") || lb.getLoadBalancerName().equals("AktoNLB")) {
                    continue;
                }
                lbInfo.put(lb.getLoadBalancerArn(), new AwsResource(lb.getLoadBalancerName(), lb.getLoadBalancerArn()));
            }
            this.dashboardHasNecessaryRole = true;
            availableLBs = new ArrayList<>(lbInfo.values());
            AwsResources resources = AwsResourcesDao.instance.findOne(AwsResourcesDao.generateFilter());
            if (resources != null && resources.getLoadBalancers() != null) {
                for (AwsResource selectedLb : resources.getLoadBalancers()) {
                    if (lbInfo.containsKey(selectedLb.getResourceId())) {
                        selectedLBs.add(lbInfo.get(selectedLb.getResourceId()));
                    }
                }
            }
        } catch (Exception e) {
            System.out.println(e.toString());
            e.printStackTrace();
            this.dashboardHasNecessaryRole = false;
        }
        this.awsRegion = System.getenv("AWS_REGION");
        this.awsAccountId = System.getenv("AWS_ACCOUNT_ID");
        this.selectedLBs = selectedLBs;
        this.availableLBs = availableLBs;
        return Action.SUCCESS.toUpperCase();
    }

    public String saveLoadBalancers() {
        Bson updates = Updates.set("loadBalancers", this.selectedLBs);
        AwsResourcesDao.instance.updateOne(Filters.eq("_id", Context.accountId.get()), updates);
        if (!stack.checkIfStackExists()) {
            this.isFirstSetup = true;
            try {
                Map<String, String> parameters = new HashMap<String, String>() {
                    {
                        put("MongoIp", System.getenv("AKTO_MONGO_CONN"));
                        put("KeyPair", System.getenv("EC2_KEY_PAIR"));
                        put("SourceLBs", extractLBs());
                        put("SubnetId", System.getenv("EC2_SUBNET_ID"));
                    }
                };
                String stackId = this.stack.createStack(parameters);
                System.out.println("Started creation of stack with id: " + stackId);
            } catch (Exception e) {
                e.printStackTrace();
            }

        } else {
            this.isFirstSetup = false;
            try {
                Map<String, String> updatedEnvVars = new HashMap<String, String>() {
                    {
                        put("ELB_NAMES", extractLBs());
                    }
                };
                String functionName = stack.fetchResourcePhysicalIdByLogicalId("CreateMirrorSession");
                UpdateFunctionRequest ufr = new UpdateFunctionRequest(updatedEnvVars);
                this.serverlessFunction.updateFunctionConfiguration(functionName, ufr);
                System.out.println("Successfully updated env var for lambda");
                // invoke lambda
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        this.stackState = stack.fetchStackStatus();
        fetchLoadBalancers();
        return Action.SUCCESS.toUpperCase();
    }

    public String extractLBs() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < this.selectedLBs.size(); i++) {
            sb.append(this.selectedLBs.get(i).getResourceName());
            if (i != this.selectedLBs.size() - 1) {
                sb.append(",");
            }
        }
        return sb.toString();
    }

    public String checkStackCreationProgress() {
        this.stackState = this.stack.fetchStackStatus();
        invokeLambdaIfNecessary(stackState);
        if(Stack.StackStatus.CREATION_FAILED.toString().equalsIgnoreCase(this.stackState.getStatus())){
            AwsResourcesDao.instance.getMCollection().deleteOne(Filters.eq("_id", Context.accountId.get()));
            logger.info("Current stack status is failed, so we are removing entry from db");
        }
        if(Stack.StackStatus.DOES_NOT_EXISTS.toString().equalsIgnoreCase(this.stackState.getStatus())){
            AwsResources resources = AwsResourcesDao.instance.findOne(AwsResourcesDao.generateFilter());
            if(resources != null && resources.getLoadBalancers().size() > 0){
                AwsResourcesDao.instance.getMCollection().deleteOne(AwsResourcesDao.generateFilter());
                logger.info("Stack does not exists but entry present in DB, removing it");
            } else {
                logger.info("Nothing set in DB, moving on");
            }
        }
        return Action.SUCCESS.toUpperCase();
    }

    private void invokeLambdaIfNecessary(StackState stackState){
        Runnable runnable = () -> {
            if(Stack.StackStatus.CREATE_COMPLETE.toString().equals(this.stackState.getStatus())){
                Context.accountId.set(1_000_000);
                BackwardCompatibility backwardCompatibility = BackwardCompatibilityDao.instance.findOne(new BasicDBObject());
                if(!backwardCompatibility.isMirroringLambdaTriggered()){
                    try{
                        String functionName = stack.fetchResourcePhysicalIdByLogicalId("CreateMirrorSession");
                        serverlessFunction.invokeFunction(functionName);
                        BackwardCompatibilityDao.instance.updateOne(
                                Filters.eq("_id", backwardCompatibility.getId()),
                                Updates.set(BackwardCompatibility.MIRRORING_LAMBDA_TRIGGERED, true)
                        );
                        logger.info("Successfully triggered CreateMirrorSession");
                    } catch(Exception e){
                        logger.error("Failed to invoke lambda for the first time", e);
                    }
                } else {
                    logger.info("Already invoked");
                }
            }
        };
        new Thread(runnable).start();
    }

    public boolean getDashboardHasNecessaryRole() {
        return dashboardHasNecessaryRole;
    }

    public void setDashboardHasNecessaryRole(boolean dashboardHasNecessaryRole) {
        this.dashboardHasNecessaryRole = dashboardHasNecessaryRole;
    }

    public String getAwsRegion() {
        return awsRegion;
    }

    public void setAwsRegion(String awsRegion) {
        this.awsRegion = awsRegion;
    }

    public String getAwsAccountId() {
        return awsAccountId;
    }

    public void setAwsAccountId(String awsAccountId) {
        this.awsAccountId = awsAccountId;
    }

    public boolean getIsFirstSetup() {
        return dashboardHasNecessaryRole;
    }

    public void setIsFirstSetup(boolean isFirstSetup) {
        this.isFirstSetup = isFirstSetup;
    }

    public List<AwsResource> getAvailableLBs() {
        return availableLBs;
    }

    public void setAvailableLBs(List<AwsResource> availableLBs) {
        this.availableLBs = availableLBs;
    }

    public List<AwsResource> getSelectedLBs() {
        return selectedLBs;
    }

    public void setSelectedLBs(List<AwsResource> selectedLBs) {
        this.selectedLBs = selectedLBs;
    }

    public StackState getStackState() {
        return stackState;
    }

    public void setStackState(StackState stackState) {
        this.stackState = stackState;
    }

    public List<String> getConfiguredItems() {
        return configuredItems;
    }

    public void setConfiguredItems(List<String> configuredItems) {
        this.configuredItems = configuredItems;
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
}