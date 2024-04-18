package com.akto.dao;

import com.akto.dto.Account;
import com.mongodb.BasicDBObject;

import java.util.List;

public class AccountsDao extends CommonContextDao<Account> {

    public static final AccountsDao instance = new AccountsDao();

    public void createIndexIfAbsent() {

        String[] fieldNames = { Account.INACTIVE_STR };
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);

    }

    @Override
    public String getCollName() {
        return "accounts";
    }

    @Override
    public Class<Account> getClassT() {
        return Account.class;
    }

    public List<Account> getAllAccounts() {
        return findAll(new BasicDBObject());
    }
}
