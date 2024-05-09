package com.akto.merging;

import com.akto.dao.AccountsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.mongodb.MongoCommandException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;

public class Lock {

    public static boolean acquireLock(int accountId) {
        int ts = Context.now();
        Bson updates = Updates.combine(
                Updates.set("mergingRunning", true),
                Updates.set("mergingInitiateTs", ts)
        );

        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER);

        Account account;
        Bson accountIdFilter = Filters.eq("_id", accountId);
        // either merging is not running, or not been initiated since 30 mins
        Bson mergingFilter = Filters.or(
                Filters.eq("mergingRunning", false),
                Filters.lte("mergingInitiateTs", ts - 1800)
        );
        Bson combinedFilter = Filters.and(accountIdFilter, mergingFilter);

        try {
            account = AccountsDao.instance.getMCollection().findOneAndUpdate(combinedFilter, updates, options);
            return account != null && account.getMergingRunning() && account.getMergingInitiateTs() == ts;
        } catch (MongoCommandException e) {
            mergingFilter = Filters.not(
                    Filters.exists("mergingRunning")
            );
            combinedFilter = Filters.and(accountIdFilter, mergingFilter);
            try {
                account = AccountsDao.instance.getMCollection().findOneAndUpdate(combinedFilter, updates, options);
                return account != null && account.getMergingRunning() && account.getMergingInitiateTs() == ts;
            } catch (Exception ex) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    public static void releaseLock(int accountId) {
        Bson updates = Updates.combine(
                Updates.set("mergingRunning", false)
        );

        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().upsert(false).returnDocument(ReturnDocument.AFTER);

        Bson accountIdFilter = Filters.eq("_id", accountId);
        Bson mergingFilter = Filters.or(
                Filters.eq("mergingRunning", true)
        );
        Bson combinedFilter = Filters.and(accountIdFilter, mergingFilter);

        try {
            AccountsDao.instance.getMCollection().findOneAndUpdate(combinedFilter, updates, options);
        } catch (MongoCommandException e) {
            //
        }
    }

}
