package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import java.util.List;

public class AccountsDao extends CommonContextDao<Account> {

    public static final AccountsDao instance = new AccountsDao();

    public void createIndexIfAbsent() {

        String[] fieldNames = { Account.INACTIVE_STR };
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);

    }

    public void updateLastActiveAccount(int accountId){
        instance.updateOneNoUpsert(
            Filters.eq(Constants.ID, accountId),
            Updates.set(Account.LAST_ACTIVE_TS, Context.now())
        );
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
