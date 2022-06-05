package com.akto.action;

import com.akto.dao.AccountsDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.UserAccountEntry;
import com.mongodb.BasicDBObject;
import com.opensymphony.xwork2.Action;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.lambda.model.FunctionConfiguration;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.lambda.model.ListFunctionsResult;
import com.amazonaws.services.lambda.model.ServiceException;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;

public class AccountAction extends UserAction {

    private String newAccountName;
    private int newAccountId;

    @Override
    public String execute() {

        return Action.SUCCESS.toUpperCase();
    }

    private void invokeLambda(String functionName) {
        InvokeRequest invokeRequest = new InvokeRequest()
                .withFunctionName(functionName)
                .withPayload("{}");
        InvokeResult invokeResult = null;

        try {
            AWSLambda awsLambda = AWSLambdaClientBuilder.standard().build();
            ListFunctionsResult functionResult = awsLambda.listFunctions();

            List<FunctionConfiguration> list = functionResult.getFunctions();

            for (FunctionConfiguration config: list) {
                System.out.println("The function name is "+config.getFunctionName());

                if(config.getFunctionName().contains(functionName)) {
                    try {

                        System.out.println("Invoke lambda "+config.getFunctionName());
                        invokeResult = awsLambda.invoke(invokeRequest);

                        String ans = new String(invokeResult.getPayload().array(), StandardCharsets.UTF_8);
            
                        //write out the return value
                        System.out.println(ans);        
                    } catch (ServiceException e) {
                        System.out.println(e);
                    }
                }
            }
        } catch (ServiceException e) {
            System.out.println(e);
        }

        System.out.println(invokeResult.getStatusCode());
    }

    public String takeUpdate() {
        invokeLambda("TrafficMirroringInstanceRefreshHandler");
        invokeLambda("DashboardInstanceRefreshHandler");
        return Action.SUCCESS.toUpperCase();
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
