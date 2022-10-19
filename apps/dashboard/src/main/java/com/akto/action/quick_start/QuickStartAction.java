package com.akto.action.quick_start;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.conversions.Bson;

import com.akto.action.UserAction;
import com.akto.dao.AwsResourcesDao;
import com.akto.dao.context.Context;
import com.akto.dto.AwsResources;
import com.akto.utils.cloud.serverless.ServerlessFunction;
import com.akto.utils.cloud.serverless.UpdateFunctionRequest;
import com.akto.utils.cloud.serverless.aws.Lambda;
import com.akto.utils.cloud.stack.Stack;
import com.akto.utils.cloud.stack.aws.AwsStack;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingClientBuilder;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersResult;
import com.amazonaws.services.elasticloadbalancingv2.model.LoadBalancer;
import com.akto.dto.AwsResource;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;

public class QuickStartAction extends UserAction {

    private boolean dashboardHasNecessaryRole;
    private List<AwsResource> availableLBs;
    private List<AwsResource> selectedLBs;
    private boolean isFirstSetup;
    private String stackStatus;

    private final Stack stack = new AwsStack();
    private final ServerlessFunction serverlessFunction = new Lambda();

    public String fetchLoadBalancers() {
        try {
            AmazonElasticLoadBalancing amazonElasticLoadBalancingClient = AmazonElasticLoadBalancingClientBuilder
                    .defaultClient();
            DescribeLoadBalancersResult result = amazonElasticLoadBalancingClient
                    .describeLoadBalancers(new DescribeLoadBalancersRequest());
            Map<String, AwsResource> lbInfo = new HashMap<>();
            for (LoadBalancer lb : result.getLoadBalancers()) {
                lbInfo.put(lb.getLoadBalancerArn(), new AwsResource(lb.getLoadBalancerName(), lb.getLoadBalancerArn()));
            }
            this.dashboardHasNecessaryRole = true;
            this.availableLBs = new ArrayList<>(lbInfo.values());// lbInfo.values().stream().collect(Collectors.toList());
            List<AwsResource> selectedLBs = new ArrayList<>();
            AwsResources resources = AwsResourcesDao.instance.findOne(AwsResourcesDao.generateFilter());
            if (resources != null && resources.getLoadBalancers() != null) {
                for (AwsResource selectedLb : resources.getLoadBalancers()) {
                    if (lbInfo.containsKey(selectedLb.getResourceId())) {
                        selectedLBs.add(lbInfo.get(selectedLb.getResourceId()));
                    }
                }
            }
            this.selectedLBs = selectedLBs;
        } catch (Exception e) {
            System.out.println(e.toString());
            e.printStackTrace();
            this.dashboardHasNecessaryRole = false;
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String saveLoadBalancers() {
        Bson updates = Updates.set("loadBalancers", this.selectedLBs);
        AwsResourcesDao.instance.updateOne(Filters.eq("_id", Context.accountId.get()), updates);
        if (!stack.checkIfStackExists()) { // is this sufficient, better would be compare all the resources, but
            // this will be complex
            this.isFirstSetup = true;
            try {
                Map<String, String> parameters = new HashMap<String, String>() {
                    {
                        put("MongoIp", System.getenv("AKTO_MONGO_CONN_CFT")); // TODO update this before checking in
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
        try {
            this.stackStatus = this.stack.fetchStackStatus();
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public boolean getDashboardHasNecessaryRole() {
        return dashboardHasNecessaryRole;
    }

    public void setDashboardHasNecessaryRole(boolean dashboardHasNecessaryRole) {
        this.dashboardHasNecessaryRole = dashboardHasNecessaryRole;
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

    public String getStackStatus() {
        return stackStatus;
    }

    public void setStackStatus(String stackStatus) {
        this.stackStatus = stackStatus;
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