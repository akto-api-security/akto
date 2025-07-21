package com.akto.action;

import static com.akto.dto.AccountSettings.DASHBOARD_VERSION;
import static com.mongodb.client.model.Filters.eq;

import com.akto.DaoInit;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.AccountsDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.BackwardCompatibilityDao;
import com.akto.dao.RBACDao;
import com.akto.dao.UsersDao;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiCollection;
import com.akto.dto.BackwardCompatibility;
import com.akto.dto.RBAC;
import com.akto.dto.User;
import com.akto.dto.billing.Organization;
import com.akto.listener.InitializerListener;
import com.akto.listener.RuntimeListener;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.runtime.Main;
import com.akto.util.Constants;
import com.akto.util.DashboardMode;
import com.akto.util.enums.GlobalEnums.YamlTemplateSource;
import com.akto.utils.TestTemplateUtils;
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
import com.amazonaws.services.lambda.model.AWSLambdaException;
import com.amazonaws.services.lambda.model.FunctionConfiguration;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.lambda.model.ListFunctionsRequest;
import com.amazonaws.services.lambda.model.ListFunctionsResult;
import com.amazonaws.util.EC2MetadataUtils;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.bson.conversions.Bson;

public class AccountAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(AccountAction.class, LogDb.DASHBOARD);;

    private String newAccountName;
    private int newAccountId;

    public static final int MAX_NUM_OF_LAMBDAS_TO_FETCH = 50;
    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private static final ExecutorService service = Executors.newFixedThreadPool(1);

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

            loggerMaker.debugAndAddToDb("Invoke lambda "+functionName, LogDb.DASHBOARD);
            invokeResult = awsLambda.invoke(invokeRequest);

            String resp = new String(invokeResult.getPayload().array(), StandardCharsets.UTF_8);
            loggerMaker.debugAndAddToDb("Function: " + functionName + ", response:" + resp, LogDb.DASHBOARD);
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
                loggerMaker.debugAndAddToDb(String.format("Found %s functions", list.size()), LogDb.DASHBOARD);

                for (FunctionConfiguration config: list) {
                    loggerMaker.debugAndAddToDb(String.format("Found function: %s",config.getFunctionName()), LogDb.DASHBOARD);

                    if(config.getFunctionName().contains(functionName)) {
                        loggerMaker.debugAndAddToDb(String.format("Invoking function: %s", config.getFunctionName()), LogDb.DASHBOARD);
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
        loggerMaker.debugAndAddToDb(String.format("instance refresh called on %s with result %s", asg, result.toString()), LogDb.DASHBOARD);
    }

    public void lambdaInstanceRefreshViaLogicalId() throws Exception{
        String lambda;
            try {
                lambda = AwsStack.getInstance().fetchResourcePhysicalIdByLogicalId(MirroringStackDetails.getStackName(), MirroringStackDetails.AKTO_CONTEXT_ANALYZER_UPDATE_LAMBDA);
                Lambda.getInstance().invokeFunction(lambda);
                loggerMaker.debugAndAddToDb("Successfully invoked lambda " + lambda, LogDb.DASHBOARD);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e,"Failed to update Akto Context Analyzer" + e, LogDb.DASHBOARD);
            }
            try{
                lambda = AwsStack.getInstance().fetchResourcePhysicalIdByLogicalId(MirroringStackDetails.getStackName(),MirroringStackDetails.AKTO_DASHBOARD_UPDATE_LAMBDA);
                Lambda.getInstance().invokeFunction(lambda);
                loggerMaker.debugAndAddToDb("Successfully invoked lambda " +lambda, LogDb.DASHBOARD);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e,"Failed to update Akto Dashboard" + e, LogDb.DASHBOARD);
            }

            try{
                lambda = AwsStack.getInstance().fetchResourcePhysicalIdByLogicalId(MirroringStackDetails.getStackName(), MirroringStackDetails.AKTO_RUNTIME_UPDATE_LAMBDA);
                Lambda.getInstance().invokeFunction(lambda);
                loggerMaker.debugAndAddToDb("Successfully invoked lambda " + lambda, LogDb.DASHBOARD);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e,"Failed to update Akto Traffic Mirroring Instance" + e, LogDb.DASHBOARD);
            }
    }

    public void dashboardReboot(){
        try{
            String instanceId = EC2MetadataUtils.getInstanceId();
            if(instanceId!=null){
                Utils.rebootInstance(instanceId);
                loggerMaker.debugAndAddToDb("Dashboard instance rebooted", LogDb.DASHBOARD);
            }
        } catch (Exception e){
            loggerMaker.errorAndAddToDb(e,"Failed to update Akto Dashboard via instance reboot" + e, LogDb.DASHBOARD);
        }
    }

    public String takeUpdate() {
        if (!DashboardMode.isOnPremDeployment()) return Action.ERROR.toUpperCase();

        RefreshPreferences refreshPreferences = new RefreshPreferences();
        StartInstanceRefreshRequest refreshRequest = new StartInstanceRefreshRequest();
        refreshPreferences.setMinHealthyPercentage(0);
        refreshPreferences.setInstanceWarmup(200);
        refreshRequest.setPreferences(refreshPreferences);
        try {
            asgInstanceRefresh(refreshRequest, MirroringStackDetails.getStackName(), MirroringStackDetails.AKTO_CONTEXT_ANALYSER_AUTO_SCALING_GROUP);
            asgInstanceRefresh(refreshRequest, MirroringStackDetails.getStackName(), MirroringStackDetails.AKTO_TRAFFIC_MIRRORING_AUTO_SCALING_GROUP);
            asgInstanceRefresh(refreshRequest, DashboardStackDetails.getStackName(), DashboardStackDetails.AKTO_DASHBOARD_AUTO_SCALING_GROUP);
            loggerMaker.debugAndAddToDb("Successfully updated Akto via instance refresh", LogDb.DASHBOARD);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e){
            loggerMaker.debugAndAddToDb("Couldn't do instance refresh : " + e.getMessage(), LogDb.DASHBOARD);
        }
        loggerMaker.debugAndAddToDb("Instance refresh failed, trying via Lambda V2", LogDb.DASHBOARD);
        try {
            lambdaInstanceRefreshViaLogicalId();
            loggerMaker.debugAndAddToDb("Successfully updated Akto via Lambda V2", LogDb.DASHBOARD);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e){
            loggerMaker.errorAndAddToDb("Couldn't update Akto via Lambda V2 : " + e.getMessage(), LogDb.DASHBOARD);
        }
        loggerMaker.debugAndAddToDb("This is an old installation, updating via old way", LogDb.DASHBOARD);
        try {
            listMatchingLambda("InstanceRefresh");
            loggerMaker.debugAndAddToDb("Successfully updated Akto via old way", LogDb.DASHBOARD);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e){
            loggerMaker.errorAndAddToDb("Couldn't update Akto via old way : " + e.getMessage(), LogDb.DASHBOARD);
        }
        loggerMaker.debugAndAddToDb("Couldn't update Akto", LogDb.DASHBOARD);
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
                    loggerMaker.debugAndAddToDb(String.format("Added account %d to organization %s", newAccountId, organization.getId()), LogDb.DASHBOARD);

                    OrganizationUtils.syncOrganizationWithAkto(organization);
                    loggerMaker.debugAndAddToDb(String.format("Synced with billing service successfully %d to organization %s", newAccountId, organization.getId()), LogDb.DASHBOARD);

                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, String.format("Error while adding account %d to organization", newAccountId), LogDb.DASHBOARD);
            }
        }
   
        User user = initializeAccount(email, newAccountId, newAccountName,true, RBAC.Role.ADMIN.name());
        getSession().put("user", user);
        getSession().put("accountId", newAccountId);
        return Action.SUCCESS.toUpperCase();
    }

    public static User initializeAccount(String email, int newAccountId, String newAccountName, boolean isNew, String role) {
        User user = UsersDao.addAccount(email, newAccountId, newAccountName);
        RBACDao.instance.insertOne(new RBAC(user.getId(), role, newAccountId));
        Context.accountId.set(newAccountId);
        try {
            loggerMaker.debugAndAddToDb("Updated dashboard version");
            AccountSettingsDao.instance.updateVersion(DASHBOARD_VERSION);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e,"Error while updating account version", LogDb.DASHBOARD);
        }

        loggerMaker.debugAndAddToDb("isNew " + isNew);
        if (isNew) intializeCollectionsForTheAccount(newAccountId);
        return user;
    }

    public static User addUserToExistingAccount(String email, int accountId, String invitedRole){
        Account account = AccountsDao.instance.findOne(eq("_id", accountId));
        UsersDao.addNewAccount(email, account);
        User user = UsersDao.instance.findOne(eq(User.LOGIN, email));
        RBACDao.instance.insertOne(new RBAC(user.getId(), invitedRole, accountId));
        Context.accountId.set(accountId);
        return user;
    }

    private static void intializeCollectionsForTheAccount(int newAccountId) {
        loggerMaker.debugAndAddToDb("running intializeCollectionsForTheAccount for " + newAccountId);
        executorService.schedule(new Runnable() {
            public void run() {
                Context.accountId.set(newAccountId);
                loggerMaker.debugAndAddToDb("inside intializeCollectionsForTheAccount for " + Context.accountId.get());
                ApiCollectionsDao.instance.insertOne(new ApiCollection(0, "Default", Context.now(), new HashSet<>(), null, 0, false, true));
                loggerMaker.debugAndAddToDb("Inserted default collection");
                BackwardCompatibility backwardCompatibility = BackwardCompatibilityDao.instance.findOne(new BasicDBObject());
                if (backwardCompatibility == null) {
                    backwardCompatibility = new BackwardCompatibility();
                    BackwardCompatibilityDao.instance.insertOne(backwardCompatibility);
                }
                InitializerListener.setBackwardCompatibilities(backwardCompatibility);
                loggerMaker.debugAndAddToDb("start create indices", LogDb.DASHBOARD);
                DaoInit.createIndices();

                loggerMaker.debugAndAddToDb("start run time filters", LogDb.DASHBOARD);
                Main.insertRuntimeFilters();

                RuntimeListener.initialiseDemoCollections();
                loggerMaker.debugAndAddToDb("created juiceshop", LogDb.DASHBOARD);
                service.submit(() ->{
                    Context.accountId.set(newAccountId);
                    loggerMaker.debugAndAddToDb("updating vulnerable api's collection for new account " + newAccountId, LogDb.DASHBOARD);
                    RuntimeListener.addSampleData();
                });
                AccountSettingsDao.instance.updateOnboardingFlag(true);
                InitializerListener.insertPiiSources();

                AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
                InitializerListener.insertStateInAccountSettings(accountSettings);
                
                try {
                    InitializerListener.executePIISourceFetch();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                try {
                    byte[] testingTemplates = TestTemplateUtils.getTestingTemplates();
                    if(testingTemplates == null){
                        loggerMaker.errorAndAddToDb("Failed to load test templates", LogDb.DASHBOARD);
                        return;
                    }
                    loggerMaker.debugAndAddToDb(String.format("Updating akto test templates for new account: %d", newAccountId), LogDb.DASHBOARD);
                    InitializerListener.processTemplateFilesZip(testingTemplates, Constants._AKTO, YamlTemplateSource.AKTO_TEMPLATES.toString(), "");
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e,String.format("Error while adding test editor templates for new account %d, Error: %s", newAccountId, e.getMessage()), LogDb.DASHBOARD);
                }

                // add threat protection filter templates
                // todo refactor and extract out similar functions like processThreatFilterTemplateFilesZip
                try {
                    byte[] threatProtectionTemplates = TestTemplateUtils.getTestingTemplates();
                    if(threatProtectionTemplates == null){
                        loggerMaker.errorAndAddToDb("Failed to load threat protection templates", LogDb.DASHBOARD);
                        return;
                    }
                    InitializerListener.processThreatFilterTemplateFilesZip(threatProtectionTemplates, Constants._AKTO, YamlTemplateSource.AKTO_TEMPLATES.toString(), "");
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e,String.format("Error while adding threat protection templates for new account %d, Error: %s", e.getMessage()), LogDb.DASHBOARD);
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
