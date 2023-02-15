package com.akto.action;


import com.akto.dao.AccountsDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.akto.utils.DashboardMode;
import com.akto.utils.Intercom;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import org.bson.internal.Base64;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.mongodb.client.model.Filters.*;

public class ProfileAction extends UserAction {

    private int accountId;

    @Override
    public String execute() {

        return SUCCESS.toUpperCase();
    }

    public static void executeMeta1(User user, HttpServletRequest request) {
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
            System.out.println("setting session: " + sessionAccId);
            request.getSession().setAttribute("accountId", sessionAccId);
            Context.accountId.set(sessionAccId);
        }

        BasicDBList listDashboards = new BasicDBList();

        userDetails.append("accounts", accounts)
                .append("username",user.getName())
                .append("avatar", "dummy")
                .append("activeAccount", sessionAccId)
                .append("dashboardMode", DashboardMode.getDashboardMode())
                .append("userHash", Intercom.getUserHash(user.getLogin()))
                .append("users", UsersDao.instance.getAllUsersInfoForTheAccount(Context.accountId.get()));

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
