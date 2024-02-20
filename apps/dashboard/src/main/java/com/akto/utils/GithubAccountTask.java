package com.akto.utils;

import com.akto.dao.AccountsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.ByteArrayWrapper;
import com.akto.util.AccountTask;
import com.akto.util.Pair;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

import static com.akto.listener.InitializerListener.loadTemplateFilesFromDirectory;

public class GithubAccountTask {

    private static final Logger logger = LoggerFactory.getLogger(AccountTask.class);
    public static final GithubAccountTask instance = new GithubAccountTask();

    public void executeTask(Consumer<Pair<Account, ByteArrayWrapper>> consumeAccount, String taskName) {

        Bson activeFilter = Filters.or(
                Filters.exists(Account.INACTIVE_STR, false),
                Filters.eq(Account.INACTIVE_STR, false)
        );

        GithubSync githubSync = new GithubSync();
        byte[] repoZip = githubSync.syncRepo("akto-api-security/tests-library", "master");
        if(repoZip == null) {
            logger.info("Failed to load test templates from github, trying to load from local directory");
            repoZip = loadTemplateFilesFromDirectory();
            if(repoZip == null) {
                logger.error("Failed to load test templates from github or local directory");
                return;
            } else {
                logger.info("Loaded test templates from local directory");
            }
        } else {
            logger.info("Loaded test templates from github");
        }

        ByteArrayWrapper baw = new ByteArrayWrapper(repoZip);

        List<Account> activeAccounts = AccountsDao.instance.findAll(activeFilter);
        for(Account account: activeAccounts) {
            try {
                Context.accountId.set(account.getId());
                consumeAccount.accept(new Pair<>(account, baw));
            } catch (Exception e) {
                String msgString = String.format("Error in executing task %s for account %d", taskName, account.getId());
                logger.error(msgString, e);
            }
        }
    }
}
