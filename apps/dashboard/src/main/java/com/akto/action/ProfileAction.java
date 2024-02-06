package com.akto.action;


import com.akto.billing.UsageMetricUtils;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.AccountsDao;
import com.akto.dao.UsersDao;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.AccountSettings;
import com.akto.dto.User;
import com.akto.dto.UserAccountEntry;
import com.akto.dto.ApiToken.Utility;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.billing.Organization;
import com.akto.listener.InitializerListener;
import com.akto.log.LoggerMaker;
import com.akto.util.Constants;
import com.akto.util.EmailAccountName;
import com.akto.util.DashboardMode;
import com.akto.utils.billing.OrganizationUtils;
import com.akto.utils.cloud.Utils;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import io.micrometer.core.instrument.util.StringUtils;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.mongodb.client.model.Filters.in;

public class ProfileAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ProfileAction.class);

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
            request.getSession().setAttribute("accountId", sessionAccId);
            Context.accountId.set(sessionAccId);
        }

        BasicDBList listDashboards = new BasicDBList();


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
        String dashboardVersion = accountSettings.getDashboardVersion();
        String[] versions = dashboardVersion.split(" - ");
        User userFromDB = UsersDao.instance.findOne(Filters.eq(Constants.ID, user.getId()));

        userDetails.append("accounts", accounts)
                .append("username",username)
                .append("avatar", "dummy")
                .append("activeAccount", sessionAccId)
                .append("dashboardMode", DashboardMode.getDashboardMode())
                .append("isSaas","true".equals(System.getenv("IS_SAAS")))
                .append("users", UsersDao.instance.getAllUsersInfoForTheAccount(Context.accountId.get()))
                .append("cloudType", Utils.getCloudType())
                .append("accountName", accountName)
                .append("aktoUIMode", userFromDB.getAktoUIMode().name());

        // only external API calls have non-null "utility"
        if (DashboardMode.isMetered() &&  utility == null) {
            Organization organization = OrganizationsDao.instance.findOne(
                    Filters.in(Organization.ACCOUNTS, sessionAccId)
            ); 
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
                if (organization.getFeatureWiseAllowed() != null) {
                    featureWiseAllowed = organization.getFeatureWiseAllowed();
                }

                isOverage = OrganizationUtils.isOverage(featureWiseAllowed);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e,"Customer not found in stigg. User: " + username + " org: " + organizationId + " acc: " + accountIdInt, LoggerMaker.LogDb.DASHBOARD);
            }

            userDetails.append("organizationId", organizationId);
            userDetails.append("organizationName", organization.getName());
            userDetails.append("stiggIsOverage", isOverage);
            BasicDBObject stiggFeatureWiseAllowed = new BasicDBObject();
            for (Map.Entry<String, FeatureAccess> entry : featureWiseAllowed.entrySet()) {
                stiggFeatureWiseAllowed.append(entry.getKey(), new BasicDBObject()
                        .append(FeatureAccess.IS_GRANTED, entry.getValue().getIsGranted())
                        .append(FeatureAccess.IS_OVERAGE_AFTER_GRACE, entry.getValue().checkOverageAfterGrace(gracePeriod)));
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
