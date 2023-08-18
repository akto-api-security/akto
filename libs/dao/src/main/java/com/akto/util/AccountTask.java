package com.akto.util;

import java.util.List;
import java.util.function.Consumer;

import com.akto.dao.AccountsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.mongodb.client.model.Filters;

import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccountTask {
    private static final Logger logger = LoggerFactory.getLogger(AccountTask.class);
    public static final AccountTask instance = new AccountTask();

    public void executeTask(Consumer<Account> consumeAccount, String taskName) {

        Bson activeFilter = Filters.or(
                Filters.exists(Account.INACTIVE_STR, false),
                Filters.eq(Account.INACTIVE_STR, false)
        );

        List<Account> activeAccounts = AccountsDao.instance.findAll(activeFilter);
        for(Account account: activeAccounts) {
            try {
                Context.accountId.set(account.getId());
                consumeAccount.accept(account);
            } catch (Exception e) {
                String msgString = String.format("Error in executing task %s for account %d", taskName, account.getId());
                logger.error(msgString, e);
            }
        }

    }
}
