package com.akto.action.quick_start;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.*;

import com.akto.util.Constants;
import com.akto.utils.DashboardMode;
import com.akto.utils.platform.DashboardStackDetails;
import com.akto.utils.platform.MirroringStackDetails;
import com.akto.utils.cloud.stack.dto.StackState;
import com.amazonaws.services.cloudformation.model.Tag;
import org.apache.commons.lang3.StringUtils;
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
import com.akto.dto.User;
import com.akto.dto.third_party_access.PostmanCredential;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.utils.cloud.CloudType;
import com.akto.utils.cloud.Utils;
import com.akto.utils.cloud.serverless.UpdateFunctionRequest;
import com.akto.utils.cloud.serverless.aws.Lambda;
import com.akto.utils.cloud.stack.Stack;
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
    private String aktoDashboardRoleName;
    private String aktoMirroringStackName;

    private String aktoDashboardStackName;


    private static final LoggerMaker loggerMaker = new LoggerMaker(QuickStartAction.class);

    public String fetchQuickStartPageState() {

        configuredItems = new ArrayList<>();

        // Fetching cloud integration
        if(DashboardMode.isOnPremDeployment()) {
            CloudType type = Utils.getCloudType();
            configuredItems.add(type.toString());
        }

        // Looking if burp is integrated or not
        ApiToken burpToken = ApiTokensDao.instance.findOne(Filters.eq(ApiToken.UTILITY, ApiToken.Utility.BURP));
        if (burpToken != null) {
            configuredItems.add(ApiToken.Utility.BURP.toString());
        }
        User u = getSUser();
        PostmanCredential postmanCredential = com.akto.utils.Utils.fetchPostmanCredential(u.getId());
        if(postmanCredential != null) {
            configuredItems.add("POSTMAN");
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchLoadBalancers() {
        List<AwsResource> availableLBs = new ArrayList<>();
        List<AwsResource> selectedLBs = new ArrayList<>();
        ExecutorService executorService = Executors.newFixedThreadPool(3);
        try {
            Future<String> dashboardLBNameFuture = executorService.submit(()-> AwsStack.getInstance().fetchResourcePhysicalIdByLogicalId(DashboardStackDetails.getStackName(), DashboardStackDetails.AKTO_LB_DASHBOARD));
            Future<String> aktoNLBNameFuture = executorService.submit(()-> AwsStack.getInstance().fetchResourcePhysicalIdByLogicalId(MirroringStackDetails.getStackName(), MirroringStackDetails.AKTO_NLB));
            AmazonElasticLoadBalancing amazonElasticLoadBalancingClient = AmazonElasticLoadBalancingClientBuilder
                    .defaultClient();
            Future<DescribeLoadBalancersResult> loadBalanersFuture = executorService.submit(() -> amazonElasticLoadBalancingClient
                    .describeLoadBalancers(new DescribeLoadBalancersRequest()));


            DescribeLoadBalancersResult result = loadBalanersFuture.get();
            String dashboardLBName = filterLBName(dashboardLBNameFuture.get());
            String aktoNLBName = filterLBName(aktoNLBNameFuture.get());
            executorService.shutdown();
            Map<String, AwsResource> lbInfo = new HashMap<>();
            for (LoadBalancer lb : result.getLoadBalancers()) {
                String lbName = lb.getLoadBalancerName().toLowerCase();
                if ( lbName.contains("akto") || lbName.equalsIgnoreCase(dashboardLBName) || lbName.equalsIgnoreCase(aktoNLBName)) {
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
            loggerMaker.errorAndAddToDb(String.format("Error occurred while fetching LBs %s", e), LogDb.DASHBOARD);
            this.dashboardHasNecessaryRole = false;
        }
        this.awsRegion = System.getenv(Constants.AWS_REGION);
        this.awsAccountId = System.getenv(Constants.AWS_ACCOUNT_ID);
        this.selectedLBs = selectedLBs;
        this.availableLBs = availableLBs;
        this.aktoDashboardRoleName = DashboardStackDetails.getAktoDashboardRole();
        this.aktoMirroringStackName = MirroringStackDetails.getStackName();
        this.aktoDashboardStackName = DashboardStackDetails.getStackName();
        return Action.SUCCESS.toUpperCase();
    }

    private String filterLBName(String lbArn) {
        if(StringUtils.isEmpty(lbArn)){
            return "";
        }
        String[] details = lbArn.split(":");
        String lastDetail = details[details.length-1];
        String[] resourceNameDetails = lastDetail.split("/");
        if(resourceNameDetails.length <= 1){
            return "";
        }
        return resourceNameDetails[resourceNameDetails.length-2];
    }

    public String saveLoadBalancers() {
        Bson updates = Updates.set("loadBalancers", this.selectedLBs);
        AwsResourcesDao.instance.updateOne(Filters.eq("_id", Context.accountId.get()), updates);
        if (!AwsStack.getInstance().checkIfStackExists(MirroringStackDetails.getStackName())) {
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
                String template = convertStreamToString(AwsStack.class
                        .getResourceAsStream("/cloud_formation_templates/akto_aws_mirroring.template"));
                List<Tag> tags = Utils.fetchTags(DashboardStackDetails.getStackName());
                String stackId = AwsStack.getInstance().createStack(MirroringStackDetails.getStackName(), parameters, template, tags);
            } catch (Exception e) {
                ;
            }

        } else {
            this.isFirstSetup = false;
            try {
                Map<String, String> updatedEnvVars = new HashMap<String, String>() {
                    {
                        put("ELB_NAMES", extractLBs());
                    }
                };
                String functionName = AwsStack.getInstance().fetchResourcePhysicalIdByLogicalId(MirroringStackDetails.getStackName(), MirroringStackDetails.CREATE_MIRROR_SESSION_LAMBDA);
                UpdateFunctionRequest ufr = new UpdateFunctionRequest(updatedEnvVars);
                Lambda.getInstance().updateFunctionConfiguration(functionName, ufr);
                // invoke lambda
            } catch (Exception e) {
                ;
            }
        }
        this.stackState = AwsStack.getInstance().fetchStackStatus(MirroringStackDetails.getStackName());
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
        this.stackState = AwsStack.getInstance().fetchStackStatus(MirroringStackDetails.getStackName());
        invokeLambdaIfNecessary(stackState);
        if(Stack.StackStatus.CREATION_FAILED.toString().equalsIgnoreCase(this.stackState.getStatus())){
            AwsResourcesDao.instance.getMCollection().deleteOne(Filters.eq("_id", Context.accountId.get()));
            loggerMaker.infoAndAddToDb("Current stack status is failed, so we are removing entry from db", LogDb.DASHBOARD);
        }
        if(Stack.StackStatus.DOES_NOT_EXISTS.toString().equalsIgnoreCase(this.stackState.getStatus())){
            AwsResources resources = AwsResourcesDao.instance.findOne(AwsResourcesDao.generateFilter());
            if(resources != null && resources.getLoadBalancers().size() > 0){
                AwsResourcesDao.instance.getMCollection().deleteOne(AwsResourcesDao.generateFilter());
                loggerMaker.infoAndAddToDb("Stack does not exists but entry present in DB, removing it", LogDb.DASHBOARD);
                fetchLoadBalancers();
            } else {
                loggerMaker.infoAndAddToDb("Nothing set in DB, moving on", LogDb.DASHBOARD);
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
                        String functionName = AwsStack.getInstance().fetchResourcePhysicalIdByLogicalId(MirroringStackDetails.getStackName(), MirroringStackDetails.CREATE_MIRROR_SESSION_LAMBDA);
                        Lambda.getInstance().invokeFunction(functionName);
                        BackwardCompatibilityDao.instance.updateOne(
                                Filters.eq("_id", backwardCompatibility.getId()),
                                Updates.set(BackwardCompatibility.MIRRORING_LAMBDA_TRIGGERED, true)
                        );
                        loggerMaker.infoAndAddToDb("Successfully triggered CreateMirrorSession", LogDb.DASHBOARD);
                    } catch(Exception e){
                        loggerMaker.errorAndAddToDb(String.format("Failed to invoke lambda for the first time : %s", e), LogDb.DASHBOARD);
                    }
                } else {
                    loggerMaker.infoAndAddToDb("Already invoked", LogDb.DASHBOARD);
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

    public String getAktoDashboardRoleName() {
        return aktoDashboardRoleName;
    }

    public void setAktoDashboardRoleName(String aktoDashboardRoleName) {
        this.aktoDashboardRoleName = aktoDashboardRoleName;
    }

    public String getAktoMirroringStackName() {
        return aktoMirroringStackName;
    }

    public void setAktoMirroringStackName(String aktoMirroringStackName) {
        this.aktoMirroringStackName = aktoMirroringStackName;
    }

    public String getAktoDashboardStackName() {
        return aktoDashboardStackName;
    }

    public void setAktoDashboardStackName(String aktoDashboardStackName) {
        this.aktoDashboardStackName = aktoDashboardStackName;
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