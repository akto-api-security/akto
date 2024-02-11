package com.akto.action;

import com.akto.DaoInit;
import com.akto.dao.*;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.akto.dto.billing.Organization;
import com.akto.listener.InitializerListener;
import com.akto.listener.RuntimeListener;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.runtime.Main;
import com.akto.util.enums.GlobalEnums.YamlTemplateSource;
import com.akto.util.DashboardMode;
import com.akto.utils.GithubSync;
import com.akto.utils.billing.OrganizationUtils;
import com.akto.utils.cloud.Utils;
import com.akto.utils.cloud.serverless.aws.Lambda;
import com.akto.utils.cloud.stack.aws.AwsStack;
import com.akto.utils.platform.DashboardStackDetails;
import com.akto.utils.platform.MirroringStackDetails;
import com.amazonaws.services.autoscaling.model.RefreshPreferences;
import com.amazonaws.services.autoscaling.model.StartInstanceRefreshRequest;
import com.amazonaws.services.autoscaling.model.StartInstanceRefreshResult;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.lambda.model.*;
import com.amazonaws.util.EC2MetadataUtils;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import org.bson.conversions.Bson;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.akto.dto.AccountSettings.DASHBOARD_VERSION;
import static com.mongodb.client.model.Filters.eq;

public class AccountAction extends UserAction {

    private String newAccountName;
    private int newAccountId;
private static final LoggerMaker loggerMaker = new LoggerMaker(AccountAction.class);

    public static final int MAX_NUM_OF_LAMBDAS_TO_FETCH = 50;
    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

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

            loggerMaker.infoAndAddToDb("Invoke lambda "+functionName, LogDb.DASHBOARD);
            invokeResult = awsLambda.invoke(invokeRequest);

            String resp = new String(invokeResult.getPayload().array(), StandardCharsets.UTF_8);
            loggerMaker.infoAndAddToDb("Function: " + functionName + ", response:" + resp, LogDb.DASHBOARD);
        } catch (AWSLambdaException e) {
            loggerMaker.errorAndAddToDb(e, String.format("Error while invoking Lambda, %s: %s", functionName, e), LogDb.DASHBOARD);
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
                loggerMaker.infoAndAddToDb(String.format("Found %s functions", list.size()), LogDb.DASHBOARD);

                for (FunctionConfiguration config: list) {
                    loggerMaker.infoAndAddToDb(String.format("Found function: %s",config.getFunctionName()), LogDb.DASHBOARD);

                    if(config.getFunctionName().contains(functionName)) {
                        loggerMaker.infoAndAddToDb(String.format("Invoking function: %s", config.getFunctionName()), LogDb.DASHBOARD);
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
            loggerMaker.errorAndAddToDb(e,String.format("Error while updating Akto: %s",e), LogDb.DASHBOARD);
        }
    }

    public void asgInstanceRefresh(StartInstanceRefreshRequest refreshRequest, String stack, String asg){
        String autoScalingGroup = AwsStack.getInstance().fetchResourcePhysicalIdByLogicalId(stack, asg);
        refreshRequest.setAutoScalingGroupName(autoScalingGroup);
        StartInstanceRefreshResult result = AwsStack.getInstance().getAsc().startInstanceRefresh(refreshRequest);
        loggerMaker.infoAndAddToDb(String.format("instance refresh called on %s with result %s", asg, result.toString()), LogDb.DASHBOARD);
    }

    public void lambdaInstanceRefreshViaLogicalId() throws Exception{
        String lambda;
            try {
                lambda = AwsStack.getInstance().fetchResourcePhysicalIdByLogicalId(MirroringStackDetails.getStackName(), MirroringStackDetails.AKTO_CONTEXT_ANALYZER_UPDATE_LAMBDA);
                Lambda.getInstance().invokeFunction(lambda);
                loggerMaker.infoAndAddToDb("Successfully invoked lambda " + lambda, LogDb.DASHBOARD);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e,"Failed to update Akto Context Analyzer" + e, LogDb.DASHBOARD);
            }
            try{
                lambda = AwsStack.getInstance().fetchResourcePhysicalIdByLogicalId(MirroringStackDetails.getStackName(),MirroringStackDetails.AKTO_DASHBOARD_UPDATE_LAMBDA);
                Lambda.getInstance().invokeFunction(lambda);
                loggerMaker.infoAndAddToDb("Successfully invoked lambda " +lambda, LogDb.DASHBOARD);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e,"Failed to update Akto Dashboard" + e, LogDb.DASHBOARD);
            }

            try{
                lambda = AwsStack.getInstance().fetchResourcePhysicalIdByLogicalId(MirroringStackDetails.getStackName(), MirroringStackDetails.AKTO_RUNTIME_UPDATE_LAMBDA);
                Lambda.getInstance().invokeFunction(lambda);
                loggerMaker.infoAndAddToDb("Successfully invoked lambda " + lambda, LogDb.DASHBOARD);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e,"Failed to update Akto Traffic Mirroring Instance" + e, LogDb.DASHBOARD);
            }
    }

    public void dashboardReboot(){
        try{
            String instanceId = EC2MetadataUtils.getInstanceId();
            if(instanceId!=null){
                Utils.rebootInstance(instanceId);
                loggerMaker.infoAndAddToDb("Dashboard instance rebooted", LogDb.DASHBOARD);
            }
        } catch (Exception e){
            loggerMaker.errorAndAddToDb(e,"Failed to update Akto Dashboard via instance reboot" + e, LogDb.DASHBOARD);
        }
    }

    public String takeUpdate() {
        RefreshPreferences refreshPreferences = new RefreshPreferences();
        StartInstanceRefreshRequest refreshRequest = new StartInstanceRefreshRequest();
        refreshPreferences.setMinHealthyPercentage(0);
        refreshPreferences.setInstanceWarmup(200);
        refreshRequest.setPreferences(refreshPreferences);
        try {
            asgInstanceRefresh(refreshRequest, MirroringStackDetails.getStackName(), MirroringStackDetails.AKTO_CONTEXT_ANALYSER_AUTO_SCALING_GROUP);
            asgInstanceRefresh(refreshRequest, MirroringStackDetails.getStackName(), MirroringStackDetails.AKTO_TRAFFIC_MIRRORING_AUTO_SCALING_GROUP);
            asgInstanceRefresh(refreshRequest, DashboardStackDetails.getStackName(), DashboardStackDetails.AKTO_DASHBOARD_AUTO_SCALING_GROUP);
            loggerMaker.infoAndAddToDb("Successfully updated Akto via instance refresh", LogDb.DASHBOARD);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e){
            loggerMaker.infoAndAddToDb("Couldn't do instance refresh : " + e.getMessage(), LogDb.DASHBOARD);
        }
        loggerMaker.infoAndAddToDb("Instance refresh failed, trying via Lambda V2", LogDb.DASHBOARD);
        try {
            lambdaInstanceRefreshViaLogicalId();
            loggerMaker.infoAndAddToDb("Successfully updated Akto via Lambda V2", LogDb.DASHBOARD);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e){
            loggerMaker.errorAndAddToDb("Couldn't update Akto via Lambda V2 : " + e.getMessage(), LogDb.DASHBOARD);
        }
        loggerMaker.infoAndAddToDb("This is an old installation, updating via old way", LogDb.DASHBOARD);
        try {
            listMatchingLambda("InstanceRefresh");
            loggerMaker.infoAndAddToDb("Successfully updated Akto via old way", LogDb.DASHBOARD);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e){
            loggerMaker.errorAndAddToDb("Couldn't update Akto via old way : " + e.getMessage(), LogDb.DASHBOARD);
        }
        loggerMaker.infoAndAddToDb("Couldn't update Akto", LogDb.DASHBOARD);
        return Action.ERROR.toUpperCase();
    }

    public static int createAccountRecord(String accountName) {

        if (accountName == null || accountName.isEmpty()) {
            throw new IllegalArgumentException("Account name can't be empty");
        }
        long existingAccountsCount = AccountsDao.instance.count(new BasicDBObject());
        if(existingAccountsCount==0){
            AccountsDao.instance.insertOne(new Account(1_000_000, accountName));
            return 1_000_000;
        }
        int triesAllowed = 10;
        int now = Context.now();
        while(triesAllowed >=0) {
            try {
                now = now - 40000;
                triesAllowed --;
                AccountsDao.instance.insertOne(new Account(now, accountName));
                return now;
            } catch (Exception e) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e1) {
                    // eat it
                }
            }
        }

        throw new IllegalStateException("Couldn't find a suitable account id. Please try after some time.");
    }

    public String createNewAccount() {
        User sessionUser = getSUser();
        if (sessionUser.getAccounts().keySet().size() > 10) {//Do not allow account creation when user has 10 accounts
            return Action.ERROR.toUpperCase();
        }
        String email = sessionUser.getLogin();
        int newAccountId = createAccountRecord(newAccountName);

        if (DashboardMode.isSaasDeployment()) {
            // Add new account to a organization
            // The account from which create new account was clicked, that account's organization will be used
            try {
                int organizationAccountId = Context.accountId.get();
                Organization organization = OrganizationsDao.instance.findOne(
                        Filters.in(Organization.ACCOUNTS, organizationAccountId)
                );

                if (organization != null) {
                    Set<Integer> organizationAccountsSet = organization.getAccounts();
                    organizationAccountsSet.add(newAccountId);

                    Bson updatesQ = Updates.combine(
                        Updates.set(Organization.ACCOUNTS, organizationAccountsSet),
                        Updates.set(Organization.SYNCED_WITH_AKTO, false)
                    );

                    OrganizationsDao.instance.updateOne(
                        Filters.eq(Organization.ID, organization.getId()),
                        updatesQ
                    );
                    loggerMaker.infoAndAddToDb(String.format("Added account %d to organization %s", newAccountId, organization.getId()), LogDb.DASHBOARD);

                    OrganizationUtils.syncOrganizationWithAkto(organization);
                    loggerMaker.infoAndAddToDb(String.format("Synced with billing service successfully %d to organization %s", newAccountId, organization.getId()), LogDb.DASHBOARD);

                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, String.format("Error while adding account %d to organization", newAccountId), LogDb.DASHBOARD);
            }
        }
   
        User user = initializeAccount(email, newAccountId, newAccountName,true);
        getSession().put("user", user);
        getSession().put("accountId", newAccountId);
        return Action.SUCCESS.toUpperCase();
    }

    public static User initializeAccount(String email, int newAccountId,String newAccountName,  boolean isNew) {
        UsersDao.addAccount(email, newAccountId, newAccountName);
        User user = UsersDao.instance.findOne(eq(User.LOGIN, email));
        RBAC.Role role = isNew ? RBAC.Role.ADMIN : RBAC.Role.MEMBER;
        RBACDao.instance.insertOne(new RBAC(user.getId(), role, newAccountId));
        Context.accountId.set(newAccountId);
        try {
            AccountSettingsDao.instance.updateVersion(DASHBOARD_VERSION);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e,"Error while updating account version", LogDb.DASHBOARD);
        }
        if (isNew) intializeCollectionsForTheAccount(newAccountId);
        return user;
    }

    public static void addUserToExistingAccount(String email, int accountId){
        Account account = AccountsDao.instance.findOne(eq("_id", accountId));
        UsersDao.addNewAccount(email, account);
        User user = UsersDao.instance.findOne(eq(User.LOGIN, email));
        //RBACDao.instance.insertOne(new RBAC(user.getId(), RBAC.Role.MEMBER, accountId));
        Context.accountId.set(accountId);
    }

    private static void intializeCollectionsForTheAccount(int newAccountId) {
        executorService.schedule(new Runnable() {
            public void run() {
                Context.accountId.set(newAccountId);
                ApiCollectionsDao.instance.insertOne(new ApiCollection(0, "Default", Context.now(), new HashSet<>(), null, 0));
                BackwardCompatibility backwardCompatibility = BackwardCompatibilityDao.instance.findOne(new BasicDBObject());
                if (backwardCompatibility == null) {
                    backwardCompatibility = new BackwardCompatibility();
                    BackwardCompatibilityDao.instance.insertOne(backwardCompatibility);
                }
                InitializerListener.setBackwardCompatibilities(backwardCompatibility);
                DaoInit.createIndices();
                Main.insertRuntimeFilters();
                RuntimeListener.initialiseDemoCollections();
                RuntimeListener.addSampleData();
                AccountSettingsDao.instance.updateOnboardingFlag(true);
                InitializerListener.insertPiiSources();

                try {
                    InitializerListener.executePIISourceFetch();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                try {
                    GithubSync githubSync = new GithubSync();
                    byte[] repoZip = githubSync.syncRepo("akto-api-security/tests-library", "master");
                    loggerMaker.infoAndAddToDb(String.format("Updating akto test templates for new account: %d", newAccountId), LogDb.DASHBOARD);
                    InitializerListener.processTemplateFilesZip(repoZip, InitializerListener._AKTO, YamlTemplateSource.AKTO_TEMPLATES.toString(), "");
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e,String.format("Error while adding test editor templates for new account %d, Error: %s", newAccountId, e.getMessage()), LogDb.DASHBOARD);
                }
            }
        }, 0, TimeUnit.SECONDS);
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
