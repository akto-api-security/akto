package com.akto.action;


import static com.mongodb.client.model.Filters.in;

import com.akto.billing.UsageMetricUtils;
import com.akto.dao.*;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.akto.dto.ApiToken.Utility;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.billing.Organization;
import com.akto.dto.jira_integration.JiraIntegration;
import com.akto.listener.InitializerListener;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.akto.util.DashboardMode;
import com.akto.util.EmailAccountName;
import com.akto.utils.Intercom;
import com.akto.utils.AlertUtils;
import com.akto.utils.billing.OrganizationUtils;
import com.akto.utils.cloud.Utils;
import com.akto.utils.crons.OrganizationCache;
import com.akto.util.OrganizationInfo;
import com.akto.notifications.slack.SlackAlerts;
import com.akto.notifications.slack.UserBlockedNoPlanAlert;
import com.akto.notifications.slack.SlackSender;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public class ProfileAction extends UserAction {

    private static final LoggerMaker logger = new LoggerMaker(ProfileAction.class, LogDb.DASHBOARD);

    private int accountId;

    @Override
    public String execute() {

        return SUCCESS.toUpperCase();
    }

    public static void executeMeta1(Utility utility, User user, HttpServletRequest request, HttpServletResponse response) {
        BasicDBObject userDetails = new BasicDBObject();
        BasicDBObject accounts = new BasicDBObject();
        Integer sessionAccId = Context.accountId.get();
        if (sessionAccId == null || sessionAccId == 0) {
            sessionAccId = 0;
        }

        List<Integer> accountIdInt = new ArrayList<>();
        for (UserAccountEntry uae: user.getAccounts().values()) {
            accountIdInt.add(uae.getAccountId());
            if (uae.isDefault() && sessionAccId == 0) {
                sessionAccId = uae.getAccountId();
            }
        }

        for (Account acc: AccountsDao.instance.findAll(in("_id", accountIdInt))) {
            accounts.append(acc.getId()+"", acc.getName());
            if (sessionAccId == 0) {
                sessionAccId = acc.getId();
            }
        };

        if (sessionAccId == 0) {
            throw new IllegalStateException("user has no accounts associated");
        } else {
            logger.debug("setting session: " + sessionAccId);
            request.getSession().setAttribute("accountId", sessionAccId);
            Context.accountId.set(sessionAccId);
        }

        BasicDBList listDashboards = new BasicDBList();
        Account currAccount = AccountsDao.instance.findOne(Filters.eq(Constants.ID, sessionAccId),Projections.include("name", "timezone"));

        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
        boolean showOnboarding = accountSettings == null ? true : accountSettings.isShowOnboarding();

        if (showOnboarding && request.getRequestURI().startsWith("/dashboard") && !request.getRequestURI().equals("/dashboard/onboarding")) {
            try {
                response.sendRedirect("/dashboard/onboarding"); 
            }  catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }
        String username = user.getLogin();

        EmailAccountName emailAccountName = new EmailAccountName(username); // username is the email id of the current user
        String accountName = emailAccountName.getAccountName();
        if(currAccount != null && !currAccount.getName().isEmpty() && !currAccount.getName().equals("My account")){
            accountName = currAccount.getName();
        }
        String timeZone = "US/Pacific";
        if(currAccount != null && !currAccount.getTimezone().isEmpty()){
            timeZone = currAccount.getTimezone();
        }
        String dashboardVersion = InitializerListener.aktoVersion;
        if(accountSettings != null){
            dashboardVersion = accountSettings.getDashboardVersion();
        }
        String[] versions = dashboardVersion.split(" - ");
        User userFromDB = UsersDao.instance.findOne(Filters.eq(Constants.ID, user.getId()));
        RBAC.Role userRole = RBACDao.getCurrentRoleForUser(user.getId(), Context.accountId.get());

        boolean jiraIntegrated = false;
        try {
            JiraIntegration jiraIntegration = JiraIntegrationDao.instance.findOne(new BasicDBObject());
            if (jiraIntegration != null) {
                jiraIntegrated = true;
            }
        } catch (Exception e) {
        }

        boolean azureBoardsIntegrated = false;
        try {
            long documentCount = AzureBoardsIntegrationDao.instance.estimatedDocumentCount();
            if (documentCount > 0) {
                azureBoardsIntegrated = true;
            }
        } catch (Exception e) {
        }


        boolean servicenowIntegrated = false;
        try {
            long documentCount = ServiceNowIntegrationDao.instance.estimatedDocumentCount();
            if (documentCount > 0) {
                servicenowIntegrated = true;
            }
        } catch (Exception e) {
        }

        boolean devrevIntegrated = false;
        try {
            long documentCount = DevRevIntegrationDao.instance.estimatedDocumentCount();
            if (documentCount > 0) {
                devrevIntegrated = true;
            }
        } catch (Exception e) {
        }

        InitializerListener.insertStateInAccountSettings(accountSettings);

        Organization organization = OrganizationsDao.instance.findOne(
                Filters.in(Organization.ACCOUNTS, sessionAccId)
        );

        String userActualName = "";
        if(userFromDB.getNameLastUpdate() > 0) {
            userActualName = userFromDB.getName();
        }

        String orgName = "";
        if(organization != null && organization.getNameLastUpdate() > 0) {
            orgName = organization.getName();
        }

        long awsWafCount = ConfigsDao.instance.count(Filters.and(
                Filters.eq(Config.CloudflareWafConfig.ACCOUNT_ID, sessionAccId),
                Filters.eq(Config.CloudflareWafConfig._CONFIG_ID, Config.ConfigType.AWS_WAF.name())
        ));

        long cloudflareWafCount = ConfigsDao.instance.count(Filters.and(
                Filters.eq(Config.CloudflareWafConfig.ACCOUNT_ID, sessionAccId),
                Filters.eq(Config.CloudflareWafConfig._CONFIG_ID, Config.ConfigType.CLOUDFLARE_WAF.name())
        ));

        userDetails.append("accounts", accounts)
                .append("username",username)
                .append("userFullName", userActualName)
                .append("avatar", "dummy")
                .append("activeAccount", sessionAccId)
                .append("dashboardMode", DashboardMode.getDashboardMode())
                .append("isSaas","true".equals(System.getenv("IS_SAAS")))
                .append("users", UsersDao.instance.getAllUsersInfoForTheAccount(Context.accountId.get()))
                .append("cloudType", Utils.getCloudType())
                .append("accountName", accountName)
                .append("aktoUIMode", userFromDB.getAktoUIMode().name())
                .append("jiraIntegrated", jiraIntegrated)
                .append("azureBoardsIntegrated", azureBoardsIntegrated)
                .append("servicenowIntegrated", servicenowIntegrated)
                .append("devrevIntegrated", devrevIntegrated)
                .append("userRole", userRole.toString().toUpperCase())
                .append("currentTimeZone", timeZone)
                .append("organizationName", orgName)
                .append("isAwsWafIntegrated", awsWafCount != 0)
                .append("isCloudflareWafIntegrated", cloudflareWafCount != 0);

        if (DashboardMode.isOnPremDeployment()) {
            userDetails.append("userHash", Intercom.getUserHash(user.getLogin()));
        }

        // only external API calls have non-null "utility"
        if (DashboardMode.isMetered() &&  utility == null) {
            if(organization == null){
                logger.debugAndAddToDb("Org not found for user: " + username + " acc: " + sessionAccId + ", creating it now!", LoggerMaker.LogDb.DASHBOARD);
                InitializerListener.createOrg(sessionAccId);
                organization = OrganizationsDao.instance.findOne(
                        Filters.in(Organization.ACCOUNTS, sessionAccId)
                );
            }
            String organizationId = organization.getId();

            HashMap<String, FeatureAccess> initialFeatureWiseAllowed = organization.getFeatureWiseAllowed();
            if(initialFeatureWiseAllowed == null) {
                initialFeatureWiseAllowed = new HashMap<>();
            }

            boolean isOverage = false;
            HashMap<String, FeatureAccess> featureWiseAllowed = new HashMap<>();
            int gracePeriod = organization.getGracePeriod();
            try {

                organization = InitializerListener.fetchAndSaveFeatureWiseAllowed(organization);
                gracePeriod = organization.getGracePeriod();
                featureWiseAllowed = organization.getFeatureWiseAllowed();
                if (featureWiseAllowed == null) {
                    featureWiseAllowed = new HashMap<>();
                }

                isOverage = OrganizationUtils.isOverage(featureWiseAllowed);
            } catch (Exception e) {
                logger.errorAndAddToDb(e,"Customer not found in stigg. User: " + username + " org: " + organizationId + " acc: " + accountIdInt, LoggerMaker.LogDb.DASHBOARD);
            }

            userDetails.append("organizationId", organizationId);
            userDetails.append("stiggIsOverage", isOverage);
            BasicDBObject stiggFeatureWiseAllowed = new BasicDBObject();
            for (String key : featureWiseAllowed.keySet()) {
                FeatureAccess featureAccess = featureWiseAllowed.get(key);
                featureAccess.setGracePeriod(gracePeriod);
                stiggFeatureWiseAllowed.append(key, new BasicDBObject()
                        .append(FeatureAccess.IS_GRANTED, featureAccess.getIsGranted())
                        .append(FeatureAccess.USAGE_LIMIT, featureAccess.getUsageLimit())
                        .append(FeatureAccess.USAGE, featureAccess.getUsage())
                        .append(FeatureAccess.OVERAGE_FIRST_DETECTED, featureAccess.getOverageFirstDetected())
                        .append(FeatureAccess.IS_OVERAGE_AFTER_GRACE, featureAccess.checkInvalidAccess()));
            }

            boolean dataIngestionPaused = UsageMetricUtils.checkActiveEndpointOverage(sessionAccId);
            boolean testRunsPaused = UsageMetricUtils.checkTestRunsOverage(sessionAccId);
            userDetails.append("usagePaused", new BasicDBObject()
                    .append("dataIngestion", dataIngestionPaused)
                    .append("testRuns", testRunsPaused));

            userDetails.append("stiggFeatureWiseAllowed", stiggFeatureWiseAllowed);

            userDetails.append("stiggCustomerId", organizationId);
            userDetails.append("stiggCustomerToken", OrganizationUtils.fetchSignature(organizationId, organization.getAdminEmail()));
            userDetails.append("stiggClientKey", OrganizationUtils.fetchClientKey(organizationId, organization.getAdminEmail()));
            userDetails.append("expired", organization.checkExpirationWithAktoSync());
            userDetails.append("hotjarSiteId", organization.getHotjarSiteId());
            // Note: planType will be resolved later in the method with fallback logic
            userDetails.append("planType", organization.getplanType());
            userDetails.append("trialMsg", organization.gettrialMsg());
            userDetails.append("protectionTrialMsg", organization.getprotectionTrialMsg());
            userDetails.append("agentTrialMsg", organization.getagentTrialMsg());


                // Check if plan type is null, empty, or not in allowed list
                String planType = organization.getplanType();
                
                // Enhanced fallback logic with caching to handle background thread race conditions
                if (planType == null || planType.isEmpty() || "planType".equals(planType)) {
                    logger.debugAndAddToDb("PlanType not found in organization, attempting fallback resolution for org: " + organization.getId());
                    
                    String userDomain = null;
                    if (organization.getAdminEmail() != null && organization.getAdminEmail().contains("@")) {
                        userDomain = organization.getAdminEmail().split("@")[1].toLowerCase();
                        
                        // Try cache again to avoid missing check in background thread
                        OrganizationInfo cachedOrgInfo = OrganizationCache.getOrganizationInfoByDomain(userDomain);
                        if (cachedOrgInfo != null && cachedOrgInfo.getPlanType() != null && 
                            !cachedOrgInfo.getPlanType().isEmpty() && !"planType".equals(cachedOrgInfo.getPlanType())) {
                            planType = cachedOrgInfo.getPlanType();
                            logger.infoAndAddToDb("Retrieved planType from cache recheck: " + planType + " for domain: " + userDomain);
                        }
                    }
                }
                
                // Update userDetails with resolved planType (may be different from organization.getplanType())
                userDetails.replace("planType", planType);
                
                boolean isInvalidPlanType = planType == null || planType.isEmpty() ||
                        !AlertUtils.isValidPlanType(planType);

                if (isInvalidPlanType) {

                    // Send Slack alert for blocked user only if organization is not whitelisted
                    boolean isWhitelistedOrg = user.getLogin() != null && (user.getLogin().contains("@akto.io")||
                            user.getLogin().contains("@lab.morganstanley.com") || user.getLogin().contains("@blinkrx.com") || user.getLogin().contains("@testmuai.com")
                            || user.getLogin().contains("@aktosecurity.com") || user.getLogin().contains("@razorpay.com") );

                    if (!isWhitelistedOrg) {
                        logger.infoAndAddToDb("Blocking this user " + user.getLogin() + " to access dashboard as invalid plantype '" + planType + "' found for org " + organizationId);

                        // Check if we should send alert
                        if (AlertUtils.shouldSendAlert(user.getLogin(), organizationId)) {
                            try {
                                SlackAlerts userBlockedAlert = new UserBlockedNoPlanAlert(user.getLogin(), organizationId, planType);
                                SlackSender.sendAlert(sessionAccId, userBlockedAlert, null, true);
                                logger.infoAndAddToDb("Sent Slack alert for user blocked due to invalid plan type '" + planType + "': " + user.getLogin());
                            } catch (Exception e) {
                                logger.errorAndAddToDb(e, "Failed to send Slack alert for blocked user: " + user.getLogin());
                            }
                        } else {
                            logger.infoAndAddToDb("Skipped duplicate Slack alert for user " + user.getLogin() + " (cooldown period active)");
                        }
                    } else {
                        logger.infoAndAddToDb("Skipped Slack alert for whitelisted organization user: " + user.getLogin());
                    }

            }
        }

        if (versions.length > 2) {
            if (versions[2].contains("akto-release-version")) {
                userDetails.append("releaseVersion", "akto-release-version");
            } else {
                userDetails.append("releaseVersion", versions[2]);
            }
        }

        for (String k: userDetails.keySet()) {
            request.setAttribute(k, userDetails.get(k));
        }

        return;
    }

    private String type;
    private String username;
    private String avatar;
    private Collection<UserAccountEntry> accounts;
    private UserAccountEntry activeAccount;
    private BasicDBList users;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Collection<UserAccountEntry> getAccounts() {
        return accounts;
    }

    public void setAccounts(Collection<UserAccountEntry> userAccountEntries) {
        this.accounts = userAccountEntries;
    }

    public UserAccountEntry getActiveAccount() {
        return activeAccount;
    }

    public void setActiveAccount(UserAccountEntry userAccountEntry) {
        this.activeAccount = userAccountEntry;
    }

    public int getAccountId() {
        return accountId;
    }

    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }

    public BasicDBList getUsers() {
        return users;
    }

    private BasicDBObject subscription;

    public BasicDBObject getSubscription() {
        return subscription;
    }

    public void setSubscription(BasicDBObject subscription) {
        this.subscription = subscription;
    }

    public String saveSubscription() {
        User user = getSUser();
        UsersDao.instance.insertPushSubscription(user.getLogin(), subscription);
        subscription = new BasicDBObject("complete", true);
        return SUCCESS.toUpperCase();
    }
}
