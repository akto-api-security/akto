package com.akto.action;


import com.akto.dao.AccountSettingsDao;
import com.akto.dao.AccountsDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.AccountSettings;
import com.akto.dto.User;
import com.akto.dto.UserAccountEntry;
import com.akto.util.EmailAccountName;
import com.akto.utils.DashboardMode;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.mongodb.client.model.Filters.in;

public class ProfileAction extends UserAction {

    private int accountId;

    @Override
    public String execute() {

        return SUCCESS.toUpperCase();
    }

    public static void executeMeta1(User user, HttpServletRequest request, HttpServletResponse response) {
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
        String username = user.getName();

        EmailAccountName emailAccountName = new EmailAccountName(username); // username is the email id of the current user
        String accountName = emailAccountName.getAccountName();

        userDetails.append("accounts", accounts)
                .append("username",username)
                .append("avatar", "dummy")
                .append("activeAccount", sessionAccId)
                .append("dashboardMode", DashboardMode.getDashboardMode())
                .append("isSaas","true".equals(System.getenv("IS_SAAS")))
                .append("users", UsersDao.instance.getAllUsersInfoForTheAccount(Context.accountId.get()))
                .append("accountName", accountName);;

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
